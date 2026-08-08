package com.yuntian.metronome.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuntian.metronome.metronome.MAX_BPM
import com.yuntian.metronome.metronome.MIN_BPM
import com.yuntian.metronome.metronome.MetronomeUiState
import com.yuntian.metronome.metronome.PlaybackMode
import com.yuntian.metronome.metronome.Subdivision
import com.yuntian.metronome.metronome.TimeSignature
import com.yuntian.metronome.metronome.parseBpmInput
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetronomeScreen(
    state: MetronomeUiState,
    onTogglePlayback: () -> Unit,
    onSetCountInEnabled: (Boolean) -> Unit = {},
    onSetBpm: (Int) -> Unit,
    onAdjustBpm: (Int) -> Unit,
    onSetTimeSignature: (TimeSignature) -> Unit,
    onSetSubdivision: (Subdivision) -> Unit,
    onSetAccentEnabled: (Boolean) -> Unit,
    onSetPlaybackMode: (PlaybackMode) -> Unit,
    onSetCustomBeatDivisions: (Int, Int) -> Unit,
    onCycleCustomCell: (Int, Int) -> Unit,
    onSaveCustomPreset: (String) -> Unit,
    onApplyCustomPreset: (String) -> Unit,
    onDeleteCustomPreset: (String) -> Unit,
    onConsumeError: () -> Unit,
    onRetryAudio: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var showBpmDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.errorMessage) {
        val message = state.errorMessage ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = message,
            actionLabel = "重试",
            duration = SnackbarDuration.Long,
        )
        onConsumeError()
        if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) onRetryAudio()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            StartStopBar(
                isPlaying = state.isPlaying,
                countInEnabled = state.countInEnabled,
                isCountIn = state.isCountIn,
                onSetCountInEnabled = onSetCountInEnabled,
                onClick = onTogglePlayback,
            )
        },
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .testTag("metronome_content"),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 24.dp,
                end = 24.dp,
                top = 24.dp,
                bottom = 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item { Header() }
            item {
                BpmDisplay(
                    bpm = state.bpm,
                    isPlaying = state.isPlaying,
                    onClick = { showBpmDialog = true },
                    onAdjustBpm = onAdjustBpm,
                )
            }
            item {
                BeatCardPager(
                    state = state,
                    onSetPlaybackMode = onSetPlaybackMode,
                    onSelectSubdivision = onSetSubdivision,
                    onSetBeatDivisions = onSetCustomBeatDivisions,
                    onCycleCell = onCycleCustomCell,
                    onSavePreset = onSaveCustomPreset,
                    onApplyPreset = onApplyCustomPreset,
                    onDeletePreset = onDeleteCustomPreset,
                )
            }
            item {
                TempoControls(
                    bpm = state.bpm,
                    onSetBpm = onSetBpm,
                )
            }
            item {
                SignatureSection(
                    active = state.activeTimeSignature,
                    selected = state.selectedTimeSignature,
                    pending = state.pendingTimeSignature,
                    onSelect = onSetTimeSignature,
                )
            }
            item {
                AnimatedVisibility(visible = state.playbackMode == PlaybackMode.PRESET) {
                    AccentControl(
                        checked = state.accentEnabled,
                        onCheckedChange = onSetAccentEnabled,
                    )
                }
            }
        }
    }

    if (showBpmDialog) {
        BpmInputDialog(
            currentBpm = state.bpm,
            onDismiss = { showBpmDialog = false },
            onConfirm = {
                onSetBpm(it)
                showBpmDialog = false
            },
        )
    }
}

@Composable
private fun Header() {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "节拍器",
            style = MaterialTheme.typography.headlineSmall,
        )
    }
}

@Composable
private fun BpmDisplay(
    bpm: Int,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onAdjustBpm: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HoldRepeatButton(
            label = "每分钟拍数减 1",
            symbol = "−",
            enabled = bpm > MIN_BPM,
            onTrigger = { onAdjustBpm(-1) },
            size = 52,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(20.dp))
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { contentDescription = "当前每分钟 $bpm 拍，点击手动输入" }
                .testTag("bpm_display")
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = bpm.toString(),
                style = MaterialTheme.typography.displayLarge,
                color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
            )
            Text(
                text = "每分钟拍数",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HoldRepeatButton(
            label = "每分钟拍数加 1",
            symbol = "+",
            enabled = bpm < MAX_BPM,
            onTrigger = { onAdjustBpm(1) },
            size = 52,
        )
    }
}

@Composable
private fun TempoControls(
    bpm: Int,
    onSetBpm: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionLabel("速度调节")
        Slider(
            value = bpm.toFloat(),
            onValueChange = { onSetBpm(it.roundToInt()) },
            valueRange = MIN_BPM.toFloat()..MAX_BPM.toFloat(),
            steps = MAX_BPM - MIN_BPM - 1,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "速度滑块，当前每分钟 $bpm 拍" }
                .testTag("bpm_slider"),
        )
    }
}

@Composable
private fun HoldRepeatButton(
    label: String,
    symbol: String,
    enabled: Boolean,
    onTrigger: () -> Unit,
    size: Int = 64,
) {
    var pressed by remember { mutableStateOf(false) }
    val latestTrigger by rememberUpdatedState(onTrigger)
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, label = "controlPress")
    val color = if (enabled) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface

    Surface(
        modifier = Modifier
            .size(size.dp)
            .scale(scale)
            .semantics {
                role = Role.Button
                contentDescription = label
                if (!enabled) disabled()
                onClick {
                    if (enabled) latestTrigger()
                    enabled
                }
            }
            .then(
                if (enabled) {
                    Modifier.pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                pressed = true
                                latestTrigger()
                                try {
                                    kotlinx.coroutines.coroutineScope {
                                        val repeatJob = launch {
                                            delay(400)
                                            while (true) {
                                                latestTrigger()
                                                delay(100)
                                            }
                                        }
                                        tryAwaitRelease()
                                        repeatJob.cancel()
                                    }
                                } finally {
                                    pressed = false
                                }
                            },
                        )
                    }
                } else Modifier
            ),
        shape = CircleShape,
        color = color,
        contentColor = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text = symbol, fontSize = 28.sp, fontWeight = FontWeight.Light)
        }
    }
}

@Composable
private fun SignatureSection(
    active: TimeSignature,
    selected: TimeSignature,
    pending: TimeSignature?,
    onSelect: (TimeSignature) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionLabel("拍号")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TimeSignature.entries.forEach { signature ->
                Box(modifier = Modifier.weight(1f)) {
                    ChoicePill(
                        text = signature.label,
                        selected = selected == signature,
                        onClick = { onSelect(signature) },
                        contentDescription = "选择 ${signature.label} 拍",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        AnimatedVisibility(visible = pending != null) {
            Text(
                text = "${active.label} 正在播放 · ${pending?.label.orEmpty()} 下一小节生效",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.testTag("pending_signature"),
            )
        }
    }
}

@Composable
private fun ChoicePill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics {
                this.contentDescription = contentDescription
                stateDescription = if (selected) "已选择" else "未选择"
            },
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        ),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text = text, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun AccentControl(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "第一拍重音", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "咚 / 哒",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.semantics { contentDescription = "第一拍重音" },
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun StartStopBar(
    isPlaying: Boolean,
    countInEnabled: Boolean,
    isCountIn: Boolean,
    onSetCountInEnabled: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            CountInControl(
                checked = countInEnabled,
                enabled = !isPlaying,
                isCountIn = isCountIn,
                onCheckedChange = onSetCountInEnabled,
                testTag = "metronome_count_in_switch",
                statusTag = "metronome_count_in_control_status",
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            Button(
                onClick = onClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 14.dp)
                    .height(60.dp)
                    .testTag("start_stop_button"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isPlaying) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    contentColor = if (isPlaying) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text(
                    text = if (isPlaying) "停止" else "开始",
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                )
            }
        }
    }
}

@Composable
private fun BpmInputDialog(
    currentBpm: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var input by rememberSaveable { mutableStateOf(currentBpm.toString()) }
    var showError by rememberSaveable { mutableStateOf(false) }
    val confirm = {
        val parsed = parseBpmInput(input)
        if (parsed == null) showError = true else onConfirm(parsed)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("输入速度") },
        text = {
            OutlinedTextField(
                value = input,
                onValueChange = { value ->
                    if (value.length <= 3 && value.all(Char::isDigit)) {
                        input = value
                        showError = false
                    }
                },
                label = { Text("每分钟 30–300 拍") },
                singleLine = true,
                isError = showError,
                supportingText = if (showError) {
                    { Text("请输入 30–300 之间的整数") }
                } else null,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { confirm() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("bpm_input"),
            )
        },
        confirmButton = {
            TextButton(onClick = confirm) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
