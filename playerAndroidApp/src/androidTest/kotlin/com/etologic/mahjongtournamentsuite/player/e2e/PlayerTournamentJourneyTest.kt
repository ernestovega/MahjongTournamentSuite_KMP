package com.etologic.mahjongtournamentsuite.player.e2e

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.etologic.mahjongtournamentsuite.player.PLAYER_SEARCH_TAG
import com.etologic.mahjongtournamentsuite.player.SEAT_OPTION_TAG
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * A whole tournament seen from the phone of one player, from the draw to the final ranking.
 *
 * The phone under test belongs to slot 1. The organizers (create, assign, Timer) and the other 15 players on their own
 * phones play through the API of the local backend, as they would from their devices. 16 players, 2 rounds, 4 tables
 * per round. Slot 1 sits at table 1 in both rounds, and its hand closes the last table of the tournament.
 */
class PlayerTournamentJourneyTest : PlayerE2eTest() {
    private lateinit var players: List<JourneyApi.Player>
    private val bySlot get() = players.withIndex().associate { (index, player) -> index + 1 to player }
    private lateinit var tournamentId: String
    private val draw = JourneyApi.SIXTEEN_IN_TWO_ROUNDS

    @Before
    fun prepareTournament() {
        E2eApi.deleteTournamentsNamed(NAME)
        JourneyApi.removePlayers()
        players = JourneyApi.createPlayers(16)
        tournamentId = JourneyApi.createTournament(NAME, draw)
        players.forEachIndexed { index, player -> JourneyApi.assign(tournamentId, index + 1, player.emaId) }
    }

    @After
    fun removeTournament() {
        E2eApi.deleteTournamentsNamed(NAME)
        JourneyApi.removePlayers()
    }

    private fun table(round: Int, number: Int) = draw.first { it.roundId == round && it.tableId == number }

    /** Opens the next table of the player that has no hands yet, from the "Me" list of the tournament. */
    private fun openMyTable(round: Int) {
        c.waitForText("Me")
        c.settle()
        c.clickButton("Me")
        c.scrollListTo("0/16 hands")
        c.clickButton("0/16 hands")
        c.waitForText("Round $round - Table 1")
    }

    private fun addDraw() {
        c.clickButton("Add draw")
        c.clickButton("Review")
        c.waitForText("Check hand")
        c.clickButton("Confirm and save")
    }

    /** The hand in play shows as "N/16" on the table. */
    private fun waitForHandInPlay(hand: Int) = c.waitForText("$hand/16", substring = true, timeoutMs = 60_000)

    @Test
    fun aPlayerPlaysAWholeTournamentFromThePhone() {
        start(players[0].email, ready = "My tournaments")
        c.openTournament(NAME)

        // Round 1. The seats open a few minutes before the planned start. The player sets them before the gong.
        openMyTable(1)
        c.waitForText("Waiting for the starting gong", substring = true)
        c.clickButton("Set the seats")
        c.waitForText("Who sits at East?")
        listOf("South", "West", "North").forEach { next ->
            c.onAllNodesWithTag(SEAT_OPTION_TAG).onFirst().performClick()
            c.waitForText(if (next == "North") "North:" else "Who sits at $next?", substring = next == "North")
        }
        c.clickButton("Save the seats")
        c.waitForText("Waiting for the starting gong", substring = true)
        c.assertNoText("Add draw")

        // The organizer presses Play on the Timer. The phone shows the hand buttons and the time left.
        JourneyApi.play(tournamentId, 1)
        c.waitForText("Add draw", timeoutMs = 30_000)
        c.waitForText("Time left:", substring = true)

        // Hand 1 from this phone: a draw. Hand 2: a self-drawn win of the East seat.
        waitForHandInPlay(1)
        addDraw()
        waitForHandInPlay(2)
        c.clickButton("Add win")
        c.waitForText("Winner")
        c.pickSeat("East", which = 0)
        c.clickButton("Self-draw")
        c.field("Hand score").performTextInput("8")
        c.clickButton("Review")
        c.waitForText("Check hand")
        c.clickButton("Confirm and save")
        waitForHandInPlay(3)

        // The other three phones of the table write hands 3 to 15. This phone follows them.
        val myFirstTable = table(1, 1)
        repeat(13) { index ->
            JourneyApi.writeHand(tournamentId, myFirstTable, bySlot.getValue(myFirstTable.playerIds[1 + index % 3]), winner = 1)
        }
        waitForHandInPlay(16)

        // The other tables of round 1 play all their hands on their phones.
        draw.filter { it.roundId == 1 && it.tableId != 1 }.forEach { JourneyApi.playTable(tournamentId, it, bySlot) }

        // Hand 16 closes the table. The phone offers no "last hand" box there: the server closes the table by itself.
        addDraw()
        c.waitForNoText("Add draw", timeoutMs = 30_000)
        check(JourneyApi.table(tournamentId, myFirstTable).getBoolean("isCompleted")) { "Table 1 of round 1 is not completed" }
        check(!JourneyApi.tournament(tournamentId).getBoolean("isCompleted")) { "The tournament finished after round 1" }

        // Round 2. Another player of the table set the seats, and the organizer pressed Play.
        val mySecondTable = table(2, 1)
        JourneyApi.setSeats(tournamentId, mySecondTable, bySlot.getValue(mySecondTable.playerIds[1]))
        JourneyApi.play(tournamentId, 2)
        c.clickButton("Back")
        openMyTable(2)
        c.waitForText("Add draw", timeoutMs = 30_000)
        draw.filter { it.roundId == 2 && it.tableId != 1 }.forEach { JourneyApi.playTable(tournamentId, it, bySlot) }
        repeat(15) { index ->
            JourneyApi.writeHand(tournamentId, mySecondTable, bySlot.getValue(mySecondTable.playerIds[1 + index % 3]), winner = 1)
        }
        waitForHandInPlay(16)

        // The last hand of the tournament, from this phone.
        addDraw()
        c.waitForNoText("Add draw", timeoutMs = 30_000)
        check(JourneyApi.tournament(tournamentId).getBoolean("isCompleted")) { "The last hand did not finish the tournament" }

        // The ranking of the finished tournament. Slot 1 won almost every hand of its tables and is first.
        c.clickButton("Back")
        c.waitForText("Ranking")
        c.clickButton("Ranking")
        c.waitForText("Players")
        c.scrollListTo(players[0].lastName, substring = true)
        check(JourneyApi.finalRank(tournamentId, players[0].emaId) == 1) { "Slot 1 is not first in the final ranking" }

        // The winner in the Players tab: the search finds the player and the player shows its tables.
        c.clickButton("Players")
        c.onNodeWithTag(PLAYER_SEARCH_TAG).performTextInput(players[0].lastName)
        c.onAllNodes(hasClickAction() and hasText("#1")).onFirst().performClick()
        c.waitForText("Stats")
        c.waitForText("Table 1", substring = true)
    }

    private companion object {
        const val NAME = "E2E journey player"
    }
}
