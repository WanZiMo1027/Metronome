package com.yuntian.metronome

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.yuntian.metronome.metronome.ArrangementChange
import com.yuntian.metronome.metronome.ArrangementMeter
import com.yuntian.metronome.metronome.ArrangementUiState
import com.yuntian.metronome.metronome.BeatPattern
import com.yuntian.metronome.metronome.CellSound
import com.yuntian.metronome.metronome.appendArrangementChange
import com.yuntian.metronome.ui.ArrangementScreen
import com.yuntian.metronome.ui.theme.MetronomeTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ArrangementScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun favoriteDestinationIsReplacedByArrangement() {
        composeRule.setContent { MetronomeTheme { MetronomeApp() } }

        composeRule.onNodeWithText("收藏").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("小节编排").performClick()
        composeRule.onNodeWithText("小节编排").assertIsDisplayed()
        composeRule.onNodeWithTag("arrangement_empty_state").assertIsDisplayed()
    }

    @Test
    fun emptyStateAddsFirstDefaultMeasure() {
        var additions = 0
        setScreen(onAddChange = { additions += 1 })

        composeRule.onNodeWithTag("arrangement_add_button").performClick()

        assertEquals(1, additions)
    }

    @Test
    fun selectedRowSupportsCellCycleAndDivisionSwipe() {
        var cycled: Triple<Int, Int, Int>? = null
        var resized: Triple<Int, Int, Int>? = null
        val changes = appendArrangementChange(appendArrangementChange(emptyList()))
        setScreen(
            state = ArrangementUiState(changes = changes, selectedRowIndex = 0),
            onCycleCell = { row, beat, cell -> cycled = Triple(row, beat, cell) },
            onSetBeatDivisions = { row, beat, divisions ->
                resized = Triple(row, beat, divisions)
            },
        )

        composeRule.onNodeWithTag("arrangement_cell_1_1_1").performClick()
        assertEquals(Triple(0, 0, 0), cycled)

        composeRule.onNodeWithTag("arrangement_beat_1_1").performTouchInput { swipeUp() }
        assertEquals(Triple(0, 0, 2), resized)
    }

    @Test
    fun selectedRowSupportsStartMeasureEditingWithoutFollowingMaximum() {
        var changedStart: Pair<Int, Int>? = null
        val changes = appendArrangementChange(appendArrangementChange(emptyList()))
        setScreen(
            state = ArrangementUiState(changes = changes, selectedRowIndex = 1),
            onSetStartMeasure = { row, measure ->
                changedStart = row to measure
                true
            },
        )

        composeRule.onNodeWithTag("arrangement_measure_2").performClick()
        composeRule.onNodeWithTag("arrangement_start_measure_input").performTextClearance()
        composeRule.onNodeWithTag("arrangement_start_measure_input").performTextInput("5")
        composeRule.onNodeWithTag("arrangement_start_measure_confirm").performClick()
        assertEquals(1 to 5, changedStart)
    }

    @Test
    fun unselectedRowOnlySelectsThenUnlocksAllEditing() {
        val changes = appendArrangementChange(appendArrangementChange(emptyList()))
        var state by mutableStateOf(
            ArrangementUiState(changes = changes, selectedRowIndex = changes.lastIndex),
        )
        var selectedCallback: Int? = null
        var cycled: Triple<Int, Int, Int>? = null

        composeRule.setContent {
            MetronomeTheme {
                ArrangementScreen(
                    state = state,
                    onTogglePlayback = {},
                    onPlayFromMeasure = {},
                    onSelectChange = {
                        selectedCallback = it
                        state = state.copy(selectedRowIndex = it)
                    },
                    onAddChange = {},
                    onDeleteChange = {},
                    onSetStartMeasure = { _, _ -> true },
                    onSetConfiguration = { _, _, _ -> },
                    onSetBeatDivisions = { _, _, _ -> },
                    onCycleCell = { row, beat, cell -> cycled = Triple(row, beat, cell) },
                    onSavePreset = {},
                    onApplyPreset = {},
                    onDeletePreset = {},
                    onConsumeError = {},
                )
            }
        }

        composeRule.onNodeWithTag("arrangement_row_1").assertIsNotSelected()
        composeRule.onNodeWithTag("arrangement_row_2").assertIsSelected()
        composeRule.onNodeWithTag(
            "arrangement_cell_1_1_1",
            useUnmergedTree = true,
        ).assertIsNotEnabled()

        composeRule.onNodeWithTag("arrangement_row_1").performClick()
        composeRule.waitForIdle()
        assertEquals(0, selectedCallback)
        composeRule.onNodeWithTag("arrangement_row_1").assertIsSelected()
        composeRule.onNodeWithTag(
            "arrangement_cell_1_1_1",
            useUnmergedTree = true,
        ).assertIsEnabled().performClick()
        assertEquals(Triple(0, 0, 0), cycled)
    }

    @Test
    fun playbackShowsDynamicMeasureAndLocksEditing() {
        setScreen(
            state = ArrangementUiState(
                changes = listOf(ArrangementChange()),
                selectedRowIndex = 0,
                isPlaying = true,
                currentMeasure = 3,
                currentRowIndex = 0,
                currentBeat = 1,
                currentSubdivisionIndex = 0,
                currentSubdivisionCount = 1,
            ),
        )

        composeRule.onNodeWithTag("arrangement_measure_1").assertTextEquals("3")
        composeRule.onNodeWithTag("arrangement_add_button").assertIsNotEnabled()
        composeRule.onNodeWithTag("arrangement_save_button").assertIsNotEnabled()
        composeRule.onNodeWithText("停止").assertIsDisplayed()
    }

    @Test
    fun silentArrangementCellUsesCrossSymbol() {
        val row = ArrangementChange(
            beats = listOf(
                BeatPattern(listOf(CellSound.SILENT)),
                BeatPattern.normal(),
                BeatPattern.normal(),
                BeatPattern.normal(),
            ),
        )
        setScreen(
            state = ArrangementUiState(
                changes = listOf(row),
                selectedRowIndex = 0,
            ),
        )

        composeRule.onNodeWithText("✕", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun countInSwitchCanBeChangedWhileStopped() {
        var enabled: Boolean? = null
        setScreen(
            state = ArrangementUiState(changes = listOf(ArrangementChange())),
            onSetCountInEnabled = { enabled = it },
        )

        composeRule.onNodeWithTag("arrangement_count_in_switch")
            .assertIsEnabled()
            .performClick()

        assertEquals(true, enabled)
    }

    @Test
    fun arrangementCountInShowsStartMeasureAndLocksTheSwitch() {
        setScreen(
            state = ArrangementUiState(
                changes = listOf(ArrangementChange()),
                countInEnabled = true,
                playbackStartMeasure = 3,
                isPlaying = true,
                isCountIn = true,
                currentMeasure = 3,
                currentRowIndex = 0,
                currentBeat = 1,
                currentSubdivisionIndex = 0,
                currentSubdivisionCount = 1,
            ),
        )

        composeRule.onNodeWithTag("arrangement_count_in_switch").assertIsNotEnabled()
        composeRule.onNodeWithTag("arrangement_count_in_status")
            .assertTextEquals("预备拍 · 第 3 小节")
    }

    @Test
    fun playbackFollowsActiveRowAndBeatThenReturnsToTheBeginning() {
        val meter = ArrangementMeter(numerator = 9, denominator = 8)
        val row = ArrangementChange(meter = meter)
        val changes = List(7) { index -> row.copy(startMeasure = index + 1) }
        var state by mutableStateOf(
            ArrangementUiState(
                changes = changes,
                selectedRowIndex = changes.lastIndex,
                isPlaying = true,
                currentMeasure = changes.size,
                currentRowIndex = changes.lastIndex,
                currentBeat = meter.numerator,
                currentSubdivisionIndex = 0,
                currentSubdivisionCount = 1,
            ),
        )

        composeRule.setContent {
            MetronomeTheme {
                ArrangementScreen(
                    state = state,
                    onTogglePlayback = {},
                    onPlayFromMeasure = {},
                    onSelectChange = {},
                    onAddChange = {},
                    onDeleteChange = {},
                    onSetStartMeasure = { _, _ -> true },
                    onSetConfiguration = { _, _, _ -> },
                    onSetBeatDivisions = { _, _, _ -> },
                    onCycleCell = { _, _, _ -> },
                    onSavePreset = {},
                    onApplyPreset = {},
                    onDeletePreset = {},
                    onConsumeError = {},
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("arrangement_row_7").assertIsDisplayed()
        composeRule.onNodeWithTag("arrangement_beat_7_9").assertIsDisplayed()

        composeRule.runOnIdle {
            state = state.copy(
                currentMeasure = 1,
                currentRowIndex = 0,
                currentBeat = 1,
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("arrangement_row_1").assertIsDisplayed()
        composeRule.onNodeWithTag("arrangement_beat_1_1").assertIsDisplayed()
    }

    @Test
    fun configurationAndPresetDialogsReturnSelectedValues() {
        var configured: Triple<Int, Int, ArrangementMeter>? = null
        var savedName: String? = null
        setScreen(
            state = ArrangementUiState(
                changes = listOf(ArrangementChange()),
                selectedRowIndex = 0,
            ),
            onSetConfiguration = { row, bpm, meter -> configured = Triple(row, bpm, meter) },
            onSavePreset = { savedName = it },
        )

        composeRule.onNodeWithTag("arrangement_config_1").performClick()
        composeRule.onNodeWithTag("arrangement_config_confirm").performClick()
        assertEquals(Triple(0, 120, ArrangementMeter()), configured)

        composeRule.onNodeWithTag("arrangement_save_button").performClick()
        composeRule.onNodeWithTag("arrangement_preset_name").performTextInput("数摇段落")
        composeRule.onNodeWithTag("arrangement_preset_save_confirm").performClick()
        assertEquals("数摇段落", savedName)
        assertTrue(savedName!!.length <= 30)
    }

    @Test
    fun measureDrawerListsMeasuresAndStartsTappedMeasure() {
        var requestedMeasure: Int? = null
        val changes = listOf(
            ArrangementChange(startMeasure = 1),
            ArrangementChange(startMeasure = 8),
        )
        setScreen(
            state = ArrangementUiState(
                changes = changes,
                selectedRowIndex = 0,
                playbackStartMeasure = 5,
            ),
            onPlayFromMeasure = { requestedMeasure = it },
        )

        composeRule.onNodeWithTag("arrangement_add_button").assertIsDisplayed().assertIsEnabled()
        composeRule.onNodeWithTag("arrangement_start_stop").assertIsDisplayed().assertIsEnabled()
        composeRule.onNodeWithTag("arrangement_measure_drawer_toggle").performClick()
        composeRule.onNodeWithTag("arrangement_measure_drawer").assertIsDisplayed()
        composeRule.onNodeWithTag("arrangement_add_button").assertIsDisplayed().assertIsEnabled()
        composeRule.onNodeWithTag("arrangement_start_stop").assertIsDisplayed().assertIsEnabled()
        composeRule.onNodeWithText("起播：第 5 小节").assertIsDisplayed()
        composeRule.onNodeWithText("共 8 小节").assertIsDisplayed()
        composeRule.onNodeWithTag("arrangement_playback_measure_5").assertIsSelected()
        composeRule.onNodeWithTag("arrangement_playback_measure_8").assertIsDisplayed()

        composeRule.onNodeWithTag("arrangement_playback_measure_7").performClick()
        composeRule.waitForIdle()
        assertEquals(7, requestedMeasure)
        composeRule.onNodeWithTag("arrangement_measure_drawer").assertDoesNotExist()
        composeRule.onNodeWithTag("arrangement_add_button").assertIsDisplayed().assertIsEnabled()
        composeRule.onNodeWithTag("arrangement_start_stop").assertIsDisplayed().assertIsEnabled()
    }

    @Test
    fun tappingMeasureWhileStoppedTransitionsToPlayingState() {
        val changes = listOf(
            ArrangementChange(startMeasure = 1),
            ArrangementChange(startMeasure = 4),
        )
        var requestedMeasure: Int? = null
        var state by mutableStateOf(
            ArrangementUiState(
                changes = changes,
                selectedRowIndex = 0,
                playbackStartMeasure = 1,
            ),
        )

        composeRule.setContent {
            MetronomeTheme {
                ArrangementScreen(
                    state = state,
                    onTogglePlayback = {},
                    onPlayFromMeasure = { measure ->
                        requestedMeasure = measure
                        state = state.copy(
                            playbackStartMeasure = measure,
                            isPlaying = true,
                            currentMeasure = measure,
                            currentRowIndex = changes.indexOfLast { it.startMeasure <= measure },
                            currentBeat = 1,
                            currentSubdivisionIndex = 0,
                            currentSubdivisionCount = 1,
                        )
                    },
                    onSelectChange = {},
                    onAddChange = {},
                    onDeleteChange = {},
                    onSetStartMeasure = { _, _ -> true },
                    onSetConfiguration = { _, _, _ -> },
                    onSetBeatDivisions = { _, _, _ -> },
                    onCycleCell = { _, _, _ -> },
                    onSavePreset = {},
                    onApplyPreset = {},
                    onDeletePreset = {},
                    onConsumeError = {},
                )
            }
        }

        composeRule.onNodeWithText("开始").assertIsDisplayed()
        composeRule.onNodeWithTag("arrangement_measure_drawer_toggle").performClick()
        composeRule.onNodeWithTag("arrangement_playback_measure_3").performClick()
        composeRule.waitForIdle()

        assertEquals(3, requestedMeasure)
        composeRule.onNodeWithText("停止").assertIsDisplayed()
        composeRule.onNodeWithTag("arrangement_measure_drawer").assertDoesNotExist()
    }

    @Test
    fun measureDrawerIsDisabledWhenArrangementIsEmpty() {
        setScreen()
        composeRule.onNodeWithTag("arrangement_measure_drawer_toggle").assertIsNotEnabled()
        composeRule.onNodeWithTag("arrangement_add_button").assertIsDisplayed().assertIsEnabled()
        composeRule.onNodeWithTag("arrangement_start_stop").assertIsDisplayed().assertIsNotEnabled()
    }

    @Test
    fun measureDrawerRemainsAvailableDuringPlayback() {
        setScreen(
            state = ArrangementUiState(
                changes = listOf(
                    ArrangementChange(startMeasure = 1),
                    ArrangementChange(startMeasure = 12),
                ),
                selectedRowIndex = 0,
                playbackStartMeasure = 5,
                isPlaying = true,
                currentMeasure = 12,
                currentRowIndex = 1,
                currentBeat = 1,
                currentSubdivisionIndex = 0,
                currentSubdivisionCount = 1,
            ),
        )
        composeRule.onNodeWithTag("arrangement_measure_drawer_toggle").performClick()
        composeRule.onNodeWithText("当前：第 12 小节").assertIsDisplayed()
        composeRule.onNodeWithTag("arrangement_playback_measure_12").assertIsDisplayed()
        composeRule.onNodeWithTag("arrangement_playback_measure_5").assertIsSelected()
    }

    private fun setScreen(
        state: ArrangementUiState = ArrangementUiState(),
        onPlayFromMeasure: (Int) -> Unit = {},
        onSetCountInEnabled: (Boolean) -> Unit = {},
        onSelectChange: (Int) -> Unit = {},
        onAddChange: () -> Unit = {},
        onSetStartMeasure: (Int, Int) -> Boolean = { _, _ -> true },
        onSetConfiguration: (Int, Int, ArrangementMeter) -> Unit = { _, _, _ -> },
        onSetBeatDivisions: (Int, Int, Int) -> Unit = { _, _, _ -> },
        onCycleCell: (Int, Int, Int) -> Unit = { _, _, _ -> },
        onSavePreset: (String) -> Unit = {},
    ) {
        composeRule.setContent {
            MetronomeTheme {
                ArrangementScreen(
                    state = state,
                    onTogglePlayback = {},
                    onSetCountInEnabled = onSetCountInEnabled,
                    onPlayFromMeasure = onPlayFromMeasure,
                    onSelectChange = onSelectChange,
                    onAddChange = onAddChange,
                    onDeleteChange = {},
                    onSetStartMeasure = onSetStartMeasure,
                    onSetConfiguration = onSetConfiguration,
                    onSetBeatDivisions = onSetBeatDivisions,
                    onCycleCell = onCycleCell,
                    onSavePreset = onSavePreset,
                    onApplyPreset = {},
                    onDeletePreset = {},
                    onConsumeError = {},
                )
            }
        }
    }
}
