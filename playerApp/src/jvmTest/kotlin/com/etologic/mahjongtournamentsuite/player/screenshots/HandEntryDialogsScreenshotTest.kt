package com.etologic.mahjongtournamentsuite.player.screenshots

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.HandPreview
import com.etologic.mahjongtournamentsuite.domain.model.HandProposal
import com.etologic.mahjongtournamentsuite.domain.model.MatchEndMode
import com.etologic.mahjongtournamentsuite.domain.model.PenaltyEntry
import com.etologic.mahjongtournamentsuite.domain.model.Player
import com.etologic.mahjongtournamentsuite.domain.model.RoundClock
import com.etologic.mahjongtournamentsuite.domain.model.TableHand
import com.etologic.mahjongtournamentsuite.domain.model.TableState
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.TournamentPlayer
import com.etologic.mahjongtournamentsuite.domain.model.TournamentRankings
import com.etologic.mahjongtournamentsuite.domain.model.TournamentRoundSchedule
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerHandRepository
import com.etologic.mahjongtournamentsuite.player.HandEntryPanel
import com.etologic.mahjongtournamentsuite.player.PLAYER_CHOICE_TAG
import com.etologic.mahjongtournamentsuite.player.PlayerTablePresenter
import com.etologic.mahjongtournamentsuite.player.buildPlayerTournamentView
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import com.etologic.mahjongtournamentsuite.screenshots.dialogVariants
import com.etologic.mahjongtournamentsuite.screenshots.screenshots
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.koin.compose.KoinApplication
import org.koin.dsl.module
import kotlin.test.Test

/** The answers of the server. [conflict] makes the save of a hand fail because another phone saved first. */
private class FixedHandRepository(private val conflict: Boolean = false) : PlayerHandRepository {
    private val preview = HandPreview(
        handId = 7,
        // Hand 7: the winds have turned, so Oskar is East. Lena wins 24 from Lucía.
        seatSlotIds = listOf(4, 3, 2, 1),
        handScores = listOf(-8, -8, 48, -32),
        tableScores = listOf("-37", "-30", "+35", "+32"),
        tablePoints = listOf("", "", "", ""),
        tableVersion = 3,
    )

    override suspend fun previewHand(tournamentId: String, roundId: Int, tableId: Int, proposal: HandProposal) = AppResult.Success(preview)

    override suspend fun recordHand(tournamentId: String, roundId: Int, tableId: Int, proposal: HandProposal, expectedVersion: Long) = if (conflict) AppResult.Failure(AppError.Conflict("Another phone saved hand 7 first.")) else AppResult.Success(preview)

    override suspend fun addPenalties(tournamentId: String, roundId: Int, tableId: Int, penalties: List<PenaltyEntry>, expectedVersion: Long) = AppResult.Success(Unit)

    override suspend fun assignSeats(tournamentId: String, roundId: Int, tableId: Int, seats: List<Int>, expectedVersion: Long) = AppResult.Success(Unit)
}

/**
 * The dialogs of the hand entry of the Player: the three steps of a win, the review, a draw, a penalty with its review,
 * and the notice of a table that another phone changed. They live inside the table screen, so each image opens them.
 */
@OptIn(ExperimentalTestApi::class)
class HandEntryDialogsScreenshotTest {
    private val registry = listOf(
        Player("04010001", "Lucía", "Martín", "ES"),
        Player("04010002", "Lena", "Schmidt", "DE"),
        Player("04010003", "Mateus", "Silva", "PT"),
        Player("04010004", "Oskar", "Lindqvist", "SE"),
    )

    private val view = run {
        val start = LocalDateTime.parse("2026-10-10T10:00").toInstant(TimeZone.UTC).toEpochMilliseconds()
        val tournament = Tournament(
            id = "t", name = "Madrid Open 2026", isTeams = false, numPlayers = 4, numRounds = 1,
            roundSchedules = listOf(TournamentRoundSchedule(1, "2026-10-10", "10:00")),
            roundClocks = listOf(RoundClock(roundId = 1, startedAtMs = start)),
            matchDurationMinutes = 120, matchEndMode = MatchEndMode.FINISH_HAND, matchExtraMinutes = 15,
        )
        val table = TableState(
            roundId = 1, tableId = 1, playerIds = listOf(1, 2, 3, 4),
            playerEastId = "1", playerSouthId = "2", playerWestId = "3", playerNorthId = "4",
            playerEastScore = "64", playerSouthScore = "-13", playerWestScore = "-22", playerNorthScore = "-29",
            isCompleted = false, useTotalsOnly = false,
        )
        val hands = List(16) { index ->
            val done = index < 6
            TableHand(
                handId = index + 1, playerWinnerId = if (done) "${index % 4 + 1}" else "", playerLooserId = "",
                handScore = if (done) "8" else "", isChickenHand = false, isDone = done,
                playerEastPenalty = "", playerSouthPenalty = "", playerWestPenalty = "", playerNorthPenalty = "",
            )
        }
        buildPlayerTournamentView(
            tournament,
            slots = registry.mapIndexed { index, player -> TournamentPlayer(id = index + 1, name = "", team = 0, assignedEmaId = player.emaId) },
            registry = registry,
            tables = listOf(table to hands),
            rankings = TournamentRankings(emptyList(), emptyList(), emptyList(), emptyList()),
            myEmaId = "04010001",
        )
    }

    private fun shot(name: String, conflict: Boolean = false, open: ComposeUiTest.() -> Unit) = screenshots("dialogs/$name", dialogVariants, ready = open) { variant ->
        MtsTheme(useDarkTheme = variant.dark) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                KoinApplication(application = { modules(module { single { PlayerTablePresenter(FixedHandRepository(conflict)) } }) }) {
                    Column(Modifier.padding(16.dp)) {
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
    }

    private fun ComposeUiTest.waitForText(text: String) = waitUntil(timeoutMillis = 10_000) { onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    private fun ComposeUiTest.tap(key: String) {
        waitForText(playerText(key))
        onAllNodesWithText(playerText(key)).onFirst().performClick()
    }

    private fun ComposeUiTest.choose(index: Int) {
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag(PLAYER_CHOICE_TAG).fetchSemanticsNodes().size > index }
        onAllNodesWithTag(PLAYER_CHOICE_TAG)[index].performClick()
    }

    /** The third choice wins 24 from the third choice of the losers: winner, loser and score. */
    private fun ComposeUiTest.winUpToTheScore() {
        tap("entry_add_win")
        choose(2)
        waitForText(playerText("entry_self_draw"))
        choose(2)
        waitForText(playerText("entry_hand_score"))
        onAllNodes(hasSetTextAction()).onFirst().performTextInput("24")
    }

    @Test
    fun winWinner() = shot("entry-win-winner") {
        tap("entry_add_win")
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag(PLAYER_CHOICE_TAG).fetchSemanticsNodes().size == 4 }
    }

    @Test
    fun winLoser() = shot("entry-win-loser") {
        tap("entry_add_win")
        choose(2)
        waitForText(playerText("entry_self_draw"))
    }

    @Test
    fun winScore() = shot("entry-win-score") { winUpToTheScore() }

    @Test
    fun checkHand() = shot("entry-check-hand") {
        winUpToTheScore()
        tap("entry_review")
        waitForText(playerText("entry_confirm_save"))
    }

    @Test
    fun draw() = shot("entry-draw") {
        tap("entry_add_draw")
        waitForText(playerText("entry_review"))
    }

    @Test
    fun penalty() = shot("entry-penalty") {
        tap("entry_add_penalty")
        choose(3)
        onAllNodes(hasSetTextAction()).onFirst().performTextInput("30")
        tap("entry_share")
    }

    @Test
    fun checkPenalty() = shot("entry-check-penalty") {
        tap("entry_add_penalty")
        choose(3)
        onAllNodes(hasSetTextAction()).onFirst().performTextInput("30")
        tap("entry_share")
        tap("entry_review")
        waitForText(playerText("entry_confirm_save"))
    }

    @Test
    fun tableChanged() = shot("entry-table-changed", conflict = true) {
        winUpToTheScore()
        tap("entry_review")
        tap("entry_confirm_save")
        waitForText(playerText("changed_close"))
    }
}
