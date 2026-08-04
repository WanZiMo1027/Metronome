package com.yuntian.metronome.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuntian.metronome.metronome.Subdivision
import com.yuntian.metronome.metronome.TimeSignature

private val LaneHeight = 112.dp
private val BeatBarWidth = 38.dp

@Composable
internal fun BeatLane(
    currentBeat: Int?,
    currentSubdivisionIndex: Int?,
    timeSignature: TimeSignature,
    subdivision: Subdivision,
    selectedSubdivision: Subdivision,
    pendingSubdivision: Subdivision?,
    onSelectSubdivision: (Subdivision) -> Unit,
    isPlaying: Boolean,
) {
    var showSubdivisionDialog by rememberSaveable { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = when {
                        isPlaying && currentBeat != null && currentSubdivisionIndex != null ->
                            "当前第 $currentBeat 拍 · ${currentSubdivisionIndex + 1}/${subdivision.stepCount}"

                        else -> "准备就绪"
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
                CompactSubdivisionButton(
                    selected = selectedSubdivision,
                    pending = pendingSubdivision != null,
                    onClick = { showSubdivisionDialog = true },
                )
            }

            AnimatedVisibility(visible = pendingSubdivision != null) {
                Text(
                    text = "${subdivision.accessibilityLabel} 正在播放 · " +
                        "${pendingSubdivision?.accessibilityLabel.orEmpty()} 下一小节生效",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .testTag("pending_subdivision"),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp),
                verticalAlignment = Alignment.Top,
            ) {
                repeat(timeSignature.beatsPerMeasure) { index ->
                    val beat = index + 1
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        BeatColumn(
                            beat = beat,
                            subdivision = subdivision,
                            activeSubdivisionIndex = currentSubdivisionIndex
                                ?.takeIf { isPlaying && currentBeat == beat },
                        )
                    }

                    if (index < timeSignature.beatsPerMeasure - 1) {
                        val isSixEightGroupBreak =
                            timeSignature == TimeSignature.SIX_EIGHT && index == 2
                        Spacer(
                            modifier = Modifier
                                .width(if (isSixEightGroupBreak) 14.dp else 4.dp)
                                .height(LaneHeight)
                                .then(
                                    if (isSixEightGroupBreak) {
                                        Modifier.testTag("six_eight_group_gap")
                                    } else {
                                        Modifier
                                    },
                                ),
                        )
                    }
                }
            }
        }
    }

    if (showSubdivisionDialog) {
        SubdivisionDialog(
            selected = selectedSubdivision,
            onDismiss = { showSubdivisionDialog = false },
            onSelect = {
                onSelectSubdivision(it)
                showSubdivisionDialog = false
            },
        )
    }
}

@Composable
private fun CompactSubdivisionButton(
    selected: Subdivision,
    pending: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .width(76.dp)
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = "当前细分：${selected.accessibilityLabel}，点击选择"
            }
            .testTag("subdivision_button"),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        border = androidx.compose.foundation.BorderStroke(
            width = if (pending) 2.dp else 1.dp,
            color = if (pending) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outline,
        ),
    ) {
        Box(contentAlignment = Alignment.Center) {
            SubdivisionGlyph(
                subdivision = selected,
                modifier = Modifier.size(width = 58.dp, height = 34.dp),
            )
        }
    }
}

@Composable
private fun BeatColumn(
    beat: Int,
    subdivision: Subdivision,
    activeSubdivisionIndex: Int?,
) {
    val downbeat = beat == 1
    val outline = if (downbeat) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val shape = RoundedCornerShape(4.dp)

    Column(
        modifier = Modifier
            .width(BeatBarWidth)
            .semantics {
                contentDescription = if (downbeat) {
                    "第 $beat 拍，小节第一拍"
                } else {
                    "第 $beat 拍"
                }
            }
            .testTag("beat_column_$beat"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .width(BeatBarWidth)
                .height(LaneHeight)
                .clip(shape)
                .border(if (downbeat) 2.dp else 1.dp, outline, shape),
        ) {
            subdivision.stepWeights.forEachIndexed { index, weight ->
                val active = activeSubdivisionIndex == index
                val idleColor = when {
                    downbeat && index == 0 -> MaterialTheme.colorScheme.primaryContainer
                    index == 0 -> MaterialTheme.colorScheme.surfaceVariant
                    else -> MaterialTheme.colorScheme.surface
                }
                val fill by animateColorAsState(
                    targetValue = if (active) MaterialTheme.colorScheme.primary else idleColor,
                    animationSpec = tween(durationMillis = 40),
                    label = "pulseFill",
                )
                Box(
                    modifier = Modifier
                        .weight(weight.toFloat())
                        .fillMaxWidth()
                        .background(fill)
                        .border(
                            width = 0.5.dp,
                            color = MaterialTheme.colorScheme.outline,
                        )
                        .semantics {
                            contentDescription = "第 $beat 拍，第 ${index + 1} 个细分"
                            stateDescription = when {
                                active && downbeat && index == 0 -> "当前小节重音"
                                active && index == 0 -> "当前拍音"
                                active -> "当前细分音"
                                index == 0 -> "拍音位置"
                                else -> "细分音位置"
                            }
                        }
                        .testTag("beat_${beat}_subdivision_$index"),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    if (index == 0) {
                        Box(
                            modifier = Modifier
                                .padding(top = 3.dp)
                                .width(if (downbeat) 24.dp else 16.dp)
                                .height(if (downbeat) 3.dp else 2.dp)
                                .background(
                                    if (active) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.primary,
                                    RoundedCornerShape(2.dp),
                                ),
                        )
                    }
                }
            }
        }
        Text(
            text = beat.toString(),
            modifier = Modifier.padding(top = 7.dp),
            color = if (downbeat) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (downbeat) FontWeight.Black else FontWeight.SemiBold,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun SubdivisionDialog(
    selected: Subdivision,
    onDismiss: () -> Unit,
    onSelect: (Subdivision) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择细分") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Subdivision.entries.chunked(3).forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        rowItems.forEach { subdivision ->
                            val isSelected = subdivision == selected
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(82.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable(role = Role.RadioButton) {
                                        onSelect(subdivision)
                                    }
                                    .semantics {
                                        contentDescription = subdivision.accessibilityLabel
                                        stateDescription = if (isSelected) "已选择" else "未选择"
                                    }
                                    .testTag("subdivision_option_${subdivision.name}"),
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                border = androidx.compose.foundation.BorderStroke(
                                    if (isSelected) 2.dp else 1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline,
                                ),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    SubdivisionGlyph(
                                        subdivision = subdivision,
                                        modifier = Modifier.size(width = 68.dp, height = 46.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
internal fun SubdivisionGlyph(
    subdivision: Subdivision,
    modifier: Modifier = Modifier,
) {
    val color = androidx.compose.material3.LocalContentColor.current
    val noteCount = when (subdivision) {
        Subdivision.QUARTER -> 1
        Subdivision.EIGHTH, Subdivision.SWING_LONG_SHORT, Subdivision.SWING_SHORT_LONG -> 2
        Subdivision.EIGHTH_TRIPLET -> 3
        Subdivision.SIXTEENTH -> 4
    }

    Box(modifier = modifier, contentAlignment = Alignment.TopCenter) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val headWidth = size.width.coerceAtMost(size.height * 1.5f) * 0.13f
            val headHeight = headWidth * 0.68f
            val headY = size.height * 0.76f
            val stemTop = size.height * 0.24f
            val startX = if (noteCount == 1) size.width * 0.48f else size.width * 0.18f
            val endX = if (noteCount == 1) startX else size.width * 0.82f
            val positions = List(noteCount) { index ->
                if (noteCount == 1) startX
                else startX + (endX - startX) * index / (noteCount - 1)
            }
            val stroke = 2.dp.toPx()

            positions.forEach { x ->
                drawOval(
                    color = color,
                    topLeft = Offset(x - headWidth / 2f, headY - headHeight / 2f),
                    size = Size(headWidth, headHeight),
                )
                drawLine(
                    color = color,
                    start = Offset(x + headWidth / 2f - stroke / 2f, headY),
                    end = Offset(x + headWidth / 2f - stroke / 2f, stemTop),
                    strokeWidth = stroke,
                    cap = StrokeCap.Square,
                )
            }

            if (noteCount > 1) {
                val beamStart = positions.first() + headWidth / 2f - stroke / 2f
                val beamEnd = positions.last() + headWidth / 2f - stroke / 2f
                drawLine(
                    color = color,
                    start = Offset(beamStart, stemTop),
                    end = Offset(beamEnd, stemTop),
                    strokeWidth = 4.dp.toPx(),
                    cap = StrokeCap.Square,
                )

                when (subdivision) {
                    Subdivision.SIXTEENTH -> drawLine(
                        color = color,
                        start = Offset(beamStart, stemTop + 7.dp.toPx()),
                        end = Offset(beamEnd, stemTop + 7.dp.toPx()),
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Square,
                    )

                    Subdivision.SWING_LONG_SHORT -> {
                        drawLine(
                            color = color,
                            start = Offset((beamStart + beamEnd) / 2f, stemTop + 7.dp.toPx()),
                            end = Offset(beamEnd, stemTop + 7.dp.toPx()),
                            strokeWidth = 3.dp.toPx(),
                            cap = StrokeCap.Square,
                        )
                        drawCircle(
                            color = color,
                            radius = 1.7.dp.toPx(),
                            center = Offset(positions.first() + headWidth, headY - headHeight * 0.15f),
                        )
                    }

                    Subdivision.SWING_SHORT_LONG -> {
                        drawLine(
                            color = color,
                            start = Offset(beamStart, stemTop + 7.dp.toPx()),
                            end = Offset((beamStart + beamEnd) / 2f, stemTop + 7.dp.toPx()),
                            strokeWidth = 3.dp.toPx(),
                            cap = StrokeCap.Square,
                        )
                        drawCircle(
                            color = color,
                            radius = 1.7.dp.toPx(),
                            center = Offset(positions.last() + headWidth, headY - headHeight * 0.15f),
                        )
                    }

                    else -> Unit
                }
            }
        }

        if (subdivision == Subdivision.EIGHTH_TRIPLET) {
            Text(
                text = "3",
                color = color,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
