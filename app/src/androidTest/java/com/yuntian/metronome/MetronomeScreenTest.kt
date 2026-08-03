package com.yuntian.metronome

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.yuntian.metronome.metronome.MetronomeUiState
import com.yuntian.metronome.metronome.TimeSignature
import com.yuntian.metronome.ui.MetronomeScreen
import com.yuntian.metronome.ui.theme.MetronomeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MetronomeScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun coreControlsAreVisibleAndStartCanBeTriggered() {
        var toggles = 0
        setScreen(onTogglePlayback = { toggles += 1 })

        composeRule.onNodeWithText("120").assertIsDisplayed()
        composeRule.onNodeWithText("准备就绪").assertIsDisplayed()
        composeRule.onNodeWithTag("bpm_slider").assertIsDisplayed()
        composeRule.onNodeWithText("开始").performClick()

        assertEquals(1, toggles)
    }

    @Test
    fun invalidManualBpmShowsRangeError() {
        setScreen()

        composeRule.onNodeWithTag("bpm_display").performClick()
        composeRule.onNodeWithTag("bpm_input").performTextClearance()
        composeRule.onNodeWithTag("bpm_input").performTextInput("12")
        composeRule.onNodeWithText("确定").performClick()

        composeRule.onNodeWithText("请输入 30–300 之间的整数").assertIsDisplayed()
    }

    @Test
    fun pendingSignatureAndActiveBeatAreAnnounced() {
        setScreen(
            state = MetronomeUiState(
                isPlaying = true,
                currentBeat = 3,
                activeTimeSignature = TimeSignature.FOUR_FOUR,
                pendingTimeSignature = TimeSignature.THREE_FOUR,
            ),
        )

        composeRule.onNodeWithText("当前第 3 拍").assertIsDisplayed()
        composeRule.onNodeWithTag("pending_signature")
            .assertTextEquals("4/4 正在播放 · 3/4 下一小节生效")
        composeRule.onNodeWithContentDescription("第一拍重音").assertIsDisplayed()
    }

    private fun setScreen(
        state: MetronomeUiState = MetronomeUiState(),
        onTogglePlayback: () -> Unit = {},
    ) {
        composeRule.setContent {
            MetronomeTheme {
                MetronomeScreen(
                    state = state,
                    onTogglePlayback = onTogglePlayback,
                    onSetBpm = {},
                    onAdjustBpm = {},
                    onSetTimeSignature = {},
                    onSetAccentEnabled = {},
                    onConsumeError = {},
                    onRetryAudio = {},
                )
            }
        }
    }
}
