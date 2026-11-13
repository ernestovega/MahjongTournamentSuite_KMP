package com.etologic.mahjongtournamentsuite.e2e

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnySibling
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * A whole tournament run from the admin app, from the form to the final ranking.
 *
 * The organizer creates the tournament with the form, assigns a player, starts every round on the Timer, follows the
 * tables that the players fill in on their own phones, writes the last hand of a table in the table editor, and shows
 * the ranking. The phones of the 16 players are played through the API of the local backend.
 */
class TournamentJourneyTest : AdminE2eTest() {
    private lateinit var players: List<JourneyApi.Player>
    private val bySlot get() = players.withIndex().associate { (index, player) -> index + 1 to player }

    @Before
    fun preparePlayers() {
        E2eApi.deleteTournamentsNamed(NAME)
        JourneyApi.removePlayers()
        players = JourneyApi.createPlayers(16)
    }

    @After
    fun removeTournament() {
        E2eApi.deleteTournamentsNamed(NAME)
        JourneyApi.removePlayers()
    }

    // The form, as in CreateTournamentTest.

    private fun isDropdownList() = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.DropdownList)

    private fun chooseCountry(name: String) {
        c.selectField("Country").performClick()
        val option = hasText(name) and hasClickAction() and !hasSetTextAction() and !isDropdownList()
        c.waitUntil(5_000) { c.onAllNodes(option).fetchSemanticsNodes().isNotEmpty() }
        c.onAllNodes(option).onLast().performClick()
        c.waitUntil(5_000) { c.onAllNodes(hasText(name) and isDropdownList()).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun createTheTournamentWithTheForm() {
        c.clickButton("New tournament")
        c.waitForText("Creates rounds, tables, and players automatically.")
        c.field("Name").performTextInputText(NAME)
        c.field("Short name").performTextInputText("JOURNEY")
        c.field("Players").performTextReplacementText("16")
        c.field("Rounds").performTextReplacementText("2")
        c.clickLastButton("Venue")
        chooseCountry("Spain")
        c.field("City").performTextInputText("Madrid")
        c.field("Venue name").performTextInputText("E2E club")
        c.field("Address").performTextInputText("Calle Mayor 1, Madrid")
        c.field("Contact email").performTextInputText("e2e@example.com")
        c.clickLastButton("Schedule")
        c.waitForText("Round 2")
        val today = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))
        val dates = c.onAllNodes(hasSetTextAction() and hasText("Date"))
        val times = c.onAllNodes(hasSetTextAction() and hasText("Start"))
        repeat(2) { index ->
            dates[index].performTextReplacement(today)
            times[index].performTextReplacement("${10 + index}:00")
        }
        c.clickLastButton("General")
        c.clickButton("Create")
        // The app draws the seating, and then it opens the players of the new tournament.
        c.waitForText("Tournament players", timeoutMs = 90_000)
        c.waitForText("Not assigned")
    }

    /** The organizer assigns slot 1 with the EMA player picker. */
    private fun assignTheFirstSlotWithThePicker(player: JourneyApi.Player) {
        c.clickButton("Assign")
        c.waitForText("ASSIGN EMA PLAYER TO PLAYER 1")
        c.field("Search by name, country, or EMA number").performTextInput(player.emaId)
        val row = hasClickAction() and hasText(player.emaId, substring = true) and !hasSetTextAction()
        c.waitUntil(10_000) { c.onAllNodes(row).fetchSemanticsNodes().isNotEmpty() }
        c.onAllNodes(row).onLast().performClick()
        c.waitForNoText("ASSIGN EMA PLAYER TO PLAYER 1")
        c.waitForText(player.lastName, substring = true)
    }

    /** Back from the players to the tournament. */
    private fun backToTheTournament() {
        c.clickButton("Back")
        c.waitForAnyText("Players & results", PROFILE_BUTTON)
        if (!c.isOnScreen("Players & results")) c.openTournament(NAME)
        c.waitForText("Players & results")
    }

    /** Play on the Timer of the tournament. The Timer opens in its own window, which the test closes again. */
    private fun startRoundOnTheTimer(tournamentId: String, round: Int) {
        c.clickButton("Timer")
        c.waitForAnyText("Start timer", "Pause timer")
        // The Timer opens on the round in play, and it hides the round arrows while its clock runs.
        // The organizer pauses the previous round first, and then moves to the next round.
        val deadline = System.currentTimeMillis() + 30_000
        while (true) {
            c.settle()
            if (c.isOnScreen("Round $round") && c.isOnScreen("Start timer")) break
            check(System.currentTimeMillis() < deadline) { "The Timer did not reach round $round" }
            when {
                c.isOnScreen("Pause timer") -> {
                    c.clickButton("Pause timer")
                    c.waitForText("Start timer")
                }
                c.isOnScreen("Increase round") -> c.clickButton("Increase round")
            }
        }
        c.waitForText("Press Play when the starting gong sounds", substring = true)
        c.clickButton("Start timer")
        c.waitForText("Pause timer")
        check(JourneyApi.hasClock(tournamentId, round)) { "Round $round has no clock on the server" }
        shell("input keyevent KEYCODE_BACK")
        c.waitForNoText("Pause timer")
        c.waitForText("Players & results")
    }

    // The table editor, as in TableEditingTest. An empty winner or loser cell shows "-".
    private val emptyChoice = SemanticsMatcher("empty winner or loser") { node ->
        node.config.getOrNull(SemanticsProperties.Role) == Role.DropdownList &&
            node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == "-" } == true
    }

    private fun playerOption(id: Int) = SemanticsMatcher("player $id") { node ->
        node.config.getOrNull(SemanticsProperties.Text)?.any { it.text.startsWith("$id - ") } == true
    } and hasClickAction()

    /** Writes the last hand of the table in the editor: [winner] wins with 8 from the discard of [loser]. */
    private fun writeTheLastHandInTheEditor(winner: Int, loser: Int) {
        val cells = c.onAllNodes(emptyChoice)
        val count = cells.fetchSemanticsNodes().size
        cells[count - 2].performScrollTo()
        cells[count - 2].performClick()
        c.onAllNodes(playerOption(winner)).onLast().performClick()
        c.onAllNodes(emptyChoice).let { it[it.fetchSemanticsNodes().size - 1].performClick() }
        c.onAllNodes(playerOption(loser)).onLast().performClick()
        c.onAllNodes(hasSetTextAction() and hasText("")).onLast().performTextInput("8")
        val toggles = c.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState))
        toggles.onLast().performClick()
        // The editor does not close a table by itself after hand 16, unlike the phones. The organizer turns on the
        // "Completed" switch. The switches above the rows are "Manual scores" and then "Completed", as in TableEditingTest.
        toggles[1].performScrollTo()
        toggles[1].performClick()
        c.clickButton("Save")
        c.waitForText("16 of 16 hands done.")
        closeTableNotices()
    }

    /** The editor tells when the phones change a table. The test closes that notice, as the organizer would. */
    private fun closeTableNotices() {
        c.settle()
        while (c.isOnScreen("Table updated") || c.isOnScreen("Tables updated")) {
            c.clickButton("OK")
            c.settle()
        }
    }

    /** The server saves in the background of the screen. This waits until it reports [completed], up to 30 s. */
    private fun waitForTournamentCompleted(tournamentId: String, tables: List<JourneyApi.DrawTable>, completed: Boolean) {
        val deadline = System.currentTimeMillis() + 30_000
        while (JourneyApi.tournament(tournamentId).getBoolean("isCompleted") != completed) {
            if (System.currentTimeMillis() > deadline) {
                val states = tables.joinToString { table ->
                    val data = JourneyApi.table(tournamentId, table)
                    "R${table.roundId}T${table.tableId}=${data.optBoolean("isCompleted")}/v${data.optInt("version")}"
                }
                error("The tournament is ${if (completed) "not " else ""}completed. Tables: $states")
            }
            Thread.sleep(500)
        }
    }

    /**
     * The phones play a round: every table writes its 16 hands, except the table of slot 1, which stops at hand 15 for
     * the organizer. Slot 1 wins every hand of its table. The other tables only draw.
     */
    private fun phonesPlayTheRound(tournamentId: String, tables: List<JourneyApi.DrawTable>, round: Int): JourneyApi.DrawTable {
        val roundTables = tables.filter { it.roundId == round }
        val ofSlotOne = roundTables.first { 1 in it.playerIds }
        roundTables.filter { it != ofSlotOne }.forEach { JourneyApi.playTable(tournamentId, it, bySlot) }
        JourneyApi.playTable(tournamentId, ofSlotOne, bySlot, hands = 15, winner = 1)
        return ofSlotOne
    }

    @Test
    fun anOrganizerRunsAWholeTournamentFromTheFormToTheRanking() {
        start(E2eAccounts.ADMIN, ready = "New tournament")
        createTheTournamentWithTheForm()
        val tournamentId = JourneyApi.tournamentId(NAME)
        val tables = JourneyApi.tables(tournamentId)
        check(tables.size == 8) { "The draw has ${tables.size} tables" }

        // Slot 1 with the picker, the other 15 slots as an import would do.
        assignTheFirstSlotWithThePicker(players[0])
        (2..16).forEach { JourneyApi.assign(tournamentId, it, players[it - 1].emaId) }
        backToTheTournament()
        c.waitForText("Round 1")

        // Round 1: Play on the Timer, and the players fill the tables on their phones.
        startRoundOnTheTimer(tournamentId, 1)
        val firstTable = phonesPlayTheRound(tournamentId, tables, 1)
        val drawTable = tables.first { it.roundId == 1 && it != firstTable }
        // The editor follows the phones: a table that the players finished shows all its hands.
        c.selectTable(drawTable.tableId)
        c.waitForText("16 of 16 hands done.", timeoutMs = 60_000)
        // The table of slot 1 waits for its last hand, which the organizer writes.
        c.selectTable(firstTable.tableId)
        c.waitForText("15 of 16 hands done.", timeoutMs = 60_000)
        writeTheLastHandInTheEditor(winner = 1, loser = firstTable.playerIds.first { it != 1 })
        val tableDeadline = System.currentTimeMillis() + 30_000
        while (!JourneyApi.table(tournamentId, firstTable).getBoolean("isCompleted")) {
            check(System.currentTimeMillis() < tableDeadline) { "The last hand of the editor did not complete table ${firstTable.tableId}" }
            Thread.sleep(500)
        }
        waitForTournamentCompleted(tournamentId, tables, completed = false)

        // Round 2: the same, and the last hand of the organizer finishes the tournament.
        startRoundOnTheTimer(tournamentId, 2)
        val lastTable = phonesPlayTheRound(tournamentId, tables, 2)
        c.clickButton("Next round")
        c.waitForText("Round 2")
        c.selectTable(lastTable.tableId)
        c.waitForText("15 of 16 hands done.", timeoutMs = 60_000)
        writeTheLastHandInTheEditor(winner = 1, loser = lastTable.playerIds.first { it != 1 })
        waitForTournamentCompleted(tournamentId, tables, completed = true)

        // The ranking hides the first three places until the organizer shows them. Slot 1 won every hand of its tables.
        c.clickButton("Ranking")
        c.waitForText("Start automatic paging")
        c.waitForText("???")
        c.assertNoText(players[0].lastName, substring = true)
        c.onAllNodes(isToggleable() and hasAnySibling(hasText("Show first 3"))).onLast().performClick()
        c.waitForText(players[0].lastName, substring = true)
        check(JourneyApi.finalRank(tournamentId, players[0].emaId) == 1) { "Slot 1 is not first in the final ranking" }
    }

    private companion object {
        const val NAME = "E2E journey admin"
    }
}
