package com.xuyutech.hongbaoshu.reader

import android.app.Application
import androidx.compose.ui.text.TextMeasurer
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xuyutech.hongbaoshu.audio.AudioManager
import com.xuyutech.hongbaoshu.core.AppLogger
import com.xuyutech.hongbaoshu.data.Chapter
import com.xuyutech.hongbaoshu.data.ContentLoader
import com.xuyutech.hongbaoshu.storage.ProgressStore
import com.xuyutech.hongbaoshu.storage.ProgressState
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class NarrationPlayRequest(
    val sentenceIds: List<String>,
    val startIndex: Int
)

internal fun resolveNarrationPlayRequest(
    requestedSentenceId: String,
    currentPageSentenceIds: List<String>,
    overrideSentenceIds: List<String>? = null
): NarrationPlayRequest {
    val base = when {
        !overrideSentenceIds.isNullOrEmpty() -> overrideSentenceIds
        currentPageSentenceIds.isNotEmpty() -> currentPageSentenceIds
        else -> listOf(requestedSentenceId)
    }
    val sentenceIds = if (requestedSentenceId in base) base else listOf(requestedSentenceId)
    val startIndex = sentenceIds.indexOf(requestedSentenceId).coerceAtLeast(0)
    return NarrationPlayRequest(sentenceIds = sentenceIds, startIndex = startIndex)
}

internal fun pickAutoTurnSentenceToPlay(
    sentenceIds: List<String>,
    lastCompletedSentenceId: String?
): String? {
    val first = sentenceIds.firstOrNull() ?: return null
    if (first == lastCompletedSentenceId && sentenceIds.size > 1) return sentenceIds[1]
    return first
}

internal data class SentenceIdsUpdatePlan(
    val newState: ReaderState,
    val consumePendingRestart: Boolean,
    val clearManualPageTurn: Boolean
)

internal fun planSentenceIdsUpdate(
    current: ReaderState,
    sentenceIds: List<String>,
    pendingNarrationRestart: Boolean
): SentenceIdsUpdatePlan {
    if (current.currentPageSentenceIds == sentenceIds) {
        return SentenceIdsUpdatePlan(
            newState = current,
            consumePendingRestart = false,
            clearManualPageTurn = false
        )
    }

    var newState = current.copy(currentPageSentenceIds = sentenceIds)
    var consumePendingRestart = false
    var clearManualPageTurn = false

    if (pendingNarrationRestart) {
        consumePendingRestart = true
        clearManualPageTurn = true
        if (sentenceIds.isNotEmpty()) {
            newState = newState.copy(needPlayFirstSentence = true)
        }
    }

    return SentenceIdsUpdatePlan(
        newState = newState,
        consumePendingRestart = consumePendingRestart,
        clearManualPageTurn = clearManualPageTurn
    )
}

internal fun areAllChaptersCachedForFontSize(
    chapterIds: List<String>,
    fontSizeLevel: Int,
    widthPx: Int,
    heightPx: Int,
    pageCacheKeys: Set<String>
): Boolean {
    if (chapterIds.isEmpty()) return true
    return chapterIds.all { chapterId ->
        "${chapterId}_${fontSizeLevel}_${widthPx}_${heightPx}" in pageCacheKeys
    }
}

class ReaderViewModel(
    application: Application,
    private val packId: String,
    private val contentLoader: ContentLoader,
    private val progressStore: ProgressStore,
    private val audioManager: AudioManager,
    private val pageCacheStore: com.xuyutech.hongbaoshu.storage.PageCacheStore
) : AndroidViewModel(application) {

    private val _state = androidx.lifecycle.MutableLiveData(ReaderState())
    val state: androidx.lifecycle.LiveData<ReaderState> = _state

    val pageEngine = PageEngine()
    
    // 内存分页缓存：key = "chapterId_fontSizeLevel_widthPx_heightPx"
    private val pageCache: MutableMap<String, List<Page>> = mutableMapOf()
    
    // 当前配置参数（用于缓存 key 生成）
    private var currentWidthPx: Int = 0
    private var currentHeightPx: Int = 0
    
    // 预计算状态
    private var precomputeJob: Job? = null
    var isPrecomputing = false
        private set
    
    // 朗读模式是否开启（从 state 中读取）
    val narrationEnabled: Boolean
        get() = _state.value?.narrationEnabled ?: false
    // 延迟播放任务，用于取消之前的延迟
    private var delayedPlayJob: Job? = null
    private var autoTurnPlayJob: Job? = null
    private var sleepTimerJob: Job? = null
    // 是否正在手动翻页（用于防止自动播放干扰）
    private var isManualPageTurn = false
    // 上一个播放完成的句子ID(用于防止跨页重复)
    private var lastCompletedSentenceId: String? = null
    private var pendingNarrationRestartAfterSentenceUpdate: Boolean = false
    // 朗读失败自动翻页防重入与熔断，避免递归风暴导致 ANR
    private var missingAudioAutoAdvanceInProgress: Boolean = false
    private var lastMissingAudioSentenceId: String? = null
    private var missingAudioFailureStreak: Int = 0


    init {
        load()
        setupNarrationCallback()
    }
    
    /**
     * 设置朗读完成回调，在句子播放完成后自动播放下一句
     * 这个回调在 ExoPlayer 的线程中执行，不依赖 UI 层
     */
    private fun setupNarrationCallback() {
        audioManager.setNarrationCompletionCallback { completedSentenceId ->
            // 在后台线程中执行，不受 UI 生命周期影响
            if (narrationEnabled && !isManualPageTurn) {
                viewModelScope.launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
                    handlePageCompleted(completedSentenceId)
                }
            }
        }
    }
    
    /**
     * 当前页播放完成（列表播完），处理翻页
     */
    private fun handlePageCompleted(completedSentenceId: String) {
        lastCompletedSentenceId = completedSentenceId
        
        // 只有当整个列表播完（ExoPlayer 回调时机），才需要翻页
        // 此时已处于 STATE_ENDED
        
        val current = _state.value ?: return
        val book = current.book ?: return
        val currentChapter = book.chapters.getOrNull(current.currentChapterIndex) ?: return
        val currentPages = getCachedPages(currentChapter.id)
            ?: loadCachedPagesFromDisk(currentChapter, current.fontSizeLevel)
            ?: return
        val pageCount = currentPages.size
        val stopAtChapterEnd = current.narrationStopAtChapterEnd
        
        if (current.pageIndex + 1 < pageCount) {
            // 翻到下一页
            autoTurnToNextPage(pageCount)
        } else if (stopAtChapterEnd) {
            toggleNarration(false)
        } else if (current.currentChapterIndex + 1 < book.chapters.size) {
            // 翻到下一章
            autoTurnToNextPage(pageCount)
        } else {
            // 全书读完
            toggleNarration(false)
        }
    }
    
    /**
     * 自动翻到下一页并播放第一句
     */
    private fun autoTurnToNextPage(currentPageCount: Int) {
        val current = _state.value ?: return
        val book = current.book ?: return
        val newIndex = current.pageIndex + 1

        val target = when {
            newIndex >= currentPageCount && current.currentChapterIndex < book.chapters.lastIndex -> {
                (current.currentChapterIndex + 1) to 0
            }
            newIndex in 0 until currentPageCount -> {
                current.currentChapterIndex to newIndex
            }
            else -> null
        } ?: return

        val (targetChapterIndex, targetPageIndex) = target
        _state.postValue(current.copy(currentChapterIndex = targetChapterIndex, pageIndex = targetPageIndex))
        if (targetChapterIndex != current.currentChapterIndex) {
            persistState(chapterIndex = targetChapterIndex, pageIndex = targetPageIndex)
        } else {
            persistState(pageIndex = targetPageIndex)
        }

        schedulePlayFirstSentence(targetChapterIndex = targetChapterIndex, targetPageIndex = targetPageIndex)
    }
    
    /**
     * 延迟播放新页面第一句（等待页面数据更新）
     */
    private fun schedulePlayFirstSentence(targetChapterIndex: Int, targetPageIndex: Int) {
        autoTurnPlayJob?.cancel()
        autoTurnPlayJob = viewModelScope.launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
            if (!narrationEnabled) return@launch

            val current = _state.value ?: return@launch
            val book = current.book ?: return@launch
            val chapter = book.chapters.getOrNull(targetChapterIndex) ?: return@launch

            val pages = getCachedPages(chapter.id) ?: loadCachedPagesFromDisk(chapter, current.fontSizeLevel)
            val page = pages?.getOrNull(targetPageIndex)
            val sentenceIds = if (page == null) emptyList() else {
                pageEngine.buildSentenceRanges(chapter)
                pageEngine.getSentenceIds(page, chapter)
            }

            updateCurrentPageSentences(sentenceIds)

            val nextSentence = pickAutoTurnSentenceToPlay(sentenceIds, lastCompletedSentenceId)
                ?: run {
                    _state.postValue(_state.value?.copy(needPlayFirstSentence = true))
                    return@launch
                }
            if (nextSentence != sentenceIds.firstOrNull()) {
                playSentence(nextSentence)
                lastCompletedSentenceId = null
            } else {
                playSentence(nextSentence)
            }
        }
    }

    private fun loadCachedPagesFromDisk(chapter: Chapter, fontSizeLevel: Int): List<Page>? {
        val key = cacheKey(chapter.id, fontSizeLevel, currentWidthPx, currentHeightPx)
        val cached = pageCache[key]
        if (cached != null) return cached

        val diskKey = diskCacheKey(fontSizeLevel, currentWidthPx, currentHeightPx)
        val diskCache = pageCacheStore.load(diskKey) ?: return null
        val pages = diskCache[chapter.id] ?: return null
        pageCache[key] = pages
        pageEngine.buildSentenceRanges(chapter)
        return pages
    }

    
    /**
     * 清除播放下一句的标记
     */
    fun clearPlayNextSentence() {
        _state.value = _state.value?.copy(needPlayNextSentence = false, lastPlayedSentenceId = null)
    }
    
    /**
     * 更新当前页的句子列表（由 UI 层调用）
     */
    fun updateCurrentPageSentences(sentenceIds: List<String>) {
        val current = _state.value ?: return
        val plan = planSentenceIdsUpdate(
            current = current,
            sentenceIds = sentenceIds,
            pendingNarrationRestart = pendingNarrationRestartAfterSentenceUpdate
        )
        if (plan.newState != current) {
            _state.value = plan.newState
        }
        if (plan.consumePendingRestart) {
            pendingNarrationRestartAfterSentenceUpdate = false
        }
        if (plan.clearManualPageTurn) {
            isManualPageTurn = false
        }
    }
    
    /**
     * 生成缓存 key
     */
    private fun cacheKey(chapterId: String, fontSizeLevel: Int, widthPx: Int, heightPx: Int): String {
        return "${chapterId}_${fontSizeLevel}_${widthPx}_${heightPx}"
    }
    
    /**
     * 获取缓存的分页数据
     */
    fun getCachedPages(chapterId: String): List<Page>? {
        val fontSizeLevel = _state.value?.fontSizeLevel ?: FONT_SIZE_DEFAULT
        val key = cacheKey(chapterId, fontSizeLevel, currentWidthPx, currentHeightPx)
        return pageCache[key]
    }
    
    /**
     * 获取指定字号的缓存分页数据
     */
    fun getCachedPages(chapterId: String, fontSizeLevel: Int): List<Page>? {
        val key = cacheKey(chapterId, fontSizeLevel, currentWidthPx, currentHeightPx)
        return pageCache[key]
    }
    
    /**
     * 缓存分页数据
     */
    fun cachePages(chapterId: String, pages: List<Page>) {
        val fontSizeLevel = _state.value?.fontSizeLevel ?: FONT_SIZE_DEFAULT
        val key = cacheKey(chapterId, fontSizeLevel, currentWidthPx, currentHeightPx)
        pageCache[key] = pages
    }
    
    /**
     * 缓存指定字号的分页数据
     */
    fun cachePages(chapterId: String, fontSizeLevel: Int, pages: List<Page>) {
        val key = cacheKey(chapterId, fontSizeLevel, currentWidthPx, currentHeightPx)
        pageCache[key] = pages
    }
    
    /**
     * 更新屏幕配置（屏幕尺寸变化时清除缓存）
     */
    fun updateScreenSize(widthPx: Int, heightPx: Int) {
        if (currentWidthPx != widthPx || currentHeightPx != heightPx) {
            currentWidthPx = widthPx
            currentHeightPx = heightPx
            pageCache.clear()
        }
    }
    
    /**
     * 更新配置哈希（已废弃，使用 updateScreenSize 替代）
     */
    @Deprecated("Use updateScreenSize instead")
    fun updateConfigHash(hash: Int) {
        // 保留兼容性
    }
    
    /**
     * 生成持久化缓存 key（不含章节ID，用于整本书）
     */
    private fun diskCacheKey(fontSizeLevel: Int, widthPx: Int, heightPx: Int): String {
        return "${packId}_${fontSizeLevel}_${widthPx}_${heightPx}"
    }
    
    fun computeCurrentChapter(
        textMeasurer: androidx.compose.ui.text.TextMeasurer,
        buildConfig: (Int) -> PageConfig,
        fontSizeLevel: Int
    ) {
        val book = _state.value?.book ?: return
        val currentChapterIndex = _state.value?.currentChapterIndex ?: return
        val chapter = book.chapters.getOrNull(currentChapterIndex) ?: return

        // 检查内存缓存
        val key = cacheKey(chapter.id, fontSizeLevel, currentWidthPx, currentHeightPx)
        if (pageCache[key] != null) return

        // 检查磁盘缓存
        val diskKey = diskCacheKey(fontSizeLevel, currentWidthPx, currentHeightPx)
        val diskCache = pageCacheStore.load(diskKey)
        if (diskCache != null) {
            diskCache[chapter.id]?.let { pages ->
                pageCache[key] = pages
                pageEngine.buildSentenceRanges(chapter)
                return
            }
        }

        // 计算当前章节
        val config = buildConfig(fontSizeLevel)
        val pages = pageEngine.paginate(chapter, config, textMeasurer)
        pageCache[key] = pages
        pageEngine.buildSentenceRanges(chapter)
    }

    fun computeRemainingChapters(
        textMeasurer: androidx.compose.ui.text.TextMeasurer,
        buildConfig: (Int) -> PageConfig,
        fontSizeLevel: Int
    ) {
        val book = _state.value?.book ?: return
        
        // 检查是否全书都已缓存
        val allCached = areAllChaptersCachedForFontSize(
            chapterIds = book.chapters.map { it.id },
            fontSizeLevel = fontSizeLevel,
            widthPx = currentWidthPx,
            heightPx = currentHeightPx,
            pageCacheKeys = pageCache.keys
        )
        if (allCached) return

        // 尝试从磁盘加载缓存
        val diskKey = diskCacheKey(fontSizeLevel, currentWidthPx, currentHeightPx)
        var diskCache = pageCacheStore.load(diskKey)
        
        if (diskCache != null) {
            // 加载到内存缓存
            book.chapters.forEach { chapter ->
                val key = cacheKey(chapter.id, fontSizeLevel, currentWidthPx, currentHeightPx)
                if (pageCache[key] == null) {
                    diskCache?.get(chapter.id)?.let { pages ->
                        pageCache[key] = pages
                        pageEngine.buildSentenceRanges(chapter)
                    }
                }
            }
            // 如果磁盘缓存覆盖了所有章节，则结束
            val allCachedAfterDisk = areAllChaptersCachedForFontSize(
                chapterIds = book.chapters.map { it.id },
                fontSizeLevel = fontSizeLevel,
                widthPx = currentWidthPx,
                heightPx = currentHeightPx,
                pageCacheKeys = pageCache.keys
            )
            if (allCachedAfterDisk) return
        }
        
        // 计算剩余章节
        val config = buildConfig(fontSizeLevel)
        val diskData = (diskCache ?: emptyMap()).toMutableMap()
        var hasNewData = false
        
        book.chapters.forEach { chapter ->
            val key = cacheKey(chapter.id, fontSizeLevel, currentWidthPx, currentHeightPx)
            if (pageCache[key] == null) {
                val pages = pageEngine.paginate(chapter, config, textMeasurer)
                pageCache[key] = pages
                diskData[chapter.id] = pages
                pageEngine.buildSentenceRanges(chapter)
                hasNewData = true
            }
        }
        
        // 保存到磁盘
        if (hasNewData) {
            pageCacheStore.save(diskKey, diskData)
        }
    }

    /**
     * 计算指定字号的所有章节分页（包含全书页码）
     * 优先从磁盘缓存加载，缓存不存在时计算并保存
     */
    fun computeAllChapters(
        textMeasurer: androidx.compose.ui.text.TextMeasurer,
        buildConfig: (Int) -> PageConfig,
        fontSizeLevel: Int
    ) {
        computeCurrentChapter(textMeasurer, buildConfig, fontSizeLevel)
        computeRemainingChapters(textMeasurer, buildConfig, fontSizeLevel)
    }
    
    /**
     * 启动后台预计算其他字号的分页
     */
    fun startPrecompute(
        textMeasurer: androidx.compose.ui.text.TextMeasurer,
        buildConfig: (Int) -> PageConfig
    ) {
        val book = _state.value?.book ?: return
        val currentFontSize = _state.value?.fontSizeLevel ?: FONT_SIZE_DEFAULT
        
        precomputeJob?.cancel()
        precomputeJob = viewModelScope.launch(Dispatchers.Default) {
            isPrecomputing = true
            try {
                // 先计算当前字号的所有章节（带全书页码）
                computeAllChapters(textMeasurer, buildConfig, currentFontSize)
                
                // 预计算其他字号档位 (仅计算常用档位，避免过多计算)
                // for (fontLevel in FONT_SIZE_MIN..FONT_SIZE_MAX) {
                //    if (fontLevel == currentFontSize) continue
                //    computeAllChapters(textMeasurer, buildConfig, fontLevel)
                // }
            } finally {
                isPrecomputing = false
            }
        }
    }

    fun refresh() = load()

    private fun load() {
        AppLogger.i("ReaderViewModel", "load start: packId=$packId")
        _state.value = ReaderState(isLoading = true)
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val bookResult = contentLoader.loadBook(getApplication())
                    val saved = progressStore.progress(packId).first()
                    Pair(bookResult, saved)
                }
            }
            result.onSuccess { (bookResult, saved) ->
                AppLogger.i("ReaderViewModel", "load success: packId=$packId, title=${bookResult.book.title}, missingAudio=${bookResult.missingSentenceAudioIds.size}")
                val cappedChapterIndex =
                    saved.chapterIndex.coerceIn(0, bookResult.book.chapters.lastIndex)
                _state.value = ReaderState(
                    isLoading = false,
                    book = bookResult.book,
                    missingAudio = bookResult.missingSentenceAudioIds,
                    currentChapterIndex = cappedChapterIndex,
                    pageIndex = saved.pageIndex,  // 由 UI 层校验
                    isNightMode = saved.isNightMode,
                    hasShownMenuGuide = saved.hasShownMenuGuide
                )
                restoreAudioState(saved)
            }.onFailure { e ->
                AppLogger.e("ReaderViewModel", "load failed: packId=$packId", e)
                _state.value = ReaderState(
                    isLoading = false,
                    error = e.message ?: "加载失败"
                )
            }
        }
    }

    fun selectChapter(index: Int) {
        val current = _state.value ?: return
        val book = current.book ?: return
        val safeIndex = index.coerceIn(0, book.chapters.lastIndex)

        delayedPlayJob?.cancel()
        lastCompletedSentenceId = null

        val shouldRestartNarration = current.narrationEnabled
        if (audioManager.state.value.narrationSentenceId != null) {
            isManualPageTurn = shouldRestartNarration
            audioManager.stopSentence()
        }
        pendingNarrationRestartAfterSentenceUpdate = shouldRestartNarration

        _state.value = current.copy(
            currentChapterIndex = safeIndex,
            pageIndex = 0,
            needPlayFirstSentence = false,
            needPlayNextSentence = false,
            currentPageSentenceIds = emptyList(),
            lastPlayedSentenceId = null
        )
        persistState(chapterIndex = safeIndex, pageIndex = 0, narrationSentenceId = null)
    }

    private fun restoreAudioState(saved: ProgressState) {
        // 朗读模式默认关闭，不自动恢复朗读状态
        // narrationEnabled 保持 false，用户需要手动打开

        // 恢复朗读语速
        audioManager.setNarrationSpeed(saved.narrationSpeed)
    }

    fun saveAudioState(narrationSentenceId: String?) {
        persistState(narrationSentenceId = narrationSentenceId)
    }

    fun setNarrationSpeed(speed: Float) {
        audioManager.setNarrationSpeed(speed)
        persistState()
    }

    fun previewNarrationSpeed(speed: Float) {
        audioManager.setNarrationSpeed(speed)
    }

    fun playSentence(sentenceId: String, pageSentenceIds: List<String>) {
        updateCurrentPageSentences(pageSentenceIds)
        playSentenceInternal(sentenceId, overrideSentenceIds = pageSentenceIds)
    }

    fun playSentence(sentenceId: String) {
        playSentenceInternal(sentenceId, overrideSentenceIds = null)
    }

    private fun playSentenceInternal(sentenceId: String, overrideSentenceIds: List<String>?) {
        _state.value = _state.value?.copy(lastPlayedSentenceId = sentenceId)

        val currentPageSentenceIds = _state.value?.currentPageSentenceIds.orEmpty()
        val request = resolveNarrationPlayRequest(
            requestedSentenceId = sentenceId,
            currentPageSentenceIds = currentPageSentenceIds,
            overrideSentenceIds = overrideSentenceIds
        )

        val success = audioManager.playNarrationList(request.sentenceIds, request.startIndex)
        if (success) {
            lastMissingAudioSentenceId = null
            missingAudioFailureStreak = 0
            persistState(narrationSentenceId = sentenceId)
        } else {
            showToast("音频缺失，已跳过")
            // 若连续失败集中在同一句，触发熔断，防止递归导致卡死
            if (lastMissingAudioSentenceId == sentenceId) {
                missingAudioFailureStreak += 1
            } else {
                lastMissingAudioSentenceId = sentenceId
                missingAudioFailureStreak = 1
            }

            if (missingAudioFailureStreak >= 3) {
                toggleNarration(false)
                showToast("朗读资源不可用，已停止自动朗读")
                return
            }

            // 如果处于连续朗读状态，自动触发翻页跳过本页剩余部分
            if (narrationEnabled && !isManualPageTurn && !missingAudioAutoAdvanceInProgress) {
                val lastId = request.sentenceIds.lastOrNull() ?: sentenceId
                missingAudioAutoAdvanceInProgress = true
                viewModelScope.launch {
                    try {
                        handlePageCompleted(lastId)
                    } finally {
                        missingAudioAutoAdvanceInProgress = false
                    }
                }
            }
        }
    }

    fun retryLastSentence() {
        val target = _state.value?.lastPlayedSentenceId ?: return
        if (!narrationEnabled) {
            toggleNarration(true)
        }
        playSentence(target)
    }

    fun playNextSentenceManual() {
        val current = _state.value ?: return
        val currentId = audioManager.state.value.narrationSentenceId ?: current.lastPlayedSentenceId
        if (currentId == null) {
            val first = current.currentPageSentenceIds.firstOrNull() ?: return
            if (!narrationEnabled) toggleNarration(true)
            playSentence(first)
            return
        }
        
        // 在列表模式下，"下一句"通常由 ExoPlayer 自动处理
        // 但如果是暂停状态下的手动点击下一首，或者当前列表已结束但还没翻页
        val sentences = current.currentPageSentenceIds
        val idx = sentences.indexOf(currentId)
        if (idx >= 0 && idx + 1 < sentences.size) {
            // 切到下一句
            if (!narrationEnabled) toggleNarration(true)
            playSentence(sentences[idx + 1])
        } else {
            // 当前页已到底，尝试翻页
            handlePageCompleted(currentId)
        }
    }

    fun playPreviousSentenceManual() {
        val current = _state.value ?: return
        val currentId = audioManager.state.value.narrationSentenceId ?: current.lastPlayedSentenceId ?: return
        val sentences = current.currentPageSentenceIds
        val idx = sentences.indexOf(currentId)
        if (idx > 0) {
            if (!narrationEnabled) toggleNarration(true)
            playSentence(sentences[idx - 1])
        }
    }

    private fun showToast(message: String) {
        _state.value = _state.value?.copy(toastMessage = message)
    }

    fun clearToast() {
        _state.value = _state.value?.copy(toastMessage = null)
    }

    /**
     * 设置字体大小档位
     */
    fun setFontSize(level: Int) {
        val current = _state.value ?: return
        val safeLevel = level.coerceIn(FONT_SIZE_MIN, FONT_SIZE_MAX)
        if (current.fontSizeLevel != safeLevel) {
            // 重置页码到第一页（因为不同字号分页数量不同）
            _state.value = current.copy(fontSizeLevel = safeLevel, pageIndex = 0)
            // 不再清除缓存，因为现在按字号分别缓存
        }
    }

    fun pauseOrResumeSentence() {
        if (audioManager.state.value.narrationSentenceId != null) {
            if (audioManager.state.value.narrationPlaying) {
                audioManager.pauseSentence()
            } else {
                audioManager.resumeSentence()
            }
        }
        persistState()
    }

    fun stopSentence() {
        audioManager.stopSentence()
        persistState(narrationSentenceId = null)
    }

    /**
     * 切换朗读开关
     */
    fun toggleNarration(enabled: Boolean) {
        _state.value = _state.value?.copy(narrationEnabled = enabled)
        if (!enabled) {
            stopSentence()
            clearNarrationTimer()
            setNarrationStopAtChapterEnd(false)
        }
        // 如果开启，由 UI 层触发播放第一句
    }

    fun setNarrationStopAtChapterEnd(enabled: Boolean) {
        val current = _state.value ?: return
        _state.value = current.copy(narrationStopAtChapterEnd = enabled)
    }

    fun startNarrationTimer(minutes: Int) {
        val current = _state.value ?: return
        val safeMinutes = minutes.coerceAtLeast(1)
        _state.value = current.copy(narrationTimerMinutes = safeMinutes)
        sleepTimerJob?.cancel()
        sleepTimerJob = viewModelScope.launch {
            delay(safeMinutes * 60_000L)
            toggleNarration(false)
        }
    }

    fun clearNarrationTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        val current = _state.value ?: return
        _state.value = current.copy(narrationTimerMinutes = null)
    }

    /**
     * 切换夜间模式
     */
    fun toggleNightMode() {
        val current = _state.value ?: return
        val newMode = !current.isNightMode
        _state.value = current.copy(isNightMode = newMode)
        persistState()
    }
    
    /**
     * 暂停朗读（保持开关状态，用于返回封面时）
     */
    fun pauseNarration() {
        audioManager.stopSentence()
    }
    
    /**
     * 设置手动翻页标记
     */
    fun setManualPageTurn(value: Boolean) {
        isManualPageTurn = value
    }
    
    /**
     * 重置朗读状态（用于手动翻页后）
     */
    fun resetNarrationState() {
        // 现在由回调机制处理，无需额外操作
    }

    private fun persistState(
        chapterIndex: Int? = null,
        pageIndex: Int? = null,
        narrationSentenceId: String? = null
    ) {
        val current = _state.value ?: return
        val finalChapter = chapterIndex ?: current.currentChapterIndex
        val finalPage = pageIndex ?: current.pageIndex
        viewModelScope.launch(Dispatchers.IO) {
            progressStore.save(
                packId = packId,
                ProgressState(
                    chapterIndex = finalChapter,
                    pageIndex = finalPage,
                    narrationSentenceId = narrationSentenceId
                        ?: audioManager.state.value.narrationSentenceId,
                    narrationPosition = audioManager.state.value.narrationPosition,
                    isNightMode = current.isNightMode,
                    hasShownMenuGuide = current.hasShownMenuGuide,
                    narrationSpeed = audioManager.state.value.narrationSpeed
                )
            )
        }
    }

    /**
     * 更新页码（由 UI 层调用，传入当前章节的总页数）
     */
    fun updatePage(delta: Int, currentChapterPageCount: Int, prevChapterPageCount: Int = 0) {
        val current = _state.value ?: return
        val book = current.book ?: return
        val newIndex = current.pageIndex + delta
        var pageChanged = false
        
        when {
            // 翻到下一章
            newIndex >= currentChapterPageCount && current.currentChapterIndex < book.chapters.lastIndex -> {
                val nextChapter = current.currentChapterIndex + 1
                audioManager.playFlip()
                _state.value = current.copy(currentChapterIndex = nextChapter, pageIndex = 0)
                persistState(chapterIndex = nextChapter, pageIndex = 0)
                pageChanged = true
            }
            // 翻到上一章最后一页
            newIndex < 0 && current.currentChapterIndex > 0 -> {
                val prevChapter = current.currentChapterIndex - 1
                audioManager.playFlip()
                _state.value = current.copy(
                    currentChapterIndex = prevChapter, 
                    pageIndex = maxOf(0, prevChapterPageCount - 1)
                )
                persistState(chapterIndex = prevChapter, pageIndex = maxOf(0, prevChapterPageCount - 1))
                pageChanged = true
            }
            // 正常翻页
            newIndex in 0 until currentChapterPageCount && newIndex != current.pageIndex -> {
                audioManager.playFlip()
                _state.value = current.copy(pageIndex = newIndex)
                persistState(pageIndex = newIndex)
                pageChanged = true
            }
        }
        
        // 如果正在朗读模式且翻页成功，延迟后从新页面第一句开始播放
        if (pageChanged && narrationEnabled) {
            delayedPlayJob?.cancel()
            isManualPageTurn = true
            audioManager.stopSentence()
            delayedPlayJob = viewModelScope.launch {
                delay(500L)
                // 触发 UI 层播放第一句
                _state.value = _state.value?.copy(needPlayFirstSentence = true)
                isManualPageTurn = false
            }
        }
    }
    
    /**
     * 清除播放第一句的标记
     */
    fun clearPlayFirstSentence() {
        _state.value = _state.value?.copy(needPlayFirstSentence = false)
    }
    
    /**
     * 静默翻页（用于朗读自动翻页）
     */
    fun updatePageSilent(delta: Int, currentChapterPageCount: Int, prevChapterPageCount: Int = 0) {
        val current = _state.value ?: return
        val book = current.book ?: return
        val newIndex = current.pageIndex + delta
        
        when {
            newIndex >= currentChapterPageCount && current.currentChapterIndex < book.chapters.lastIndex -> {
                val nextChapter = current.currentChapterIndex + 1
                _state.postValue(current.copy(currentChapterIndex = nextChapter, pageIndex = 0))
                persistState(chapterIndex = nextChapter, pageIndex = 0)
            }
            newIndex < 0 && current.currentChapterIndex > 0 -> {
                val prevChapter = current.currentChapterIndex - 1
                _state.postValue(current.copy(
                    currentChapterIndex = prevChapter, 
                    pageIndex = maxOf(0, prevChapterPageCount - 1)
                ))
                persistState(chapterIndex = prevChapter, pageIndex = maxOf(0, prevChapterPageCount - 1))
            }
            newIndex in 0 until currentChapterPageCount -> {
                _state.postValue(current.copy(pageIndex = newIndex))
                persistState(pageIndex = newIndex)
            }
        }
    }
    
    /**
     * 设置页码（用于校验恢复的页码）
     */
    fun setPageIndex(index: Int) {
        val current = _state.value ?: return
        if (current.pageIndex != index) {
            _state.value = current.copy(pageIndex = index)
            persistState(pageIndex = index)
        }
    }

    fun dismissMenuGuide() {
        val current = _state.value ?: return
        // 同时更新持久化状态和会话状态
        _state.value = current.copy(
            hasShownMenuGuide = true,
            isMenuGuideDismissedInSession = true
        )
        persistState()
    }

    fun dismissMenuGuideInSession() {
        val current = _state.value ?: return
        _state.value = current.copy(isMenuGuideDismissedInSession = true)
    }


}
