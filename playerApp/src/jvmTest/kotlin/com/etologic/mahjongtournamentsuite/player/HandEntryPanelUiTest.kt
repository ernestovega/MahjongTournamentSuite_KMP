package com.etologic.mahjongtournamentsuite.player

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.HandPreview
import com.etologic.mahjongtournamentsuite.domain.model.HandProposal
import com.etologic.mahjongtournamentsuite.domain.model.MatchEndMode
import com.etologic.mahjongtournamentsuite.domain.model.PenaltyEntry
import com.etologic.mahjongtournamentsuite.domain.model.TableHand
import com.etologic.mahjongtournamentsuite.domain.model.TableState
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.TournamentPlayer
import com.etologic.mahjongtournamentsuite.domain.model.TournamentRankings
import com.etologic.mahjongtournamentsuite.domain.model.TournamentRoundSchedule
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerHandRepository
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import kotlinx.datetime.LocalDateTime
import org.koin.compose.KoinApplication
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Records the proposals that the panel sends. The answers are fixed. */
private class RecordingHandRepository : PlayerHandRepository {
    val previews = mutableListOf<HandProposal>()

    override suspend fun addPenalties(
        tournamentId: String, roundId: Int, tableId: Int, penalties: List<PenaltyEntry>, expectedVersion: Long,
    ): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun previewHand(
        tournamentId: String, roundId: Int, tableId: Int, proposal: HandProposal,
    ): AppResult<HandPreview> {
        previews += proposal
        return AppResult.Success(
            HandPreview(
                handId = 1,
                seatSlotIds = listOf(1, 2, 3, 4),
                handScores = listOf(0, 0, 0, 0),
                tableScores = listOf("0", "0", "0", "0"),
                tablePoints = listOf("", "", "", ""),
                tableVersion = 0,
            ),
        )
    }

    override suspend fun recordHand(
        tournamentId: String, roundId: Int, tableId: Int, proposal: HandProposal, expectedVersion: Long,
    ): AppResult<HandPreview> = previewHand(tournamentId, roundId, tableId, proposal)
}

@OptIn(ExperimentalTestApi::class)
class HandEntryPanelUiTest {
    // Koin keeps one global context. Each test needs its own presenter, so the context stops after the test.
    private val originalLocale = java.util.Locale.getDefault()

    @AfterTest
    fun stopKoinContext() {
        stopKoin()
        java.util.Locale.setDefault(originalLocale)
    }

    private fun androidx.compose.ui.test.ComposeUiTest.awaitText(text: String) =
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    /** The round starts on 2026-10-10 at 10:00. The match lasts 120 minutes and the extra time lasts 15 minutes. */
    private fun view(mode: MatchEndMode): PlayerTournamentView {
        val tournament = Tournament(
            id = "t", name = "T", isTeams = false, numPlayers = 4, numRounds = 1,
            roundSchedules = listOf(TournamentRoundSchedule(1, "2026-10-10", "10:00")),
            matchDurationMinutes = 120, matchEndMode = mode, matchExtraMinutes = 15,
        )
        val table = TableState(
            roundId = 1, tableId = 1, playerIds = listOf(1, 2, 3, 4),
            playerEastId = "1", playerSouthId = "2", playerWestId = "3", playerNorthId = "4",
            playerEastScore = "", playerSouthScore = "", playerWestScore = "", playerNorthScore = "",
            isCompleted = false, useTotalsOnly = false,
        )
        val hands = List(16) { index ->
            TableHand(
                handId = index + 1, playerWinnerId = "", playerLooserId = "", handScore = "", isChickenHand = false,
                isDone = false, playerEastPenalty = "", playerSouthPenalty = "", playerWestPenalty = "", playerNorthPenalty = "",
            )
        }
        return buildPlayerTournamentView(
            tournament,
            slots = (1..4).map { TournamentPlayer(id = it, name = "", team = 0, assignedEmaId = if (it == 1) "10000001" else null) },
            registry = emptyList(),
            tables = listOf(table to hands),
            rankings = TournamentRankings(emptyList(), emptyList(), emptyList(), emptyList()),
            myEmaId = "10000001",
        )
    }

    private fun androidx.compose.ui.test.ComposeUiTest.show(
        mode: MatchEndMode,
        time: String,
        repository: RecordingHandRepository = RecordingHandRepository(),
    ) {
        val view = view(mode)
        setContent {
            MtsTheme(useDarkTheme = false) {
                KoinApplication(application = { modules(module { single { PlayerTablePresenter(repository) } }) }) {
                    HandEntryPanel(
                        tournamentId = "t", roundId = 1, tableId = 1,
                        view = view, table = view.tables.single(),
                        now = LocalDateTime.parse("2026-10-10T$time"),
                        onSaved = {},
                    )
                }
            }
        }
    }

    @Test
    fun whileThePlayersPlayTheyCanMarkTheLastHandThemselves() = runComposeUiTest {
        val repository = RecordingHandRepository()
        show(MatchEndMode.FINISH_HAND, "11:00", repository)

        onNodeWithText("Add draw").performClick()
        onNodeWithText("Last hand of the table").assertIsDisplayed()
        onNodeWithText("Review").performClick()
        waitUntil(timeoutMillis = 5_000) { repository.previews.isNotEmpty() }

        assertEquals(listOf(false), repository.previews.map { it.isLastHand })
    }

    @Test
    fun inExtraTimeTheHandInPlayIsTheLastHandOfTheTable() = runComposeUiTest {
        val repository = RecordingHandRepository()
        show(MatchEndMode.FINISH_HAND, "12:05", repository)

        onNodeWithText("The time is over. You can add the hand in play. After it, the table is completed.").assertIsDisplayed()
        onNodeWithText("Add draw").performClick()
        onNodeWithText("The time is over. This hand is the last hand of the table.").assertIsDisplayed()
        onNodeWithText("Last hand of the table").assertDoesNotExist()
        onNodeWithText("Review").performClick()
        waitUntil(timeoutMillis = 5_000) { repository.previews.isNotEmpty() }

        assertEquals(listOf(true), repository.previews.map { it.isLastHand })
    }

    @Test
    fun afterTheEndTimeOfAStopMatchThePlayersCannotAddHands() = runComposeUiTest {
        show(MatchEndMode.STOP, "12:00")

        onNodeWithText("The time of this match is over. You cannot add hands now. The organizers enter the missing data.")
            .assertIsDisplayed()
        onNodeWithText("Add win").assertDoesNotExist()
        onNodeWithText("Add draw").assertDoesNotExist()
        onNodeWithText("Add penalty").assertDoesNotExist()
    }

    @Test
    fun afterTheExtraTimeThePlayersCannotAddHands() = runComposeUiTest {
        show(MatchEndMode.FINISH_HAND, "12:15")

        onNodeWithText("The time of this match is over. You cannot add hands now. The organizers enter the missing data.")
            .assertIsDisplayed()
        onNodeWithText("Add win").assertDoesNotExist()
    }

    @Test
    fun theButtonsOfThePanelAreInSpanish() = runComposeUiTest {
        java.util.Locale.setDefault(java.util.Locale.forLanguageTag("es"))
        show(MatchEndMode.FINISH_HAND, "11:00")

        awaitText("Añadir mano sin ganador")
        onNodeWithText("Añadir victoria").assertIsDisplayed()
        onNodeWithText("Añadir penalización").assertIsDisplayed()
        onNodeWithText("Add draw").assertDoesNotExist()
    }

    @Test
    fun theEntryDialogIsInSpanish() = runComposeUiTest {
        java.util.Locale.setDefault(java.util.Locale.forLanguageTag("es"))
        show(MatchEndMode.FINISH_HAND, "11:00")

        awaitText("Añadir mano sin ganador")
        onNodeWithText("Añadir mano sin ganador").performClick()
        awaitText("Última mano de la mesa")
        onNodeWithText("Ningún jugador gana esta mano. Todos los asientos puntúan cero.").assertIsDisplayed()
        onNodeWithText("Revisar").assertIsDisplayed()
        onNodeWithText("Cancelar").assertIsDisplayed()
    }

    @Test
    fun theMessageOfAnOverMatchIsInSpanish() = runComposeUiTest {
        java.util.Locale.setDefault(java.util.Locale.forLanguageTag("es"))
        show(MatchEndMode.STOP, "12:00")

        awaitText("El tiempo de esta partida ha terminado. Ya no puedes añadir manos. Los organizadores introducen los datos que faltan.")
        onNodeWithText("Añadir victoria").assertDoesNotExist()
    }

    @Test
    fun anEmptySlotHasASpanishName() = runComposeUiTest {
        java.util.Locale.setDefault(java.util.Locale.forLanguageTag("es"))
        com.etologic.mahjongtournamentsuite.presentation.util.PlaceholderNames.playerTemplate = { "Jugador $it" }
        try {
            // Slots 2 to 4 have no person, so their names are the placeholder of the data.
            assertEquals("Jugador 2", view(MatchEndMode.STOP).nameOf(2))
            assertEquals("Jugador 3", view(MatchEndMode.STOP).firstNameOf(3))
        } finally {
            com.etologic.mahjongtournamentsuite.presentation.util.PlaceholderNames.playerTemplate = null
        }
    }
}
