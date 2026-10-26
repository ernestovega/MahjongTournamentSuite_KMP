package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.ImportRowPlan
import com.etologic.mahjongtournamentsuite.domain.model.ImportRowStatus
import com.etologic.mahjongtournamentsuite.domain.model.PlayerImportFailure
import com.etologic.mahjongtournamentsuite.domain.model.PlayerExportFile
import com.etologic.mahjongtournamentsuite.domain.model.PlayerImportPreview
import com.etologic.mahjongtournamentsuite.domain.model.PlayerImportResult
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerImportRepository
import com.etologic.mahjongtournamentsuite.presentation.platform.SelectedSpreadsheet
import com.etologic.mahjongtournamentsuite.presentation.platform.SpreadsheetPicker
import com.etologic.mahjongtournamentsuite.presentation.platform.SpreadsheetSaver
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

    private fun row(line: Int, status: ImportRowStatus) = ImportRowPlan(
        row = line, slotId = line, emaId = "0000000$line", email = null, team = null,
        status = status, issues = emptyList(), current = null,
    )

    private class FakeImportRepository(
        var preview: AppResult<PlayerImportPreview>,
        var result: AppResult<PlayerImportResult>,
    ) : PlayerImportRepository {
        var applied: Pair<Boolean, Int>? = null
        var exportResult: AppResult<PlayerExportFile> = AppResult.Success(PlayerExportFile("players.xlsx", byteArrayOf(7, 8)))
        var exportedTournament: String? = null

        override suspend fun export(tournamentId: String): AppResult<PlayerExportFile> {
            exportedTournament = tournamentId
            return exportResult
        }

        override suspend fun preview(tournamentId: String, fileName: String, bytes: ByteArray) = preview

        override suspend fun apply(
            tournamentId: String,
            fileName: String,
            bytes: ByteArray,
            preview: PlayerImportPreview,
            replaceExisting: Boolean,
            onProgress: (done: Int, total: Int) -> Unit,
        ): AppResult<PlayerImportResult> {
            applied = replaceExisting to preview.rows.size
            onProgress(1, 1)
            return result
        }
    }

    private fun preview(vararg rows: ImportRowPlan) = PlayerImportPreview(
        rows = rows.toList(), untouchedSlotIds = emptyList(), isTeams = false,
        tournamentStarted = false, hasEmailColumn = false, hasTeamColumn = false,
    )

    private fun ComposeUiTest.showDialog(
        repository: PlayerImportRepository,
        onChanged: () -> Unit = {},
        onDismiss: () -> Unit = {},
        saved: MutableList<Pair<String, List<Byte>>> = mutableListOf(),
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
                            saverFactory = { _ ->
                                remember { SpreadsheetSaver { fileName, bytes -> saved += fileName to bytes.toList() } }
                            },
                            pickerFactory = { onSelected, _ ->
                                remember { SpreadsheetPicker { onSelected(SelectedSpreadsheet("players.csv", byteArrayOf(1))) } }
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
    fun introListsTheColumnsAndTheFileIsChosenFromThere() = runComposeUiTest {
        showDialog(FakeImportRepository(AppResult.Success(preview(row(2, ImportRowStatus.NEW))), AppResult.Failure(AppError.Network)))

        onNodeWithText("• id: the number of the player slot. This column is required.").assertExists()
        onNodeWithText("• ema id: the EMA number of the player. This column is required.").assertExists()
        onNodeWithText("Choose file").assertExists()
    }

    @Test
    fun confirmingAppliesTheNewRowsAndShowsTheResult() = runComposeUiTest {
        val repository = FakeImportRepository(
            AppResult.Success(preview(row(2, ImportRowStatus.NEW), row(3, ImportRowStatus.NEW))),
            AppResult.Success(PlayerImportResult(2, 1, 0, 0, listOf(PlayerImportFailure(3, 2, "boom")))),
        )
        var changed = false
        showDialog(repository, onChanged = { changed = true })

        onNodeWithText("Choose file").performClick()
        awaitText("Import 2 rows")
        onNodeWithText("Import 2 rows").performClick()
        awaitText("Import finished")

        onNodeWithText("Players assigned: 2").assertExists()
        onNodeWithText("Emails saved: 1").assertExists()
        onNodeWithText("• Line 3: boom").assertExists()
        assertTrue(changed)
        assertEquals(false to 2, repository.applied)
    }

    @Test
    fun replacingPlayersNeedsTheCheckbox() = runComposeUiTest {
        val repository = FakeImportRepository(
            AppResult.Success(preview(row(2, ImportRowStatus.REPLACE))),
            AppResult.Success(PlayerImportResult(1, 0, 0, 0, emptyList())),
        )
        showDialog(repository)

        onNodeWithText("Choose file").performClick()
        awaitText("Import 0 rows")
        onNodeWithText("Import 0 rows").assertIsNotEnabled()

        onNodeWithText("Replace the players that are in a slot already (1 rows)").performClick()
        onNodeWithText("Import 1 rows").assertIsEnabled()
        onNodeWithText("Import 1 rows").performClick()
        awaitText("Import finished")

        assertEquals(true to 1, repository.applied)
    }

    @Test
    fun aFileErrorShowsTheErrorAndKeepsTheChooser() = runComposeUiTest {
        showDialog(FakeImportRepository(AppResult.Failure(AppError.Conflict("Bad file")), AppResult.Failure(AppError.Network)))

        onNodeWithText("Choose file").performClick()
        awaitText("Bad file")

        onNodeWithText("Choose file").assertExists()
    }

    @Test
    fun aFileWithOnlyErrorsCannotBeImported() = runComposeUiTest {
        showDialog(
            FakeImportRepository(AppResult.Success(preview(row(2, ImportRowStatus.ERROR))), AppResult.Failure(AppError.Network)),
        )

        onNodeWithText("Choose file").performClick()
        awaitText("Import 0 rows")

        onNodeWithText("Import 0 rows").assertIsNotEnabled()
    }

    @Test
    fun theDownloadButtonSavesTheCurrentPlayersFile() = runComposeUiTest {
        val repository = FakeImportRepository(AppResult.Success(preview()), AppResult.Failure(AppError.Network))
        val saved = mutableListOf<Pair<String, List<Byte>>>()
        showDialog(repository, saved = saved)

        onNodeWithText("Download players file").performClick()
        waitUntil(timeoutMillis = 5_000) { saved.isNotEmpty() }

        assertEquals(listOf("players.xlsx" to listOf<Byte>(7, 8)), saved)
        assertEquals("t1", repository.exportedTournament)
        onNodeWithText("Choose file").assertExists()
    }

    @Test
    fun aFailedDownloadShowsTheErrorAndSavesNothing() = runComposeUiTest {
        val repository = FakeImportRepository(AppResult.Success(preview()), AppResult.Failure(AppError.Network))
        repository.exportResult = AppResult.Failure(AppError.Conflict("Cannot export"))
        val saved = mutableListOf<Pair<String, List<Byte>>>()
        showDialog(repository, saved = saved)

        onNodeWithText("Download players file").performClick()
        awaitText("Cannot export")

        assertTrue(saved.isEmpty())
        assertEquals("t1", repository.exportedTournament)
    }
}
