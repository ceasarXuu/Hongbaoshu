package com.xuyutech.hongbaoshu.reader

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xuyutech.hongbaoshu.audio.AudioManager
import com.xuyutech.hongbaoshu.core.AppLogger
import com.xuyutech.hongbaoshu.data.Chapter
import com.xuyutech.hongbaoshu.data.ContentLoader
import com.xuyutech.hongbaoshu.storage.ProgressStore
import com.xuyutech.hongbaoshu.storage.ProgressState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    val narrationEnabled: Boolean
        get() = _state.value?.narrationEnabled ?: false

    private var sleepTimerJob: Job? = null
    private var pendingNarrationRestartAfterSentenceUpdate: Boolean = false

    private val paginationController = ReaderPaginationController(
        packId = packId,
        pageCacheStore = pageCacheStore,
        pageEngine = pageEngine,
        scope = viewModelScope,
        bookProvider = { _state.value?.book }
    )
    private val narrationController = ReaderNarrationController(
        audioManager = audioManager,
        pageEngine = pageEngine,
        scope = viewModelScope,
        stateProvider = { _state.value },
        setState = { _state.value = it },
        postState = { _state.postValue(it) },
        persistState = { chapterIndex, pageIndex, sentenceId ->
            persistState(chapterIndex = chapterIndex, pageIndex = pageIndex, narrationSentenceId = sentenceId)
        },
        getCachedPages = { chapterId -> getCachedPages(chapterId) },
        loadCachedPagesFromDisk = { chapter, fontSizeLevel ->
            loadCachedPagesFromDisk(chapter, fontSizeLevel)
        },
        updateCurrentPageSentences = { sentenceIds -> updateCurrentPageSentences(sentenceIds) },
        stopNarration = { toggleNarration(false) },
        enableNarration = { toggleNarration(true) },
        showToast = { message -> showToast(message) }
    )
    private val pageNavigationController = ReaderPageNavigationController(
        audioManager = audioManager,
        scope = viewModelScope,
        stateProvider = { _state.value },
        setState = { _state.value = it },
        postState = { _state.postValue(it) },
        persistPageState = { chapterIndex, pageIndex ->
            persistState(chapterIndex = chapterIndex, pageIndex = pageIndex)
        },
        narrationEnabledProvider = { narrationEnabled },
        setManualPageTurn = { narrationController.setManualPageTurn(it) }
    )

    val isPrecomputing: Boolean
        get() = paginationController.isPrecomputing

    init {
        load()
        narrationController.setupCompletionCallback()
    }

    private fun loadCachedPagesFromDisk(chapter: Chapter, fontSizeLevel: Int): List<Page>? {
        return paginationController.loadCachedPagesFromDisk(chapter, fontSizeLevel)
    }

    
    fun clearPlayNextSentence() {
        _state.value = _state.value?.copy(needPlayNextSentence = false, lastPlayedSentenceId = null)
    }

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
        narrationController.onSentenceIdsUpdated(plan)
    }
    
    fun getCachedPages(chapterId: String): List<Page>? {
        val fontSizeLevel = _state.value?.fontSizeLevel ?: FONT_SIZE_DEFAULT
        return paginationController.getCachedPages(chapterId, fontSizeLevel)
    }

    fun getCachedPages(chapterId: String, fontSizeLevel: Int): List<Page>? {
        return paginationController.getCachedPages(chapterId, fontSizeLevel)
    }

    fun cachePages(chapterId: String, pages: List<Page>) {
        val fontSizeLevel = _state.value?.fontSizeLevel ?: FONT_SIZE_DEFAULT
        paginationController.cachePages(chapterId, fontSizeLevel, pages)
    }

    fun cachePages(chapterId: String, fontSizeLevel: Int, pages: List<Page>) {
        paginationController.cachePages(chapterId, fontSizeLevel, pages)
    }

    fun updateScreenSize(widthPx: Int, heightPx: Int) {
        paginationController.updateScreenSize(widthPx, heightPx)
    }
    
    /**
     * 更新配置哈希（已废弃，使用 updateScreenSize 替代）
     */
    @Deprecated("Use updateScreenSize instead")
    fun updateConfigHash(hash: Int) {
        // 保留兼容性
    }
    
    fun computeCurrentChapter(
        textMeasurer: androidx.compose.ui.text.TextMeasurer,
        buildConfig: (Int) -> PageConfig,
        fontSizeLevel: Int
    ) {
        val currentChapterIndex = _state.value?.currentChapterIndex ?: return
        paginationController.computeCurrentChapter(
            currentChapterIndex = currentChapterIndex,
            textMeasurer = textMeasurer,
            buildConfig = buildConfig,
            fontSizeLevel = fontSizeLevel
        )
    }

    fun computeRemainingChapters(
        textMeasurer: androidx.compose.ui.text.TextMeasurer,
        buildConfig: (Int) -> PageConfig,
        fontSizeLevel: Int
    ) {
        paginationController.computeRemainingChapters(textMeasurer, buildConfig, fontSizeLevel)
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
        val currentChapterIndex = _state.value?.currentChapterIndex ?: return
        paginationController.computeAllChapters(
            currentChapterIndex = currentChapterIndex,
            textMeasurer = textMeasurer,
            buildConfig = buildConfig,
            fontSizeLevel = fontSizeLevel
        )
    }
    
    /**
     * 启动后台预计算其他字号的分页
     */
    fun startPrecompute(
        textMeasurer: androidx.compose.ui.text.TextMeasurer,
        buildConfig: (Int) -> PageConfig
    ) {
        _state.value?.book ?: return
        val currentChapterIndex = _state.value?.currentChapterIndex ?: return
        val currentFontSize = _state.value?.fontSizeLevel ?: FONT_SIZE_DEFAULT
        paginationController.startPrecompute(
            currentChapterIndex = currentChapterIndex,
            currentFontSize = currentFontSize,
            textMeasurer = textMeasurer,
            buildConfig = buildConfig
        )
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

        pageNavigationController.cancelDelayedPlay()
        narrationController.resetChapterSwitchTracking()

        val shouldRestartNarration = current.narrationEnabled
        if (audioManager.state.value.narrationSentenceId != null) {
            narrationController.setManualPageTurn(shouldRestartNarration)
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
        narrationController.playSentence(sentenceId, overrideSentenceIds = pageSentenceIds)
    }

    fun playSentence(sentenceId: String) {
        narrationController.playSentence(sentenceId)
    }

    fun retryLastSentence() {
        val target = _state.value?.lastPlayedSentenceId ?: return
        if (!narrationEnabled) {
            toggleNarration(true)
        }
        playSentence(target)
    }

    fun playNextSentenceManual() {
        narrationController.playNextSentenceManual()
    }

    fun playPreviousSentenceManual() {
        narrationController.playPreviousSentenceManual()
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
        narrationController.setManualPageTurn(value)
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
        pageNavigationController.updatePage(delta, currentChapterPageCount, prevChapterPageCount)
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
        pageNavigationController.updatePageSilent(delta, currentChapterPageCount, prevChapterPageCount)
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
