package com.etologic.mahjongtournamentsuite.player

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.ControlMode
import com.etologic.mahjongtournamentsuite.domain.model.HandPreview
import com.etologic.mahjongtournamentsuite.domain.model.HandProposal
import com.etologic.mahjongtournamentsuite.domain.model.MatchEndMode
import com.etologic.mahjongtournamentsuite.domain.model.PenaltyEntry
import com.etologic.mahjongtournamentsuite.domain.model.RoundClock
import com.etologic.mahjongtournamentsuite.domain.model.RoundControl
import com.etologic.mahjongtournamentsuite.domain.model.TableHand
import com.etologic.mahjongtournamentsuite.domain.model.TableState
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.TournamentPlayer
import com.etologic.mahjongtournamentsuite.domain.model.TournamentRankings
import com.etologic.mahjongtournamentsuite.domain.model.TournamentRoundSchedule
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerHandRepository
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.koin.compose.KoinApplication
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Records the proposals that the panel sends. The answers are fixed, unless a test gives an error. */
private class RecordingHandRepository : PlayerHandRepository {
    val previews = mutableListOf<HandProposal>()
    val recorded = mutableListOf<Pair<HandProposal, Long>>()
    val penalties = mutableListOf<List<PenaltyEntry>>()

    /** The answer of the preview, of the save of a hand and of the save of penalties when it is not a success. */
    var previewError: AppError? = null
    var recordError: AppError? = null
    var penaltyError: AppError? = null

    override suspend fun addPenalties(
        tournamentId: String,
        roundId: Int,
        tableId: Int,
        penalties: List<PenaltyEntry>,
        expectedVersion: Long,
    ): AppResult<Unit> {
        this.penalties += penalties
        return penaltyError?.let { AppResult.Failure(it) } ?: AppResult.Success(Unit)
    }

    override suspend fun previewHand(
        tournamentId: String,
        roundId: Int,
        tableId: Int,
        proposal: HandProposal,
    ): AppResult<HandPreview> {
        previews += proposal
        previewError?.let { return AppResult.Failure(it) }
        return AppResult.Success(
            HandPreview(
                handId = 1,
                seatSlotIds = listOf(1, 2, 3, 4),
                handScores = listOf(0, 0, 0, 0),
                tableScores = listOf("0", "0", "0", "0"),
                tablePoints = listOf("", "", "", ""),
                tableVersion = 7,
            ),
        )
    }

    override suspend fun recordHand(
        tournamentId: String,
        roundId: Int,
        tableId: Int,
        proposal: HandProposal,
        expectedVersion: Long,
    ): AppResult<HandPreview> {
        recorded += proposal to expectedVersion
        recordError?.let { return AppResult.Failure(it) }
        return previewHand(tournamentId, roundId, tableId, proposal)
    }

    val seats = mutableListOf<List<Int>>()

    override suspend fun assignSeats(
        tournamentId: String,
        roundId: Int,
        tableId: Int,
        seats: List<Int>,
        expectedVersion: Long,
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

    private fun androidx.compose.ui.test.ComposeUiTest.awaitText(text: String) = waitUntil(timeoutMillis = 5_000) { onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

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
        controls: List<RoundControl> = emptyList(),
        totalsOnly: Boolean = false,
    ): PlayerTournamentView {
        val start = LocalDateTime.parse("2026-10-10T10:00").toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
        val tournament = Tournament(
            id = "t", name = "T", isTeams = false, numPlayers = 4, numRounds = 1,
            roundSchedules = listOf(TournamentRoundSchedule(1, "2026-10-10", "10:00")),
            roundClocks = if (started) listOf(RoundClock(roundId = 1, startedAtMs = start)) else emptyList(),
            matchDurationMinutes = 120, matchEndMode = mode, matchExtraMinutes = 15, roundControls = controls,
        )
        val seatIds = if (seats) listOf("1", "2", "3", "4") else listOf("", "", "", "")
        val table = TableState(
            roundId = 1, tableId = 1, playerIds = listOf(1, 2, 3, 4),
            playerEastId = seatIds[0], playerSouthId = seatIds[1], playerWestId = seatIds[2], playerNorthId = seatIds[3],
            playerEastScore = "", playerSouthScore = "", playerWestScore = "", playerNorthScore = "",
            isCompleted = false, useTotalsOnly = totalsOnly,
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
        controls: List<RoundControl> = emptyList(),
        totalsOnly: Boolean = false,
    ) {
        val view = view(mode, doneHands, time, started, seats, controls, totalsOnly)
        setContent {
            MtsTheme(useDarkTheme = false) {
                KoinApplication(application = { modules(module { single { PlayerTablePresenter(repository) } }) }) {
                    HandEntryPanel(
                        tournamentId = "t",
                        roundId = 1,
                        tableId = 1,
                        view = view,
                        table = view.tables.single(),
                        now = LocalDateTime.parse("2026-10-10T$time"),
                        onSaved = {},
                    )
                }
            }
        }
    }

    @Test
    fun whileThePlayersPlayAHandIsNotTheLastOne() = runComposeUiTest {
        val repository = RecordingHandRepository()
        show(MatchEndMode.FINISH_HAND, "11:00", repository)

        onNodeWithText("Add draw").performClick()
        onNodeWithText("Review").performClick()
        waitUntil(timeoutMillis = 5_000) { repository.previews.isNotEmpty() }
        // Only hand 16 and a hand in the extra time end the table. The review has no last-hand box.
        awaitText("Confirm")
        onNodeWithText("Last hand", substring = true).assertDoesNotExist()

        assertEquals(listOf(false), repository.previews.map { it.isLastHand })
    }

    @Test
    fun inExtraTimeTheHandInPlayIsTheLastHandOfTheTable() = runComposeUiTest {
        val repository = RecordingHandRepository()
        show(MatchEndMode.FINISH_HAND, "12:05", repository)

        onNodeWithText("The time is over. You can add the hand in play. After it, the table is completed.").assertIsDisplayed()
        onNodeWithText("Add draw").performClick()
        onNodeWithText("Review").performClick()
        waitUntil(timeoutMillis = 5_000) { repository.previews.isNotEmpty() }
        // The hand in the extra time ends the table on its own.
        awaitText("Confirm")

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
        awaitText("Revisar")
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
    fun aWinIsAskedInSmallSteps() = runComposeUiTest {
        show(MatchEndMode.FINISH_HAND, "11:00")

        awaitText("Add win")
        onNodeWithText("Add win").performClick()
        // Step 1: a tap on the winner goes on, with no OK button.
        awaitText("Who won?")
        onNodeWithText("Who discarded?").assertDoesNotExist()
        onAllNodesWithText("Player 2").onFirst().performClick()
        // Step 2: Self-draw or one of the other three players.
        awaitText("Who discarded?")
        onNodeWithText("Self-draw").performClick()
        // Step 3: the score. A self-drawn hand is never a chicken hand.
        awaitText("Hand score")
        onNodeWithText("Chicken hand").assertDoesNotExist()
        onNodeWithText("Back").performClick()
        awaitText("Who discarded?")
        onAllNodesWithText("Player 1").onFirst().performClick()
        awaitText("Hand score")
        onNodeWithText("Chicken hand").assertIsDisplayed()
    }

    @Test
    fun onAPhoneTheScoreIsTypedOnTheNumpad() = runComposeUiTest {
        val view = view(MatchEndMode.FINISH_HAND, 0, "11:00")
        setContent {
            MtsTheme(useDarkTheme = false) {
                KoinApplication(application = { modules(module { single { PlayerTablePresenter(RecordingHandRepository()) } }) }) {
                    androidx.compose.runtime.CompositionLocalProvider(LocalNumpadEntry provides true) {
                        HandEntryPanel(
                            tournamentId = "t",
                            roundId = 1,
                            tableId = 1,
                            view = view,
                            table = view.tables.single(),
                            now = LocalDateTime.parse("2026-10-10T11:00"),
                            onSaved = {},
                        )
                    }
                }
            }
        }
        awaitText("Add win")
        onNodeWithText("Add win").performClick()
        awaitText("Who won?")
        onAllNodesWithText("Player 2").onFirst().performClick()
        awaitText("Who discarded?")
        onNodeWithText("Self-draw").performClick()
        awaitText("Hand score")
        // A leading zero does nothing. Delete takes the last digit away.
        listOf("0", "1", "2", "5").forEach { key -> onAllNodesWithText(key).let { nodes -> nodes[nodes.fetchSemanticsNodes().size - 1] }.performClick() }
        onNodeWithContentDescription("Delete").performClick()
        onAllNodesWithText("12").onFirst().assertIsDisplayed()
        onNodeWithText("Clear").performClick()
        assertEquals(0, onAllNodesWithText("12").fetchSemanticsNodes().size)
    }

    // Round control, the plan B of the Timer: an admin decides by hand what the players can do.

    @Test
    fun aRoundClosedByAnOrganizerTellsThePlayersWhyTheyCannotAddHands() = runComposeUiTest {
        show(MatchEndMode.STOP, "10:30", controls = listOf(RoundControl(1, hands = ControlMode.CLOSED)))

        onNodeWithText("An organizer closed the hand entry of this table for now. Ask a referee if you need to add a hand.").assertIsDisplayed()
        onNodeWithText("Add win").assertDoesNotExist()
        onNodeWithText("Add draw").assertDoesNotExist()
    }

    @Test
    fun aClosedTableWinsOverAnOpenRound() = runComposeUiTest {
        show(MatchEndMode.STOP, "10:30", controls = listOf(RoundControl(1, hands = ControlMode.OPEN, tables = mapOf(1 to ControlMode.CLOSED))))

        onNodeWithText("An organizer closed the hand entry of this table for now. Ask a referee if you need to add a hand.").assertIsDisplayed()
        onNodeWithText("Add win").assertDoesNotExist()
    }

    @Test
    fun anOpenRoundLetsThePlayersAddHandsBeforeTheGong() = runComposeUiTest {
        show(MatchEndMode.STOP, "09:00", started = false, controls = listOf(RoundControl(1, hands = ControlMode.OPEN)))

        onNodeWithText("Waiting for the starting gong", substring = true).assertDoesNotExist()
        onNodeWithText("Add win").assertIsDisplayed()
    }

    @Test
    fun anOpenRoundLetsThePlayersAddHandsAfterTheTime() = runComposeUiTest {
        show(MatchEndMode.STOP, "13:00", doneHands = 3, controls = listOf(RoundControl(1, hands = ControlMode.OPEN)))

        onNodeWithText("The time of this match is over", substring = true).assertDoesNotExist()
        onNodeWithText("Add win").assertIsDisplayed()
    }

    @Test
    fun anOpenTableLetsThePlayersAddHandsInAClosedRound() = runComposeUiTest {
        show(MatchEndMode.STOP, "10:30", controls = listOf(RoundControl(1, hands = ControlMode.CLOSED, tables = mapOf(1 to ControlMode.OPEN))))

        onNodeWithText("An organizer closed the hand entry of this table for now. Ask a referee if you need to add a hand.").assertDoesNotExist()
        onNodeWithText("Add win").assertIsDisplayed()
    }

    @Test
    fun openSeatsLetThePlayersSetTheSeatsLongBeforeTheStart() = runComposeUiTest {
        show(MatchEndMode.STOP, "09:00", started = false, seats = false, controls = listOf(RoundControl(1, seats = ControlMode.OPEN)))

        onNodeWithText("Set the seats").assertIsDisplayed()
    }

    @Test
    fun closedSeatsHideTheSeatButtonsBeforeTheGong() = runComposeUiTest {
        show(MatchEndMode.STOP, "09:55", started = false, seats = false, controls = listOf(RoundControl(1, seats = ControlMode.CLOSED)))

        onNodeWithText("Waiting for the starting gong. The hand buttons appear when the round starts.").assertIsDisplayed()
        onNodeWithText("Set the seats").assertDoesNotExist()
    }

    // The whole entry of a hand: the steps, the review and the save, and what the player sees when the save fails.

    private fun androidx.compose.ui.test.ComposeUiTest.typeScore(score: String) {
        awaitText("Hand score")
        onAllNodes(hasSetTextAction()).onFirst().performTextInput(score)
    }

    /** Winner, then who discarded (or Self-draw), then the score. Leaves the score step open. */
    private fun androidx.compose.ui.test.ComposeUiTest.enterWin(winner: String, discarder: String, score: String) {
        awaitText("Add win")
        onNodeWithText("Add win").performClick()
        awaitText("Who won?")
        onAllNodesWithText(winner).onFirst().performClick()
        awaitText("Who discarded?")
        onAllNodesWithText(discarder).onFirst().performClick()
        typeScore(score)
    }

    @Test
    fun aDiscardWinIsReviewedAndSavedWithTheVersionOfTheReview() = runComposeUiTest {
        val repository = RecordingHandRepository()
        show(MatchEndMode.FINISH_HAND, "11:00", repository)

        enterWin("Player 2", "Player 4", "24")
        onNodeWithText("Review").performClick()
        awaitText("Check hand")
        onNodeWithText("wins with 24 points").assertIsDisplayed()
        onNodeWithText("Confirm").performClick()
        waitUntil(timeoutMillis = 5_000) { repository.recorded.isNotEmpty() }

        val expected = HandProposal(winnerSlotId = 2, loserSlotId = 4, score = 24, isChickenHand = false, isLastHand = false)
        assertEquals(listOf(expected), repository.previews.take(1))
        // The save carries the version of the table that the review read, so a change in between is found.
        assertEquals(listOf(expected to 7L), repository.recorded)
    }

    @Test
    fun aSelfDrawnWinHasNoLoser() = runComposeUiTest {
        val repository = RecordingHandRepository()
        show(MatchEndMode.FINISH_HAND, "11:00", repository)

        enterWin("Player 3", "Self-draw", "8")
        onNodeWithText("Review").performClick()
        awaitText("Check hand")
        onNodeWithText("Confirm").performClick()
        waitUntil(timeoutMillis = 5_000) { repository.recorded.isNotEmpty() }

        assertEquals(HandProposal(winnerSlotId = 3, loserSlotId = null, score = 8), repository.recorded.single().first)
    }

    @Test
    fun aScoreBelowEightCannotBeReviewed() = runComposeUiTest {
        show(MatchEndMode.FINISH_HAND, "11:00")

        enterWin("Player 2", "Player 1", "7")
        onNodeWithText("Review").assertIsNotEnabled()
        onAllNodes(hasSetTextAction()).onFirst().performTextInput("0")
        // 70 is a valid score.
        onNodeWithText("Review").assertIsEnabled()
    }

    @Test
    fun aChickenHandIsSavedAsOneAndCannotBeAboveTwelve() = runComposeUiTest {
        val repository = RecordingHandRepository()
        show(MatchEndMode.FINISH_HAND, "11:00", repository)

        enterWin("Player 1", "Player 2", "13")
        onNodeWithText("Chicken hand").performClick()
        onNodeWithText("Review").assertIsNotEnabled()
        onNodeWithText("Back").performClick()
        onAllNodesWithText("Player 2").onFirst().performClick()
        awaitText("Hand score")
        // Back to the score step: the score and the chicken box stay, so only the score changes.
        onAllNodes(hasSetTextAction()).onFirst().performTextClearance()
        onAllNodes(hasSetTextAction()).onFirst().performTextInput("9")
        onNodeWithText("Review").performClick()
        awaitText("Check hand")
        onNodeWithText("Confirm").performClick()
        waitUntil(timeoutMillis = 5_000) { repository.recorded.isNotEmpty() }

        assertEquals(HandProposal(winnerSlotId = 1, loserSlotId = 2, score = 9, isChickenHand = true), repository.recorded.single().first)
    }

    @Test
    fun editInTheReviewGoesBackWithTheDataKept() = runComposeUiTest {
        val repository = RecordingHandRepository()
        show(MatchEndMode.FINISH_HAND, "11:00", repository)

        enterWin("Player 2", "Player 4", "16")
        onNodeWithText("Review").performClick()
        awaitText("Check hand")
        onNodeWithText("Edit").performClick()
        awaitText("Hand score")
        onNodeWithText("Check hand").assertDoesNotExist()
        onAllNodesWithText("16").onFirst().assertIsDisplayed()
        assertEquals(0, repository.recorded.size)
    }

    @Test
    fun aDrawIsSavedWithoutWinnerOrScore() = runComposeUiTest {
        val repository = RecordingHandRepository()
        show(MatchEndMode.FINISH_HAND, "11:00", repository)

        onNodeWithText("Add draw").performClick()
        onNodeWithText("Review").performClick()
        awaitText("Confirm")
        onNodeWithText("Confirm").performClick()
        waitUntil(timeoutMillis = 5_000) { repository.recorded.isNotEmpty() }

        assertEquals(HandProposal(winnerSlotId = null, loserSlotId = null, score = 0), repository.recorded.single().first)
    }

    @Test
    fun aSaveRefusedBecauseTheTableChangedShowsTheEnteredHand() = runComposeUiTest {
        val repository = RecordingHandRepository().apply { recordError = AppError.Conflict("Table changed") }
        show(MatchEndMode.FINISH_HAND, "11:00", repository)

        enterWin("Player 2", "Player 4", "24")
        onNodeWithText("Review").performClick()
        awaitText("Check hand")
        onNodeWithText("Confirm").performClick()

        // Nothing typed is lost: the dialog shows the hand of the player next to the data of the server.
        awaitText("The table changed")
        onNodeWithText("Your hand, not saved").assertIsDisplayed()
        onNodeWithText("Player 2 won with 24 from Player 4").assertIsDisplayed()
        onNodeWithText("If the hand is already in the table, do not enter it again.").assertIsDisplayed()
        // The entry waits behind the notice, so the notice is the only dialog.
        onNodeWithText("Hand score").assertDoesNotExist()
        onNodeWithText("Close and continue").performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("The table changed").fetchSemanticsNodes().isEmpty() }
        // Then the entry comes back with what the player typed.
        awaitText("Hand score")
        onAllNodesWithText("24").onFirst().assertIsDisplayed()
    }

    @Test
    fun aNetworkErrorInTheReviewIsShownAndNothingIsSaved() = runComposeUiTest {
        val repository = RecordingHandRepository().apply { previewError = AppError.Network }
        show(MatchEndMode.FINISH_HAND, "11:00", repository)

        enterWin("Player 2", "Self-draw", "8")
        onNodeWithText("Review").performClick()

        awaitText("Network request failed.")
        onNodeWithText("Check hand").assertDoesNotExist()
        assertEquals(0, repository.recorded.size)
    }

    @Test
    fun aNetworkErrorOnTheSaveIsShown() = runComposeUiTest {
        val repository = RecordingHandRepository().apply { recordError = AppError.Network }
        show(MatchEndMode.FINISH_HAND, "11:00", repository)

        enterWin("Player 2", "Self-draw", "8")
        onNodeWithText("Review").performClick()
        awaitText("Check hand")
        onNodeWithText("Confirm").performClick()

        awaitText("Network request failed.")
        assertEquals(1, repository.recorded.size)
    }

    @Test
    fun aSharedPenaltyIsReviewedAndSaved() = runComposeUiTest {
        val repository = RecordingHandRepository()
        show(MatchEndMode.FINISH_HAND, "11:00", repository)

        onNodeWithText("Add penalty").performClick()
        awaitText("Penalized player")
        onNodeWithText("Review").assertIsNotEnabled()
        onAllNodesWithText("Player 3").onFirst().performClick()
        onAllNodes(hasSetTextAction()).onFirst().performTextInput("10")
        onNodeWithText("Share it with the other players").performClick()
        // A shared penalty is split in three.
        onNodeWithText("A shared penalty must be a multiple of 3.").assertIsDisplayed()
        onNodeWithText("Review").assertIsNotEnabled()
        onAllNodes(hasSetTextAction()).onFirst().performTextClearance()
        onAllNodes(hasSetTextAction()).onFirst().performTextInput("30")
        onNodeWithText("The other players get +10 each.").assertIsDisplayed()
        onNodeWithText("Review").performClick()

        awaitText("Check penalty")
        onNodeWithText("-30").assertIsDisplayed()
        onNodeWithText("others +10 each").assertIsDisplayed()
        onNodeWithText("Confirm").performClick()
        waitUntil(timeoutMillis = 5_000) { repository.penalties.isNotEmpty() }

        // Player 3 sits at West, the third seat.
        assertEquals(listOf(listOf(PenaltyEntry(2, 30, true))), repository.penalties)
    }

    @Test
    fun severalPenaltiesGoInOneSaveAndOneCanBeRemoved() = runComposeUiTest {
        val repository = RecordingHandRepository()
        show(MatchEndMode.FINISH_HAND, "11:00", repository)

        onNodeWithText("Add penalty").performClick()
        awaitText("Penalty 1")
        onAllNodesWithText("Player 1").onFirst().performClick()
        onAllNodes(hasSetTextAction()).onFirst().performTextInput("5")
        onNodeWithText("Add another penalty").performClick()
        awaitText("Penalty 2")
        // The second card is below the first one: scroll to it, or the tap lands outside the dialog and closes it.
        onAllNodesWithText("Player 4")[1].performScrollTo().performClick()
        onAllNodes(hasSetTextAction())[1].performScrollTo().performTextInput("20")
        onNodeWithText("Add another penalty").performScrollTo()
        Thread.sleep(700) // The same button again within 500 ms is taken as a double press.
        onNodeWithText("Add another penalty").performClick()
        awaitText("Penalty 3")
        onNodeWithContentDescription("Remove penalty 3").performClick()
        onNodeWithText("Penalty 3").assertDoesNotExist()
        onNodeWithText("Review").performClick()
        awaitText("Check penalty")
        onNodeWithText("Confirm").performClick()
        waitUntil(timeoutMillis = 5_000) { repository.penalties.isNotEmpty() }

        assertEquals(listOf(listOf(PenaltyEntry(0, 5, false), PenaltyEntry(3, 20, false))), repository.penalties)
    }

    @Test
    fun removingTheOnlyPenaltyClosesTheDialog() = runComposeUiTest {
        show(MatchEndMode.FINISH_HAND, "11:00")

        onNodeWithText("Add penalty").performClick()
        awaitText("Penalty 1")
        onNodeWithContentDescription("Remove penalty 1").performClick()
        onNodeWithText("Penalized player").assertDoesNotExist()
        onNodeWithText("Add win").assertIsDisplayed()
    }

    @Test
    fun aPenaltyRefusedBecauseTheTableChangedSaysSo() = runComposeUiTest {
        val repository = RecordingHandRepository().apply { penaltyError = AppError.Conflict("Table changed") }
        show(MatchEndMode.FINISH_HAND, "11:00", repository)

        onNodeWithText("Add penalty").performClick()
        awaitText("Penalized player")
        onAllNodesWithText("Player 2").onFirst().performClick()
        onAllNodes(hasSetTextAction()).onFirst().performTextInput("10")
        onNodeWithText("Review").performClick()
        awaitText("Check penalty")
        onNodeWithText("Confirm").performClick()

        awaitText("The table changed")
        // A penalty is not a hand, so there is no "your hand" part.
        onNodeWithText("Your hand, not saved").assertDoesNotExist()
    }

    @Test
    fun aTableOfTotalsOnlyTakesNoHands() = runComposeUiTest {
        show(MatchEndMode.FINISH_HAND, "11:00", totalsOnly = true)

        onNodeWithText("The organizers enter only the final scores of this table. You cannot add hands here.").assertIsDisplayed()
        onNodeWithText("Add win").assertDoesNotExist()
    }

    @Test
    fun aTableWithoutSeatsWhileTheSeatsAreClosedCannotTakeHands() = runComposeUiTest {
        // Long after the gong the seats are closed, so a table without seats only says why.
        show(MatchEndMode.FINISH_HAND, "11:00", seats = false, controls = listOf(RoundControl(1, seats = ControlMode.CLOSED)))

        onNodeWithText("The seats of this table are not set yet. You can add hands when the four seats are set.").assertIsDisplayed()
        onNodeWithText("Add win").assertDoesNotExist()
    }
}
