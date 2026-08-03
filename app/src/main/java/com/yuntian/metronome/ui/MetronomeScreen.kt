package com.yuntian.metronome.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
    onSetBpm: (Int) -> Unit,
    onAdjustBpm: (Int) -> Unit,
    onSetStep: (Int) -> Unit,
    onSetTimeSignature: (TimeSignature) -> Unit,
    onSetAccentEnabled: (Boolean) -> Unit,
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
                onClick = onTogglePlayback,
            )
        },
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
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
                )
            }
            item {
                BeatLane(
                    currentBeat = state.currentBeat,
                    timeSignature = state.activeTimeSignature,
                    accentEnabled = state.accentEnabled,
                    isPlaying = state.isPlaying,
                )
            }
            item {
                TempoControls(
                    bpm = state.bpm,
                    step = state.step,
                    onSetBpm = onSetBpm,
                    onAdjustBpm = onAdjustBpm,
                    onSetStep = onSetStep,
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
                AccentControl(
                    checked = state.accentEnabled,
                    onCheckedChange = onSetAccentEnabled,
                )
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
    Column {
        Text(
            text = "PRECISION PRACTICE",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "节拍器",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun BpmDisplay(bpm: Int, isPlaying: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "当前速度 $bpm BPM，点击手动输入" }
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
            text = "每分钟拍数 · BPM",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BeatLane(
    currentBeat: Int?,
    timeSignature: TimeSignature,
    accentEnabled: Boolean,
    isPlaying: Boolean,
) {
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
            Text(
                text = when {
                    isPlaying && currentBeat != null -> "当前第 $currentBeat 拍 · BEAT $currentBeat"
                    else -> "准备就绪 · READY"
                },
                style = MaterialTheme.typography.titleMedium,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(timeSignature.beatsPerMeasure) { index ->
                    val beat = index + 1
                    BeatDot(
                        beat = beat,
                        active = isPlaying && currentBeat == beat,
                        accent = accentEnabled && beat == 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun BeatDot(beat: Int, active: Boolean, accent: Boolean) {
    val size by animateDpAsState(if (active) 52.dp else 42.dp, label = "beatSize")
    val fill by animateColorAsState(
        targetValue = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        label = "beatColor",
    )
    val textColor = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(fill)
            .border(
                width = if (accent) 2.dp else 1.dp,
                color = if (accent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                shape = CircleShape,
            )
            .semantics {
                stateDescription = when {
                    active && accent -> "第 $beat 拍，当前重音拍"
                    active -> "第 $beat 拍，当前拍"
                    accent -> "第 $beat 拍，重音位置"
                    else -> "第 $beat 拍"
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = beat.toString(), color = textColor, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TempoControls(
    bpm: Int,
    step: Int,
    onSetBpm: (Int) -> Unit,
    onAdjustBpm: (Int) -> Unit,
    onSetStep: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionLabel(chinese = "速度调节", english = "TEMPO")
        Slider(
            value = bpm.toFloat(),
            onValueChange = { onSetBpm(it.roundToInt()) },
            valueRange = MIN_BPM.toFloat()..MAX_BPM.toFloat(),
            steps = MAX_BPM - MIN_BPM - 1,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "BPM 滑块，当前 $bpm" }
                .testTag("bpm_slider"),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HoldRepeatButton(
                label = "减 $step BPM",
                symbol = "−",
                enabled = bpm > MIN_BPM,
                onTrigger = { onAdjustBpm(-1) },
            )
            StepSelector(step = step, onSetStep = onSetStep)
            HoldRepeatButton(
                label = "加 $step BPM",
                symbol = "+",
                enabled = bpm < MAX_BPM,
                onTrigger = { onAdjustBpm(1) },
            )
        }
    }
}

@Composable
private fun HoldRepeatButton(
    label: String,
    symbol: String,
    enabled: Boolean,
    onTrigger: () -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    val latestTrigger by rememberUpdatedState(onTrigger)
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, label = "controlPress")
    val color = if (enabled) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface

    Surface(
        modifier = Modifier
            .size(64.dp)
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
            Text(text = symbol, fontSize = 32.sp, fontWeight = FontWeight.Light)
        }
    }
}

@Composable
private fun StepSelector(step: Int, onSetStep: (Int) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = "步长 · STEP", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            modifier = Modifier.padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf(1, 5).forEach { option ->
                ChoicePill(
                    text = "±$option",
                    selected = step == option,
                    onClick = { onSetStep(option) },
                    contentDescription = "步长 $option BPM",
                )
            }
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
        SectionLabel(chinese = "拍号", english = "TIME SIGNATURE")
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
                    text = "ACCENT · 咚 / 哒",
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
private fun SectionLabel(chinese: String, english: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = chinese, style = MaterialTheme.typography.titleMedium)
        Text(
            text = "  $english",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StartStopBar(isPlaying: Boolean, onClick: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
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
                    text = if (isPlaying) "停止  STOP" else "开始  START",
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
        title = { Text("输入 BPM") },
        text = {
            OutlinedTextField(
                value = input,
                onValueChange = { value ->
                    if (value.length <= 3 && value.all(Char::isDigit)) {
                        input = value
                        showError = false
                    }
                },
                label = { Text("30–300 BPM") },
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
