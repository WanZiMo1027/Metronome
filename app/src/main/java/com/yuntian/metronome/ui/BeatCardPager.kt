package com.yuntian.metronome.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuntian.metronome.metronome.BeatPattern
import com.yuntian.metronome.metronome.CellSound
import com.yuntian.metronome.metronome.CustomPreset
import com.yuntian.metronome.metronome.MAX_CUSTOM_DIVISIONS
import com.yuntian.metronome.metronome.MIN_CUSTOM_DIVISIONS
import com.yuntian.metronome.metronome.MetronomeUiState
import com.yuntian.metronome.metronome.PlaybackMode
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.abs

private val PresetCardHeight = 210.dp
private val CustomCardHeight = 348.dp
private val CellShape = RoundedCornerShape(5.dp)

@Composable
internal fun BeatCardPager(
    state: MetronomeUiState,
    onSetPlaybackMode: (PlaybackMode) -> Unit,
    onSelectSubdivision: (com.yuntian.metronome.metronome.Subdivision) -> Unit,
    onSetBeatDivisions: (beatIndex: Int, divisions: Int) -> Unit,
    onCycleCell: (beatIndex: Int, cellIndex: Int) -> Unit,
    onSavePreset: (String) -> Unit,
    onApplyPreset: (String) -> Unit,
    onDeletePreset: (String) -> Unit,
) {
    val initialPage = if (state.playbackMode == PlaybackMode.CUSTOM) 1 else 0
    val pagerState = rememberPagerState(initialPage = initialPage) { 2 }
    val cardHeight by animateDpAsState(
        targetValue = if (pagerState.currentPage == 0) PresetCardHeight else CustomCardHeight,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "beatCardHeight",
    )

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                onSetPlaybackMode(if (page == 0) PlaybackMode.PRESET else PlaybackMode.CUSTOM)
            }
    }

    LaunchedEffect(state.playbackMode) {
        val requestedPage = if (state.playbackMode == PlaybackMode.CUSTOM) 1 else 0
        if (pagerState.settledPage != requestedPage) {
            pagerState.animateScrollToPage(requestedPage)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("beat_card_pager"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(cardHeight),
            pageSpacing = 12.dp,
        ) { page ->
            when (page) {
                0 -> BeatLane(
                    currentBeat = state.currentBeat,
                    currentSubdivisionIndex = state.currentSubdivisionIndex,
                    timeSignature = state.activeTimeSignature,
                    subdivision = state.activeSubdivision,
                    selectedSubdivision = state.selectedSubdivision,
                    pendingSubdivision = state.pendingSubdivision,
                    onSelectSubdivision = onSelectSubdivision,
                    isPlaying = state.isPlaying && state.activePlaybackMode == PlaybackMode.PRESET,
                    isCountIn = state.isCountIn,
                    modifier = Modifier.testTag("beat_card_page_preset"),
                )

                else -> CustomBeatCard(
                    state = state,
                    onSetBeatDivisions = onSetBeatDivisions,
                    onCycleCell = onCycleCell,
                    onSavePreset = onSavePreset,
                    onApplyPreset = onApplyPreset,
                    onDeletePreset = onDeletePreset,
                    modifier = Modifier.testTag("beat_card_page_custom"),
                )
            }
        }

        Row(
            modifier = Modifier.padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(2) { page ->
                val selected = pagerState.currentPage == page
                Box(
                    modifier = Modifier
                        .size(if (selected) 7.dp else 5.dp)
                        .background(
                            color = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline,
                            shape = CircleShape,
                        )
                        .semantics {
                            contentDescription = if (page == 0) "预设节拍页" else "自定义节奏页"
                            stateDescription = if (selected) "当前页" else "非当前页"
                        }
                        .testTag("beat_page_indicator_$page"),
                )
            }
        }
    }
}

@Composable
private fun CustomBeatCard(
    state: MetronomeUiState,
    onSetBeatDivisions: (beatIndex: Int, divisions: Int) -> Unit,
    onCycleCell: (beatIndex: Int, cellIndex: Int) -> Unit,
    onSavePreset: (String) -> Unit,
    onApplyPreset: (String) -> Unit,
    onDeletePreset: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSaveDialog by rememberSaveable { mutableStateOf(false) }
    var overwriteName by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<CustomPreset?>(null) }

    Surface(
        modifier = modifier.fillMaxSize(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "自定义节奏",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = if (
                            state.isCountIn &&
                            state.activePlaybackMode == PlaybackMode.CUSTOM &&
                            state.currentBeat != null
                        ) {
                            "预备拍 · 第 ${state.currentBeat} 拍 · " +
                                "${state.currentSubdivisionIndex?.plus(1) ?: 1}/" +
                                "${state.currentSubdivisionCount ?: 1}"
                        } else if (state.hasPendingConfiguration) {
                            "已编辑 · 下一小节生效"
                        } else if (
                            state.isPlaying &&
                            state.activePlaybackMode == PlaybackMode.CUSTOM &&
                            state.currentBeat != null
                        ) {
                            "第 ${state.currentBeat} 拍 · ${state.currentSubdivisionIndex?.plus(1) ?: 1}/" +
                                "${state.currentSubdivisionCount ?: 1}"
                        } else {
                            "点击变声 · 上下滑动改变格数"
                        },
                        color = if (state.hasPendingConfiguration) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .semantics {
                                stateDescription = when {
                                    state.isCountIn -> "正在预备"
                                    state.isPlaying -> "正式播放"
                                    else -> "准备就绪"
                                }
                            }
                            .testTag("custom_playback_status"),
                    )
                }
                PresetMenu(
                    presets = state.customPresets,
                    onApplyPreset = onApplyPreset,
                    onRequestDelete = { deleteTarget = it },
                )
                Spacer(Modifier.width(4.dp))
                Button(
                    onClick = { showSaveDialog = true },
                    modifier = Modifier
                        .height(40.dp)
                        .testTag("custom_save_button"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 13.dp),
                ) {
                    Text("保存", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 13.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Top,
            ) {
                state.customPattern.forEachIndexed { beatIndex, beat ->
                    CustomBeatColumn(
                        beatIndex = beatIndex,
                        beat = beat,
                        activeCellIndex = state.currentSubdivisionIndex?.takeIf {
                            state.isPlaying &&
                                state.activePlaybackMode == PlaybackMode.CUSTOM &&
                                state.currentBeat == beatIndex + 1
                        },
                        onSetDivisions = { onSetBeatDivisions(beatIndex, it) },
                        onCycleCell = { onCycleCell(beatIndex, it) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }

    if (showSaveDialog) {
        SavePresetDialog(
            presets = state.customPresets,
            onDismiss = { showSaveDialog = false },
            onSave = { name ->
                val duplicate = state.customPresets.any {
                    it.name.equals(name.trim(), ignoreCase = true)
                }
                showSaveDialog = false
                if (duplicate) overwriteName = name.trim() else onSavePreset(name)
            },
        )
    }

    overwriteName?.let { name ->
        AlertDialog(
            onDismissRequest = { overwriteName = null },
            title = { Text("覆盖同名预设？") },
            text = { Text("“$name”已经存在，继续保存会替换原配置。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onSavePreset(name)
                        overwriteName = null
                    },
                ) { Text("覆盖") }
            },
            dismissButton = {
                TextButton(onClick = { overwriteName = null }) { Text("取消") }
            },
        )
    }

    deleteTarget?.let { preset ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除预设？") },
            text = { Text("删除“${preset.name}”后无法恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeletePreset(preset.id)
                        deleteTarget = null
                    },
                ) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun CustomBeatColumn(
    beatIndex: Int,
    beat: BeatPattern,
    activeCellIndex: Int?,
    onSetDivisions: (Int) -> Unit,
    onCycleCell: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .pointerInput(beat.divisionCount) {
                var totalDrag = 0f
                detectVerticalDragGestures(
                    onDragStart = { totalDrag = 0f },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        totalDrag += dragAmount
                    },
                    onDragCancel = { totalDrag = 0f },
                    onDragEnd = {
                        if (abs(totalDrag) >= 24.dp.toPx()) {
                            val delta = if (totalDrag < 0f) 1 else -1
                            val requested = (beat.divisionCount + delta).coerceIn(
                                MIN_CUSTOM_DIVISIONS,
                                MAX_CUSTOM_DIVISIONS,
                            )
                            if (requested != beat.divisionCount) onSetDivisions(requested)
                        }
                        totalDrag = 0f
                    },
                )
            }
            .semantics {
                contentDescription = "第 ${beatIndex + 1} 拍，${beat.divisionCount} 等分，上滑增加，下滑减少"
            }
            .testTag("custom_beat_column_${beatIndex + 1}"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            beat.cells.forEachIndexed { cellIndex, sound ->
                CustomRhythmCell(
                    sound = sound,
                    active = activeCellIndex == cellIndex,
                    beat = beatIndex + 1,
                    cell = cellIndex + 1,
                    count = beat.divisionCount,
                    onClick = { onCycleCell(cellIndex) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Text(
            text = (beatIndex + 1).toString(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun CustomRhythmCell(
    sound: CellSound,
    active: Boolean,
    beat: Int,
    cell: Int,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fill = when (sound) {
        CellSound.ACCENT -> MaterialTheme.colorScheme.primary
        CellSound.NORMAL -> MaterialTheme.colorScheme.surfaceVariant
        CellSound.SILENT -> MaterialTheme.colorScheme.surface
    }
    val borderColor = when {
        active && sound == CellSound.ACCENT -> MaterialTheme.colorScheme.onPrimary
        active -> MaterialTheme.colorScheme.primary
        sound == CellSound.ACCENT -> MaterialTheme.colorScheme.primary
        sound == CellSound.SILENT -> MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
        else -> MaterialTheme.colorScheme.outline
    }
    val soundLabel = when (sound) {
        CellSound.NORMAL -> "普通"
        CellSound.ACCENT -> "重音"
        CellSound.SILENT -> "静音"
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(CellShape)
            .background(fill)
            .border(if (active) 2.dp else 1.dp, borderColor, CellShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = "第 $beat 拍，第 $cell/$count 格"
                stateDescription = if (active) "$soundLabel，当前播放位置" else soundLabel
            }
            .testTag("custom_cell_${beat}_$cell"),
        contentAlignment = Alignment.Center,
    ) {
        when (sound) {
            CellSound.ACCENT -> Box(
                modifier = Modifier
                    .width(14.dp)
                    .height(3.dp)
                    .background(MaterialTheme.colorScheme.onPrimary, RoundedCornerShape(2.dp)),
            )

            CellSound.SILENT -> Text(
                text = "✕",
                color = MaterialTheme.colorScheme.outline,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )

            CellSound.NORMAL -> if (active) Box(
                modifier = Modifier
                    .size(5.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
            )
        }
    }
}

@Composable
private fun PresetMenu(
    presets: List<CustomPreset>,
    onApplyPreset: (String) -> Unit,
    onRequestDelete: (CustomPreset) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(
            onClick = { expanded = true },
            modifier = Modifier
                .height(40.dp)
                .testTag("custom_preset_menu_button"),
        ) {
            Text("预设", fontWeight = FontWeight.SemiBold)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            if (presets.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("暂无已保存预设") },
                    onClick = {},
                    enabled = false,
                )
            } else {
                presets.forEach { preset ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(preset.name, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${preset.bpm} BPM · ${preset.timeSignature.label}",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp,
                                )
                            }
                        },
                        onClick = {
                            expanded = false
                            onApplyPreset(preset.id)
                        },
                        trailingIcon = {
                            Text(
                                text = "删除",
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 12.sp,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable {
                                        expanded = false
                                        onRequestDelete(preset)
                                    }
                                    .padding(7.dp),
                            )
                        },
                        modifier = Modifier.testTag("custom_preset_${preset.id}"),
                    )
                }
            }
        }
    }
}

@Composable
private fun SavePresetDialog(
    presets: List<CustomPreset>,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    val trimmed = name.trim()
    val duplicate = presets.any { it.name.equals(trimmed, ignoreCase = true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("保存自定义预设") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= 30) name = it },
                label = { Text("预设名称") },
                supportingText = {
                    Text(if (duplicate) "同名预设将要求确认覆盖" else "最多 30 个字符")
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("custom_preset_name"),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(trimmed) },
                enabled = trimmed.isNotEmpty(),
                modifier = Modifier.testTag("custom_preset_save_confirm"),
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
