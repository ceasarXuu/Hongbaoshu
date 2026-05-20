@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.xuyutech.hongbaoshu.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
@Composable
internal fun ReaderViewport(
    state: androidx.compose.runtime.State<ReaderState>,
    audioState: androidx.compose.runtime.State<com.xuyutech.hongbaoshu.audio.AudioState>,
    viewModel: ReaderViewModel,
    narrationControlsEnabled: Boolean,
    toolbarState: androidx.compose.runtime.MutableState<ToolBarState>,
    showNarrationPanel: androidx.compose.runtime.MutableState<Boolean>,
    showFontSettings: androidx.compose.runtime.MutableState<Boolean>,
    showToc: androidx.compose.runtime.MutableState<Boolean>,
    activeTooltipSentenceId: androidx.compose.runtime.MutableState<String?>,
    clickedSentenceId: androidx.compose.runtime.MutableState<String?>,
    suppressNextCenterTap: androidx.compose.runtime.MutableState<Boolean>,
    snackbarHostState: SnackbarHostState,
    manualTapSignal: kotlinx.coroutines.flow.MutableSharedFlow<androidx.compose.ui.geometry.Offset>,
    showNarrationUnsupported: () -> Unit,
    updateToolbarInteraction: () -> Unit,
    hideToolbar: () -> Unit,
    openNarrationPanel: () -> Unit,
    openFontSettings: () -> Unit,
    onBack: () -> Unit
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    com.xuyutech.hongbaoshu.ui.theme.HongbaoshuTheme(darkTheme = state.value.isNightMode) {
        androidx.compose.material3.Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize()
            ) {
        val screenHeightPx = with(density) { maxHeight.toPx().toInt() }
        val screenWidthPx = with(density) { maxWidth.toPx().toInt() }
        // 页面正文区域上下各 10% 留白，中间 80% 填充文字
        val contentHeightPx = (screenHeightPx * 0.8f).toInt()
        // 文字区域扣掉 PageContent 的水平 padding 20.dp * 2
        val horizontalPaddingPx = with(density) { 20.dp.toPx().toInt() }
        val contentWidthPx = (screenWidthPx - horizontalPaddingPx * 2).coerceAtLeast(0)
        
        val fontSizeLevel = state.value.fontSizeLevel
        val fontSizeSp = fontSizeLevel.sp
        val lineHeightSp = fontSizeSp * 1.5f  // 行高为字体大小的 1.5 倍
        val textStyle = TextStyle(
            fontSize = fontSizeSp,
            fontWeight = FontWeight.Normal,
            lineHeight = lineHeightSp,
            color = MaterialTheme.colorScheme.onBackground
        )
        val annotationFontSizeSp = fontSizeSp * 0.85f
        val annotationStyle = TextStyle(
            fontSize = annotationFontSizeSp,
            color = Color.Gray,
            lineHeight = annotationFontSizeSp * 1.5f
        )
        val textParagraphSpacingPx = with(density) { 16.dp.toPx().toInt() }
        val annotationSpacingPx = with(density) { 12.dp.toPx().toInt() }
        
        // 测量章节标题高度
        val titleStyle = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
        val titlePaddingPx = with(density) { 24.dp.toPx().toInt() }
        val titleHeightPx = remember(fontSizeLevel, screenWidthPx) {
            val result = textMeasurer.measure(
                text = "测试标题",
                style = titleStyle,
                constraints = androidx.compose.ui.unit.Constraints(maxWidth = contentWidthPx)
            )
            result.size.height + titlePaddingPx
        }
        
        // 构建分页配置的函数（用于预计算不同字号）
        fun buildPageConfig(fontLevel: Int): PageConfig {
            val fSizeSp = fontLevel.sp
            val lHeightSp = fSizeSp * 1.5f
            val tStyle = TextStyle(
                fontSize = fSizeSp,
                fontWeight = FontWeight.Normal,
                lineHeight = lHeightSp,
                color = Color.Black  // PageConfig is used for measurement, not rendering
            )
            val aFontSizeSp = fSizeSp * 0.85f
            val aStyle = TextStyle(
                fontSize = aFontSizeSp,
                color = Color.Gray,
                lineHeight = aFontSizeSp * 1.5f
            )
            return PageConfig(
                availableHeightPx = contentHeightPx,
                availableWidthPx = contentWidthPx,
                titleHeightPx = titleHeightPx,
                textStyle = tStyle,
                annotationStyle = aStyle,
                textParagraphSpacingPx = textParagraphSpacingPx,
                annotationSpacingPx = annotationSpacingPx
            )
        }
        
        // 当前字号的分页配置
        val pageConfig = remember(contentHeightPx, contentWidthPx, fontSizeLevel) {
            buildPageConfig(fontSizeLevel)
        }
        
        val book = state.value.book
        val currentChapterIndex = state.value.currentChapterIndex
        val currentChapter = book?.chapters?.getOrNull(currentChapterIndex)
        
        // 分页计算状态
        var pagesReady by remember { mutableStateOf(false) }
        var globalPagesReady by remember { mutableStateOf(false) }
        
        LaunchedEffect(book, currentChapterIndex, fontSizeLevel, contentWidthPx, contentHeightPx) {
            if (book != null &&
                currentChapterIndex in book.chapters.indices &&
                contentWidthPx > 0 &&
                contentHeightPx > 0
            ) {
                // 同步更新尺寸，避免“先分页后清缓存”导致的卡加载竞态
                viewModel.updateScreenSize(contentWidthPx, contentHeightPx)
                pagesReady = false
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    viewModel.computeCurrentChapter(textMeasurer, ::buildPageConfig, fontSizeLevel)
                }
                pagesReady = true
            }
        }

        LaunchedEffect(book, fontSizeLevel, contentWidthPx, contentHeightPx) {
            if (book != null && contentWidthPx > 0 && contentHeightPx > 0) {
                // 与当前章节计算保持同一时序
                viewModel.updateScreenSize(contentWidthPx, contentHeightPx)
                globalPagesReady = false
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    viewModel.computeRemainingChapters(textMeasurer, ::buildPageConfig, fontSizeLevel)
                }
                globalPagesReady = true
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    viewModel.startPrecompute(textMeasurer, ::buildPageConfig)
                }
            }
        }
        
        // 从缓存获取当前章节的分页（已包含全书页码）
        val currentPages = if (pagesReady && currentChapter != null) {
            viewModel.getCachedPages(currentChapter.id) ?: emptyList()
        } else {
            emptyList()
        }
        
        // 从缓存获取上一章的分页（用于跨章节翻页）
        val prevChapter = book?.chapters?.getOrNull(currentChapterIndex - 1)
        val prevChapterPages = if (pagesReady && prevChapter != null) {
            viewModel.getCachedPages(prevChapter.id) ?: emptyList()
        } else {
            emptyList()
        }
        
        val pageCount = currentPages.size
        val currentPageIndex = state.value.pageIndex.coerceIn(0, maxOf(0, pageCount - 1))
        val globalPagination = remember(globalPagesReady, book, fontSizeLevel, contentWidthPx, contentHeightPx) {
            if (!globalPagesReady || book == null) return@remember null
            val chapterPageCounts = book.chapters.map { chapter ->
                viewModel.getCachedPages(chapter.id, fontSizeLevel)?.size
            }
            if (chapterPageCounts.any { it == null }) return@remember null
            val counts = chapterPageCounts.filterNotNull()
            val startByChapterId = linkedMapOf<String, Int>()
            var acc = 0
            book.chapters.forEachIndexed { idx, chapter ->
                startByChapterId[chapter.id] = acc
                acc += counts[idx]
            }
            GlobalPaginationInfo(
                chapterStartPageById = startByChapterId,
                totalPages = acc
            )
        }
        
        // 校验页码
        LaunchedEffect(currentPageIndex, state.value.pageIndex) {
            if (state.value.pageIndex != currentPageIndex && pageCount > 0) {
                viewModel.setPageIndex(currentPageIndex)
            }
        }
        
        // 获取当前页面的句子列表（用于朗读）
        val currentPageSentences = remember(currentChapter, currentPages, currentPageIndex) {
            if (currentChapter == null || currentPages.isEmpty()) emptyList()
            else {
                val page = currentPages.getOrNull(currentPageIndex) ?: return@remember emptyList()
                viewModel.pageEngine.getSentenceIds(page, currentChapter)
            }
        }
        
        // 当句子列表变化时，更新到 ViewModel
        LaunchedEffect(currentPageSentences) {
            viewModel.updateCurrentPageSentences(currentPageSentences)
        }
        
        // 标记是否已经初始化过朗读（用于区分首次进入和翻页）
        var narrationInitialized by remember { mutableStateOf(false) }
        
        // 进入页面时，如果朗读开关开着且当前没有在播放，自动播放第一句
        // 只在首次进入时触发，翻页由 needPlayFirstSentence 处理
        LaunchedEffect(pagesReady) {
            if (pagesReady && 
                !narrationInitialized &&
                state.value.narrationEnabled && 
                audioState.value.narrationSentenceId == null &&
                currentPageSentences.isNotEmpty()) {
                narrationInitialized = true
                val firstPlayable = currentPageSentences.firstOrNull { it !in state.value.missingAudio }
                if (firstPlayable != null) {
                    viewModel.playSentence(firstPlayable)
                } else {
                    viewModel.playSentence(currentPageSentences.first())
                }
            }
        }
        
        // 处理朗读逻辑
        val currentNarrationId = audioState.value.narrationSentenceId
        
        // 处理翻页后播放第一句（自动翻页或手动翻页都会触发）
        LaunchedEffect(state.value.needPlayFirstSentence, currentPageSentences) {
            if (state.value.needPlayFirstSentence && currentPageSentences.isNotEmpty()) {
                viewModel.clearPlayFirstSentence()
                viewModel.resetNarrationState()
                val firstPlayable = currentPageSentences.firstOrNull { it !in state.value.missingAudio }
                if (firstPlayable != null) {
                    viewModel.playSentence(firstPlayable)
                } else {
                    viewModel.playSentence(currentPageSentences.first())
                }
            }
        }

        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            when {
                state.value.isLoading || !pagesReady || currentPages.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("加载中...", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                state.value.error != null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "加载失败：${state.value.error}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                book != null && currentChapter != null -> {
                    // 第一章第一页时也可以向前翻（返回封面）
                    val isFirstPageOfBook = currentChapterIndex == 0 && currentPageIndex == 0
                    val canGoPrev = currentPageIndex > 0 || currentChapterIndex > 0 || isFirstPageOfBook
                    val canGoNext = currentPageIndex < pageCount - 1 || 
                                   currentChapterIndex < (book.chapters.size - 1)
                    
                    SwipeablePageContainer(
                        pageIndex = currentPageIndex,
                        pageCount = pageCount,
                        canGoPrev = canGoPrev,
                        canGoNext = canGoNext,
                        onPageChange = { delta -> 
                            // 在第一章第一页向前翻，返回封面
                            if (delta < 0 && isFirstPageOfBook) {
                                // 返回封面前暂停朗读（保持开关状态）
                                if (state.value.narrationEnabled) {
                                    viewModel.pauseNarration()
                                }
                                onBack()
                            } else {
                                // Dismiss tooltip on page turn
                                activeTooltipSentenceId.value = null
                                clickedSentenceId.value = null
                                viewModel.updatePage(delta, pageCount, prevChapterPages.size)
                            }
                        },
                        onCenterTap = {
                            if (suppressNextCenterTap.value) {
                                suppressNextCenterTap.value = false
                                return@SwipeablePageContainer
                            }
                            if (toolbarState.value.isVisible) {
                                hideToolbar()
                                showNarrationPanel.value = false
                                showFontSettings.value = false
                            } else {
                                updateToolbarInteraction()
                            }
                            // 点击任何位置都尝试关闭 Tooltip 和清除高亮
                            activeTooltipSentenceId.value = null
                            clickedSentenceId.value = null
                        },
                        onTopDoubleTap = {
                            updateToolbarInteraction()
                        },
                        onDragStart = {
                            activeTooltipSentenceId.value = null
                            clickedSentenceId.value = null
                        },
                        manualTapSignal = manualTapSignal
                    ) { pageIndexToRender ->
                        val (targetChapter, targetPage) =
                            getPageByIndex(
                                book = book,
                                currentChapterIndex = currentChapterIndex,
                                currentPages = currentPages,
                                prevChapterPages = prevChapterPages,
                                pageIndexToRender = pageIndexToRender,
                                nextPagesProvider = { id -> viewModel.getCachedPages(id) }
                            ) ?: return@SwipeablePageContainer
                        
                        PageContent(
                            chapter = targetChapter,
                            page = targetPage,
                            currentNarrationId = if (pageIndexToRender == currentPageIndex) currentNarrationId else null,
                            pageIndicatorText = run {
                                val globalStart = globalPagination?.chapterStartPageById?.get(targetChapter.id)
                                val globalPageIndex0 = if (globalStart != null) globalStart + targetPage.index else null
                                buildPageIndicatorText(
                                    globalPageIndex0 = globalPageIndex0,
                                    globalTotalPages = globalPagination?.totalPages
                                )
                            },
                            fontSizeLevel = fontSizeLevel,
                            textStyle = textStyle,
                            annotationStyle = annotationStyle,
                            textParagraphSpacingPx = textParagraphSpacingPx,
                            annotationSpacingPx = annotationSpacingPx,
                            pageEngine = viewModel.pageEngine,
                            activeTooltipSentenceId = activeTooltipSentenceId.value,
                            clickedSentenceId = clickedSentenceId.value,
                            onTooltipRequest = { id -> 
                                activeTooltipSentenceId.value = id
                                clickedSentenceId.value = id
                                suppressNextCenterTap.value = true
                            },
                            onSingleTap = { offset ->
                                manualTapSignal.tryEmit(offset)
                            },
                            onPlayNarration = { id, pageSentenceIds ->
                                activeTooltipSentenceId.value = null
                                clickedSentenceId.value = null
                                if (!state.value.narrationEnabled) {
                                    viewModel.toggleNarration(true)
                                }
                                viewModel.playSentence(id, pageSentenceIds)
                            }
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = toolbarState.value.isVisible,
                enter = slideInVertically { -it },
                exit = slideOutVertically { -it },
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                ReaderTopBar(
                    title = book?.title ?: "",
                    chapterTitle = currentChapter?.title ?: "",
                    onBack = {
                        updateToolbarInteraction()
                        if (state.value.narrationEnabled) {
                            viewModel.pauseNarration()
                        }
                        onBack()
                    }
                )
            }

            val playPauseIcon = if (audioState.value.narrationPlaying) {
                androidx.compose.material.icons.Icons.Default.Pause
            } else {
                androidx.compose.material.icons.Icons.Default.PlayArrow
            }
            val playPauseLabel = if (audioState.value.narrationPlaying) {
                "暂停"
            } else {
                "播放"
            }

            AnimatedVisibility(
                visible = toolbarState.value.isVisible && !showNarrationPanel.value && !showFontSettings.value,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                ReaderBottomBar(
                    onOpenToc = {
                        updateToolbarInteraction()
                        showToc.value = true
                    },
                    onPlayPause = {
                        updateToolbarInteraction()
                        if (!narrationControlsEnabled) {
                            viewModel.toggleNarration(false)
                            showNarrationUnsupported()
                            return@ReaderBottomBar
                        }
                        if (!state.value.narrationEnabled) {
                            viewModel.toggleNarration(true)
                        }
                        if (audioState.value.narrationSentenceId == null) {
                            viewModel.playNextSentenceManual()
                        } else {
                            viewModel.pauseOrResumeSentence()
                        }
                    },
                    playPauseIcon = playPauseIcon,
                    playPauseLabel = playPauseLabel,
                    onOpenNarrationPanel = {
                        updateToolbarInteraction()
                        if (!narrationControlsEnabled) {
                            showNarrationUnsupported()
                            return@ReaderBottomBar
                        }
                        openNarrationPanel()
                    },
                    onOpenFontSettings = {
                        updateToolbarInteraction()
                        openFontSettings()
                    },
                    isNightMode = state.value.isNightMode,
                    onToggleNightMode = {
                        updateToolbarInteraction()
                        viewModel.toggleNightMode()
                    }
                )
            }

            ReaderOverlays(
                state = state,
                audioState = audioState,
                viewModel = viewModel,
                showNarrationPanel = showNarrationPanel,
                showFontSettings = showFontSettings,
                showToc = showToc,
                snackbarHostState = snackbarHostState,
                updateToolbarInteraction = updateToolbarInteraction,
                hideToolbar = hideToolbar
            )
        }
    }
    }
    }

}
