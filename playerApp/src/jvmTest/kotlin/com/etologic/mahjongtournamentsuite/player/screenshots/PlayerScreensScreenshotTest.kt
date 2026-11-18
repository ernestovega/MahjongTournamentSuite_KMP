package com.etologic.mahjongtournamentsuite.player.screenshots

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.player.COMBINATION_DIALOG_TAG
import com.etologic.mahjongtournamentsuite.player.CombinationDialog
import com.etologic.mahjongtournamentsuite.player.Combinations
import com.etologic.mahjongtournamentsuite.player.CombinationsScreen
import com.etologic.mahjongtournamentsuite.player.CreditsDialog
import com.etologic.mahjongtournamentsuite.player.MyTableButton
import com.etologic.mahjongtournamentsuite.player.MyTableReminderDialog
import com.etologic.mahjongtournamentsuite.player.PenaltiesScreen
import com.etologic.mahjongtournamentsuite.player.PlayerTab
import com.etologic.mahjongtournamentsuite.player.RoundMoment
import com.etologic.mahjongtournamentsuite.player.SignInScreen
import com.etologic.mahjongtournamentsuite.player.StatisticsRoute
import com.etologic.mahjongtournamentsuite.player.StatisticsScreen
import com.etologic.mahjongtournamentsuite.player.TableScreen
import com.etologic.mahjongtournamentsuite.player.TournamentCard
import com.etologic.mahjongtournamentsuite.player.TournamentPlayerRoute
import com.etologic.mahjongtournamentsuite.player.TournamentPlayerScreen
import com.etologic.mahjongtournamentsuite.player.TournamentScreen
import com.etologic.mahjongtournamentsuite.player.TournamentStanding
import com.etologic.mahjongtournamentsuite.player.TournamentsScreen
import com.etologic.mahjongtournamentsuite.presentation.components.StatisticsScope
import com.etologic.mahjongtournamentsuite.screenshots.ScreenSize
import com.etologic.mahjongtournamentsuite.screenshots.Variant
import com.etologic.mahjongtournamentsuite.screenshots.componentVariants
import com.etologic.mahjongtournamentsuite.screenshots.dialogVariants
import com.etologic.mahjongtournamentsuite.screenshots.fake.ScreenshotData
import com.etologic.mahjongtournamentsuite.screenshots.fake.ScreenshotData.COMPLETED
import com.etologic.mahjongtournamentsuite.screenshots.fake.ScreenshotData.INDIVIDUAL
import com.etologic.mahjongtournamentsuite.screenshots.fake.ScreenshotData.ONGOING
import kotlinx.coroutines.runBlocking
import mahjongtournamentsuite.playerapp.generated.resources.Res
import mahjongtournamentsuite.playerapp.generated.resources.combinations_points
import org.jetbrains.compose.resources.getPluralString
import com.etologic.mahjongtournamentsuite.player.MainTab
import kotlin.test.Test

/** Every screen and tab of the player app with the data of ScreenshotData, signed in as Lucía (player 1). */
@OptIn(ExperimentalTestApi::class)
class PlayerScreensScreenshotTest {
    /** Opens a main tab of the tournament ([MainTab]) by its title in the language of the screenshot, once the tournament is loaded. */
    private fun ComposeUiTest.openTab(tab: Int) {
        waitForText("Madrid Open 2026", substring = true)
        val title = playerText(MainTab.title(tab).key)
        waitForText(title)
        onAllNodesWithText(title).onFirst().performClick()
        waitForIdle()
    }

    @Test
    fun myTableReminder() = playerScreenshots(
        "dialogs/my-table-reminder",
        variants = dialogVariants,
        ready = { waitForText(playerText("my_table_go")) },
    ) {
        MyTableReminderDialog("Madrid Open 2026", roundId = 3, tableId = 7, moment = RoundMoment.StartedAgo(12 * 60_000L), onOpen = {}, onDismiss = {})
    }

    @Test
    fun combinationsCredits() = playerScreenshots(
        "dialogs/combinations-credits",
        variants = dialogVariants,
        ready = { waitForText("www.martinpersson.org") },
    ) {
        CreditsDialog(onDismiss = {})
    }

    /** The dialog of a combination: its tiles, its detail and the examples of the Green Book. */
    private fun combinationDialog(id: String) = playerScreenshots(
        "dialogs/combination-${id.replace('_', '-')}",
        variants = dialogVariants,
        ready = { waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag(COMBINATION_DIALOG_TAG).fetchSemanticsNodes().isNotEmpty() } },
    ) {
        CombinationDialog(Combinations.first { it.id == id }, onDismiss = {})
    }

    @Test
    fun combinationWithExamples() = combinationDialog("big_four_winds")

    @Test
    fun combinationWithoutExamples() = combinationDialog("pure_double_chow")

    @Test
    fun myTableButton() = playerScreenshots("components/my-table-button", variants = componentVariants, ready = { waitForText("Go to my table") }) {
        Box(Modifier.fillMaxSize()) { MyTableButton(onClick = {}, modifier = Modifier.align(Alignment.BottomEnd)) }
    }

    /**
     * The places of the player on the cards of the list of tournaments: gold, silver and bronze for the first three,
     * the color of the text for the others, a tournament without teams and one without the places of the hands.
     */
    @Composable
    private fun StandingCards() {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            TournamentCard(
                ScreenshotData.tournament(ONGOING),
                TournamentStanding(
                    position = 1, of = 16, points = 12.0, score = 210, teamRank = 2, teamOf = 4, teamPoints = 18.5,
                    chickenRank = 3, chickenHands = 2, chickenOf = 7, bestHandRank = 1, bestHand = 88, bestHandOf = 64,
                ),
                mine = true,
            ) {}
            TournamentCard(
                ScreenshotData.tournament(COMPLETED),
                TournamentStanding(
                    position = 7, of = 16, points = 4.5, score = -35, teamRank = 4, teamOf = 4, teamPoints = -6.0,
                    chickenRank = 5, chickenHands = 1, chickenOf = 9, bestHandRank = 12, bestHand = 32, bestHandOf = 64,
                ),
                mine = true,
            ) {}
            TournamentCard(
                ScreenshotData.tournament(INDIVIDUAL),
                TournamentStanding(position = 3, of = 16, points = 9.0, score = 120, bestHandRank = 2, bestHand = 64, bestHandOf = 64),
                mine = true,
            ) {}
        }
    }

    @Test
    fun tournamentStandings() = playerScreenshots(
        "components/tournament-standings",
        variants = componentVariants,
        ready = { waitForText("Paris Classic 2026") },
    ) { StandingCards() }

    @Test
    fun tournamentStandingsTooltip() = playerScreenshots(
        "components/tournament-standings-tooltip",
        variants = componentVariants,
        ready = {
            waitForText("Paris Classic 2026")
            // The mouse over the ranking line (its number and icon) shows its tooltip.
            onAllNodes(hasContentDescription("Ranking")).onFirst().performMouseInput { moveTo(center) }
            waitForText("Ranking", substring = true)
        },
    ) { StandingCards() }

    @Test
    fun signIn() = playerScreenshots("sign-in", modules = { playerModules(account = null) }) { nav -> SignInScreen(nav, null) }

    @Test
    fun tournaments() = playerScreenshots("tournaments", ready = { waitForText("Madrid Open 2026", substring = true) }) { nav ->
        TournamentsScreen(nav)
    }

    /** The Stats tab of the start screen: the own player is chosen, with the picker to change it on top. */
    @Test
    fun statisticsTab() = playerScreenshots(
        "statistics-tab",
        ready = {
            openStatsTab()
            waitForText(uiText("stats_skills"), substring = true)
        },
    ) { nav -> TournamentsScreen(nav) }

    /** The tournaments of the own player, each card with its round chips, the second round chosen in the newest one. */
    @Test
    fun statisticsTabByTournaments() = playerScreenshots(
        "statistics-tab-by-tournaments",
        ready = {
            openStatsTab()
            waitForText(uiText("stats_skills"), substring = true)
            onAllNodesWithText(playerText("scope_by_tournaments")).onFirst().performClick()
            val round = uiText("round_short", 2)
            waitForText(round)
            onAllNodesWithText(round).onFirst().performClick()
            waitForIdle()
        },
    ) { nav -> TournamentsScreen(nav) }

    /** Without an account the Stats tab asks to choose a player. */
    @Test
    fun statisticsTabGuest() = playerScreenshots(
        "statistics-tab-guest",
        modules = { playerModules(account = null) },
        ready = {
            openStatsTab()
            waitForText(playerText("stats_choose_player_title"), substring = true)
        },
    ) { nav -> TournamentsScreen(nav) }

    private fun ComposeUiTest.openStatsTab() {
        waitForText("Madrid Open 2026", substring = true)
        val title = playerText("stats_title")
        waitForText(title)
        onAllNodesWithText(title).onFirst().performClick()
        waitForIdle()
    }

    @Test
    fun tournamentsGuest() = playerScreenshots(
        "tournaments-guest",
        modules = { playerModules(account = null) },
        ready = { waitForText("Madrid Open 2026", substring = true) },
    ) { nav -> TournamentsScreen(nav) }

    /**
     * The More menu of the top bar, open: Contact, Privacy and the rules. On a phone they are entries of the three-dots
     * menu of the bar, so the test opens the menus until Privacy shows.
     */
    @Test
    fun tournamentsMoreMenu() = playerScreenshots(
        "tournaments-more-menu",
        variants = listOf(ScreenSize.Desktop, ScreenSize.Phone).flatMap { listOf(Variant(it, false), Variant(it, true)) },
        ready = {
            waitForText("Madrid Open 2026", substring = true)
            val menus = onAllNodes((hasText("More") or hasContentDescription("More") or hasContentDescription("Menu")) and hasClickAction())
            var index = 0
            while (onAllNodesWithText("Privacy").fetchSemanticsNodes().isEmpty() && index < menus.fetchSemanticsNodes().size) {
                menus[index++].performClick()
                waitForIdle()
            }
            waitForText("Privacy")
        },
    ) { nav -> TournamentsScreen(nav) }

    @Test
    fun tournamentTables() = playerScreenshots("tournament-tables", ready = { openTab(MainTab.TABLES) }) { nav -> TournamentScreen(nav, ONGOING) }

    @Test
    fun tournamentRanking() = playerScreenshots("tournament-ranking", ready = { openTab(MainTab.RANKING) }) { nav -> TournamentScreen(nav, ONGOING) }

    @Test
    fun tournamentPlayers() = playerScreenshots("tournament-players", ready = { openTab(MainTab.PLAYERS) }) { nav -> TournamentScreen(nav, ONGOING) }

    @Test
    fun tournamentPlayerTables() = playerScreenshots("tournament-player-tables", ready = { waitForText("EMA 04010001", substring = true) }) { nav ->
        TournamentPlayerScreen(nav, TournamentPlayerRoute(ONGOING, slotId = 1))
    }

    @Test
    fun tournamentPlayerStatistics() = playerScreenshots(
        "tournament-player-statistics",
        ready = { waitForText("EMA 04010001", substring = true) },
    ) { nav -> TournamentPlayerScreen(nav, TournamentPlayerRoute(ONGOING, slotId = 1, tab = PlayerTab.STATISTICS)) }

    @Test
    fun tournamentPlayerAllTournaments() = playerScreenshots(
        "tournament-player-all-tournaments",
        ready = {
            waitForText(uiText("stats_totals"))
            onAllNodesWithText("Madrid Open 2026").onFirst().performClick()
            waitForIdle()
            onAllNodesWithText(playerText("player_tournaments_all")).onFirst().performClick()
            waitForText(uiText("stat_tournaments"))
        },
    ) { nav -> TournamentPlayerScreen(nav, TournamentPlayerRoute(ONGOING, slotId = 1, tab = PlayerTab.STATISTICS)) }

    @Test
    fun tournamentTeams() = playerScreenshots("tournament-teams", ready = { openTab(MainTab.TEAMS) }) { nav -> TournamentScreen(nav, ONGOING) }

    @Test
    fun tournamentStatistics() = playerScreenshots("tournament-statistics", ready = { openTab(MainTab.STATISTICS) }) { nav -> TournamentScreen(nav, ONGOING) }

    @Test
    fun tournamentInfo() = playerScreenshots("tournament-info", ready = { openTab(MainTab.INFO) }) { nav -> TournamentScreen(nav, ONGOING) }

    @Test
    fun tableCompleted() = playerScreenshots("table-completed", ready = { waitForText("LUCÍA MARTÍN", substring = true) }) { nav ->
        TableScreen(nav, ONGOING, roundId = 1, tableId = 1)
    }

    @Test
    fun tableInPlay() = playerScreenshots("table-in-play", ready = { waitForText("LUCÍA MARTÍN", substring = true) }) { nav ->
        TableScreen(nav, ONGOING, roundId = 3, tableId = 1)
    }

    @Test
    fun combinations() = playerScreenshots("combinations", ready = { waitForText(playerText("comb_pure_double_chow")) }) { nav -> CombinationsScreen(nav) }

    @Test
    fun combinationsFolded() = playerScreenshots(
        "combinations-folded",
        ready = {
            waitForText(playerText("comb_pure_double_chow"))
            onNode(hasText(playerText("combinations_fold_all")) or hasContentDescription(playerText("combinations_fold_all"))).performClick()
            waitForIdle()
        },
    ) { nav -> CombinationsScreen(nav) }

    @Test
    fun combinationsOneValue() = playerScreenshots(
        "combinations-one-value",
        ready = {
            waitForText(playerText("comb_pure_double_chow"))
            onNode(hasText(playerText("combinations_fold_all")) or hasContentDescription(playerText("combinations_fold_all"))).performClick()
            onNode(hasText(runBlocking { getPluralString(Res.plurals.combinations_points, 24, 24) } + " (9)")).performScrollTo().performClick()
            waitForIdle()
        },
    ) { nav -> CombinationsScreen(nav) }

    @Test
    fun penalties() = playerScreenshots("penalties", ready = { waitForText(playerText("penalties_section_dead_hand")) }) { nav -> PenaltiesScreen(nav) }

    @Test
    fun statisticsTournament() = playerScreenshots("statistics-tournament", ready = { waitForText("Madrid Open 2026", substring = true) }) { nav ->
        StatisticsScreen(nav, StatisticsRoute(StatisticsScope.TOURNAMENT.name, tournamentId = ONGOING))
    }

    @Test
    fun statisticsPlayer() = playerScreenshots("statistics-player", ready = { waitForText("EMA 04010001", substring = true) }) { nav ->
        StatisticsScreen(nav, StatisticsRoute(StatisticsScope.PLAYER.name, tournamentId = ONGOING, slotId = 1))
    }

    @Test
    fun statisticsPerson() = playerScreenshots("statistics-person", ready = { waitForText(uiText("stats_skills"), substring = true) }) { nav ->
        StatisticsScreen(nav, StatisticsRoute(StatisticsScope.PERSON.name))
    }

    /** The place history of a person with several tournaments: each tournament has a band of its own color. */
    @Test
    fun statisticsPersonPlaces() = playerScreenshots(
        "statistics-person-places",
        ready = {
            waitForText(uiText("stats_skills"), substring = true)
            onAllNodesWithText(uiText("places_history_title", 10).substringBefore("10").trim(), substring = true, ignoreCase = true).onFirst().performScrollTo()
            // The chart is under its title. A swipe brings it into view.
            onAllNodes(isRoot()).onFirst().performTouchInput { swipeUp(startY = centerY + 160f, endY = centerY - 160f) }
            waitForIdle()
        },
    ) { nav ->
        StatisticsScreen(nav, StatisticsRoute(StatisticsScope.PERSON.name))
    }
}
