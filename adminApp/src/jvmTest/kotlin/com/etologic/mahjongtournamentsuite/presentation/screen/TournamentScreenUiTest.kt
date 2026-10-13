package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.navigation.compose.rememberNavController
import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.domain.model.AdminStatus
import com.etologic.mahjongtournamentsuite.domain.model.GlobalUserRole
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.TournamentPlayer
import com.etologic.mahjongtournamentsuite.domain.repository.AdminRepository
import com.etologic.mahjongtournamentsuite.domain.repository.AuthRepository
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerRepository
import com.etologic.mahjongtournamentsuite.presentation.presenter.TournamentsPresenter
import com.etologic.mahjongtournamentsuite.domain.repository.TournamentRepository
import com.etologic.mahjongtournamentsuite.domain.usecase.CalculateTournamentRankingsUseCase
import com.etologic.mahjongtournamentsuite.presentation.presenter.RankingPresenter
import com.etologic.mahjongtournamentsuite.presentation.presenter.TableManagerPresenter
import com.etologic.mahjongtournamentsuite.presentation.presenter.TablesPresenter
import com.etologic.mahjongtournamentsuite.presentation.store.AppMemoryStore
import com.etologic.mahjongtournamentsuite.presentation.sync.ActivityTracker
import com.etologic.mahjongtournamentsuite.presentation.sync.PollSchedule
import com.etologic.mahjongtournamentsuite.presentation.theme.LocalThemeController
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import com.etologic.mahjongtournamentsuite.presentation.theme.ThemeController
import com.etologic.mahjongtournamentsuite.presentation.theme.ThemePreference
import org.koin.compose.KoinContext
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

/**
 * Drives the real tournament screen with a fake server. The virtual clock of the test moves the polling,
 * and the test clock of the activity tracker moves with it.
 */
@OptIn(ExperimentalTestApi::class)
private class ScreenHarness(private val role: GlobalUserRole? = null) {
    val server = FakeTournamentRepository().apply {
        tournaments = listOf(Tournament(id = "t1", name = "Open", isTeams = false, numPlayers = 8, numRounds = 1))
        players = (1..8).map { TournamentPlayer(id = it, name = "Player $it", team = 0) }
        seed(syncTable(version = 1, roundId = 1, tableId = 1))
        seed(syncTable(version = 1, roundId = 1, tableId = 2))
    }
    val clock = TestTimeSource()
    val activity = ActivityTracker(clock)

    // The real pace is 10 s, 5 s with edits, slow after 2 minutes and stop after 5. The test pace is much shorter,
    // because the test clock runs frame by frame and long spans are slow.
    val schedule = PollSchedule(
        activeInterval = 1.seconds,
        editingInterval = 500.milliseconds,
        slowInterval = 2.seconds,
        slowAfter = 20.seconds,
        stopAfter = 30.seconds,
    )

    fun ComposeUiTest.show() {
        setContent {
            val themeController = ThemeController(ThemePreference.Light, false) {}
            // An isolated Koin for each test. The global one would keep the server of the first test.
            val koin = remember {
                koinApplication {
                    modules(
                        module {
                            single { Logger.withTag("ui-test") }
                            single<TournamentRepository> { server }
                            single<PlayerRepository> { NoPlayersRepository() }
                            single<AdminRepository> { FakeAdminRepository(role ?: GlobalUserRole.EDITOR) }
                            single<AuthRepository> { FakeSignedInAuthRepository() }
                            factory { TournamentsPresenter(get(), get(), get()) }
                            single { AppMemoryStore().also { store -> role?.let { store.adminStatus.value = AdminStatus("u1", it) } } }
                            factory { TablesPresenter(get(), get(), get()) }
                            factory { TableManagerPresenter(get(), get()) }
                            factory { RankingPresenter(get(), get(), CalculateTournamentRankingsUseCase(), get()) }
                        },
                    )
                }.koin
            }
            KoinContext(koin) {
                CompositionLocalProvider(LocalThemeController provides themeController) {
                    MtsTheme(useDarkTheme = false) {
                        TournamentScreen(
                            navController = rememberNavController(),
                            tournamentId = "t1",
                            activity = activity,
                            pollSchedule = schedule,
                        )
                    }
                }
            }
        }
    }

    /** Moves the test clock and the activity clock together. */
    fun ComposeUiTest.pass(seconds: Int) {
        clock += seconds.seconds
        mainClock.advanceTimeBy(seconds * 1_000L)
        waitForIdle()
    }

    fun ComposeUiTest.typeInFirstField(text: String) {
        onAllNodes(hasSetTextAction())[0].performTextInput(text)
        waitForIdle()
    }

    fun ComposeUiTest.loadScreen() {
        show()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Round 1 • Table 1").fetchSemanticsNodes().isNotEmpty() }
    }
}

@OptIn(ExperimentalTestApi::class)
class TournamentScreenUiTest {
    @Test
    fun theScreenLoadsTheFirstTableOfTheRound() = runComposeUiTest {
        with(ScreenHarness()) {
            loadScreen()

            onNodeWithText("Round 1 • Table 1").assertExistsOnScreen()
        }
    }

    @Test
    fun anotherUsersSaveShowsADialogAfterTheNextCheck() = runComposeUiTest {
        with(ScreenHarness()) {
            loadScreen()
            server.otherUserSaves(1, 1, handScore = "24")

            pass(2)

            onNodeWithText("Table updated").assertExistsOnScreen()
            assertTrue(server.calls.contains("manifest"))
            assertTrue(server.calls.any { it == "get 1/1 FORCE" })
        }
    }

    @Test
    fun noDialogAppearsWhenNobodyChangedAnything() = runComposeUiTest {
        with(ScreenHarness()) {
            loadScreen()

            pass(5)

            onAllNodesWithText("Table updated").assertCountEquals(0)
            assertTrue(server.calls.count { it == "manifest" } >= 2)
            assertTrue(server.calls.none { it == "get 1/1 FORCE" })
        }
    }

    @Test
    fun okClosesTheDialogAndLeavesTheScreenUsable() = runComposeUiTest {
        with(ScreenHarness()) {
            loadScreen()
            server.otherUserSaves(1, 1, handScore = "24")
            pass(2)

            onNodeWithText("OK").performClick()
            waitForIdle()

            onAllNodesWithText("Table updated").assertCountEquals(0)
            onNodeWithText("Round 1 • Table 1").assertExistsOnScreen()
        }
    }

    @Test
    fun typedValuesAreReportedAsLostWhenSomeoneElseSavesFirst() = runComposeUiTest {
        with(ScreenHarness()) {
            loadScreen()
            typeInFirstField("7")
            onNodeWithText("Save").assertExistsOnScreen()
            server.otherUserSaves(1, 1, handScore = "24")

            pass(1) // checks run at the short pace while there are unsaved edits

            onNodeWithText("Table updated").assertExistsOnScreen()
            onNodeWithText("Round 1 · Table 1: another user changed this table. Your unsaved changes were replaced.").assertExistsOnScreen()
            onAllNodesWithText("Save").assertCountEquals(0)
        }
    }

    @Test
    fun aSaveThatTheServerRejectsShowsWhatWasLost() = runComposeUiTest {
        with(ScreenHarness()) {
            loadScreen()
            typeInFirstField("7")
            server.otherUserSaves(1, 1, handScore = "24") // before the next check

            onNodeWithText("Save").performClick()
            waitForIdle()

            onNodeWithText("Round 1 · Table 1: someone else saved this table first. Your changes were not saved.").assertExistsOnScreen()
        }
    }

    @Test
    fun savingWithoutAConflictShowsNoDialog() = runComposeUiTest {
        with(ScreenHarness()) {
            loadScreen()
            typeInFirstField("7")

            onNodeWithText("Save").performClick()
            waitForIdle()

            onAllNodesWithText("Table updated").assertCountEquals(0)
            assertTrue(server.calls.any { it.startsWith("save 1/1 v1") })
        }
    }

    @Test
    fun leavingATableWithUnsavedChangesAsksFirst() = runComposeUiTest {
        with(ScreenHarness()) {
            loadScreen()
            typeInFirstField("7")

            onNodeWithTag("table-chip-2").performClick()
            waitForIdle()

            onNodeWithText("Unsaved changes").assertExistsOnScreen()
            onAllNodesWithText("Round 1 • Table 2").assertCountEquals(0)
        }
    }

    @Test
    fun discardingTheChangesOpensTheOtherTable() = runComposeUiTest {
        with(ScreenHarness()) {
            loadScreen()
            typeInFirstField("7")
            onNodeWithTag("table-chip-2").performClick()
            waitForIdle()

            onNodeWithText("Discard").performClick()
            waitForIdle()

            onNodeWithText("Round 1 • Table 2").assertExistsOnScreen()
        }
    }

    @Test
    fun cancellingKeepsTheTableAndTheTyping() = runComposeUiTest {
        with(ScreenHarness()) {
            loadScreen()
            typeInFirstField("7")
            onNodeWithTag("table-chip-2").performClick()
            waitForIdle()

            onNodeWithText("Cancel").performClick()
            waitForIdle()

            onNodeWithText("Round 1 • Table 1").assertExistsOnScreen()
            onNodeWithText("Save").assertExistsOnScreen()
        }
    }

    @Test
    fun anIdleUserGetsThePauseDialogAndContinuingChecksAgain() = runComposeUiTest {
        with(ScreenHarness()) {
            loadScreen()

            pass(32)

            onNodeWithText("Updates paused").assertExistsOnScreen()
            val checksBefore = server.calls.count { it == "manifest" }
            server.otherUserSaves(1, 1, handScore = "24")

            onNodeWithText("Continue").performClick()
            waitForIdle()
            mainClock.advanceTimeBy(1_000)
            waitForIdle()

            assertTrue(server.calls.count { it == "manifest" } > checksBefore, "continuing runs one check at once")
            onNodeWithText("Table updated").assertExistsOnScreen()
        }
    }

    @Test
    fun pollingStopsWhileThePauseDialogIsOpen() = runComposeUiTest {
        with(ScreenHarness()) {
            loadScreen()
            pass(32)
            val checks = server.calls.count { it == "manifest" }

            pass(10)

            assertEquals(checks, server.calls.count { it == "manifest" })
        }
    }

    @Test
    fun saveAndNextTableSavesAndOpensTheNextTable() = runComposeUiTest {
        with(ScreenHarness()) {
            loadScreen()
            typeInFirstField("7")

            onNodeWithText("Save and next table").performClick()
            waitForIdle()

            onNodeWithText("Round 1 • Table 2").assertExistsOnScreen()
            assertTrue(server.calls.any { it.startsWith("save 1/1 v1") })
        }
    }

    @Test
    fun saveAndNextTableStaysOnTheTableWhenTheServerRejectsTheSave() = runComposeUiTest {
        with(ScreenHarness()) {
            loadScreen()
            typeInFirstField("7")
            server.otherUserSaves(1, 1, handScore = "24")

            onNodeWithText("Save and next table").performClick()
            waitForIdle()

            onNodeWithText("Round 1 • Table 1").assertExistsOnScreen()
            onAllNodesWithText("Round 1 • Table 2").assertCountEquals(0)
            onNodeWithText("Table updated").assertExistsOnScreen()
        }
    }

    @Test
    fun resetButtonIsOnlyForAdmins() = runComposeUiTest {
        with(ScreenHarness(role = GlobalUserRole.EDITOR)) {
            loadScreen()

            onAllNodesWithText("Reset table").assertCountEquals(0)
        }
    }

    @Test
    fun adminsSeeTheResetButton() = runComposeUiTest {
        with(ScreenHarness(role = GlobalUserRole.ADMIN)) {
            loadScreen()

            onNodeWithText("Reset table").assertExistsOnScreen()
        }
    }

    @Test
    fun aSecondUserSavingBetweenTwoChecksIsStillCaughtByTheNextCheck() = runComposeUiTest {
        with(ScreenHarness()) {
            loadScreen()
            server.otherUserSaves(1, 2, handScore = "8") // a table that is not open

            pass(2)

            onNodeWithText("Table updated").assertExistsOnScreen()
            onNodeWithText("Round 1 · Table 2: another user changed this table. It now shows the latest data.").assertExistsOnScreen()
        }
    }

    @Test
    fun resetAsksForConfirmationAndThenResetsTheOpenTable() = runComposeUiTest {
        with(ScreenHarness(role = GlobalUserRole.ADMIN)) {
            loadScreen()

            onNodeWithText("Reset table").performClick()
            waitForIdle()
            onNodeWithText("Reset Round 1, Table 1?").assertExistsOnScreen()
            assertTrue(server.calls.none { it.startsWith("reset") }, "nothing happens before the confirmation")
            onAllNodesWithText("Reset table").onLast().performClick()
            waitForIdle()

            assertTrue(server.calls.contains("reset 1/1"))
            assertTrue(server.calls.any { it == "round 1 FORCE" }, "the round loads again after the reset")
        }
    }

    @Test
    fun cancellingTheResetChangesNothing() = runComposeUiTest {
        with(ScreenHarness(role = GlobalUserRole.ADMIN)) {
            loadScreen()
            onNodeWithText("Reset table").performClick()
            waitForIdle()

            onNodeWithText("Cancel").performClick()
            waitForIdle()

            assertTrue(server.calls.none { it.startsWith("reset") })
            onAllNodesWithText("Reset Round 1, Table 1?").assertCountEquals(0)
        }
    }

    @Test
    fun savingFromTheUnsavedChangesDialogSavesAndThenOpensTheOtherTable() = runComposeUiTest {
        with(ScreenHarness()) {
            loadScreen()
            typeInFirstField("7")
            onNodeWithTag("table-chip-2").performClick()
            waitForIdle()

            onAllNodesWithText("Save").onLast().performClick()
            waitForIdle()

            assertTrue(server.calls.any { it.startsWith("save 1/1 v1") })
            onNodeWithText("Round 1 • Table 2").assertExistsOnScreen()
        }
    }

    @Test
    fun savingFromTheUnsavedChangesDialogStaysWhenTheServerRejectsTheSave() = runComposeUiTest {
        with(ScreenHarness()) {
            loadScreen()
            typeInFirstField("7")
            onNodeWithTag("table-chip-2").performClick()
            waitForIdle()
            server.otherUserSaves(1, 1, handScore = "24")

            onAllNodesWithText("Save").onLast().performClick()
            waitForIdle()

            onNodeWithText("Round 1 • Table 1").assertExistsOnScreen()
            onAllNodesWithText("Round 1 • Table 2").assertCountEquals(0)
            onNodeWithText("Table updated").assertExistsOnScreen()
        }
    }

    @Test
    fun thePauseDialogNeverAppearsWhileTheUserKeepsWorking() = runComposeUiTest {
        with(ScreenHarness()) {
            loadScreen()

            repeat(8) {
                activity.markActive()
                pass(5)
            }

            onAllNodesWithText("Updates paused").assertCountEquals(0)
        }
    }

    @Test
    fun newTournamentSettingsReachTheScreenAfterThePollSeesThem() = runComposeUiTest {
        with(ScreenHarness()) {
            loadScreen()
            pass(2) // the first check records the revisions
            val loadsBefore = server.calls.count { it == "tournaments FORCE" }

            server.tournamentsRevision = 2
            pass(2)

            assertTrue(server.calls.count { it == "tournaments FORCE" } > loadsBefore)
        }
    }

    @Test
    fun theScreenShowsTheRevisionOfTheResultsItHolds() = runComposeUiTest {
        with(ScreenHarness()) {
            loadScreen()

            // Two tables at version 1: revision 2.
            onNodeWithText("Results r2", substring = true).assertExistsOnScreen()
        }
    }

    @Test
    fun theRevisionRisesWhenSomeoneElseSavesAndThePollSeesIt() = runComposeUiTest {
        with(ScreenHarness()) {
            loadScreen()
            server.otherUserSaves(1, 1, handScore = "24")

            pass(2)
            onNodeWithText("OK").performClick()
            waitForIdle()

            onNodeWithText("Results r3", substring = true).assertExistsOnScreen()
        }
    }

    @Test
    fun theRevisionRisesAfterTheUsersOwnSave() = runComposeUiTest {
        with(ScreenHarness()) {
            loadScreen()
            typeInFirstField("7")

            onNodeWithText("Save").performClick()
            waitForIdle()

            onNodeWithText("Results r3", substring = true).assertExistsOnScreen()
        }
    }

    @Test
    fun theScreenShowsWhenTheServerWasLastAsked() = runComposeUiTest {
        with(ScreenHarness()) {
            loadScreen()
            pass(2)

            onNodeWithText("checked ", substring = true).assertExistsOnScreen()
        }
    }

    @Test
    fun theEmaExportChecksForNewerDataAndUsesTheHeldData() = runComposeUiTest {
        with(ScreenHarness()) {
            loadScreen()
            server.calls.clear()

            onNodeWithText("EMA report").performClick()
            waitForIdle()

            val manifest = server.calls.indexOf("manifest")
            val tables = server.calls.indexOf("all CACHED")
            assertTrue(manifest >= 0 && tables > manifest, "one check first, then the held tables: ${server.calls}")
            assertTrue(server.calls.none { it == "all FORCE" })
        }
    }
}

private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertExistsOnScreen() = assertExists()
