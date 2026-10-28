package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.ImportFileErrorCode
import com.etologic.mahjongtournamentsuite.domain.model.ImportIssue
import com.etologic.mahjongtournamentsuite.domain.model.ImportIssueCode
import com.etologic.mahjongtournamentsuite.domain.model.ImportIssueSeverity
import com.etologic.mahjongtournamentsuite.domain.model.ImportRowPlan
import com.etologic.mahjongtournamentsuite.domain.model.PlayerExportFile
import com.etologic.mahjongtournamentsuite.domain.model.PlayerImportPreview
import com.etologic.mahjongtournamentsuite.domain.model.PlayerImportResult
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerImportRepository
import com.etologic.mahjongtournamentsuite.presentation.platform.SelectedSpreadsheet
import com.etologic.mahjongtournamentsuite.presentation.platform.SpreadsheetPicker
import com.etologic.mahjongtournamentsuite.presentation.presenter.PlayerImportPresenter
import com.etologic.mahjongtournamentsuite.presentation.theme.LocalThemeController
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import com.etologic.mahjongtournamentsuite.presentation.theme.ThemeController
import com.etologic.mahjongtournamentsuite.presentation.theme.ThemePreference
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.koin.compose.KoinContext
import org.koin.dsl.koinApplication
import org.koin.dsl.module

/** The editor chooses a file, reads the preview, confirms, and reads the result. */
@OptIn(ExperimentalTestApi::class)
class PlayerImportDialogUiTest {
    private val originalLocale = java.util.Locale.getDefault()

    @AfterTest
    fun restoreLocale() = java.util.Locale.setDefault(originalLocale)

    private fun row(line: Int, vararg issues: ImportIssue) = ImportRowPlan(
        row = line, slotId = line, emaId = "0000000$line", email = null, team = null, issues = issues.toList(),
    )

    private fun error(code: ImportIssueCode, vararg args: String) = ImportIssue(code, ImportIssueSeverity.ERROR, args.toList())

    private class FakeImportRepository(
        var preview: AppResult<PlayerImportPreview>,
        var result: AppResult<PlayerImportResult>,
    ) : PlayerImportRepository {
        var applyCalls = 0
        override suspend fun export(tournamentId: String): AppResult<PlayerExportFile> = error("Not used.")

        override suspend fun preview(tournamentId: String, fileName: String, bytes: ByteArray) = preview

        override suspend fun apply(tournamentId: String, fileName: String, bytes: ByteArray): AppResult<PlayerImportResult> {
            applyCalls += 1
            return result
        }
    }

    private fun preview(
        vararg rows: ImportRowPlan,
        existingPlayers: Int = 0,
        importedPlayers: Int = rows.size,
        tournamentStarted: Boolean = false,
    ) = PlayerImportPreview(
        rows = rows.toList(),
        valid = rows.none { it.hasError },
        isTeams = false,
        tournamentStarted = tournamentStarted,
        existingPlayers = existingPlayers,
        importedPlayers = importedPlayers,
        hasEmailColumn = false,
        hasTeamColumn = false,
    )

    private val noResult = AppResult.Failure(AppError.Network)

    private fun ComposeUiTest.showDialog(
        repository: PlayerImportRepository,
        onChanged: () -> Unit = {},
        onDismiss: () -> Unit = {},
        pickedFile: Boolean = true,
    ) {
        java.util.Locale.setDefault(java.util.Locale.ENGLISH)
        setContent {
            val themeController = ThemeController(ThemePreference.Light, false, {})
            val koin = remember {
                koinApplication {
                    modules(
                        module {
                            single { Logger.withTag("ui-test") }
                            single<PlayerImportRepository> { repository }
                            factory { PlayerImportPresenter(get(), get()) }
                        },
                    )
                }.koin
            }
            KoinContext(koin) {
                CompositionLocalProvider(LocalThemeController provides themeController) {
                    MtsTheme(useDarkTheme = false) {
                        PlayerImportDialog(
                            tournamentId = "t1",
                            playerNameOf = { null },
                            onChanged = onChanged,
                            onDismiss = onDismiss,
                            pickerFactory = { onSelected, _, onCancelled ->
                                remember {
                                    SpreadsheetPicker {
                                        if (pickedFile) onSelected(SelectedSpreadsheet("players.csv", byteArrayOf(1))) else onCancelled()
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    private fun ComposeUiTest.awaitText(text: String) =
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun theFileChooserOpensAtOnceAndShowsTheCheckOfTheFile() = runComposeUiTest {
        showDialog(FakeImportRepository(AppResult.Success(preview(row(2), row(3))), noResult))

        awaitText("Import (2)")

        onNodeWithText("Players in the file: 2").assertExists()
        onAllNodesWithText("Rows with an error", substring = true).assertCountEquals(0)
    }

    @Test
    fun cancellingTheFileChooserClosesTheDialog() = runComposeUiTest {
        var dismissed = false
        showDialog(FakeImportRepository(AppResult.Success(preview()), noResult), onDismiss = { dismissed = true }, pickedFile = false)

        waitUntil(timeoutMillis = 5_000) { dismissed }
    }

    @Test
    fun anEmptyTournamentImportsAtOnceWithoutAskingAndShowsTheResult() = runComposeUiTest {
        val repository = FakeImportRepository(
            AppResult.Success(preview(row(2), row(3))),
            AppResult.Success(PlayerImportResult(players = 2, changedSlots = 2, emptiedSlots = 3)),
        )
        var changed = false
        showDialog(repository, onChanged = { changed = true })

        awaitText("Import (2)")
        onNodeWithText("Import (2)").performClick()
        awaitText("Import finished")

        onNodeWithText("Players saved: 2").assertExists()
        onNodeWithText("Slots that changed: 2").assertExists()
        onNodeWithText("Slots left empty: 3").assertExists()
        onNodeWithText("The import creates no accounts. Open Invitations to create the accounts.").assertExists()
        onAllNodesWithText("Replace all players?").assertCountEquals(0)
        assertTrue(changed)
        assertEquals(1, repository.applyCalls)
    }

    @Test
    fun theResultHidesTheEmptiedLineWhenNoSlotIsLeftEmpty() = runComposeUiTest {
        val repository = FakeImportRepository(
            AppResult.Success(preview(row(2))),
            AppResult.Success(PlayerImportResult(players = 1, changedSlots = 1, emptiedSlots = 0)),
        )
        showDialog(repository)

        awaitText("Import (1)")
        onNodeWithText("Import (1)").performClick()
        awaitText("Import finished")

        onAllNodesWithText("Slots left empty", substring = true).assertCountEquals(0)
    }

    @Test
    fun aTournamentWithPlayersAsksBeforeItOverwritesThem() = runComposeUiTest {
        val repository = FakeImportRepository(
            AppResult.Success(preview(row(2), row(3), existingPlayers = 4)),
            AppResult.Success(PlayerImportResult(players = 2, changedSlots = 4, emptiedSlots = 2)),
        )
        showDialog(repository)

        awaitText("Import (2)")
        onNodeWithText("Players in the tournament now: 4. The import replaces them with the players of the file.").assertExists()
        onNodeWithText("Import (2)").performClick()
        awaitText("Replace all players?")

        assertEquals(0, repository.applyCalls)
        onNodeWithText(
            "The import replaces all players of the tournament (now: 4) and all team names with the data of the file (players: 2). You cannot undo this.",
        ).assertExists()
        onNodeWithText("Replace").performClick()
        awaitText("Import finished")

        assertEquals(1, repository.applyCalls)
    }

    @Test
    fun cancellingTheConfirmationChangesNothingAndKeepsThePreview() = runComposeUiTest {
        val repository = FakeImportRepository(AppResult.Success(preview(row(2), existingPlayers = 4)), noResult)
        showDialog(repository)

        awaitText("Import (1)")
        onNodeWithText("Import (1)").performClick()
        awaitText("Replace all players?")
        onAllNodesWithText("Cancel").onLast().performClick()
        waitForIdle()

        onAllNodesWithText("Replace all players?").assertCountEquals(0)
        onNodeWithText("Import (1)").assertExists()
        assertEquals(0, repository.applyCalls)
    }

    @Test
    fun aFileWithErrorsListsEveryErrorAndCannotBeImported() = runComposeUiTest {
        val repository = FakeImportRepository(
            AppResult.Success(
                preview(
                    row(2, error(ImportIssueCode.DUPLICATE_EMAIL, "a@b.es")),
                    row(3, error(ImportIssueCode.UNKNOWN_EMA_ID, "00000099"), error(ImportIssueCode.INVALID_EMAIL, "oops")),
                    row(4),
                ),
            ),
            noResult,
        )
        showDialog(repository)

        awaitText("Import (3)")

        onNodeWithText("Import (3)").assertIsNotEnabled()
        onNodeWithText("Rows with an error: 2. Fix them and choose the file again.").assertExists()
        onNodeWithText("Line 2 · Slot 2").assertExists()
        onNodeWithText("Line 3 · Slot 3").assertExists()
        onNodeWithText("The email a@b.es appears in more than one row. One email belongs to one account.").assertExists()
        onNodeWithText("The EMA registry has no player with the number 00000099.").assertExists()
        onNodeWithText("The email oops is not valid.").assertExists()
        onAllNodesWithText("Line 4", substring = true).assertCountEquals(0)
        assertEquals(0, repository.applyCalls)
    }

    @Test
    fun aTournamentWithTableDataBlocksTheImportEvenWithAValidFile() = runComposeUiTest {
        val repository = FakeImportRepository(
            AppResult.Success(preview(row(2), existingPlayers = 4, tournamentStarted = true)),
            noResult,
        )
        showDialog(repository)

        awaitText("Import (1)")

        onNodeWithText("Import (1)").assertIsNotEnabled()
        onNodeWithText("This tournament has table results, so its players cannot change. Use the substitution of a player instead.").assertExists()
        onAllNodesWithText("Players in the tournament now", substring = true).assertCountEquals(0)
    }

    @Test
    fun aWarningDoesNotBlockTheImport() = runComposeUiTest {
        val warning = ImportIssue(ImportIssueCode.TEAM_IGNORED, ImportIssueSeverity.WARNING, emptyList())
        showDialog(FakeImportRepository(AppResult.Success(preview(row(2, warning))), noResult))

        awaitText("Import (1)")

        onNodeWithText("Import (1)").assertIsEnabled()
        onNodeWithText("This tournament has no teams. The app ignores the team name.").assertExists()
    }

    @Test
    fun aServerRefusalOnApplyShowsTheReasonAndKeepsThePreview() = runComposeUiTest {
        val repository = FakeImportRepository(
            AppResult.Success(preview(row(2))),
            AppResult.Failure(AppError.ImportFile(ImportFileErrorCode.TOURNAMENT_STARTED)),
        )
        showDialog(repository)

        awaitText("Import (1)")
        onNodeWithText("Import (1)").performClick()
        awaitText("The tournament has table data, so its players cannot change. Use the substitution of a player instead.")
        onNodeWithText("OK").performClick()
        waitForIdle()

        onNodeWithText("Import (1)").assertExists()
    }

    @Test
    fun aFileErrorShowsTheErrorAndClosesTheDialogOnDismiss() = runComposeUiTest {
        var dismissed = false
        showDialog(
            FakeImportRepository(AppResult.Failure(AppError.Conflict("Bad file")), noResult),
            onDismiss = { dismissed = true },
        )

        awaitText("Bad file")
        onNodeWithText("OK").performClick()

        assertTrue(dismissed)
    }

    @Test
    fun aFileWithMissingColumnsNamesThem() = runComposeUiTest {
        showDialog(
            FakeImportRepository(AppResult.Failure(AppError.ImportFile(ImportFileErrorCode.MISSING_COLUMNS, listOf("id, ema id"))), noResult),
        )

        awaitText("The first line of the file needs these columns: id, ema id.")
    }
}
