package com.etologic.mahjongtournamentsuite.player

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.TableHand
import com.etologic.mahjongtournamentsuite.domain.model.TableState
import com.etologic.mahjongtournamentsuite.domain.model.TeamRanking
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.TournamentPlayer
import com.etologic.mahjongtournamentsuite.domain.repository.AuthRepository
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerRepository
import com.etologic.mahjongtournamentsuite.domain.repository.StatisticsRepository
import com.etologic.mahjongtournamentsuite.domain.repository.TournamentRepository
import com.etologic.mahjongtournamentsuite.domain.usecase.CalculateStatisticsUseCase
import com.etologic.mahjongtournamentsuite.domain.usecase.LoadStatisticsUseCase
import com.etologic.mahjongtournamentsuite.domain.usecase.LoadStoredStatisticsUseCase
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import org.koin.compose.KoinApplication
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.lang.reflect.Proxy
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

private inline fun <reified T : Any> answering(crossinline answer: (name: String) -> Any?): T = Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { proxy, method, args ->
    when (method.name) {
        "toString" -> "fake ${T::class.simpleName}"
        "hashCode" -> System.identityHashCode(proxy)
        "equals" -> proxy === args?.firstOrNull()
        else -> answer(method.name).let { if (it === Unused) error("${T::class.simpleName}.${method.name} is not used in this test") else it }
    }
} as T

private object Unused

/** The Stats tab of a team: its place, its members by points with a tap that opens each one, and the tables together. */
@OptIn(ExperimentalTestApi::class)
class TeamStatisticsTabUiTest {
    @AfterTest
    fun stopKoinContext() {
        stopKoin()
    }

    private val tournament = Tournament(id = "t-1", name = "Team Cup", isTeams = true, numPlayers = 4, numRounds = 1)
    private val slots = listOf(
        TournamentPlayer(id = 1, name = "", team = 1, country = "ESP"),
        TournamentPlayer(id = 2, name = "", team = 1, country = "ITA"),
        TournamentPlayer(id = 3, name = "", team = 2),
        TournamentPlayer(id = 4, name = "", team = 2),
    )

    /** One table: player 2 (south) wins a self-drawn hand of 8, so she has 4 points and the others share the rest. */
    private val table = TableState(
        roundId = 1, tableId = 1, playerIds = listOf(1, 2, 3, 4),
        playerEastId = "1", playerSouthId = "2", playerWestId = "3", playerNorthId = "4",
        playerEastScore = "-16", playerSouthScore = "48", playerWestScore = "-16", playerNorthScore = "-16",
        isCompleted = true, useTotalsOnly = false,
    )
    private val hand = TableHand(
        handId = 1, playerWinnerId = "2", playerLooserId = "", handScore = "8", isChickenHand = false, isDone = true,
        playerEastPenalty = "", playerSouthPenalty = "", playerWestPenalty = "", playerNorthPenalty = "",
    )

    private fun show(test: androidx.compose.ui.test.ComposeUiTest.(opened: MutableList<Int>) -> Unit, failing: Boolean = false) = runComposeUiTest {
        val tournaments = answering<TournamentRepository> { name ->
            when (name) {
                "listTournaments" -> AppResult.Success(listOf(tournament))
                "listTournamentPlayers" -> if (failing) AppResult.Failure(AppError.Network) else AppResult.Success(slots)
                "getAllTablesWithHands" -> AppResult.Success(listOf(table to listOf(hand)))
                else -> Unused
            }
        }
        val players = answering<PlayerRepository> { name -> if (name == "listPlayers") AppResult.Success(emptyList<Any>()) else Unused }
        val statistics = answering<StatisticsRepository> { Unused }
        val auth = answering<AuthRepository> { name ->
            when (name) {
                "currentSession" -> null
                "getMe" -> AppResult.Failure(AppError.Network)
                else -> Unused
            }
        }
        val opened = mutableListOf<Int>()
        var loading: Boolean? = null
        setContent {
            MtsTheme(useDarkTheme = false) {
                KoinApplication(application = {
                    modules(
                        module {
                            single {
                                PlayerStatisticsPresenter(
                                    loadStatistics = LoadStatisticsUseCase(tournaments, players, CalculateStatisticsUseCase()),
                                    loadStored = LoadStoredStatisticsUseCase(statistics, tournaments, players),
                                    authRepository = auth,
                                    playerRepository = players,
                                    tournamentRepository = tournaments,
                                )
                            }
                            single { PlayerSessionPresenter(auth, Logger.withTag("ui-test")) }
                        },
                    )
                }) {
                    TeamStatisticsTab(
                        tournamentId = "t-1",
                        members = listOf(1, 2),
                        ranking = TeamRanking(position = 1, teamId = 1, points = 5.0, score = 32),
                        places = mapOf(1 to 1),
                        teamCount = 2,
                        onOpenPlayer = { opened += it },
                        onLoadingChange = { loading = it },
                    )
                }
            }
        }
        test(opened)
        assertEquals(false, loading, "the tab says when it stops loading")
    }

    @Test
    fun theMembersAreSortedByPointsAndATapOpensThePlayer() = show({ opened ->
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText("Members")).fetchSemanticsNodes().isNotEmpty() }
        // The names come from the EMA registry; these slots have none, so they show their numbers.
        val members = onAllNodesWithText("Points ", substring = true).fetchSemanticsNodes()
            .mapNotNull { node -> node.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text } }
        assertEquals(listOf("Player 2, Points 4  ·  Score 48  ·  Win rate 100%", "Player 1, Points 1  ·  Score -16  ·  Win rate 0%"), members)
        onNodeWithText("The statistics add up the tables of every player of the team.").performScrollTo().assertExists()

        onNodeWithText("Points 4  ·  Score 48", substring = true).performScrollTo().performClick()
        assertEquals(listOf(2), opened)
    })

    @Test
    fun aFailedLoadShowsTheError() = show({
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText("OK")).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Members").assertDoesNotExist()
    }, failing = true)
}
