@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.xuyutech.hongbaoshu.reader

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward

import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics


import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import com.xuyutech.hongbaoshu.audio.AudioManager
import com.xuyutech.hongbaoshu.data.Chapter
import com.xuyutech.hongbaoshu.data.ParagraphType
import kotlin.math.roundToInt


/**
 * 页面内容组件（基于 PageSlice 渲染）
 */
@Composable
internal fun PageContent(
    chapter: Chapter,
    page: Page,
    currentNarrationId: String?,
    pageIndicatorText: String,
    fontSizeLevel: Int,
    textStyle: TextStyle,
    annotationStyle: TextStyle,
    textParagraphSpacingPx: Int,
    annotationSpacingPx: Int,
    pageEngine: PageEngine,
    activeTooltipSentenceId: String?,
    clickedSentenceId: String?,
    onTooltipRequest: (String) -> Unit,
    onSingleTap: (androidx.compose.ui.geometry.Offset) -> Unit,
    onPlayNarration: (String, List<String>) -> Unit
) {
    val density = LocalDensity.current
    val textParagraphSpacingDp = with(density) { textParagraphSpacingPx.toDp() }
    val annotationSpacingDp = with(density) { annotationSpacingPx.toDp() }
    
    // 构建段落 ID 到段落的映射
    val paragraphMap = remember(chapter.id) {
        chapter.paragraphs.associateBy { it.id }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        // 正文区域布局：上部 10% 留白，中间 80% 容器，3% 缓冲区，底部 7% 留白
        // 使用 padding 来定位，而不是 fillMaxHeight + padding 组合
        // 上部 10% + 容器 80% + 缓冲 3% + 底部 7% = 100%
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(
                    top = LocalConfiguration.current.screenHeightDp.dp * 0.1f,   // 上部 10% 留白
                    bottom = LocalConfiguration.current.screenHeightDp.dp * 0.07f // 底部 7% 留白（3% 缓冲在容器内）
                )
        ) {
            // 章节标题只在第一页显示
            if (page.isFirstPage) {
                Text(
                    text = chapter.title,
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    textAlign = TextAlign.Center
                )
            }
            // 页面内容
            Column(modifier = Modifier.weight(1f)) {
                val pageSentenceIds = remember(chapter.id, page) {
                    pageEngine.getSentenceIds(page, chapter)
                }
                page.slices.forEach { slice ->
                    val para = paragraphMap[slice.paragraphId] ?: return@forEach
                    androidx.compose.runtime.key(slice.paragraphId, slice.startChar, slice.endChar) {
                        SliceContent(
                            slice = slice,
                            paragraph = para,
                            currentNarrationId = currentNarrationId,
                            textStyle = textStyle,
                            annotationStyle = annotationStyle,
                            textParagraphSpacingDp = textParagraphSpacingDp,
                            annotationSpacingDp = annotationSpacingDp,
                            pageEngine = pageEngine,
                            onTextTap = { _, id -> onTooltipRequest(id) },
                            onSingleTap = onSingleTap,
                            activeTooltipSentenceId = activeTooltipSentenceId,
                            clickedSentenceId = clickedSentenceId,
                            onPlayNarration = { id -> onPlayNarration(id, pageSentenceIds) }
                        )
                    }
                }
            }
        }

        if (pageIndicatorText.isNotEmpty()) {
            Text(
                text = pageIndicatorText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 6.dp, bottom = 6.dp)
            )
        }
    }
}

/**
 * 渲染单个 PageSlice
 */
// ... (existing helper methods if any)

/**
 * 提示框数据
 */
data class TooltipData(
    val isVisible: Boolean = false,
    val position: androidx.compose.ui.geometry.Offset = androidx.compose.ui.geometry.Offset.Zero,
    val sentenceId: String = "",
    val sentenceContent: String = "" // Optional, for debug
)

@Composable
internal fun SliceContent(
    slice: PageSlice,
    paragraph: com.xuyutech.hongbaoshu.data.Paragraph,
    currentNarrationId: String?,
    textStyle: TextStyle,
    annotationStyle: TextStyle,
    textParagraphSpacingDp: androidx.compose.ui.unit.Dp,
    annotationSpacingDp: androidx.compose.ui.unit.Dp,
    pageEngine: PageEngine,
    onTextTap: (androidx.compose.ui.geometry.Offset, String) -> Unit,
    onSingleTap: (androidx.compose.ui.geometry.Offset) -> Unit,
    activeTooltipSentenceId: String?,
    clickedSentenceId: String?,
    onPlayNarration: (String) -> Unit
) {
    val viewConfiguration = LocalViewConfiguration.current
    val isText = slice.paragraphType == ParagraphType.text
    val spacing = if (slice.isLastSlice) {
        if (isText) textParagraphSpacingDp else annotationSpacingDp
    } else 0.dp
    
    if (isText) {
        // 获取句子范围映射
        val sentenceRanges = pageEngine.getSentenceRanges(slice.paragraphId)
        
        // 构建带高亮的文本（缩进通过 textIndent 样式实现，不要手动添加空格）
        val annotatedText = remember(slice, currentNarrationId, clickedSentenceId) {
            buildAnnotatedString {
                val sliceText = paragraph.content.substring(
                    slice.startChar.coerceIn(0, paragraph.content.length),
                    slice.endChar.coerceIn(0, paragraph.content.length)
                )
                
                // Determine which sentence to highlight (clicked takes precedence for visual feedback)
                val highlightSentenceId = clickedSentenceId ?: currentNarrationId
                
                if (highlightSentenceId != null && sentenceRanges != null) {
                    // 找到当前朗读/点击句子的范围
                    val sentenceIdx = paragraph.sentences.indexOfFirst { it.id == highlightSentenceId }
                    if (sentenceIdx >= 0 && sentenceIdx < sentenceRanges.size) {
                        val highlightRange = sentenceRanges[sentenceIdx]
                        // 计算高亮范围与当前片段的交集
                        val sliceRange = slice.startChar until slice.endChar
                        val intersectStart = maxOf(highlightRange.first, sliceRange.first) - slice.startChar
                        val intersectEnd = minOf(highlightRange.last + 1, sliceRange.last + 1) - slice.startChar
                        
                        if (intersectStart < intersectEnd && intersectStart >= 0 && intersectEnd <= sliceText.length) {
                            // 有交集，分段渲染
                            if (intersectStart > 0) {
                                append(sliceText.substring(0, intersectStart))
                            }
                            withStyle(SpanStyle(background = Color(0x40FFC107))) {
                                append(sliceText.substring(intersectStart, intersectEnd))
                            }
                            if (intersectEnd < sliceText.length) {
                                append(sliceText.substring(intersectEnd))
                            }
                        } else {
                            append(sliceText)
                        }
                    } else {
                        append(sliceText)
                    }
                } else {
                    append(sliceText)
                }
            }
        }
        
        val finalStyle = if (slice.isFirstSlice) {
            textStyle.copy(textIndent = TextIndent(firstLine = (textStyle.fontSize.value * 2).sp))
        } else {
            textStyle
        }
        

        
        Box {
            var textLayoutResult by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
            var localClickedCharOffset by remember { mutableStateOf<Int?>(null) }
            var localClickedSentenceId by remember { mutableStateOf<String?>(null) }
            var elementPosition by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
            
            val handleSentencePress: (androidx.compose.ui.geometry.Offset) -> Unit = { pos ->
                textLayoutResult?.let { layoutResult ->
                    val offset = layoutResult.getOffsetForPosition(pos)
                    val paragraphCharIndex = slice.startChar + offset
                    if (sentenceRanges != null) {
                        val sentenceIdx = sentenceRanges.indexOfFirst { range ->
                            paragraphCharIndex in range
                        }
                        if (sentenceIdx >= 0 && sentenceIdx < paragraph.sentences.size) {
                            val sentence = paragraph.sentences[sentenceIdx]
                            onTextTap(pos, sentence.id)
                            localClickedCharOffset = offset
                            localClickedSentenceId = sentence.id
                        }
                    }
                }
            }

            Text(
                text = annotatedText,
                style = finalStyle,
                modifier = Modifier
                    .padding(bottom = spacing)
                    .onGloballyPositioned { elementPosition = it.positionInRoot() }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = { offset ->
                                handleSentencePress(offset)
                            },
                            onTap = { offset ->
                                onSingleTap(elementPosition + offset)
                            }
                        )
                    },
                onTextLayout = { textLayoutResult = it }
            )
            
            // Popup Logic - use character bounding box for accurate positioning
            if (activeTooltipSentenceId != null && 
                activeTooltipSentenceId == localClickedSentenceId && 
                localClickedCharOffset != null &&
                textLayoutResult != null) {
                
                // Get the bounding box of the clicked character
                val boundingBox = textLayoutResult!!.getBoundingBox(localClickedCharOffset!!)
                
                // Measure tooltip width (approximate)
                val tooltipWidth = 100.dp
                val tooltipWidthPx = with(LocalDensity.current) { tooltipWidth.toPx() }
                
                val tooltipHeightPx = with(LocalDensity.current) { 60.dp.toPx() }
                
                // Position tooltip: centered horizontally above the character
                // Since we are in a Box wrapping the Text, these coordinates are local to the Text
                val popupOffset = androidx.compose.ui.unit.IntOffset(
                    x = (boundingBox.left + boundingBox.width / 2 - tooltipWidthPx / 2).toInt(),
                    y = (boundingBox.top - tooltipHeightPx).toInt()
                )
                
                androidx.compose.ui.window.Popup(
                    alignment = Alignment.TopStart,
                    offset = popupOffset,
                    onDismissRequest = { /* Dismissal handled by parent */ }
                ) {
                    ReadingTooltip(
                        onClick = { onPlayNarration(localClickedSentenceId!!) }
                    )
                }
            }
        }

    } else {
        // annotation 类型
        val prefix = if (slice.isFirstSlice) "【注】" else ""
        val sliceText = paragraph.content.substring(
            slice.startChar.coerceIn(0, paragraph.content.length),
            slice.endChar.coerceIn(0, paragraph.content.length)
        )
        
        Text(
            text = "$prefix$sliceText",
            style = annotationStyle,
            modifier = Modifier.padding(bottom = spacing)
        )
    }
}


@Composable
internal fun ReadingTooltip(
    onClick: () -> Unit
) {
    androidx.compose.material3.Surface(
        onClick = onClick,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.inverseSurface,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.material3.Icon(
                imageVector = androidx.compose.material.icons.Icons.Default.PlayArrow,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.inverseOnSurface,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "开始朗读",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.inverseOnSurface
            )
        }
    }
}
