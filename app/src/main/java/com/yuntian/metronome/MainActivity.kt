package com.yuntian.metronome

import android.os.Bundle
import android.graphics.Color as AndroidColor
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yuntian.metronome.metronome.ArrangementUiState
import com.yuntian.metronome.metronome.ArrangementExportOptions
import com.yuntian.metronome.metronome.MetronomeUiState
import com.yuntian.metronome.metronome.MetronomeViewModel
import com.yuntian.metronome.ui.ArrangementScreen
import com.yuntian.metronome.ui.MetronomeScreen
import com.yuntian.metronome.ui.theme.MetronomeTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val metronomeViewModel: MetronomeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        setContent {
            MetronomeTheme {
                MetronomeApp(metronomeViewModel)
            }
        }
    }

    override fun onStop() {
        if (!isChangingConfigurations) {
            metronomeViewModel.stop()
            metronomeViewModel.cancelActiveArrangementExport()
        }
        super.onStop()
    }

    override fun onUserLeaveHint() {
        metronomeViewModel.stop()
        metronomeViewModel.cancelActiveArrangementExport()
        super.onUserLeaveHint()
    }
}

@PreviewScreenSizes
@Composable
fun MetronomeApp(viewModel: MetronomeViewModel? = null) {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestination.METRONOME) }
    val state by viewModel?.uiState?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(MetronomeUiState()) }
    val arrangementState by viewModel?.arrangementUiState?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(ArrangementUiState()) }
    val exportDocumentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("audio/mpeg"),
    ) { uri ->
        if (uri == null) viewModel?.cancelArrangementExport()
        else viewModel?.exportArrangementTo(uri)
    }
    val requestArrangementExport: (ArrangementExportOptions) -> Unit = { options ->
        if (viewModel?.beginArrangementExport(options) == true) {
            exportDocumentLauncher.launch(defaultArrangementExportFileName())
        }
    }
    val selectDestination: (AppDestination) -> Unit = { destination ->
        if (destination != currentDestination) {
            viewModel?.stop()
            viewModel?.cancelActiveArrangementExport()
        }
        currentDestination = destination
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        if (maxWidth < 600.dp) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = MaterialTheme.colorScheme.background,
                bottomBar = {
                    CompactNavigationBar(
                        selected = currentDestination,
                        onSelect = selectDestination,
                    )
                },
            ) { contentPadding ->
                DestinationContent(
                    destination = currentDestination,
                    state = state,
                    arrangementState = arrangementState,
                    viewModel = viewModel,
                    onRequestArrangementExport = requestArrangementExport,
                    modifier = Modifier.padding(contentPadding),
                )
            }
        } else {
            NavigationSuiteScaffold(
                navigationSuiteItems = {
                    AppDestination.entries.forEach { destination ->
                        item(
                            icon = {
                                Icon(
                                    painter = painterResource(destination.icon),
                                    contentDescription = destination.contentDescription,
                                )
                            },
                            label = { NavigationLabel(destination) },
                            selected = destination == currentDestination,
                            onClick = { selectDestination(destination) },
                        )
                    }
                },
            ) {
                DestinationContent(
                    destination = currentDestination,
                    state = state,
                    arrangementState = arrangementState,
                    viewModel = viewModel,
                    onRequestArrangementExport = requestArrangementExport,
                )
            }
        }
    }
}

@Composable
private fun CompactNavigationBar(
    selected: AppDestination,
    onSelect: (AppDestination) -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                AppDestination.entries.forEach { destination ->
                    val isSelected = destination == selected
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .clickable(role = Role.Tab) { onSelect(destination) }
                            .semantics {
                                contentDescription = destination.contentDescription
                                stateDescription = if (isSelected) "已选择" else "未选择"
                            }
                            .padding(vertical = 3.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .width(50.dp)
                                .height(27.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                    else Color.Transparent,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(destination.icon),
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        NavigationLabel(destination, compact = true)
                    }
                }
            }
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

@Composable
private fun NavigationLabel(destination: AppDestination, compact: Boolean = false) {
    Text(
        text = destination.chineseLabel,
        fontWeight = FontWeight.SemiBold,
        fontSize = if (compact) 10.sp else 12.sp,
        lineHeight = if (compact) 12.sp else 14.sp,
        maxLines = 1,
    )
}

@Composable
private fun DestinationContent(
    destination: AppDestination,
    state: MetronomeUiState,
    arrangementState: ArrangementUiState,
    viewModel: MetronomeViewModel?,
    onRequestArrangementExport: (ArrangementExportOptions) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (destination) {
        AppDestination.METRONOME -> MetronomeScreen(
            state = state,
            onTogglePlayback = { viewModel?.togglePlayback() },
            onSetCountInEnabled = { viewModel?.setCountInEnabled(it) },
            onSetBpm = { viewModel?.setBpm(it) },
            onAdjustBpm = { viewModel?.adjustBpm(it) },
            onSetTimeSignature = { viewModel?.setTimeSignature(it) },
            onSetSubdivision = { viewModel?.setSubdivision(it) },
            onSetAccentEnabled = { viewModel?.setAccentEnabled(it) },
            onSetPlaybackMode = { viewModel?.setPlaybackMode(it) },
            onSetCustomBeatDivisions = { beat, divisions ->
                viewModel?.setCustomBeatDivisions(beat, divisions)
            },
            onCycleCustomCell = { beat, cell -> viewModel?.cycleCustomCell(beat, cell) },
            onSaveCustomPreset = { viewModel?.saveCustomPreset(it) },
            onApplyCustomPreset = { viewModel?.applyCustomPreset(it) },
            onDeleteCustomPreset = { viewModel?.deleteCustomPreset(it) },
            onConsumeError = { viewModel?.consumeError() },
            onRetryAudio = { viewModel?.start() },
            modifier = modifier,
        )

        AppDestination.ARRANGEMENT -> ArrangementScreen(
            state = arrangementState,
            onTogglePlayback = { viewModel?.toggleArrangementPlayback() },
            onSetCountInEnabled = { viewModel?.setCountInEnabled(it) },
            onPlayFromMeasure = { viewModel?.playArrangementFromMeasure(it) },
            onSelectChange = { viewModel?.selectArrangementChange(it) },
            onAddChange = { viewModel?.addArrangementChange() },
            onDeleteChange = { viewModel?.deleteArrangementChange(it) },
            onSetStartMeasure = { row, measure ->
                viewModel?.setArrangementStartMeasure(row, measure) ?: false
            },
            onSetConfiguration = { row, bpm, meter ->
                viewModel?.setArrangementConfiguration(row, bpm, meter)
            },
            onSetBeatDivisions = { row, beat, divisions ->
                viewModel?.setArrangementBeatDivisions(row, beat, divisions)
            },
            onCycleCell = { row, beat, cell ->
                viewModel?.cycleArrangementCell(row, beat, cell)
            },
            onSavePreset = { viewModel?.saveArrangementPreset(it) },
            onApplyPreset = { viewModel?.applyArrangementPreset(it) },
            onDeletePreset = { viewModel?.deleteArrangementPreset(it) },
            onConsumeError = { viewModel?.consumeArrangementError() },
            onRequestExport = onRequestArrangementExport,
            onCancelExport = { viewModel?.cancelArrangementExport() },
            onConsumeExportResult = { viewModel?.consumeArrangementExportResult() },
            modifier = modifier,
        )

        AppDestination.PROFILE -> ComingSoonScreen(
            title = "我的",
            modifier = modifier,
        )
    }
}

private fun defaultArrangementExportFileName(now: Date = Date()): String =
    "节拍器编排_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(now)}.mp3"

private enum class AppDestination(
    val chineseLabel: String,
    val contentDescription: String,
    val icon: Int,
) {
    METRONOME("节拍器", "节拍器", R.drawable.ic_home),
    ARRANGEMENT("编排", "小节编排", R.drawable.ic_arrangement),
    PROFILE("我的", "我的", R.drawable.ic_account_box),
}

@Composable
private fun ComingSoonScreen(title: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            Text(text = title, style = MaterialTheme.typography.headlineSmall)
            Text(
                text = "即将推出",
                modifier = Modifier.padding(top = 12.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
        }
    }
}
