package com.yuntian.metronome.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuntian.metronome.metronome.ArrangementChange
import com.yuntian.metronome.metronome.ArrangementMeter
import com.yuntian.metronome.metronome.ArrangementPreset
import com.yuntian.metronome.metronome.ArrangementUiState
import com.yuntian.metronome.metronome.BeatPattern
import com.yuntian.metronome.metronome.CellSound
import com.yuntian.metronome.metronome.MAX_ARRANGEMENT_DENOMINATOR
import com.yuntian.metronome.metronome.MAX_ARRANGEMENT_NUMERATOR
import com.yuntian.metronome.metronome.MAX_BPM
import com.yuntian.metronome.metronome.MAX_CUSTOM_DIVISIONS
import com.yuntian.metronome.metronome.MIN_ARRANGEMENT_DENOMINATOR
import com.yuntian.metronome.metronome.MIN_ARRANGEMENT_NUMERATOR
import com.yuntian.metronome.metronome.MIN_BPM
import com.yuntian.metronome.metronome.MIN_CUSTOM_DIVISIONS
import kotlin.math.abs

private val ArrangementRowShape = RoundedCornerShape(18.dp)
private val ArrangementCellShape = RoundedCornerShape(4.dp)

@Composable
fun ArrangementScreen(
    state: ArrangementUiState,
    onTogglePlayback: () -> Unit,
    onSelectChange: (Int) -> Unit,
    onAddChange: () -> Unit,
    onDeleteChange: (Int) -> Unit,
    onSetStartMeasure: (Int, Int) -> Boolean,
    onSetConfiguration: (Int, Int, ArrangementMeter) -> Unit,
    onSetBeatDivisions: (Int, Int, Int) -> Unit,
    onCycleCell: (Int, Int, Int) -> Unit,
    onSavePreset: (String) -> Unit,
    onApplyPreset: (String) -> Unit,
    onDeletePreset: (String) -> Unit,
    onConsumeError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    var configurationIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var startMeasureIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var showSaveDialog by rememberSaveable { mutableStateOf(false) }
    var overwriteName by rememberSaveable { mutableStateOf<String?>(null) }
    var deletePresetTarget by remember { mutableStateOf<ArrangementPreset?>(null) }

    LaunchedEffect(state.errorMessage) {
        val message = state.errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Long)
        onConsumeError()
    }

    LaunchedEffect(state.isPlaying, state.currentRowIndex) {
        val rowIndex = state.currentRowIndex
        if (!state.isPlaying || rowIndex == null || rowIndex !in state.changes.indices) {
            return@LaunchedEffect
        }
        val itemIndex = rowIndex + 1 // Header occupies the first lazy-list item.
        var visibleItem = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == itemIndex }
        if (visibleItem == null) {
            listState.animateScrollToItem(itemIndex)
            visibleItem = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == itemIndex }
                ?: return@LaunchedEffect
        }
        val activeItem = visibleItem
        val viewportCenter = (
            listState.layoutInfo.viewportStartOffset + listState.layoutInfo.viewportEndOffset
        ) / 2
        val itemCenter = activeItem.offset + activeItem.size / 2
        listState.animateScrollBy((itemCenter - viewportCenter).toFloat())
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            ArrangementBottomBar(
                isPlaying = state.isPlaying,
                addEnabled = !state.isPlaying && (
                    state.changes.isEmpty() || state.selectedRowIndex in state.changes.indices
                ),
                playbackEnabled = state.changes.isNotEmpty(),
                onAdd = onAddChange,
                onTogglePlayback = onTogglePlayback,
            )
        },
    ) { contentPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .testTag("arrangement_content"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ArrangementHeader(
                    presets = state.presets,
                    canSave = state.changes.isNotEmpty() && !state.isPlaying,
                    enabled = !state.isPlaying,
                    onSave = { showSaveDialog = true },
                    onApplyPreset = onApplyPreset,
                    onRequestDeletePreset = { deletePresetTarget = it },
                )
            }

            if (state.changes.isEmpty()) {
                item { ArrangementEmptyState() }
            } else {
                itemsIndexed(
                    items = state.changes,
                    key = { _, change -> change.startMeasure },
                ) { rowIndex, change ->
                    ArrangementRow(
                        rowIndex = rowIndex,
                        change = change,
                        isLast = rowIndex == state.changes.lastIndex,
                        isPlaying = state.isPlaying,
                        isSelected = state.selectedRowIndex == rowIndex,
                        isActive = state.isPlaying && state.currentRowIndex == rowIndex,
                        displayedMeasure = if (state.currentRowIndex == rowIndex) {
                            state.currentMeasure ?: change.startMeasure
                        } else {
                            change.startMeasure
                        },
                        currentBeat = state.currentBeat,
                        currentSubdivisionIndex = state.currentSubdivisionIndex,
                        onSelect = { onSelectChange(rowIndex) },
                        onEditStart = { startMeasureIndex = rowIndex },
                        onEditConfiguration = { configurationIndex = rowIndex },
                        onSetBeatDivisions = { beat, divisions ->
                            onSetBeatDivisions(rowIndex, beat, divisions)
                        },
                        onCycleCell = { beat, cell -> onCycleCell(rowIndex, beat, cell) },
                    )
                }
            }
        }
    }

    configurationIndex?.let { rowIndex ->
        state.changes.getOrNull(rowIndex)?.let { change ->
            ArrangementConfigurationDialog(
                change = change,
                onDismiss = { configurationIndex = null },
                onConfirm = { bpm, meter ->
                    onSetConfiguration(rowIndex, bpm, meter)
                    configurationIndex = null
                },
                onDelete = {
                    onDeleteChange(rowIndex)
                    configurationIndex = null
                },
            )
        }
    }

    startMeasureIndex?.let { rowIndex ->
        state.changes.getOrNull(rowIndex)?.let { change ->
            val minimum = state.changes[rowIndex - 1].startMeasure + 1
            StartMeasureDialog(
                current = change.startMeasure,
                minimum = minimum,
                maximum = null,
                onDismiss = { startMeasureIndex = null },
                onConfirm = { value ->
                    if (onSetStartMeasure(rowIndex, value)) startMeasureIndex = null
                },
            )
        }
    }

    if (showSaveDialog) {
        ArrangementSavePresetDialog(
            presets = state.presets,
            onDismiss = { showSaveDialog = false },
            onSave = { name ->
                val safeName = name.trim()
                val duplicate = state.presets.any { it.name.equals(safeName, ignoreCase = true) }
                showSaveDialog = false
                if (duplicate) overwriteName = safeName else onSavePreset(safeName)
            },
        )
    }

    overwriteName?.let { name ->
        AlertDialog(
            onDismissRequest = { overwriteName = null },
            title = { Text("覆盖同名预设？") },
            text = { Text("“$name”已经存在，继续保存会替换原编排。") },
            confirmButton = {
                TextButton(onClick = {
                    onSavePreset(name)
                    overwriteName = null
                }) { Text("覆盖") }
            },
            dismissButton = {
                TextButton(onClick = { overwriteName = null }) { Text("取消") }
            },
        )
    }

    deletePresetTarget?.let { preset ->
        AlertDialog(
            onDismissRequest = { deletePresetTarget = null },
            title = { Text("删除编排预设？") },
            text = { Text("删除“${preset.name}”后无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    onDeletePreset(preset.id)
                    deletePresetTarget = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deletePresetTarget = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun ArrangementHeader(
    presets: List<ArrangementPreset>,
    canSave: Boolean,
    enabled: Boolean,
    onSave: () -> Unit,
    onApplyPreset: (String) -> Unit,
    onRequestDeletePreset: (ArrangementPreset) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("小节编排", style = MaterialTheme.typography.headlineSmall)
            Text(
                "用变化点组织整首歌曲",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        ArrangementPresetMenu(
            presets = presets,
            enabled = enabled,
            onApplyPreset = onApplyPreset,
            onRequestDelete = onRequestDeletePreset,
        )
        Spacer(Modifier.width(4.dp))
        Button(
            onClick = onSave,
            enabled = canSave,
            modifier = Modifier
                .height(40.dp)
                .testTag("arrangement_save_button"),
            shape = RoundedCornerShape(10.dp),
            contentPadding = PaddingValues(horizontal = 14.dp),
        ) {
            Text("保存", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}

@Composable
private fun ArrangementEmptyState() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(310.dp)
            .testTag("arrangement_empty_state"),
        shape = ArrangementRowShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            modifier = Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text("1", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }
            Text(
                "从第一个小节开始",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 18.dp),
            )
            Text(
                "之后只需在节奏发生变化的位置添加新行",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun ArrangementRow(
    rowIndex: Int,
    change: ArrangementChange,
    isLast: Boolean,
    isPlaying: Boolean,
    isSelected: Boolean,
    isActive: Boolean,
    displayedMeasure: Int,
    currentBeat: Int?,
    currentSubdivisionIndex: Int?,
    onSelect: () -> Unit,
    onEditStart: () -> Unit,
    onEditConfiguration: () -> Unit,
    onSetBeatDivisions: (Int, Int) -> Unit,
    onCycleCell: (Int, Int) -> Unit,
) {
    val patternScrollState = rememberScrollState()
    var patternViewportWidth by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val background by animateColorAsState(
        targetValue = when {
            isActive -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
            isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f)
            else -> MaterialTheme.colorScheme.surface
        },
        animationSpec = tween(180),
        label = "arrangementRowBackground",
    )
    val outline by animateColorAsState(
        targetValue = if (isActive || isSelected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.outline,
        animationSpec = tween(180),
        label = "arrangementRowOutline",
    )

    LaunchedEffect(
        isPlaying,
        isActive,
        currentBeat,
        patternViewportWidth,
        patternScrollState.maxValue,
    ) {
        if (!isPlaying || !isActive || currentBeat == null || patternViewportWidth <= 0) {
            return@LaunchedEffect
        }
        val beatStep = with(density) { 43.dp.toPx() }
        val beatCenter = with(density) { 19.dp.toPx() }
        val target = ((currentBeat - 1) * beatStep + beatCenter - patternViewportWidth / 2f)
            .toInt()
            .coerceIn(0, patternScrollState.maxValue)
        patternScrollState.animateScrollTo(target)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(178.dp)
            .pointerInput(isPlaying, isSelected) {
                if (isPlaying || isSelected) return@pointerInput
                awaitEachGesture {
                    awaitFirstDown(
                        requireUnconsumed = false,
                        pass = PointerEventPass.Initial,
                    )
                    val up = waitForUpOrCancellation(pass = PointerEventPass.Initial)
                    if (up != null) {
                        up.consume()
                        onSelect()
                    }
                }
            }
            .semantics {
                role = Role.RadioButton
                selected = isSelected
                if (!isPlaying && !isSelected) {
                    onClick(label = "选择第 $displayedMeasure 小节") {
                        onSelect()
                        true
                    }
                }
            }
            .testTag("arrangement_row_${rowIndex + 1}"),
        shape = ArrangementRowShape,
        color = background,
        border = BorderStroke(if (isActive || isSelected) 2.dp else 1.dp, outline),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.width(46.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Surface(
                    modifier = Modifier
                        .size(44.dp)
                        .clickable(
                            enabled = isSelected && !isPlaying && rowIndex > 0,
                            role = Role.Button,
                            onClick = onEditStart,
                        )
                        .semantics {
                            contentDescription = if (rowIndex == 0) "第 1 小节，固定起点"
                            else "起始小节 $displayedMeasure，点击编辑"
                            stateDescription = when {
                                isActive -> "当前播放小节"
                                isSelected -> "已选中"
                                else -> "未选中"
                            }
                        }
                        .testTag("arrangement_measure_${rowIndex + 1}"),
                    shape = CircleShape,
                    color = if (isActive) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (isActive) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurface,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            displayedMeasure.toString(),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = if (displayedMeasure < 100) 18.sp else 14.sp,
                        )
                    }
                }
                Text(
                    if (isLast) "结束" else "起始",
                    color = if (isLast) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 7.dp),
                )
            }

            Spacer(Modifier.width(8.dp))
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .onSizeChanged { patternViewportWidth = it.width }
                    .horizontalScroll(
                        state = patternScrollState,
                        enabled = isSelected && !isPlaying,
                    )
                    .testTag("arrangement_pattern_${rowIndex + 1}"),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                change.beats.forEachIndexed { beatIndex, beat ->
                    ArrangementBeatColumn(
                        rowIndex = rowIndex,
                        beatIndex = beatIndex,
                        beat = beat,
                        enabled = isSelected && !isPlaying,
                        activeCellIndex = currentSubdivisionIndex?.takeIf {
                            isActive && currentBeat == beatIndex + 1
                        },
                        onSetDivisions = { onSetBeatDivisions(beatIndex, it) },
                        onCycleCell = { onCycleCell(beatIndex, it) },
                    )
                }
            }
            Spacer(Modifier.width(8.dp))

            Surface(
                modifier = Modifier
                    .width(60.dp)
                    .height(66.dp)
                    .clickable(
                        enabled = isSelected && !isPlaying,
                        role = Role.Button,
                        onClick = onEditConfiguration,
                    )
                    .semantics { contentDescription = "${change.bpm} BPM，${change.meter.label}，点击设置" }
                    .testTag("arrangement_config_${rowIndex + 1}"),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(change.bpm.toString(), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(
                        "BPM",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 8.sp,
                        letterSpacing = 0.8.sp,
                    )
                    Text(
                        change.meter.label,
                        color = MaterialTheme.colorScheme.primary,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun ArrangementBeatColumn(
    rowIndex: Int,
    beatIndex: Int,
    beat: BeatPattern,
    enabled: Boolean,
    activeCellIndex: Int?,
    onSetDivisions: (Int) -> Unit,
    onCycleCell: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .width(38.dp)
            .fillMaxHeight()
            .pointerInput(enabled, beat.divisionCount) {
                if (!enabled) return@pointerInput
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
                    },
                )
            }
            .semantics {
                contentDescription = "第 ${beatIndex + 1} 拍，${beat.divisionCount} 等分，上滑增加，下滑减少"
            }
            .testTag("arrangement_beat_${rowIndex + 1}_${beatIndex + 1}"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            beat.cells.forEachIndexed { cellIndex, sound ->
                ArrangementRhythmCell(
                    sound = sound,
                    active = activeCellIndex == cellIndex,
                    enabled = enabled,
                    rowIndex = rowIndex,
                    beatIndex = beatIndex,
                    cellIndex = cellIndex,
                    count = beat.divisionCount,
                    onClick = { onCycleCell(cellIndex) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Text(
            (beatIndex + 1).toString(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 5.dp),
        )
    }
}

@Composable
private fun ArrangementRhythmCell(
    sound: CellSound,
    active: Boolean,
    enabled: Boolean,
    rowIndex: Int,
    beatIndex: Int,
    cellIndex: Int,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fill = when (sound) {
        CellSound.ACCENT -> MaterialTheme.colorScheme.primary
        CellSound.NORMAL -> MaterialTheme.colorScheme.surfaceVariant
        CellSound.SILENT -> Color.Transparent
    }
    val border = when {
        active && sound == CellSound.ACCENT -> MaterialTheme.colorScheme.onPrimary
        active -> MaterialTheme.colorScheme.primary
        sound == CellSound.ACCENT -> MaterialTheme.colorScheme.primary
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
            .clip(ArrangementCellShape)
            .background(fill)
            .border(if (active) 2.dp else 1.dp, border, ArrangementCellShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = "第 ${beatIndex + 1} 拍，第 ${cellIndex + 1}/$count 格"
                stateDescription = if (active) "$soundLabel，当前播放" else soundLabel
            }
            .testTag("arrangement_cell_${rowIndex + 1}_${beatIndex + 1}_${cellIndex + 1}"),
        contentAlignment = Alignment.Center,
    ) {
        when (sound) {
            CellSound.ACCENT -> Box(
                Modifier
                    .width(12.dp)
                    .height(3.dp)
                    .background(MaterialTheme.colorScheme.onPrimary, RoundedCornerShape(2.dp)),
            )
            CellSound.SILENT -> Box(
                Modifier
                    .width(11.dp)
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outline),
            )
            CellSound.NORMAL -> if (active) Box(
                Modifier
                    .size(5.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
            )
        }
    }
}

@Composable
private fun ArrangementBottomBar(
    isPlaying: Boolean,
    addEnabled: Boolean,
    playbackEnabled: Boolean,
    onAdd: () -> Unit,
    onTogglePlayback: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                onClick = onAdd,
                enabled = addEnabled,
                modifier = Modifier
                    .weight(1f)
                    .height(58.dp)
                    .testTag("arrangement_add_button"),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Text("＋  添加小节", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            Button(
                onClick = onTogglePlayback,
                enabled = playbackEnabled,
                modifier = Modifier
                    .weight(1f)
                    .height(58.dp)
                    .testTag("arrangement_start_stop"),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isPlaying) MaterialTheme.colorScheme.surfaceVariant
                    else MaterialTheme.colorScheme.primary,
                    contentColor = if (isPlaying) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text(
                    if (isPlaying) "停止" else "开始",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                )
            }
        }
    }
}

@Composable
private fun ArrangementConfigurationDialog(
    change: ArrangementChange,
    onDismiss: () -> Unit,
    onConfirm: (Int, ArrangementMeter) -> Unit,
    onDelete: () -> Unit,
) {
    var bpm by rememberSaveable(change.startMeasure) { mutableIntStateOf(change.bpm) }
    var numerator by rememberSaveable(change.startMeasure) {
        mutableIntStateOf(change.meter.numerator)
    }
    var denominator by rememberSaveable(change.startMeasure) {
        mutableIntStateOf(change.meter.denominator)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置第 ${change.startMeasure} 小节") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NumberWheel(
                        label = "BPM",
                        value = bpm,
                        range = MIN_BPM..MAX_BPM,
                        onValueChange = { bpm = it },
                        modifier = Modifier.width(82.dp),
                        testTag = "arrangement_bpm_wheel",
                    )
                    Spacer(Modifier.width(12.dp))
                    NumberWheel(
                        label = "分子",
                        value = numerator,
                        range = MIN_ARRANGEMENT_NUMERATOR..MAX_ARRANGEMENT_NUMERATOR,
                        onValueChange = { numerator = it },
                        modifier = Modifier.width(58.dp),
                        testTag = "arrangement_numerator_wheel",
                    )
                    Text(
                        "/",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 20.dp),
                    )
                    NumberWheel(
                        label = "分母",
                        value = denominator,
                        range = MIN_ARRANGEMENT_DENOMINATOR..MAX_ARRANGEMENT_DENOMINATOR,
                        onValueChange = { denominator = it },
                        modifier = Modifier.width(58.dp),
                        testTag = "arrangement_denominator_wheel",
                    )
                }
                TextButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .testTag("arrangement_delete_row"),
                ) { Text("删除此小节变化点", color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(bpm, ArrangementMeter(numerator, denominator)) },
                modifier = Modifier.testTag("arrangement_config_confirm"),
            ) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun NumberWheel(
    label: String,
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    testTag: String,
) {
    val pagerState = rememberPagerState(initialPage = value - range.first) { range.count() }
    LaunchedEffect(value) {
        val target = value.coerceIn(range).minus(range.first)
        if (pagerState.settledPage != target) pagerState.scrollToPage(target)
    }
    LaunchedEffect(pagerState.settledPage) {
        val selected = range.first + pagerState.settledPage
        if (selected != value) onValueChange(selected)
    }

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(132.dp)
                .testTag(testTag),
        ) {
            VerticalPager(
                state = pagerState,
                pageSize = PageSize.Fixed(44.dp),
                contentPadding = PaddingValues(vertical = 44.dp),
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                val selected = page == pagerState.currentPage
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        (range.first + page).toString(),
                        color = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                        fontFamily = FontFamily.Monospace,
                        fontSize = if (selected) 20.sp else 14.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(top = 44.dp),
                color = MaterialTheme.colorScheme.outline,
            )
            HorizontalDivider(
                modifier = Modifier.padding(top = 88.dp),
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
private fun StartMeasureDialog(
    current: Int,
    minimum: Int,
    maximum: Int?,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var input by rememberSaveable(current) { mutableStateOf(current.toString()) }
    val parsed = input.toIntOrNull()
    val valid = parsed != null && parsed >= minimum && (maximum == null || parsed <= maximum)
    val rangeText = if (maximum == null) "请输入不小于 $minimum 的整数"
    else "可用范围：$minimum–$maximum"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修改起始小节") },
        text = {
            OutlinedTextField(
                value = input,
                onValueChange = { value ->
                    if (value.length <= 9 && value.all(Char::isDigit)) input = value
                },
                label = { Text("小节数") },
                supportingText = { Text(rangeText) },
                isError = input.isNotEmpty() && !valid,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("arrangement_start_measure_input"),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { parsed?.let(onConfirm) },
                enabled = valid,
                modifier = Modifier.testTag("arrangement_start_measure_confirm"),
            ) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun ArrangementPresetMenu(
    presets: List<ArrangementPreset>,
    enabled: Boolean,
    onApplyPreset: (String) -> Unit,
    onRequestDelete: (ArrangementPreset) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(
            onClick = { expanded = true },
            enabled = enabled,
            modifier = Modifier
                .height(40.dp)
                .testTag("arrangement_preset_menu"),
        ) { Text("预设", fontWeight = FontWeight.SemiBold) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (presets.isEmpty()) {
                DropdownMenuItem(text = { Text("暂无已保存预设") }, onClick = {}, enabled = false)
            } else {
                presets.forEach { preset ->
                    DropdownMenuItem(
                        text = {
                            Column(modifier = Modifier.widthIn(min = 150.dp)) {
                                Text(preset.name, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${preset.changes.size} 个变化点 · 结束于 ${preset.changes.last().startMeasure}",
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
                                "删除",
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
                    )
                }
            }
        }
    }
}

@Composable
private fun ArrangementSavePresetDialog(
    presets: List<ArrangementPreset>,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    val trimmed = name.trim()
    val duplicate = presets.any { it.name.equals(trimmed, ignoreCase = true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("保存编排预设") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= 30) name = it },
                label = { Text("预设名称") },
                supportingText = { Text(if (duplicate) "同名预设将要求确认覆盖" else "最多 30 个字符") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("arrangement_preset_name"),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(trimmed) },
                enabled = trimmed.isNotEmpty(),
                modifier = Modifier.testTag("arrangement_preset_save_confirm"),
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
