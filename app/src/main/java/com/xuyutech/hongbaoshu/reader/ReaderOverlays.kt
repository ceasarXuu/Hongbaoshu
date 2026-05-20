@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.xuyutech.hongbaoshu.reader

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.xuyutech.hongbaoshu.audio.AudioState

@Composable
internal fun BoxScope.ReaderOverlays(
    state: State<ReaderState>,
    audioState: State<AudioState>,
    viewModel: ReaderViewModel,
    showNarrationPanel: MutableState<Boolean>,
    showFontSettings: MutableState<Boolean>,
    showToc: MutableState<Boolean>,
    snackbarHostState: SnackbarHostState,
    updateToolbarInteraction: () -> Unit,
    hideToolbar: () -> Unit
) {
    if (showNarrationPanel.value) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { hideToolbar() },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = MaterialTheme.shapes.large
        ) {
            NarrationPanelContent(
                audioState = audioState.value,
                narrationEnabled = state.value.narrationEnabled,
                narrationTimerMinutes = state.value.narrationTimerMinutes,
                narrationStopAtChapterEnd = state.value.narrationStopAtChapterEnd,
                onPlayPause = {
                    updateToolbarInteraction()
                    if (!state.value.narrationEnabled) {
                        viewModel.toggleNarration(true)
                    }
                    if (audioState.value.narrationSentenceId == null) {
                        viewModel.playNextSentenceManual()
                    } else {
                        viewModel.pauseOrResumeSentence()
                    }
                },
                onPrev = {
                    updateToolbarInteraction()
                    viewModel.playPreviousSentenceManual()
                },
                onNext = {
                    updateToolbarInteraction()
                    viewModel.playNextSentenceManual()
                },
                onSpeedPreview = {
                    updateToolbarInteraction()
                    viewModel.previewNarrationSpeed(it)
                },
                onSpeedCommit = {
                    updateToolbarInteraction()
                    viewModel.setNarrationSpeed(it)
                },
                onTimerStart = {
                    updateToolbarInteraction()
                    viewModel.setNarrationStopAtChapterEnd(false)
                    viewModel.startNarrationTimer(it)
                },
                onStopAtChapterEnd = {
                    updateToolbarInteraction()
                    viewModel.clearNarrationTimer()
                    viewModel.setNarrationStopAtChapterEnd(true)
                },
                onTimerClear = {
                    updateToolbarInteraction()
                    viewModel.clearNarrationTimer()
                    viewModel.setNarrationStopAtChapterEnd(false)
                },
                onRetry = {
                    updateToolbarInteraction()
                    viewModel.retryLastSentence()
                },
                onDismiss = { hideToolbar() }
            )
        }
    }

    if (showFontSettings.value) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { hideToolbar() },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = MaterialTheme.shapes.large
        ) {
            FontSettingsPanelContent(
                currentFontSize = state.value.fontSizeLevel,
                onFontSizeChange = {
                    updateToolbarInteraction()
                    viewModel.setFontSize(it)
                },
                onDismiss = { hideToolbar() }
            )
        }
    }

    if (showToc.value) {
        TocDialog(
            titles = state.value.book?.chapters?.map { it.title } ?: emptyList(),
            currentIndex = state.value.currentChapterIndex,
            onSelect = {
                viewModel.selectChapter(it)
                showToc.value = false
                hideToolbar()
            },
            onDismiss = { showToc.value = false }
        )
    }

    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier.align(Alignment.BottomCenter)
    )
}
