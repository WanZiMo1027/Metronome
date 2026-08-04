package com.yuntian.metronome

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeUp
import com.yuntian.metronome.metronome.PlaybackMode
import com.yuntian.metronome.metronome.MetronomeUiState
import com.yuntian.metronome.metronome.Subdivision
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
                currentSubdivisionIndex = 0,
                activeTimeSignature = TimeSignature.FOUR_FOUR,
                pendingTimeSignature = TimeSignature.THREE_FOUR,
                activeSubdivision = Subdivision.EIGHTH,
                pendingSubdivision = Subdivision.SIXTEENTH,
            ),
        )

        composeRule.onNodeWithText("当前第 3 拍 · 1/2").assertIsDisplayed()
        composeRule.onNodeWithTag("pending_signature")
            .assertTextEquals("4/4 正在播放 · 3/4 下一小节生效")
        composeRule.onNodeWithTag("pending_subdivision")
            .assertTextEquals("八分音符 正在播放 · 十六分音符 下一小节生效")
        composeRule.onNodeWithTag("metronome_content").performScrollToIndex(5)
        composeRule.onNodeWithContentDescription("第一拍重音").assertIsDisplayed()
    }

    @Test
    fun subdivisionDialogShowsSixAccessibleOptionsAndSelectsOne() {
        var selected: Subdivision? = null
        setScreen(onSetSubdivision = { selected = it })

        composeRule.onNodeWithTag("subdivision_button").performClick()
        Subdivision.entries.forEach { subdivision ->
            composeRule.onNodeWithContentDescription(subdivision.accessibilityLabel)
                .assertIsDisplayed()
        }
        composeRule.onNodeWithContentDescription("Swing 长短").performClick()

        assertEquals(Subdivision.SWING_LONG_SHORT, selected)
    }

    @Test
    fun sixEightLaneIncludesThreePlusThreeGroupGap() {
        setScreen(
            state = MetronomeUiState(
                activeTimeSignature = TimeSignature.SIX_EIGHT,
                activeSubdivision = Subdivision.EIGHTH_TRIPLET,
            ),
        )

        composeRule.onNodeWithTag("six_eight_group_gap").assertIsDisplayed()
        composeRule.onNodeWithTag("beat_column_6").assertIsDisplayed()
        composeRule.onNodeWithTag("beat_1_subdivision_2").assertIsDisplayed()
    }

    @Test
    fun swipingBeatCardSelectsCustomMode() {
        var selectedMode = PlaybackMode.PRESET
        setScreen(onSetPlaybackMode = { selectedMode = it })

        composeRule.onNodeWithTag("beat_card_pager").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()

        assertEquals(PlaybackMode.CUSTOM, selectedMode)
        composeRule.onNodeWithTag("beat_card_page_custom").assertIsDisplayed()
    }

    @Test
    fun customModeHidesLegacyAccentSwitch() {
        setScreen(
            state = MetronomeUiState(
                playbackMode = PlaybackMode.CUSTOM,
                activePlaybackMode = PlaybackMode.CUSTOM,
            ),
        )

        composeRule.onNodeWithContentDescription("第一拍重音").assertDoesNotExist()
    }

    @Test
    fun customCellCanBeClickedAndColumnSwipeRequestsNextDivisionCount() {
        var clicked: Pair<Int, Int>? = null
        var resized: Pair<Int, Int>? = null
        setScreen(
            state = MetronomeUiState(
                playbackMode = PlaybackMode.CUSTOM,
                activePlaybackMode = PlaybackMode.CUSTOM,
            ),
            onCycleCustomCell = { beat, cell -> clicked = beat to cell },
            onSetCustomBeatDivisions = { beat, divisions -> resized = beat to divisions },
        )

        composeRule.onNodeWithTag("custom_cell_1_1").performClick()
        assertEquals(0 to 0, clicked)

        composeRule.onNodeWithTag("custom_beat_column_1").performTouchInput { swipeUp() }
        assertEquals(0 to 2, resized)
    }

    @Test
    fun customPresetCanBeNamedAndSaved() {
        var savedName: String? = null
        setScreen(
            state = MetronomeUiState(
                playbackMode = PlaybackMode.CUSTOM,
                activePlaybackMode = PlaybackMode.CUSTOM,
            ),
            onSaveCustomPreset = { savedName = it },
        )

        composeRule.onNodeWithTag("custom_save_button").performClick()
        composeRule.onNodeWithTag("custom_preset_name").performTextInput("练习节奏")
        composeRule.onNodeWithTag("custom_preset_save_confirm").performClick()

        assertEquals("练习节奏", savedName)
    }

    private fun setScreen(
        state: MetronomeUiState = MetronomeUiState(),
        onTogglePlayback: () -> Unit = {},
        onSetSubdivision: (Subdivision) -> Unit = {},
        onSetPlaybackMode: (PlaybackMode) -> Unit = {},
        onSetCustomBeatDivisions: (Int, Int) -> Unit = { _, _ -> },
        onCycleCustomCell: (Int, Int) -> Unit = { _, _ -> },
        onSaveCustomPreset: (String) -> Unit = {},
    ) {
        composeRule.setContent {
            MetronomeTheme {
                MetronomeScreen(
                    state = state,
                    onTogglePlayback = onTogglePlayback,
                    onSetBpm = {},
                    onAdjustBpm = {},
                    onSetTimeSignature = {},
                    onSetSubdivision = onSetSubdivision,
                    onSetAccentEnabled = {},
                    onSetPlaybackMode = onSetPlaybackMode,
                    onSetCustomBeatDivisions = onSetCustomBeatDivisions,
                    onCycleCustomCell = onCycleCustomCell,
                    onSaveCustomPreset = onSaveCustomPreset,
                    onApplyCustomPreset = {},
                    onDeleteCustomPreset = {},
                    onConsumeError = {},
                    onRetryAudio = {},
                )
            }
        }
    }
}
