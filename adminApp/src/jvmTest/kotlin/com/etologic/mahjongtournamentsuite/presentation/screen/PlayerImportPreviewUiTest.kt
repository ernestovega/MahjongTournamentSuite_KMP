package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.etologic.mahjongtournamentsuite.domain.model.ImportIssue
import com.etologic.mahjongtournamentsuite.domain.model.ImportIssueCode
import com.etologic.mahjongtournamentsuite.domain.model.ImportIssueSeverity
import com.etologic.mahjongtournamentsuite.domain.model.ImportRowPlan
import com.etologic.mahjongtournamentsuite.domain.model.ImportRowStatus
import com.etologic.mahjongtournamentsuite.domain.model.ImportSlotState
import com.etologic.mahjongtournamentsuite.domain.model.PlayerImportPreview
import com.etologic.mahjongtournamentsuite.presentation.theme.LocalThemeController
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import com.etologic.mahjongtournamentsuite.presentation.theme.ThemeController
import com.etologic.mahjongtournamentsuite.presentation.theme.ThemePreference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The preview of a player import shows the counts, the problems of each row and the replace choice. */
@OptIn(ExperimentalTestApi::class)
class PlayerImportPreviewUiTest {
    private val preview = PlayerImportPreview(
        rows = listOf(
            ImportRowPlan(
                row = 2, slotId = 1, emaId = "00000001", email = "new@b.es", team = "Reds",
                status = ImportRowStatus.REPLACE,
                issues = listOf(
                    ImportIssue(ImportIssueCode.REPLACES_PLAYER, ImportIssueSeverity.WARNING, emptyList()),
                    ImportIssue(ImportIssueCode.EMAIL_KEPT_ACCOUNT, ImportIssueSeverity.WARNING, listOf("old@b.es", "new@b.es")),
                ),
                current = ImportSlotState("00000002", "old@b.es", "Team 1"),
            ),
            ImportRowPlan(
                row = 3, slotId = 2, emaId = "00000003", email = null, team = null,
                status = ImportRowStatus.NEW, issues = emptyList(), createsAccount = true, current = ImportSlotState(null, null, null),
            ),
            ImportRowPlan(
                row = 5, slotId = 3, emaId = "00000004", email = "same@b.es", team = null,
                status = ImportRowStatus.UNCHANGED, issues = emptyList(),
                current = ImportSlotState("00000004", "same@b.es", null),
            ),
            ImportRowPlan(
                row = 4, slotId = null, emaId = null, email = null, team = null,
                status = ImportRowStatus.ERROR,
                issues = listOf(ImportIssue(ImportIssueCode.MISSING_EMA_ID, ImportIssueSeverity.ERROR, emptyList())),
                current = null,
            ),
        ),
        untouchedSlotIds = listOf(3, 4),
        isTeams = true,
        tournamentStarted = false,
        hasEmailColumn = true,
        hasTeamColumn = true,
    )

    @Test
    fun countsRowsByStatus() {
        assertEquals(1, preview.newCount)
        assertEquals(1, preview.replaceCount)
        assertEquals(1, preview.errorCount)
        assertEquals(1, preview.unchangedCount)
    }

    @Test
    fun showsProblemsAndReplaceChoice() = runComposeUiTest {
        java.util.Locale.setDefault(java.util.Locale.ENGLISH)
        var replace by mutableStateOf(false)
        setContent {
            val themeController = ThemeController(ThemePreference.Light, false, {})
            CompositionLocalProvider(LocalThemeController provides themeController) {
                MtsTheme(useDarkTheme = false) {
                    ImportPreviewContent(
                        preview = preview,
                        replaceExisting = replace,
                        onReplaceExistingChange = { replace = it },
                        playerNameOf = { emaId -> if (emaId == "00000002") "ANA PEREZ" else null },
                    )
                }
            }
        }

        onNodeWithText("Rows with an error: 1. The import skips these rows.").assertExists()
        onNodeWithText("The EMA number is empty.").assertExists()
        // A row shows only what changes: the old value and the new value.
        onNodeWithText("ANA PEREZ · 00000002").assertExists()
        onNodeWithText("EMA 00000001").assertExists()
        onNodeWithText("Team 1").assertExists()
        onNodeWithText("Reds").assertExists()
        onNodeWithText("EMA 00000003").assertExists()
        onNodeWithText("The account of this player has the email old@b.es. The account email stays and the email new@b.es is not saved.").assertExists()
        onNodeWithText("The app creates the account of this player.").assertExists()
        // The rows without change do not show.
        onNodeWithText("Line 5 · Slot 3").assertDoesNotExist()
        onNodeWithText("2 slots are not in the file. They stay as they are.").assertExists()
        onNodeWithText("If you do not select this box, the import skips the rows that replace a player.").assertExists()

        onNodeWithText("Replace the players that are in a slot already (1 rows)").performClick()
        assertTrue(replace)
    }
}
