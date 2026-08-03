package com.yuntian.metronome

import android.os.Bundle
import android.graphics.Color as AndroidColor
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yuntian.metronome.metronome.MetronomeUiState
import com.yuntian.metronome.metronome.MetronomeViewModel
import com.yuntian.metronome.ui.MetronomeScreen
import com.yuntian.metronome.ui.theme.MetronomeTheme

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
        if (!isChangingConfigurations) metronomeViewModel.stop()
        super.onStop()
    }

    override fun onUserLeaveHint() {
        metronomeViewModel.stop()
        super.onUserLeaveHint()
    }
}

@PreviewScreenSizes
@Composable
fun MetronomeApp(viewModel: MetronomeViewModel? = null) {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestination.METRONOME) }
    val state by viewModel?.uiState?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(MetronomeUiState()) }

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
                    label = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = destination.chineseLabel,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp,
                            )
                            Text(text = destination.englishLabel, fontSize = 9.sp)
                        }
                    },
                    selected = destination == currentDestination,
                    onClick = {
                        if (destination != AppDestination.METRONOME) viewModel?.stop()
                        currentDestination = destination
                    },
                )
            }
        },
    ) {
        when (currentDestination) {
            AppDestination.METRONOME -> MetronomeScreen(
                state = state,
                onTogglePlayback = { viewModel?.togglePlayback() },
                onSetBpm = { viewModel?.setBpm(it) },
                onAdjustBpm = { viewModel?.adjustBpm(it) },
                onSetStep = { viewModel?.setStep(it) },
                onSetTimeSignature = { viewModel?.setTimeSignature(it) },
                onSetAccentEnabled = { viewModel?.setAccentEnabled(it) },
                onConsumeError = { viewModel?.consumeError() },
                onRetryAudio = { viewModel?.start() },
                modifier = Modifier,
            )

            AppDestination.FAVORITES -> ComingSoonScreen(
                title = "收藏",
                englishTitle = "FAVORITES",
                modifier = Modifier,
            )

            AppDestination.PROFILE -> ComingSoonScreen(
                title = "我的",
                englishTitle = "PROFILE",
                modifier = Modifier,
            )
        }
    }
}

private enum class AppDestination(
    val chineseLabel: String,
    val englishLabel: String,
    val contentDescription: String,
    val icon: Int,
) {
    METRONOME("节拍器", "Metronome", "节拍器 Metronome", R.drawable.ic_home),
    FAVORITES("收藏", "Favorites", "收藏 Favorites", R.drawable.ic_favorite),
    PROFILE("我的", "Profile", "我的 Profile", R.drawable.ic_account_box),
}

@Composable
private fun ComingSoonScreen(title: String, englishTitle: String, modifier: Modifier = Modifier) {
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
            Text(text = englishTitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Text(text = title, style = MaterialTheme.typography.headlineSmall)
            Text(
                text = "即将推出 · Coming soon",
                modifier = Modifier.padding(top = 12.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
        }
    }
}
