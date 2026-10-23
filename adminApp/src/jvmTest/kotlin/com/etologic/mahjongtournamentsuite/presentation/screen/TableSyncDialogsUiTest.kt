package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.etologic.mahjongtournamentsuite.domain.model.TableKey
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import com.etologic.mahjongtournamentsuite.presentation.util.UiText
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class TableSyncDialogsUiTest {
    private val lost = listOf(LostChange(UiText.Plain("Hand 3 · Hand score"), UiText.Plain("24")), LostChange(UiText.Plain("Hand 3 · Player winner id"), UiText.Plain("2")))

    @Test
    fun updateDialogNamesTheTableAndListsEveryLostValue() = runComposeUiTest {
        setContent {
            MtsTheme(useDarkTheme = false) {
                TableUpdatesDialog(listOf(TableUpdateNotice(TableKey(2, 5), lost)), onDismiss = {})
            }
        }

        onNodeWithText("Table updated").assertExists()
        onNodeWithText("Round 2 · Table 5: another user changed this table. Your unsaved changes were replaced.").assertExists()
        onNodeWithText("• Hand 3 · Hand score: 24").assertExists()
        onNodeWithText("• Hand 3 · Player winner id: 2").assertExists()
    }

    @Test
    fun updateDialogForATableWithoutTypingHasNoList() = runComposeUiTest {
        setContent {
            MtsTheme(useDarkTheme = false) {
                TableUpdatesDialog(listOf(TableUpdateNotice(TableKey(1, 1), emptyList())), onDismiss = {})
            }
        }

        onNodeWithText("Round 1 · Table 1: another user changed this table. It now shows the latest data.").assertExists()
        onNodeWithText("Enter these values again if they are still correct:").assertDoesNotExist()
    }

    @Test
    fun rejectedSaveSaysTheChangesWereNotSaved() = runComposeUiTest {
        setContent {
            MtsTheme(useDarkTheme = false) {
                TableUpdatesDialog(listOf(TableUpdateNotice(TableKey(1, 4), lost, saveRejected = true)), onDismiss = {})
            }
        }

        onNodeWithText("Round 1 · Table 4: someone else saved this table first. Your changes were not saved.").assertExists()
    }

    @Test
    fun severalTablesUseThePluralTitleAndShowEveryNotice() = runComposeUiTest {
        setContent {
            MtsTheme(useDarkTheme = false) {
                TableUpdatesDialog(
                    listOf(TableUpdateNotice(TableKey(1, 1), emptyList()), TableUpdateNotice(TableKey(1, 2), lost)),
                    onDismiss = {},
                )
            }
        }

        onNodeWithText("Tables updated").assertExists()
        onNodeWithText("Round 1 · Table 1: another user changed this table. It now shows the latest data.").assertExists()
        onNodeWithText("Round 1 · Table 2: another user changed this table. Your unsaved changes were replaced.").assertExists()
    }

    @Test
    fun okClosesTheUpdateDialog() = runComposeUiTest {
        var dismissed = 0
        setContent {
            MtsTheme(useDarkTheme = false) {
                TableUpdatesDialog(listOf(TableUpdateNotice(TableKey(1, 1), emptyList())), onDismiss = { dismissed++ })
            }
        }

        onNodeWithText("OK").performClick()

        assertEquals(1, dismissed)
    }

    @Test
    fun idleDialogLetsTheUserContinue() = runComposeUiTest {
        var continued = 0
        setContent {
            MtsTheme(useDarkTheme = false) {
                IdlePauseDialog(onContinue = { continued++ }, isChecking = false)
            }
        }

        onNodeWithText("Updates paused").assertExists()
        onNodeWithText("Continue").assertIsEnabled().performClick()

        assertEquals(1, continued)
    }

    @Test
    fun idleDialogCannotBeUsedAgainWhileItChecks() = runComposeUiTest {
        setContent {
            MtsTheme(useDarkTheme = false) {
                IdlePauseDialog(onContinue = {}, isChecking = true)
            }
        }

        onNodeWithText("Checking…").assertIsNotEnabled()
    }
}
