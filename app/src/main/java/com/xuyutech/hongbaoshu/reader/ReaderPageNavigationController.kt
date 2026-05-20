package com.xuyutech.hongbaoshu.reader

import com.xuyutech.hongbaoshu.audio.AudioManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal class ReaderPageNavigationController(
    private val audioManager: AudioManager,
    private val scope: CoroutineScope,
    private val stateProvider: () -> ReaderState?,
    private val setState: (ReaderState) -> Unit,
    private val postState: (ReaderState) -> Unit,
    private val persistPageState: (chapterIndex: Int?, pageIndex: Int?) -> Unit,
    private val narrationEnabledProvider: () -> Boolean,
    private val setManualPageTurn: (Boolean) -> Unit
) {
    private var delayedPlayJob: Job? = null

    fun cancelDelayedPlay() {
        delayedPlayJob?.cancel()
    }

    fun updatePage(delta: Int, currentChapterPageCount: Int, prevChapterPageCount: Int = 0) {
        val current = stateProvider() ?: return
        val book = current.book ?: return
        val newIndex = current.pageIndex + delta
        var pageChanged = false

        when {
            newIndex >= currentChapterPageCount && current.currentChapterIndex < book.chapters.lastIndex -> {
                val nextChapter = current.currentChapterIndex + 1
                audioManager.playFlip()
                setState(current.copy(currentChapterIndex = nextChapter, pageIndex = 0))
                persistPageState(nextChapter, 0)
                pageChanged = true
            }
            newIndex < 0 && current.currentChapterIndex > 0 -> {
                val prevChapter = current.currentChapterIndex - 1
                val prevPage = maxOf(0, prevChapterPageCount - 1)
                audioManager.playFlip()
                setState(current.copy(currentChapterIndex = prevChapter, pageIndex = prevPage))
                persistPageState(prevChapter, prevPage)
                pageChanged = true
            }
            newIndex in 0 until currentChapterPageCount && newIndex != current.pageIndex -> {
                audioManager.playFlip()
                setState(current.copy(pageIndex = newIndex))
                persistPageState(null, newIndex)
                pageChanged = true
            }
        }

        if (pageChanged && narrationEnabledProvider()) {
            delayedPlayJob?.cancel()
            setManualPageTurn(true)
            audioManager.stopSentence()
            delayedPlayJob = scope.launch {
                delay(500L)
                stateProvider()?.let { setState(it.copy(needPlayFirstSentence = true)) }
                setManualPageTurn(false)
            }
        }
    }

    fun updatePageSilent(delta: Int, currentChapterPageCount: Int, prevChapterPageCount: Int = 0) {
        val current = stateProvider() ?: return
        val book = current.book ?: return
        val newIndex = current.pageIndex + delta

        when {
            newIndex >= currentChapterPageCount && current.currentChapterIndex < book.chapters.lastIndex -> {
                val nextChapter = current.currentChapterIndex + 1
                postState(current.copy(currentChapterIndex = nextChapter, pageIndex = 0))
                persistPageState(nextChapter, 0)
            }
            newIndex < 0 && current.currentChapterIndex > 0 -> {
                val prevChapter = current.currentChapterIndex - 1
                val prevPage = maxOf(0, prevChapterPageCount - 1)
                postState(current.copy(currentChapterIndex = prevChapter, pageIndex = prevPage))
                persistPageState(prevChapter, prevPage)
            }
            newIndex in 0 until currentChapterPageCount -> {
                postState(current.copy(pageIndex = newIndex))
                persistPageState(null, newIndex)
            }
        }
    }
}
