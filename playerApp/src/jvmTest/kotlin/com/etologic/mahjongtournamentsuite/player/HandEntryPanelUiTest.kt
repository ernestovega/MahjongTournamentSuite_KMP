package com.etologic.mahjongtournamentsuite.player

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
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
import com.etologic.mahjongtournamentsuite.domain.model.RoundClock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
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

    val seats = mutableListOf<List<Int>>()

    override suspend fun assignSeats(
        tournamentId: String, roundId: Int, tableId: Int, seats: List<Int>, expectedVersion: Long,
    ): AppResult<Unit> {
        this.seats += seats
        return AppResult.Success(Unit)
    }
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

    /**
     * The round is planned on 2026-10-10 at 10:00, and the Timer started it then unless [started] is false.
     * The match lasts 120 minutes and the extra time lasts 15 minutes.
     */
    private fun view(
        mode: MatchEndMode,
        doneHands: Int = 0,
        now: String? = null,
        started: Boolean = true,
        seats: Boolean = true,
    ): PlayerTournamentView {
        val start = LocalDateTime.parse("2026-10-10T10:00").toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
        val tournament = Tournament(
            id = "t", name = "T", isTeams = false, numPlayers = 4, numRounds = 1,
            roundSchedules = listOf(TournamentRoundSchedule(1, "2026-10-10", "10:00")),
            roundClocks = if (started) listOf(RoundClock(roundId = 1, startedAtMs = start)) else emptyList(),
            matchDurationMinutes = 120, matchEndMode = mode, matchExtraMinutes = 15,
        )
        val seatIds = if (seats) listOf("1", "2", "3", "4") else listOf("", "", "", "")
        val table = TableState(
            roundId = 1, tableId = 1, playerIds = listOf(1, 2, 3, 4),
            playerEastId = seatIds[0], playerSouthId = seatIds[1], playerWestId = seatIds[2], playerNorthId = seatIds[3],
            playerEastScore = "", playerSouthScore = "", playerWestScore = "", playerNorthScore = "",
            isCompleted = false, useTotalsOnly = false,
        )
        val hands = List(16) { index ->
            TableHand(
                handId = index + 1, playerWinnerId = "", playerLooserId = "", handScore = "", isChickenHand = false,
                isDone = index < doneHands, playerEastPenalty = "", playerSouthPenalty = "", playerWestPenalty = "", playerNorthPenalty = "",
            )
        }
        return buildPlayerTournamentView(
            tournament,
            slots = (1..4).map { TournamentPlayer(id = it, name = "", team = 0, assignedEmaId = if (it == 1) "10000001" else null) },
            registry = emptyList(),
            // The presenter closes a table with played hands when its time is over, as the app does.
            tables = now?.let { tournament.closeExpiredTables(listOf(table to hands), LocalDateTime.parse("2026-10-10T$it")) } ?: listOf(table to hands),
            rankings = TournamentRankings(emptyList(), emptyList(), emptyList(), emptyList()),
            myEmaId = "10000001",
        )
    }

    private fun androidx.compose.ui.test.ComposeUiTest.show(
        mode: MatchEndMode,
        time: String,
        repository: RecordingHandRepository = RecordingHandRepository(),
        doneHands: Int = 0,
        started: Boolean = true,
        seats: Boolean = true,
    ) {
        val view = view(mode, doneHands, time, started, seats)
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

    /** The table has played hands, so the time closes it. The player still reads why no hand can be added. */
    @Test
    fun aTableThatTheTimeClosedTellsWhyNoHandCanBeAdded() = runComposeUiTest {
        show(MatchEndMode.STOP, "12:00", doneHands = 7)

        onNodeWithText("The time of this match is over. You cannot add hands now. The organizers enter the missing data.")
            .assertIsDisplayed()
        onNodeWithText("Add win").assertDoesNotExist()
        onNodeWithText("Add draw").assertDoesNotExist()
    }

    /** A table with its 16 hands is simply finished. The time message does not show. */
    @Test
    fun aFinishedTableShowsNoTimeMessage() = runComposeUiTest {
        show(MatchEndMode.STOP, "12:00", doneHands = 16)

        onNodeWithText("The time of this match is over. You cannot add hands now. The organizers enter the missing data.")
            .assertDoesNotExist()
        onNodeWithText("Add win").assertDoesNotExist()
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

        awaitText("Añadir empate")
        onNodeWithText("Añadir victoria").assertIsDisplayed()
        onNodeWithText("Añadir penalización").assertIsDisplayed()
        onNodeWithText("Add draw").assertDoesNotExist()
    }

    @Test
    fun theEntryDialogIsInSpanish() = runComposeUiTest {
        java.util.Locale.setDefault(java.util.Locale.forLanguageTag("es"))
        show(MatchEndMode.FINISH_HAND, "11:00")

        awaitText("Añadir empate")
        onNodeWithText("Añadir empate").performClick()
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

    @Test
    fun beforeTheGongThePlayersWaitAndCanSetTheSeats() = runComposeUiTest {
        val repository = RecordingHandRepository()
        show(MatchEndMode.STOP, "09:55", repository, started = false, seats = false)

        onNodeWithText("Waiting for the starting gong. The hand buttons appear when the round starts.").assertIsDisplayed()
        onNodeWithText("Add win").assertDoesNotExist()
        onNodeWithText("Set the seats").performClick()
        // East, South and West are asked. North takes the player who is left.
        onNodeWithText("Who sits at East?").assertIsDisplayed()
        onNodeWithText("Player 3").performClick()
        onNodeWithText("Who sits at South?").assertIsDisplayed()
        onNodeWithText("Player 1").performClick()
        onNodeWithText("Player 4").performClick()
        onNodeWithText("Save the seats").performClick()
        waitUntil(timeoutMillis = 5_000) { repository.seats.isNotEmpty() }

        assertEquals(listOf(listOf(3, 1, 4, 2)), repository.seats)
    }

    @Test
    fun quickPicksOfTheTopNameAllCount() = runComposeUiTest {
        val repository = RecordingHandRepository()
        show(MatchEndMode.STOP, "09:55", repository, started = false, seats = false)

        onNodeWithText("Set the seats").performClick()
        // The name at the top changes after each pick. A quick press on it is a new press, not a double press.
        repeat(3) { onAllNodesWithTag(SEAT_OPTION_TAG).onFirst().performClick() }
        onNodeWithText("Save the seats").performClick()
        waitUntil(timeoutMillis = 5_000) { repository.seats.isNotEmpty() }

        assertEquals(listOf(listOf(1, 2, 3, 4)), repository.seats)
    }

    @Test
    fun longBeforeThePlannedStartTheSeatsAreClosed() = runComposeUiTest {
        show(MatchEndMode.STOP, "09:00", started = false, seats = false)

        onNodeWithText("Waiting for the starting gong. The hand buttons appear when the round starts.").assertIsDisplayed()
        onNodeWithText("Set the seats").assertDoesNotExist()
    }

    @Test
    fun aLateRoundStaysWaitingUntilThePlay() = runComposeUiTest {
        show(MatchEndMode.STOP, "11:00", started = false)

        onNodeWithText("Waiting for the starting gong. The hand buttons appear when the round starts.").assertIsDisplayed()
        onNodeWithText("Add win").assertDoesNotExist()
        onNodeWithText("Change the seats").assertIsDisplayed()
    }

    @Test
    fun afterThePlayATableWithoutSeatsAsksForThem() = runComposeUiTest {
        show(MatchEndMode.STOP, "10:05", seats = false)

        onNodeWithText("Set the seats of the table to start entering hands.").assertIsDisplayed()
        onNodeWithText("Set the seats").assertIsDisplayed()
        onNodeWithText("Add win").assertDoesNotExist()
    }

    @Test
    fun theSeatsCanChangeUntilTheFirstHand() = runComposeUiTest {
        show(MatchEndMode.STOP, "10:05")
        onNodeWithText("Change the seats").assertIsDisplayed()
        onNodeWithText("Add win").assertIsDisplayed()
    }

    @Test
    fun afterTheFirstHandTheSeatsAreFixed() = runComposeUiTest {
        show(MatchEndMode.STOP, "10:30", doneHands = 1)
        onNodeWithText("Add win").assertIsDisplayed()
        onNodeWithText("Change the seats").assertDoesNotExist()
    }

    @Test
    fun selfDrawIsTheFirstChoiceOfWhoDiscarded() = runComposeUiTest {
        show(MatchEndMode.FINISH_HAND, "11:00")

        awaitText("Add win")
        onNodeWithText("Add win").performClick()
        awaitText("Who discarded")
        onNodeWithText("Self-draw").assertIsDisplayed()
        onAllNodesWithText("Player 2").onFirst().performClick()
        onNodeWithText("Hand score").assertIsDisplayed()
        // A self-drawn hand is never a chicken hand, so the choice hides it.
        onNodeWithText("Chicken hand").assertIsDisplayed()
        onNodeWithText("Self-draw").performClick()
        onNodeWithText("Chicken hand").assertDoesNotExist()
    }
}
