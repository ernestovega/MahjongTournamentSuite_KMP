package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.domain.model.AdminStatus
import com.etologic.mahjongtournamentsuite.domain.model.GlobalUserRole
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.TournamentPlayer
import com.etologic.mahjongtournamentsuite.domain.model.TournamentRound
import com.etologic.mahjongtournamentsuite.domain.repository.AdminRepository
import com.etologic.mahjongtournamentsuite.domain.repository.AuthRepository
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerAccountRepository
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerRepository
import com.etologic.mahjongtournamentsuite.domain.repository.TournamentRepository
import com.etologic.mahjongtournamentsuite.domain.usecase.CalculateTournamentRankingsUseCase
import com.etologic.mahjongtournamentsuite.presentation.ActivityRoute
import com.etologic.mahjongtournamentsuite.presentation.PlayerCountRoute
import com.etologic.mahjongtournamentsuite.presentation.PlayersRoute
import com.etologic.mahjongtournamentsuite.presentation.RankingControlRoute
import com.etologic.mahjongtournamentsuite.presentation.RoundControlRoute
import com.etologic.mahjongtournamentsuite.presentation.StatisticsRoute
import com.etologic.mahjongtournamentsuite.presentation.TournamentRoute
import com.etologic.mahjongtournamentsuite.presentation.TournamentsRoute
import com.etologic.mahjongtournamentsuite.presentation.platform.LocalScrollPositionStore
import com.etologic.mahjongtournamentsuite.presentation.platform.ScrollPositionStore
import com.etologic.mahjongtournamentsuite.presentation.platform.SessionStateStorage
import com.etologic.mahjongtournamentsuite.presentation.presenter.PlayersPresenter
import com.etologic.mahjongtournamentsuite.presentation.presenter.RankingPresenter
import com.etologic.mahjongtournamentsuite.presentation.presenter.TableManagerPresenter
import com.etologic.mahjongtournamentsuite.presentation.presenter.TablesPresenter
import com.etologic.mahjongtournamentsuite.presentation.presenter.TournamentsPresenter
import com.etologic.mahjongtournamentsuite.presentation.store.AppMemoryStore
import com.etologic.mahjongtournamentsuite.presentation.sync.ActivityTracker
import com.etologic.mahjongtournamentsuite.presentation.sync.PollSchedule
import com.etologic.mahjongtournamentsuite.presentation.theme.LocalThemeController
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import com.etologic.mahjongtournamentsuite.presentation.theme.ThemeController
import com.etologic.mahjongtournamentsuite.presentation.theme.ThemePreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.koin.compose.KoinContext
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
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
private class ScreenHarness(
    private val role: GlobalUserRole? = null,
    tableCount: Int = 2,
    /** The table saved from an earlier visit, restored as after a page refresh. */
    private val savedTableId: Int? = null,
    /** Tables that take only the final scores, without hands. */
    useTotalsOnly: Boolean = false,
    /** Opens the screen from the tournament list, so Back has somewhere to go. */
    private val fromList: Boolean = false,
    roundCount: Int = 1,
) {
    val server = FakeTournamentRepository().apply {
        tournaments = listOf(Tournament(id = "t1", name = "Open", isTeams = false, numPlayers = 8, numRounds = 1))
        players = (1..8).map { TournamentPlayer(id = it, name = "Player $it", team = 0) }
        rounds = (1..roundCount).map { TournamentRound(it) }
        for (round in 1..roundCount) {
            (1..tableCount).forEach { seed(syncTable(version = 1, roundId = round, tableId = it, useTotalsOnly = useTotalsOnly)) }
        }
    }
    val clock = TestTimeSource()
    private var openedFromList = 0
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
            val themeController = ThemeController(ThemePreference.Light, false, {})
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
                            single<PlayerAccountRepository> { FakePlayerAccountRepository() }
                            factory { PlayersPresenter(get(), get(), get(), get()) }
                            factory { TableManagerPresenter(get(), get()) }
                            factory { RankingPresenter(get(), get(), CalculateTournamentRankingsUseCase(), get()) }
                        },
                    )
                }.koin
            }
            val scrollPositions = remember {
                savedTableId?.let { tableId ->
                    ScrollPositionStore(
                        object : SessionStateStorage {
                            private val saved = mutableMapOf("scrollPositions" to "1,0,/value:round\n$tableId,0,/value:table")
                            override fun read(name: String) = saved[name]
                            override fun write(name: String, value: String) {
                                saved[name] = value
                            }
                        },
                    )
                }
            }
            KoinContext(koin) {
                CompositionLocalProvider(
                    LocalThemeController provides themeController,
                    LocalScrollPositionStore provides scrollPositions,
                ) {
                    MtsTheme(useDarkTheme = false) {
                        // A real graph, so the screens that the tournament screen opens (Round control) open too.
                        val navController = rememberNavController()
                        NavHost(navController, startDestination = if (fromList) TournamentsRoute else TournamentRoute("t1")) {
                            // The other screens are stubs that say where the navigation went.
                            composable<TournamentsRoute> {
                                Text("Tournament list")
                                LaunchedEffect(Unit) { if (fromList && openedFromList++ == 0) navController.navigate(TournamentRoute("t1")) }
                            }
                            composable<PlayersRoute> { Text("Players of ${it.toRoute<PlayersRoute>().tournamentId}") }
                            composable<ActivityRoute> { Text("Activity of ${it.toRoute<ActivityRoute>().tournamentId}") }
                            composable<PlayerCountRoute> { Text("Player count of ${it.toRoute<PlayerCountRoute>().tournamentId}") }
                            composable<StatisticsRoute> {
                                val args = it.toRoute<StatisticsRoute>()
                                Text("Stats ${args.scope} ${args.roundId} ${args.tableId}")
                            }
                            composable<TournamentRoute> {
                                TournamentScreen(
                                    navController = navController,
                                    tournamentId = "t1",
                                    activity = activity,
                                    pollSchedule = schedule,
                                )
                            }
                            composable<RankingControlRoute> { Text("Ranking control of ${it.toRoute<RankingControlRoute>().tournamentId}") }
                            composable<RoundControlRoute> { entry ->
                                val args = entry.toRoute<RoundControlRoute>()
                                RoundControlScreen(args.tournamentId, args.round, onBack = { navController.popBackStack() })
                            }
                        }
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

    fun ComposeUiTest.loadScreen(tableId: Int = 1) {
        show()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Round 1 • Table $tableId").fetchSemanticsNodes().isNotEmpty() }
        waitForIdle()
    }
}

@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class TournamentScreenUiTest {
    // The screen goes back to the main thread before it navigates after a save. The JVM tests have no UI thread of
    // their own, so the main dispatcher runs the work right away.
    @BeforeTest
    fun useATestMainDispatcher() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun resetTheMainDispatcher() = Dispatchers.resetMain()

    @Test
    fun theScreenLoadsTheFirstTableOfTheRound() = runComposeUiTest {
        with(ScreenHarness()) {
            loadScreen()

            onNodeWithText("Round 1 • Table 1").assertExistsOnScreen()
        }
    }

    @Test
    fun anAdminOpensRoundControlFromTheTopBar() = runComposeUiTest {
        with(ScreenHarness(role = GlobalUserRole.SUPER_ADMIN)) {
            loadScreen()

            onNode(hasText("Round control") or hasContentDescription("Round control")).performClick()
            waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("What the players can do").fetchSemanticsNodes().isNotEmpty() }
            onNodeWithTag("round_chip_1").assertIsSelected()
            onNodeWithTag("hands_auto").assertIsSelected()
        }
    }

    @Test
    fun closingTheOpenTableInRoundControlShowsNoTableChangedNoticeOnTheWayBack() = runComposeUiTest {
        with(ScreenHarness(role = GlobalUserRole.SUPER_ADMIN)) {
            loadScreen()
            onNode(hasText("Round control") or hasContentDescription("Round control")).performClick()
            waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag("table_1_completed").fetchSemanticsNodes().isNotEmpty() }

            // Table 1 is the one open in the editor behind. Round control is a screen, so the editor is not there to
            // notice the change while the admin works here.
            onNodeWithTag("table_1_completed").performScrollTo().performClick()
            waitUntil(timeoutMillis = 5_000) { runCatching { onNodeWithTag("table_1_completed").assertIsSelected() }.isSuccess }
            pass(3)
            onAllNodesWithText("Table updated").assertCountEquals(0)

            onAllNodes(hasText("Back") or hasContentDescription("Back")).onFirst().performClick()
            waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Round 1 • Table 1").fetchSemanticsNodes().isNotEmpty() }
            pass(5)

            // Back on the table, the screen loaded the tables again: no notice about a change the admin made.
            onAllNodesWithText("Table updated").assertCountEquals(0)
            onNodeWithText("Round 1 • Table 1").assertExistsOnScreen()
        }
    }

    @Test
    fun anEditorHasTheRoundControlButNoReset() = runComposeUiTest {
        with(ScreenHarness(role = GlobalUserRole.EDITOR)) {
            loadScreen()

            // The editors of a tournament also open and close its rounds. Resetting a table is for the admins.
            onAllNodes(hasText("Round control") or hasContentDescription("Round control")).onFirst().assertExists()
        }
    }

    @Test
    fun enteringWithASavedTableScrollsTheTableStripToIt() = runComposeUiTest {
        // Far more tables than fit in the strip, so the saved one starts out of view.
        with(ScreenHarness(tableCount = 40, savedTableId = 38)) {
            loadScreen(tableId = 38)

            onNodeWithTag("table-chip-38").assertIsDisplayed()
            assertNotOnScreen("table-chip-1")
        }
    }

    @Test
    fun enteringWithoutASavedTableShowsTheStripFromTheStart() = runComposeUiTest {
        with(ScreenHarness(tableCount = 40)) {
            loadScreen()

            onNodeWithTag("table-chip-1").assertIsDisplayed()
            assertNotOnScreen("table-chip-38")
        }
    }

    @Test
    fun enteringATotalsOnlyTableGivesTheFirstScoreFieldTheFocus() = runComposeUiTest {
        with(ScreenHarness(useTotalsOnly = true)) {
            loadScreen()

            waitUntil(timeoutMillis = 5_000) { onAllNodes(isFocused()).fetchSemanticsNodes().isNotEmpty() }
            onNode(isFocused()).assert(hasSetTextAction())
            onAllNodes(hasSetTextAction())[0].assertIsFocused()
        }
    }

    @Test
    fun enteringWithASavedTotalsOnlyTableGivesItsFirstScoreFieldTheFocus() = runComposeUiTest {
        with(ScreenHarness(tableCount = 40, savedTableId = 38, useTotalsOnly = true)) {
            loadScreen(tableId = 38)

            waitUntil(timeoutMillis = 5_000) { onAllNodes(isFocused()).fetchSemanticsNodes().isNotEmpty() }
            onAllNodes(hasSetTextAction())[0].assertIsFocused()
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
            onNodeWithText("Round 1 · Table 1").assertExistsOnScreen()
            onNodeWithText("Another user changed this table.\nYour unsaved changes were replaced.").assertExistsOnScreen()
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

            onNodeWithText("Round 1 · Table 1").assertExistsOnScreen()
            onNodeWithText("Someone else saved this table first.\nYour changes were not saved.").assertExistsOnScreen()
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
    fun anIdleUserSeesNoDialogAndComingBackChecksAgain() = runComposeUiTest {
        with(ScreenHarness()) {
            loadScreen()

            pass(32)

            onAllNodesWithText("Table updated").assertCountEquals(0)
            val checksBefore = server.calls.count { it == "manifest" }
            server.otherUserSaves(1, 1, handScore = "24")

            activity.markActive()
            mainClock.advanceTimeBy(1_500)
            waitForIdle()

            assertTrue(server.calls.count { it == "manifest" } > checksBefore, "coming back runs one check at once")
            onNodeWithText("Table updated").assertExistsOnScreen()
        }
    }

    @Test
    fun pollingStopsWhileTheUserIsAway() = runComposeUiTest {
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
    fun aSecondUserSavingBetweenTwoChecksIsStillCaughtByTheNextCheck() = runComposeUiTest {
        with(ScreenHarness()) {
            loadScreen()
            server.otherUserSaves(1, 1, handScore = "8")

            pass(2)

            onNodeWithText("Table updated").assertExistsOnScreen()
            onNodeWithText("Round 1 · Table 1").assertExistsOnScreen()
            onNodeWithText("Another user changed this table.\nIt now shows the latest data.").assertExistsOnScreen()
        }
    }

    @Test
    fun aTableThatIsNotOnScreenChangesWithoutADialog() = runComposeUiTest {
        with(ScreenHarness()) {
            loadScreen()
            server.otherUserSaves(1, 2, handScore = "8")

            pass(2)

            onAllNodesWithText("Table updated").assertCountEquals(0)
            onAllNodesWithText("Tables updated").assertCountEquals(0)
            // The data is current when the user opens the table.
            onNodeWithTag("table-chip-2").performClick()
            waitForIdle()
            assertTrue(server.calls.any { it == "manifest" }, "the poll saw the change")
            onNodeWithText("Round 1 • Table 2").assertExistsOnScreen()
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
    fun theTopBarKeepsTheToolsOfTheRoundsAndTheOtherScreensWaitInMore() = runComposeUiTest {
        with(ScreenHarness()) {
            loadScreen()
            fun button(label: String) = onAllNodes(hasText(label) or hasContentDescription(label)).fetchSemanticsNodes().size

            // In the bar: the projection of the QR, next to the Timer and the Ranking.
            assertTrue(button("QR") >= 1, "No QR button in the top bar.")
            for (label in listOf("Players", "Tables IDs", "Stats", "EMA report")) {
                assertEquals(0, button(label), "$label is in the top bar, not in More.")
            }

            onNode(hasText("More") or hasContentDescription("More")).performClick()
            waitForIdle()

            for (label in listOf("Players", "Tables IDs", "Stats", "EMA report")) {
                assertTrue(button(label) >= 1, "$label is not in the More menu.")
            }
            // Teams opens from Players since 24-nov, not from here.
            assertEquals(0, button("Teams"), "Teams is still in the More menu.")
        }
    }

    @Test
    fun theRevisionIsAtTheEndOfMoreAndInTheCornerNotUnderTheTitle() = runComposeUiTest {
        with(ScreenHarness()) {
            loadScreen()

            // On a large screen: only in the bottom right corner, in small faint letters.
            assertEquals(1, onAllNodesWithText("Results r2", substring = true).fetchSemanticsNodes().size)

            onNode(hasText("More") or hasContentDescription("More")).performClick()
            waitForIdle()

            // And as the last line of the More menu.
            assertEquals(2, onAllNodesWithText("Results r2", substring = true).fetchSemanticsNodes().size)
        }
    }

    @Test
    fun theEmaExportChecksForNewerDataAndUsesTheHeldData() = runComposeUiTest {
        with(ScreenHarness()) {
            loadScreen()
            server.calls.clear()

            // EMA report is in the "More" menu of the top bar, or in the three-dots menu on a narrow window.
            if (onAllNodes(hasText("More") or hasContentDescription("More")).fetchSemanticsNodes().isNotEmpty()) {
                onNode(hasText("More") or hasContentDescription("More")).performClick()
            } else {
                onNode(hasContentDescription("Menu")).performClick()
            }
            waitForIdle()
            onNodeWithText("EMA report").performClick()
            waitForIdle()

            val manifest = server.calls.indexOf("manifest")
            val tables = server.calls.indexOf("all CACHED")
            assertTrue(manifest >= 0 && tables > manifest, "one check first, then the held tables: ${server.calls}")
            assertTrue(server.calls.none { it == "all FORCE" })
        }
    }

    /** Both tables of the round with their final scores, so the results can be exported. */
    private fun ScreenHarness.scoreBothTables() {
        server.seed(syncTable(tableId = 1, useTotalsOnly = true).copy(playerEastScore = "30", playerSouthScore = "10", playerWestScore = "-10", playerNorthScore = "-30"))
        server.seed(
            syncTable(tableId = 2, east = "5", useTotalsOnly = true).copy(
                playerIds = listOf(5, 6, 7, 8),
                playerSouthId = "6",
                playerWestId = "7",
                playerNorthId = "8",
                playerEastScore = "30",
                playerSouthScore = "10",
                playerWestScore = "-10",
                playerNorthScore = "-30",
            ),
        )
    }

    private fun ComposeUiTest.chooseTheEmaReport() {
        if (onAllNodes(hasText("More") or hasContentDescription("More")).fetchSemanticsNodes().isNotEmpty()) {
            onNode(hasText("More") or hasContentDescription("More")).performClick()
        } else {
            onNode(hasContentDescription("Menu")).performClick()
        }
        waitForIdle()
        onNodeWithText("EMA report").performClick()
        waitForIdle()
    }

    @Test
    fun theEmaExportNeedsAnEmaPlayerInEverySlot() = runComposeUiTest {
        with(ScreenHarness()) {
            scoreBothTables()
            loadScreen()
            chooseTheEmaReport()
            waitForText("Assign an EMA player to every tournament slot before export.")
        }
    }

    @Test
    fun withEverySlotAssignedTheEmaReportIsMadeInTheBackground() = runComposeUiTest {
        with(ScreenHarness()) {
            scoreBothTables()
            server.players = (1..8).map { TournamentPlayer(id = it, name = "Player $it", team = 0, assignedEmaId = "0000000$it") }
            server.writesSucceed = true
            loadScreen()
            chooseTheEmaReport()
            waitForIdle()
            onAllNodesWithText("Assign an EMA player", substring = true).assertCountEquals(0)
            onAllNodesWithText("Official results cannot be exported.", substring = true).assertCountEquals(0)
        }
    }

    private fun ComposeUiTest.openMore() {
        onNode(hasText("More") or hasContentDescription("More")).performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.waitForText(text: String, substring: Boolean = false) =
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun playersInMoreOpensThePlayersOfTheTournament() = runComposeUiTest {
        with(ScreenHarness()) {
            loadScreen()
            openMore()
            onNodeWithText("Players").performClick()
            waitForText("Players of t1")
        }
    }

    @Test
    fun statsInMoreOpensTheStatsOfTheOpenTable() = runComposeUiTest {
        with(ScreenHarness()) {
            loadScreen()
            openMore()
            onNodeWithText("Stats").performClick()
            waitForText("Stats TABLE 1 1")
        }
    }

    @Test
    fun anAdminOpensTheActivityOfTheTournamentFromMore() = runComposeUiTest {
        with(ScreenHarness(role = GlobalUserRole.SUPER_ADMIN)) {
            loadScreen()
            openMore()
            onNode(hasText("Activity") or hasContentDescription("Activity")).performClick()
            waitForText("Activity of t1")
        }
    }

    @Test
    fun tablesIdsInMoreOpensItsDialogAndCloses() = runComposeUiTest {
        with(ScreenHarness()) {
            loadScreen()
            openMore()
            onNodeWithText("Tables IDs").performClick()
            waitForText("One card for each table (2 tables)", substring = true)
            onAllNodesWithText("Cancel").onLast().performClick()
            waitForIdle()
            onAllNodesWithText("One card for each table", substring = true).assertCountEquals(0)
        }
    }

    @Test
    fun backGoesToTheScreenBefore() = runComposeUiTest {
        with(ScreenHarness(fromList = true)) {
            show()
            waitForText("Round 1 • Table 1")
            onAllNodes(hasText("Back") or hasContentDescription("Back")).onFirst().performClick()
            waitForText("Tournament list")
        }
    }

    @Test
    fun anAdminClearsTheOpenTableAfterConfirming() = runComposeUiTest {
        with(ScreenHarness(role = GlobalUserRole.SUPER_ADMIN)) {
            loadScreen()
            onNodeWithTag("resetTableButton").performClick()
            waitForText("Clear table 1?")
            onNodeWithTag("resetTableConfirm").performClick()
            waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Clear table 1?").fetchSemanticsNodes().isEmpty() }
            assertTrue("reset 1/1" in server.calls, "No reset: ${server.calls}")
        }
    }

    @Test
    fun cancellingTheClearKeepsTheTable() = runComposeUiTest {
        with(ScreenHarness(role = GlobalUserRole.SUPER_ADMIN)) {
            loadScreen()
            onNodeWithTag("resetTableButton").performClick()
            waitForText("Clear table 1?")
            onAllNodesWithText("Cancel").onLast().performClick()
            waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Clear table 1?").fetchSemanticsNodes().isEmpty() }
            assertTrue(server.calls.none { it.startsWith("reset") })
        }
    }

    @Test
    fun thePencilOpensTheEditDialogOfTheTournament() = runComposeUiTest {
        with(ScreenHarness(role = GlobalUserRole.SUPER_ADMIN)) {
            loadScreen()
            onNode(hasContentDescription("Edit tournament")).performClick()
            waitUntil(timeoutMillis = 5_000) { onAllNodes(hasSetTextAction()).fetchSemanticsNodes().any { true } }
            onAllNodesWithText("Cancel").onLast().performClick()
            waitForIdle()
        }
    }

    @Test
    fun theEditDialogLeadsToTheChangeOfPlayers() = runComposeUiTest {
        with(ScreenHarness(role = GlobalUserRole.SUPER_ADMIN)) {
            loadScreen()
            onNode(hasContentDescription("Edit tournament")).performClick()
            waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Data").fetchSemanticsNodes().isNotEmpty() }
            onAllNodes(hasText("Data") and hasClickAction()).onFirst().performClick()
            waitForIdle()
            onNodeWithTag("changePlayerCount").performScrollTo().performClick()
            waitForText("Player count of t1")
        }
    }

    @Test
    fun theTimerTheRankingAndTheQrOpenFromTheTopBar() = runComposeUiTest {
        with(ScreenHarness()) {
            loadScreen()
            listOf("Timer", "QR").forEach { name ->
                onAllNodes((hasText(name) or hasContentDescription(name)) and hasClickAction()).onFirst().performClick()
                waitForIdle()
            }
            onNodeWithText("Round 1 • Table 1").assertExistsOnScreen()
            // The ranking opens in a window of its own, and this window goes to its control (desktop and web). On iOS
            // and Android the ranking has its controls inside, so the tournament stays.
            onAllNodes((hasText("Ranking") or hasContentDescription("Ranking")) and hasClickAction()).onFirst().performClick()
            waitForIdle()
            if (rankingControlsInSeparateScreen) {
                onNodeWithText("Ranking control of t1").assertExists()
            } else {
                onNodeWithText("Round 1 • Table 1").assertExistsOnScreen()
            }
        }
    }

    @Test
    fun theEmaExportNamesTheTablesThatAreNotReady() = runComposeUiTest {
        with(ScreenHarness()) {
            loadScreen()
            openMore()
            onNodeWithText("EMA report").performClick()
            waitForText("Official results cannot be exported.", substring = true)
        }
    }

    @Test
    fun theNextRoundButtonOpensTheTablesOfTheNextRound() = runComposeUiTest {
        with(ScreenHarness(roundCount = 2)) {
            loadScreen()
            onNode(hasContentDescription("Next round")).performClick()
            waitForText("Round 2 • Table 1")
        }
    }

    @Test
    fun aFailedListOfRoundsShowsTheError() = runComposeUiTest {
        with(ScreenHarness()) {
            server.failRounds = true
            show()
            waitForText("Network request failed.")
        }
    }

    @Test
    fun aFailedListOfAllTablesStillOpensTheRoundFromItsOwnList() = runComposeUiTest {
        with(ScreenHarness()) {
            server.failTableLists = setOf(null)
            loadScreen()
            assertTrue(server.calls.any { it.startsWith("list 1 ") }, "The round did not load its own list: ${server.calls}")
        }
    }

    @Test
    fun aFailedListOfTheRoundShowsTheError() = runComposeUiTest {
        with(ScreenHarness()) {
            server.failTableLists = setOf(null, 1)
            show()
            waitForText("Network request failed.")
        }
    }

    @Test
    fun withoutTheBatchOfTheRoundEachTableLoadsOnItsOwn() = runComposeUiTest {
        with(ScreenHarness()) {
            server.failRoundBatch = true
            loadScreen()
            waitUntil(timeoutMillis = 5_000) { server.calls.any { it.startsWith("get 1/2 ") } }
            assertTrue(server.calls.any { it.startsWith("get 1/1 ") }, "${server.calls}")
        }
    }

    @Test
    fun aTableThatFailsToLoadOnItsOwnShowsTheError() = runComposeUiTest {
        with(ScreenHarness()) {
            server.failRoundBatch = true
            server.failTableReads = 1
            show()
            waitForText("Network request failed.")
        }
    }

    /** Hand 1 of table 1 has a result marked as done. Taking Done off asks before the save. */
    private fun ComposeUiTest.untickTheDoneOfTheFirstHand(harness: ScreenHarness) = with(harness) {
        val hands = syncHands().mapIndexed { i, h ->
            if (i == 0) h.copy(playerWinnerId = "1", playerLooserId = "2", handScore = "24", isDone = true) else h
        }
        server.seed(syncTable(version = 1, roundId = 1, tableId = 1), hands)
        loadScreen()
        onNode(hasAnyAncestor(hasTestTag("hand-1-done")) and isToggleable()).performScrollTo().performClick()
        waitForIdle()
        onNodeWithText("Save").performClick()
        waitForText("Hands without Done")
        onNodeWithText("Round 1 · Table 1 · Hand 1").assertExistsOnScreen()
    }

    @Test
    fun savingAHandWithoutDoneAsksAndSavesAnyway() = runComposeUiTest {
        val harness = ScreenHarness()
        untickTheDoneOfTheFirstHand(harness)

        onNodeWithTag("handsNotDoneSave").performClick()
        waitUntil(timeoutMillis = 5_000) { harness.server.calls.any { it.startsWith("save 1/1 v1") } }
        onAllNodesWithText("Hands without Done").assertCountEquals(0)
    }

    @Test
    fun cancellingTheHandsWithoutDoneKeepsTheChanges() = runComposeUiTest {
        val harness = ScreenHarness()
        untickTheDoneOfTheFirstHand(harness)

        onNodeWithText("Cancel").performClick()
        waitForIdle()
        onAllNodesWithText("Hands without Done").assertCountEquals(0)
        assertTrue(harness.server.calls.none { it.startsWith("save ") })
        onNodeWithText("Save").assertExistsOnScreen()
    }
}

/** A lazy list does not compose the items out of view, so a chip out of view may not exist at all. */
@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.assertNotOnScreen(tag: String) {
    if (onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()) onNodeWithTag(tag).assertIsNotDisplayed()
}

private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertExistsOnScreen() = assertExists()
