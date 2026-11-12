package com.etologic.mahjongtournamentsuite.player

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.AuthSession
import com.etologic.mahjongtournamentsuite.domain.model.Player
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

/** A repository that answers only the calls of [answer]. Any other call fails the test. */
private inline fun <reified T : Any> fake(crossinline answer: (name: String) -> Any?): T = Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { proxy, method, args ->
    when (method.name) {
        "toString" -> "fake ${T::class.simpleName}"
        "hashCode" -> System.identityHashCode(proxy)
        "equals" -> proxy === args?.firstOrNull()
        else -> answer(method.name).let { if (it === NotUsed) error("${T::class.simpleName}.${method.name} is not used in this test") else it }
    }
} as T

private object NotUsed

/** The tabs of the statistics: the bar with the chosen player in a tournament, and the tabs of a guest. */
@OptIn(ExperimentalTestApi::class)
class StatisticsTabsUiTest {
    @AfterTest
    fun stopKoinContext() {
        stopKoin()
    }

    private val tournament = Tournament(id = "t-1", name = "Madrid Open", isTeams = false, numPlayers = 4, numRounds = 1)
    private val slots = listOf(
        TournamentPlayer(id = 1, name = "", team = 0, country = "ESP", assignedEmaId = "04000001"),
        TournamentPlayer(id = 2, name = "Bea Ruiz", team = 0, country = "ITA"),
        TournamentPlayer(id = 3, name = "Carla Gil", team = 0),
        TournamentPlayer(id = 4, name = "Dani Sol", team = 0),
    )
    private val registry = listOf(Player(emaId = "04000001", firstName = "Ana", lastName = "Lopez", country = "ESP"))

    private val tournaments = fake<TournamentRepository> { name ->
        when (name) {
            "listTournaments" -> AppResult.Success(listOf(tournament))
            "listTournamentPlayers" -> AppResult.Success(slots)
            "getAllTablesWithHands" -> AppResult.Success(emptyList<Any>())
            else -> NotUsed
        }
    }
    private val players = fake<PlayerRepository> { name -> if (name == "listPlayers") AppResult.Success(registry) else NotUsed }
    private val statistics = fake<StatisticsRepository> { NotUsed }

    private fun auth(session: AuthSession?) = fake<AuthRepository> { name ->
        when (name) {
            "currentSession" -> session
            "getMe" -> AppResult.Failure(AppError.Network)
            else -> NotUsed
        }
    }

    private fun ComposeUiTest.show(session: AuthSession?, content: @androidx.compose.runtime.Composable () -> Unit) = setContent {
        MtsTheme(useDarkTheme = false) {
            KoinApplication(application = {
                modules(
                    module {
                        single {
                            PlayerStatisticsPresenter(
                                loadStatistics = LoadStatisticsUseCase(tournaments, players, CalculateStatisticsUseCase()),
                                loadStored = LoadStoredStatisticsUseCase(statistics, tournaments, players),
                                authRepository = auth(session),
                                playerRepository = players,
                            )
                        }
                        single { PlayerSessionPresenter(auth(session), Logger.withTag("ui-test")) }
                    },
                )
            }) { content() }
        }
    }

    private fun ComposeUiTest.waitForText(text: String) = waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun thePlayersTabShowsTheChosenPlayerInABarWithAChangeButton() = runComposeUiTest {
        show(session = null) { TournamentStatisticsTab(tournamentId = "t-1", mySlotId = 1) }
        waitForText("Players")
        onAllNodes(hasText("Players"))[0].performClick()

        // The slot with an EMA number shows the name of the registry, in capitals.
        waitForText("ANA LOPEZ")
        onNodeWithText("Change").assertExists()
        onNodeWithText("Cancel").assertDoesNotExist()
    }

    @Test
    fun changeOpensThePlayerDropdownAndCancelGoesBackToTheBar() = runComposeUiTest {
        show(session = null) { TournamentStatisticsTab(tournamentId = "t-1", mySlotId = 2) }
        waitForText("Players")
        onAllNodes(hasText("Players"))[0].performClick()
        waitForText("Bea Ruiz")

        onNodeWithText("Change").performClick()
        waitForText("Cancel")
        onNodeWithText("Change").assertDoesNotExist()

        onNodeWithText("Cancel").performClick()
        waitForText("Change")
        onNodeWithText("Cancel").assertDoesNotExist()
    }

    @Test
    fun aGuestSeesOnlyThePlayersTabInTheGeneralStatistics() = runComposeUiTest {
        show(session = null) { MyStatisticsTab() }
        waitForText("Players")
        waitForText("Choose a player")
        onNodeWithText("Me").assertDoesNotExist()
        onNodeWithText("My tournaments").assertDoesNotExist()
        onNodeWithText("Others").assertDoesNotExist()
    }

    @Test
    fun aSignedInPlayerSeesMeMyTournamentsAndOthers() = runComposeUiTest {
        show(session = AuthSession(uid = "u1", idToken = "id", refreshToken = "refresh")) { MyStatisticsTab() }
        waitForText("Others")
        onNodeWithText("Me").assertExists()
        onNodeWithText("My tournaments").assertExists()
        onNodeWithText("Players").assertDoesNotExist()
    }
}
