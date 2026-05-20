package com.xuyutech.hongbaoshu.reader

import com.xuyutech.hongbaoshu.audio.AudioManager
import com.xuyutech.hongbaoshu.data.Chapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal class ReaderNarrationController(
    private val audioManager: AudioManager,
    private val pageEngine: PageEngine,
    private val scope: CoroutineScope,
    private val stateProvider: () -> ReaderState?,
    private val setState: (ReaderState) -> Unit,
    private val postState: (ReaderState) -> Unit,
    private val persistState: (chapterIndex: Int?, pageIndex: Int?, sentenceId: String?) -> Unit,
    private val getCachedPages: (String) -> List<Page>?,
    private val loadCachedPagesFromDisk: (Chapter, Int) -> List<Page>?,
    private val updateCurrentPageSentences: (List<String>) -> Unit,
    private val stopNarration: () -> Unit,
    private val enableNarration: () -> Unit,
    private val showToast: (String) -> Unit
) {
    private var autoTurnPlayJob: Job? = null
    private var isManualPageTurn = false
    private var lastCompletedSentenceId: String? = null
    private var missingAudioAutoAdvanceInProgress: Boolean = false
    private var lastMissingAudioSentenceId: String? = null
    private var missingAudioFailureStreak: Int = 0

    val narrationEnabled: Boolean
        get() = stateProvider()?.narrationEnabled ?: false

    fun setupCompletionCallback() {
        audioManager.setNarrationCompletionCallback { completedSentenceId ->
            if (narrationEnabled && !isManualPageTurn) {
                scope.launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
                    handlePageCompleted(completedSentenceId)
                }
            }
        }
    }

    fun resetChapterSwitchTracking() {
        lastCompletedSentenceId = null
    }

    fun setManualPageTurn(value: Boolean) {
        isManualPageTurn = value
    }

    fun onSentenceIdsUpdated(plan: SentenceIdsUpdatePlan) {
        if (plan.clearManualPageTurn) {
            isManualPageTurn = false
        }
    }

    fun playSentence(sentenceId: String, overrideSentenceIds: List<String>? = null) {
        val current = stateProvider()
        if (current != null) {
            setState(current.copy(lastPlayedSentenceId = sentenceId))
        }

        val request = resolveNarrationPlayRequest(
            requestedSentenceId = sentenceId,
            currentPageSentenceIds = stateProvider()?.currentPageSentenceIds.orEmpty(),
            overrideSentenceIds = overrideSentenceIds
        )

        if (audioManager.playNarrationList(request.sentenceIds, request.startIndex)) {
            lastMissingAudioSentenceId = null
            missingAudioFailureStreak = 0
            persistState(null, null, sentenceId)
            return
        }

        handleMissingAudio(sentenceId, request)
    }

    fun playNextSentenceManual() {
        val current = stateProvider() ?: return
        val currentId = audioManager.state.value.narrationSentenceId ?: current.lastPlayedSentenceId
        if (currentId == null) {
            val first = current.currentPageSentenceIds.firstOrNull() ?: return
            if (!narrationEnabled) enableNarration()
            playSentence(first)
            return
        }

        val sentences = current.currentPageSentenceIds
        val idx = sentences.indexOf(currentId)
        if (idx >= 0 && idx + 1 < sentences.size) {
            if (!narrationEnabled) enableNarration()
            playSentence(sentences[idx + 1])
        } else {
            handlePageCompleted(currentId)
        }
    }

    fun playPreviousSentenceManual() {
        val current = stateProvider() ?: return
        val currentId = audioManager.state.value.narrationSentenceId
            ?: current.lastPlayedSentenceId
            ?: return
        val sentences = current.currentPageSentenceIds
        val idx = sentences.indexOf(currentId)
        if (idx > 0) {
            if (!narrationEnabled) enableNarration()
            playSentence(sentences[idx - 1])
        }
    }

    private fun handlePageCompleted(completedSentenceId: String) {
        lastCompletedSentenceId = completedSentenceId
        val current = stateProvider() ?: return
        val book = current.book ?: return
        val currentChapter = book.chapters.getOrNull(current.currentChapterIndex) ?: return
        val currentPages = getCachedPages(currentChapter.id)
            ?: loadCachedPagesFromDisk(currentChapter, current.fontSizeLevel)
            ?: return
        val pageCount = currentPages.size

        if (current.pageIndex + 1 < pageCount) {
            autoTurnToNextPage(pageCount)
        } else if (current.narrationStopAtChapterEnd) {
            stopNarration()
        } else if (current.currentChapterIndex + 1 < book.chapters.size) {
            autoTurnToNextPage(pageCount)
        } else {
            stopNarration()
        }
    }

    private fun autoTurnToNextPage(currentPageCount: Int) {
        val current = stateProvider() ?: return
        val book = current.book ?: return
        val newIndex = current.pageIndex + 1
        val target = when {
            newIndex >= currentPageCount && current.currentChapterIndex < book.chapters.lastIndex -> {
                (current.currentChapterIndex + 1) to 0
            }
            newIndex in 0 until currentPageCount -> current.currentChapterIndex to newIndex
            else -> null
        } ?: return

        val (targetChapterIndex, targetPageIndex) = target
        postState(current.copy(currentChapterIndex = targetChapterIndex, pageIndex = targetPageIndex))
        if (targetChapterIndex != current.currentChapterIndex) {
            persistState(targetChapterIndex, targetPageIndex, null)
        } else {
            persistState(null, targetPageIndex, null)
        }
        schedulePlayFirstSentence(targetChapterIndex, targetPageIndex)
    }

    private fun schedulePlayFirstSentence(targetChapterIndex: Int, targetPageIndex: Int) {
        autoTurnPlayJob?.cancel()
        autoTurnPlayJob = scope.launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
            if (!narrationEnabled) return@launch

            val current = stateProvider() ?: return@launch
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
                    stateProvider()?.let { postState(it.copy(needPlayFirstSentence = true)) }
                    return@launch
                }
            playSentence(nextSentence)
            if (nextSentence != sentenceIds.firstOrNull()) {
                lastCompletedSentenceId = null
            }
        }
    }

    private fun handleMissingAudio(sentenceId: String, request: NarrationPlayRequest) {
        showToast("音频缺失，已跳过")
        if (lastMissingAudioSentenceId == sentenceId) {
            missingAudioFailureStreak += 1
        } else {
            lastMissingAudioSentenceId = sentenceId
            missingAudioFailureStreak = 1
        }

        if (missingAudioFailureStreak >= 3) {
            stopNarration()
            showToast("朗读资源不可用，已停止自动朗读")
            return
        }

        if (narrationEnabled && !isManualPageTurn && !missingAudioAutoAdvanceInProgress) {
            val lastId = request.sentenceIds.lastOrNull() ?: sentenceId
            missingAudioAutoAdvanceInProgress = true
            scope.launch {
                try {
                    handlePageCompleted(lastId)
                } finally {
                    missingAudioAutoAdvanceInProgress = false
                }
            }
        }
    }
}
