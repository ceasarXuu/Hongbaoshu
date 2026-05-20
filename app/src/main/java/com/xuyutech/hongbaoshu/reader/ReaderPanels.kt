@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.xuyutech.hongbaoshu.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xuyutech.hongbaoshu.audio.AudioState
import kotlin.math.roundToInt
@Composable
internal fun FontSettingsPanel(
    currentFontSize: Int,
    onFontSizeChange: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    // Content-only: ModalBottomSheet provides the outer container, shape, and background.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "字体大小",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            androidx.compose.material3.IconButton(onClick = onDismiss) {
                androidx.compose.material3.Icon(
                    imageVector = androidx.compose.material.icons.Icons.Default.Close,
                    contentDescription = "关闭"
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "$currentFontSize",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Slider(
            value = currentFontSize.toFloat(),
            onValueChange = { onFontSizeChange(it.roundToInt()) },
            valueRange = com.xuyutech.hongbaoshu.reader.FONT_SIZE_MIN.toFloat()..com.xuyutech.hongbaoshu.reader.FONT_SIZE_MAX.toFloat(),
            steps = (com.xuyutech.hongbaoshu.reader.FONT_SIZE_MAX - com.xuyutech.hongbaoshu.reader.FONT_SIZE_MIN) / 2 - 1
        )

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
internal fun FontSettingsPanelContent(
    currentFontSize: Int,
    onFontSizeChange: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    FontSettingsPanel(
        currentFontSize = currentFontSize,
        onFontSizeChange = onFontSizeChange,
        onDismiss = onDismiss
    )
}

@Composable
internal fun NarrationPanel(
    audioState: com.xuyutech.hongbaoshu.audio.AudioState,
    narrationEnabled: Boolean,
    narrationTimerMinutes: Int?,
    narrationStopAtChapterEnd: Boolean,
    onPlayPause: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSpeedPreview: (Float) -> Unit,
    onSpeedCommit: (Float) -> Unit,
    onTimerStart: (Int) -> Unit,
    onStopAtChapterEnd: () -> Unit,
    onTimerClear: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    // Content-only: ModalBottomSheet provides the outer container, shape, and background.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column {
                Text(
                    text = "朗读",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                val status = when {
                    !narrationEnabled -> "未开启"
                    audioState.narrationPlaying -> "正在播放"
                    audioState.narrationSentenceId != null -> "已暂停"
                    else -> "准备就绪"
                }
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            androidx.compose.material3.IconButton(onClick = onDismiss) {
                androidx.compose.material3.Icon(
                    imageVector = androidx.compose.material.icons.Icons.Default.Close,
                    contentDescription = "关闭"
                )
            }
        }

        if (audioState.narrationError != null) {
            Text(
                text = audioState.narrationError,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp)
            )
            TextButton(onClick = onRetry) {
                Text("重试")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.material3.FilledTonalIconButton(
                onClick = onPrev,
                modifier = Modifier.size(48.dp),
                colors = androidx.compose.material3.IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
            ) {
                androidx.compose.material3.Icon(
                    imageVector = androidx.compose.material.icons.Icons.Default.SkipPrevious,
                    contentDescription = "上一句"
                )
            }

            androidx.compose.material3.FilledIconButton(
                onClick = onPlayPause,
                modifier = Modifier.size(72.dp)
            ) {
                androidx.compose.material3.Icon(
                    imageVector = if (audioState.narrationPlaying) {
                        androidx.compose.material.icons.Icons.Default.Pause
                    } else {
                        androidx.compose.material.icons.Icons.Default.PlayArrow
                    },
                    contentDescription = "播放暂停",
                    modifier = Modifier.size(32.dp)
                )
            }

            androidx.compose.material3.FilledTonalIconButton(
                onClick = onNext,
                modifier = Modifier.size(48.dp),
                colors = androidx.compose.material3.IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
            ) {
                androidx.compose.material3.Icon(
                    imageVector = androidx.compose.material.icons.Icons.Default.SkipNext,
                    contentDescription = "下一句"
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "语速",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = String.format(java.util.Locale.US, "%.2fx", audioState.narrationSpeed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            val minSpeed = 0.75f
            val maxSpeed = 1.25f
            val step = 0.05f
            val steps = (((maxSpeed - minSpeed) / step).toInt() - 1).coerceAtLeast(0)
            var sliderValue by remember { mutableStateOf(audioState.narrationSpeed) }
            LaunchedEffect(audioState.narrationSpeed) {
                sliderValue = audioState.narrationSpeed
            }

            Slider(
                value = sliderValue.coerceIn(minSpeed, maxSpeed),
                onValueChange = { raw ->
                    val stepsFromMin = ((raw.coerceIn(minSpeed, maxSpeed) - minSpeed) / step).roundToInt()
                    val snapped = (minSpeed + stepsFromMin * step).coerceIn(minSpeed, maxSpeed)
                    sliderValue = snapped
                    onSpeedPreview(snapped)
                },
                onValueChangeFinished = {
                    onSpeedCommit(sliderValue.coerceIn(minSpeed, maxSpeed))
                },
                valueRange = minSpeed..maxSpeed,
                steps = steps
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "定时关闭",
                    style = MaterialTheme.typography.titleSmall
                )
                if (narrationTimerMinutes != null || narrationStopAtChapterEnd) {
                    TextButton(
                        onClick = onTimerClear,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                        modifier = Modifier.height(24.dp)
                    ) {
                        Text("关闭定时")
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val timerOptions = listOf(15, 30, 60)
                timerOptions.forEach { minutes ->
                    val selected = narrationTimerMinutes == minutes && !narrationStopAtChapterEnd
                    TimerChip(
                        label = "${minutes}分钟",
                        selected = selected,
                        onClick = { onTimerStart(minutes) },
                        modifier = Modifier.weight(1f)
                    )
                }

                val endChapterSelected = narrationStopAtChapterEnd
                TimerChip(
                    label = "读完本章",
                    selected = endChapterSelected,
                    onClick = onStopAtChapterEnd,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
internal fun NarrationPanelContent(
    audioState: com.xuyutech.hongbaoshu.audio.AudioState,
    narrationEnabled: Boolean,
    narrationTimerMinutes: Int?,
    narrationStopAtChapterEnd: Boolean,
    onPlayPause: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSpeedPreview: (Float) -> Unit,
    onSpeedCommit: (Float) -> Unit,
    onTimerStart: (Int) -> Unit,
    onStopAtChapterEnd: () -> Unit,
    onTimerClear: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    NarrationPanel(
        audioState = audioState,
        narrationEnabled = narrationEnabled,
        narrationTimerMinutes = narrationTimerMinutes,
        narrationStopAtChapterEnd = narrationStopAtChapterEnd,
        onPlayPause = onPlayPause,
        onPrev = onPrev,
        onNext = onNext,
        onSpeedPreview = onSpeedPreview,
        onSpeedCommit = onSpeedCommit,
        onTimerStart = onTimerStart,
        onStopAtChapterEnd = onStopAtChapterEnd,
        onTimerClear = onTimerClear,
        onRetry = onRetry,
        onDismiss = onDismiss
    )
}

@Composable
internal fun TimerChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    androidx.compose.material3.Surface(
        onClick = onClick,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = if (selected) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.height(36.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}



@Composable
internal fun TocDialog(
    titles: List<String>,
    currentIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(
                text = "目录",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            ) 
        },
        text = {
            var query by remember { mutableStateOf("") }
            val normalizedQuery = query.trim()
            val filteredIndices = remember(titles, normalizedQuery) {
                if (normalizedQuery.isEmpty()) {
                    titles.indices.toList()
                } else {
                    titles.indices.filter { idx ->
                        titles[idx].contains(normalizedQuery, ignoreCase = true)
                    }
                }
            }
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("搜索章节") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { onSelect(currentIndex) }) {
                        Text("回到当前进度")
                    }
                    Text(
                        text = "当前第 ${currentIndex + 1} 章",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (filteredIndices.isEmpty()) {
                    Text(
                        text = "暂无匹配章节",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 420.dp)
                    ) {
                        items(filteredIndices) { idx ->
                            val isSelected = idx == currentIndex
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                        else Color.Transparent,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { onSelect(idx) }
                                    .padding(horizontal = 12.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${idx + 1}.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary 
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(32.dp)
                                )
                                Text(
                                    text = titles[idx],
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary 
                                            else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("关闭") }
        }
    )
}
