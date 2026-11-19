package com.etologic.mahjongtournamentsuite.e2e

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnySibling
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * The organizer of a game day scenario (docs/scenarios.md), on a tablet emulator with the real admin app in staging.
 * It is not a check of one screen: it prepares a whole tournament as a person would, from the plan that
 * scripts/rehearsal/scenario.mjs wrote. Without the scenario arguments it does nothing, so the normal test runs skip it.
 *
 * Phase "setup": signs in, creates the accounts that exist before the tournament in Users, creates the tournament in
 * the form (the app computes the draw), imports the players (first the file with mistakes, then the right one),
 * assigns the last slots by hand, and sends the invitations, typing the emails of the slots assigned by hand.
 *
 * Phase "play": presses Play of each round on the Timer, waits until the tables of the round close or its time with
 * the extra time is over, and closes the tables that stay open from the review of Round control.
 *
 * Arguments: e2eBackendUrl (the staging API), scenarioEmail and scenarioPassword (the bot admin), scenarioDir (the
 * folder of plan.json and the files on the device, /data/local/tmp/scenario by default), scenarioPhase.
 * Logs: adb logcat -s SCENARIO
 */
class ScenarioOrganizerTest : AdminE2eTest() {
    private val args = InstrumentationRegistry.getArguments()
    private val email = args.getString("scenarioEmail").orEmpty()
    private val password = args.getString("scenarioPassword").orEmpty()
    private val dir = args.getString("scenarioDir") ?: "/data/local/tmp/scenario"
    private val phase = args.getString("scenarioPhase") ?: "setup"
    private lateinit var plan: JSONObject

    private fun log(message: String) = Log.i(TAG, "[$phase] $message")

    private data class Slot(
        val slot: Int,
        val emaId: String,
        val name: String,
        val lastName: String,
        val email: String,
        val via: String,
        val accountBefore: Boolean,
    )

    private val slots: List<Slot> by lazy {
        val list = plan.getJSONArray("slots")
        (0 until list.length()).map { index ->
            list.getJSONObject(index).let {
                Slot(
                    it.getInt("slot"), it.getString("emaId"), it.getString("name"), it.getString("lastName"), it.getString("email"),
                    it.getString("via"), it.getBoolean("accountBefore"),
                )
            }
        }
    }

    @Before
    fun readPlan() {
        assumeTrue("Not a scenario run", email.isNotBlank() && password.isNotBlank())
        plan = JSONObject(shell("cat $dir/plan.json"))
        Intents.init()
    }

    @After
    fun releaseIntents() {
        if (email.isNotBlank()) runCatching { Intents.release() }
    }

    /** A file of the plan, copied into the app, so the file picker can hand it over as a person picks it. */
    private fun planFile(name: String): Uri {
        val target = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, name)
        target.writeText(shell("cat $dir/$name"))
        return Uri.fromFile(target)
    }

    /** The next time the app opens the file picker, the person picks this file. */
    private fun pickNext(name: String) {
        val uri = planFile(name)
        intending(hasAction(Intent.ACTION_OPEN_DOCUMENT))
            .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(uri)))
    }

    @Test
    fun organize() {
        when (phase) {
            "setup" -> setup()
            "play" -> play()
            else -> error("Unknown phase $phase")
        }
    }

    private fun setup() {
        log("${plan.getString("name")}: ${slots.size} players")
        app.launch()
        c.signIn(email, password)
        // An account that is a player and staff chooses the part of the app after the sign-in.
        c.waitForAnyText("Management", PROFILE_BUTTON, timeoutMs = 60_000)
        if (c.isOnScreen("What do you want to open?")) c.clickButton("Management")
        c.waitForText(PROFILE_BUTTON, timeoutMs = 60_000)
        slots.filter { it.accountBefore }.forEach { createAccountInUsers(it) }
        createTheTournamentWithTheForm()
        importThePlayers()
        slots.filter { it.via == "manual" }.forEach { assignByHand(it) }
        sendTheInvitations()
        log("setup done")
    }

    // Users: the accounts of some players exist before the tournament, as people who played other tournaments.

    private fun createAccountInUsers(slot: Slot) {
        if (!c.isOnScreen("New user")) {
            // The list moves while it loads, so a first tap can miss. The organizer taps again.
            repeat(3) {
                if (!c.isOnScreen("New user")) runCatching { c.settle(); c.clickButton("Users") }
                runCatching { c.waitForText("New user", timeoutMs = 20_000) }
            }
            c.waitForText("New user", timeoutMs = 30_000)
        }
        c.clickButton("New user")
        c.waitForText("Add account")
        c.field("Email").performTextInput(slot.email)
        c.field("Confirm email").performTextInput(slot.email)
        chooseInSelect("Global role", "Player")
        c.field("EMA ID").performTextInput(slot.emaId)
        // The field shows the EMA player of the number when the registry has it.
        c.waitForText("EMA Player: ", substring = true, timeoutMs = 30_000)
        c.clickButton("Save and Send password-reset email")
        c.waitForText("Account created", substring = true, timeoutMs = 60_000)
        c.clickButton("OK")
        log("account of slot ${slot.slot} created")
    }

    private fun isDropdownList() = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.DropdownList)

    private fun chooseInSelect(label: String, option: String) {
        c.selectField(label).performClick()
        val choice = hasText(option) and hasClickAction() and !hasSetTextAction() and !isDropdownList()
        c.waitUntil(10_000) { c.onAllNodes(choice).fetchSemanticsNodes().isNotEmpty() }
        c.onAllNodes(choice).onLast().performClick()
        c.waitUntil(10_000) { c.onAllNodes(hasText(option) and isDropdownList()).fetchSemanticsNodes().isNotEmpty() }
    }

    // The form: the app computes the draw of the tournament.

    /** Sets a switch of the form to on or off. A switch is either the toggle of its label or a switch next to it. */
    private fun setSwitch(label: String, on: Boolean) {
        val own = hasText(label) and isToggleable()
        val next = isToggleable() and hasAnySibling(hasText(label))
        val nodes = c.onAllNodes(own).fetchSemanticsNodes().ifEmpty { c.onAllNodes(next).fetchSemanticsNodes() }
        val matcher = if (c.onAllNodes(own).fetchSemanticsNodes().isNotEmpty()) own else next
        check(nodes.isNotEmpty()) { "The form has no switch $label" }
        val state = nodes.last().config.getOrNull(SemanticsProperties.ToggleableState)
        val isOn = state == androidx.compose.ui.state.ToggleableState.On
        if (isOn != on) c.onAllNodes(matcher).onLast().apply { runCatching { performScrollTo() } }.performClick()
    }

    private fun createTheTournamentWithTheForm() {
        if (c.isOnScreen("New user")) c.clickButton("Back")
        c.waitForText("New tournament", timeoutMs = 60_000)
        c.clickButton("New tournament")
        c.waitForText("Creates rounds, tables, and players automatically.")
        c.field("Name").performTextInput(plan.getString("name"))
        c.field("Short name").performTextInput(plan.getString("shortName"))
        c.field("Players").performTextReplacement(plan.getInt("players").toString())
        c.field("Rounds").performTextReplacement(plan.getInt("rounds").toString())
        setSwitch("Teams", plan.getBoolean("isTeams"))
        setSwitch("Best hands", plan.getBoolean("countBestHands"))
        setSwitch("Chicken hands", plan.getBoolean("countChickenHands"))
        c.field("Match duration (minutes)").performTextReplacement(plan.getInt("matchDurationMinutes").toString())
        c.onAllNodes(isToggleable() and hasAnySibling(hasText("Finish the hand in play"))).onLast().performClick()
        c.field("Extra time (minutes)").performTextReplacement(plan.getInt("matchExtraMinutes").toString())
        c.clickLastButton("Venue")
        chooseCountry("Spain")
        c.field("City").performTextInput("Madrid")
        c.field("Venue name").performTextInput("Game day")
        c.field("Address").performTextInput("Calle Mayor 1, Madrid")
        c.field("Contact email").performTextInput(slots.first().email.replace(Regex("\\+[^@]*"), ""))
        c.clickLastButton("Schedule")
        val rounds = plan.getInt("rounds")
        c.waitForText("Round $rounds")
        val schedules = plan.getJSONArray("roundSchedules")
        val dates = c.onAllNodes(hasSetTextAction() and hasText("Date"))
        val times = c.onAllNodes(hasSetTextAction() and hasText("Start"))
        repeat(rounds) { index ->
            val schedule = schedules.getJSONObject(index)
            val (year, month, day) = schedule.getString("date").split("-")
            dates[index].performTextReplacement("$day/$month/$year")
            times[index].performTextReplacement(schedule.getString("startTime"))
        }
        c.clickLastButton("General")
        c.clickButton("Create")
        // The app computes the draw, and then it opens the players of the new tournament. A big draw takes a while.
        c.waitForText("Tournament players", timeoutMs = 600_000)
        log("tournament created")
    }

    private fun chooseCountry(name: String) {
        c.selectField("Country").performClick()
        // The menu lists every country and composes only the first ones. Its search field finds the country.
        c.waitUntil(10_000) { c.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
        c.onAllNodes(hasSetTextAction()).onLast().performTextInput(name)
        val option = hasText(name) and hasClickAction() and !hasSetTextAction() and !isDropdownList()
        c.waitUntil(5_000) { c.onAllNodes(option).fetchSemanticsNodes().isNotEmpty() }
        c.onAllNodes(option).onLast().performClick()
        c.waitUntil(5_000) { c.onAllNodes(hasText(name) and isDropdownList()).fetchSemanticsNodes().isNotEmpty() }
    }

    // The import: first a file with the mistakes of a hurried organizer, then the right file.

    private fun importThePlayers() {
        pickNext("players-errors.csv")
        c.clickButton("Import")
        c.waitForText("Import players from a file", timeoutMs = 60_000)
        // The preview marks the cells with mistakes and counts them in its summary line.
        c.waitForText("· Errors: ", substring = true, timeoutMs = 60_000)
        check(c.onAllNodes(hasText("· Errors: 0 ·", substring = true)).fetchSemanticsNodes().isEmpty()) { "The preview found no mistakes in the file with mistakes" }
        log("the file with mistakes shows its errors")
        pickNext("players.csv")
        c.clickButton("Choose another file")
        val imported = slots.count { it.via == "import" }
        c.waitForText("Players: $imported ·", substring = true, timeoutMs = 60_000)
        c.waitForText("· Errors: 0 ·", substring = true)
        c.waitForText("Import ($imported)")
        c.clickButton("Import ($imported)")
        // The app asks before it replaces players. A new tournament has none, so the import goes on at once.
        c.waitForAnyText("Replace all players?", "Import finished", timeoutMs = 120_000)
        if (c.isOnScreen("Replace all players?")) c.clickButton("Replace")
        c.waitForText("Import finished", timeoutMs = 120_000)
        c.waitForText("Players saved: $imported")
        // The list reloads behind the dialog when the import ends: a tap during that reload can be lost, so the
        // organizer looks again and closes the dialog until it is gone.
        repeat(3) {
            if (c.isOnScreen("Import finished")) c.clickLastButton("Close")
            runCatching { c.waitForNoText("Import finished", timeoutMs = 10_000) }
        }
        c.waitForNoText("Import finished")
        log("$imported players imported")
    }

    // By hand: the organizer finds the player in the EMA registry and assigns the slot.

    private fun assignByHand(slot: Slot) {
        openAssignDialogOf(slot.slot)
        c.onAllNodes(hasSetTextAction() and hasText("Search")).onLast().performTextInput(slot.emaId)
        val row = hasClickAction() and hasText(slot.emaId, substring = true) and !hasSetTextAction()
        c.waitUntil(20_000) { c.onAllNodes(row).fetchSemanticsNodes().isNotEmpty() }
        c.onAllNodes(row).onLast().performClick()
        c.waitForNoText("ASSIGN EMA PLAYER TO PLAYER ${slot.slot}")
        log("slot ${slot.slot} assigned by hand")
    }

    /** The empty slots have an Assign button each. The search of the list brings the slot to the screen. */
    private fun openAssignDialogOf(slot: Int) {
        val title = "ASSIGN EMA PLAYER TO PLAYER $slot"
        // The list is lazy and the free slots are at its end: it scrolls until an Assign button is composed.
        c.scrollListTo("Assign")
        val buttons = c.onAllNodes(hasText("Assign") and hasClickAction())
        c.waitUntil(20_000) { buttons.fetchSemanticsNodes().isNotEmpty() }
        // A lazy list composes a row a little before it shows it: the list scrolls until the button is on screen, or
        // the tap lands outside the screen. The free slots are the last ones, so the first Assign button left is the
        // slot of the plan.
        val button = hasText("Assign") and hasClickAction()
        repeat(3) {
            if (c.isOnScreen(title)) return@repeat
            c.onAllNodes(hasScrollToNodeAction()).fetchSemanticsNodes().indices.forEach { index ->
                runCatching { c.onAllNodes(hasScrollToNodeAction())[index].performScrollToNode(button) }
            }
            c.waitForIdle()
            buttons[0].performClick()
            runCatching { c.waitForText(title, timeoutMs = 10_000) }
        }
        c.waitForText(title)
    }

    // The invitations: one email to every player. The players without an account get one, the others a reminder.

    private fun sendTheInvitations() {
        c.clickButton("Invitations")
        c.waitForText("Invitations and reminders", timeoutMs = 60_000)
        // The dialog opens on the players without an account. The players with one get a reminder, so all are shown.
        c.clickButton("All (${slots.size})")
        c.waitForText("Select all shown (${slots.size})")
        c.clickButton("Select all shown (${slots.size})")
        c.waitForText("${slots.size} selected")
        c.clickButton("Send (${slots.size})")
        // The slots assigned by hand have no email yet, unless the person had an account before.
        val manual = slots.filter { it.via == "manual" && !it.accountBefore }
        if (manual.isNotEmpty()) {
            c.waitForText("Emails are missing", timeoutMs = 60_000)
            manual.forEach { slot ->
                c.onAllNodes(hasSetTextAction() and hasText("${slot.slot} · ", substring = true)).onLast()
                    .apply { runCatching { performScrollTo() } }.performTextInput(slot.email)
            }
            c.clickLastButton("Send (${manual.size})")
        }
        c.waitForText("Done", timeoutMs = 300_000)
        val invited = slots.count { !it.accountBefore }
        // The summary has one section for each outcome: its title, then its count.
        c.waitForText("Invited", timeoutMs = 60_000)
        check(c.onAllNodes(hasText("$invited")).fetchSemanticsNodes().isNotEmpty()) { "The summary does not count $invited invited players." }
        if (invited < slots.size) c.waitForText("Reminded")
        log("invitations sent: $invited new accounts, ${slots.size - invited} reminders")
    }

    // The rounds: Play on the Timer, then the tables that stay open after the time are closed by the organizer.

    private fun play() {
        app.launch()
        c.signIn(email, password)
        c.waitForAnyText("Management", PROFILE_BUTTON, timeoutMs = 60_000)
        if (c.isOnScreen("What do you want to open?")) c.clickButton("Management")
        val name = plan.getString("name")
        c.openTournament(name)
        c.waitForText("Players & results", timeoutMs = 60_000)
        val tournamentId = tournamentIdOf(name)
        val rounds = plan.getInt("rounds")
        val minutes = plan.getInt("matchDurationMinutes") + plan.getInt("matchExtraMinutes")
        for (round in 1..rounds) {
            startRoundOnTheTimer(tournamentId, round)
            log("round $round: Play")
            if (round > 1) closeOpenTablesOfEndedRounds()
            substituteALatePlayer(tournamentId, round)
            correctTheHands(tournamentId, round)
            val deadline = System.currentTimeMillis() + (minutes + 2) * 60_000L
            while (System.currentTimeMillis() < deadline && openTables(tournamentId, round) > 0) idle(30_000)
            log("round $round: ${openTables(tournamentId, round)} tables open at the end")
        }
        // The last round has no next round: its clock with the extra time is over, so its open tables show in the review.
        closeOpenTablesOfEndedRounds()
        log("play done")
    }

    // The corner cases of the editor: a player calls the organization, and the editor deletes or corrects a hand in the
    // table editor while the table goes on playing. The bots of the table see it, and write a deleted hand again.

    private fun correctTheHands(tournamentId: String, round: Int) {
        val cases = plan.optJSONArray("cornerCases") ?: return
        for (index in 0 until cases.length()) {
            val item = cases.getJSONObject(index)
            if (item.getInt("round") != round || item.getString("kind") !in setOf("delete", "edit")) continue
            val table = item.getInt("table")
            // The player calls when the table has played a few hands.
            val deadline = System.currentTimeMillis() + 8 * 60_000L
            var hands = doneHands(tournamentId, round, table)
            while (hands.size < item.getInt("minHands") && System.currentTimeMillis() < deadline) {
                idle(10_000)
                hands = doneHands(tournamentId, round, table)
            }
            check(hands.size >= item.getInt("minHands")) { "Table $round-$table played only ${hands.size} hands" }
            val newest = hands.maxOf { it.getInt("handId") }
            val handId = if (item.getString("which") == "last") newest else newest - item.optInt("back", 3)
            val before = hands.first { it.getInt("handId") == handId }
            openTable(round, table)
            // A hand of the player saved while the editor types replaces the unsaved changes ("Table updated"), as in a
            // real venue. The editor reads the message and does it again.
            var reached = false
            for (attempt in 1..3) {
                dismissTableUpdated()
                // The message can come while the editor types: the change is lost, and the editor does it again.
                val typed = runCatching { if (item.getString("kind") == "delete") deleteHand(handId) else editHand(handId, before) }
                if (tableUpdatedShown()) continue
                typed.getOrThrow()
                // Save shows only when the table has a change. A change that did not take (a poll drew the table again)
                // leaves no Save: the editor looks at the hand and does it again.
                val hasSave = runCatching {
                    c.waitUntil(10_000) { c.onAllNodes(hasText("Save") and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }
                }.isSuccess
                if (!hasSave) {
                    val shown = runCatching { scoreField(handId).fetchSemanticsNode().config.getOrNull(SemanticsProperties.EditableText)?.text }.getOrNull()
                    log("table $round-$table: no change to save after the ${item.getString("kind")} of hand $handId (score shown: $shown), attempt $attempt")
                    closeOpenMenu()
                    continue
                }
                // The message can also open just as Save is tapped: the tap goes nowhere, and the editor does it again.
                val saved = runCatching { c.clickButton("Save") }
                if (tableUpdatedShown()) continue
                if (saved.isFailure) {
                    c.waitForIdle()
                    if (tableUpdatedShown()) continue
                    closeOpenMenu()
                    c.clickButton("Save")
                }
                idle(3_000)
                dismissTableUpdated()
                // The table on the server has the change. A deleted hand can already be written again by the player.
                val after = handOf(tournamentId, round, table, handId)
                reached = after == null || !after.optBoolean("isDone") ||
                    after.optString("handScore") != before.optString("handScore") ||
                    after.optString("playerWinnerId") != before.optString("playerWinnerId")
                if (reached) break
                log("table $round-$table: the ${item.getString("kind")} did not reach the server, attempt $attempt")
            }
            check(reached) { "The ${item.getString("kind")} of hand $handId at table $round-$table did not reach the server" }
            log("table $round-$table: ${item.getString("kind")} of hand $handId (${item.getString("which")}, newest $newest)")
            closeTheEditor()
        }
    }

    /**
     * A player of a late table did not come. Minutes after the gong the organizer substitutes the player in Players:
     * the reason is late, from this round. The highest slot of the table, so the scorer of the bots stays.
     */
    private fun substituteALatePlayer(tournamentId: String, round: Int) {
        val cases = plan.optJSONArray("cornerCases") ?: return
        val item = (0 until cases.length()).map { cases.getJSONObject(it) }
            .firstOrNull { it.getString("kind") == "substitution" && it.getInt("round") == round } ?: return
        idle(item.optLong("afterMinutes", 1) * 60_000)
        val table = item.getInt("table")
        val list = E2eApi.call("GET", "/public/tournaments/$tournamentId/tables?roundId=$round").json().getJSONArray("tables")
        val ids = (0 until list.length()).map { list.getJSONObject(it) }.firstOrNull { it.getInt("tableId") == table }?.optJSONArray("playerIds")
        checkNotNull(ids) { "Table $round-$table has no players" }
        val slotId = (0 until ids.length()).maxOf { ids.getInt(it) }
        val slot = slots.first { it.slot == slotId }
        c.clickButton("Players")
        c.waitForText("Tournament players", timeoutMs = 60_000)
        // The search finds the slot by its EMA number. The email of the slot is the other key that only it has: it is
        // tried when the EMA number shows no single row.
        val oneRow = { c.onAllNodes(hasContentDescription("Player actions") and hasClickAction()).fetchSemanticsNodes().size == 1 }
        val rowsBefore = c.onAllNodes(hasContentDescription("Player actions") and hasClickAction()).fetchSemanticsNodes().size
        val unassignedBefore = c.onAllNodes(hasText("Not assigned")).fetchSemanticsNodes().size
        log("players screen: $rowsBefore rows shown, $unassignedBefore of them not assigned")
        c.onAllNodes(hasSetTextAction() and hasText("Search")).onLast().performTextInput(slot.emaId)
        // The EMA registry and the tables load after the slots: the row of the player can take a while to show.
        if (!runCatching { c.waitUntil(90_000) { oneRow() } }.isSuccess) {
            val rows = c.onAllNodes(hasContentDescription("Player actions") and hasClickAction()).fetchSemanticsNodes().size
            log("the search of EMA ${slot.emaId} (slot $slotId) shows $rows rows, so the test searches the email")
            c.onAllNodes(hasSetTextAction()).onLast().performTextClearance()
            c.onAllNodes(hasSetTextAction()).onLast().performTextInput(slot.email)
            c.waitUntil(20_000) { oneRow() }
        }
        // The list filters while the search is typed: a tap on the row being replaced is lost, so the menu is opened again.
        // The menu has Substitute once the tables of the tournament show progress: until then it is closed and opened again.
        for (attempt in 1..8) {
            if (c.isOnScreen("Substitute")) break
            c.waitForIdle()
            c.onAllNodes(hasContentDescription("Player actions") and hasClickAction()).onFirst().performClick()
            if (runCatching { c.waitForText("Substitute", timeoutMs = 10_000) }.isSuccess) break
            log("the menu of slot $slotId has no Substitute yet, attempt $attempt")
            closeOpenMenu()
            idle(10_000)
        }
        c.waitForText("Substitute")
        c.clickLastButton("Substitute")
        c.waitForText("A substitute plays for this player from the chosen round.", substring = true)
        c.onAllNodes(hasText("The player came late.")).onLast().performClick()
        c.onAllNodes(hasText("$round") and hasClickAction()).onLast().performClick()
        c.clickLastButton("Substitute")
        c.waitForNoText("A substitute plays for this player from the chosen round.", substring = true, timeoutMs = 60_000)
        val saved = E2eApi.call("GET", "/public/tournaments/$tournamentId/players").json().getJSONArray("players")
        val substitution = (0 until saved.length()).map { saved.getJSONObject(it) }.first { it.getInt("id") == slotId }.optJSONObject("substitution")
        check(substitution?.optString("reason") == item.getString("reason") && substitution.optInt("fromRound") == round) {
            "The substitution of slot $slotId is not on the server: $substitution"
        }
        log("slot $slotId of table $round-$table substituted (late, from round $round)")
        // The keyboard of the search is still open: the first back key only closes it.
        backToTheTournament()
    }

    private fun tableData(tournamentId: String, round: Int, table: Int): JSONObject? {
        val reply = runCatching { E2eApi.call("GET", "/public/tournaments/$tournamentId/tables/$round/$table") }.getOrNull()
        return if (reply?.status == 200) reply.json() else null
    }

    private fun doneHands(tournamentId: String, round: Int, table: Int): List<JSONObject> {
        val hands = tableData(tournamentId, round, table)?.optJSONArray("hands") ?: return emptyList()
        return (0 until hands.length()).map { hands.getJSONObject(it) }.filter { it.optBoolean("isDone") }
    }

    private fun handOf(tournamentId: String, round: Int, table: Int, handId: Int): JSONObject? {
        val hands = tableData(tournamentId, round, table)?.optJSONArray("hands") ?: return null
        return (0 until hands.length()).map { hands.getJSONObject(it) }.firstOrNull { it.getInt("handId") == handId }
    }

    /** The table editor of the tournament screen: the round with Previous and Next, then the chip of the table. */
    private fun openTable(round: Int, table: Int) {
        // The round chooser shows "Round N" between Previous round and Next round.
        val header = Regex("""^Round (\d+)$""")
        for (attempt in 1..20) {
            val current = c.onAllNodes(SemanticsMatcher("round header") { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.any { header.containsMatchIn(it.text) } == true
            }).fetchSemanticsNodes().firstNotNullOfOrNull { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.firstNotNullOfOrNull { header.find(it.text)?.groupValues?.get(1)?.toInt() }
            }
            if (current == round) break
            c.clickButton(if (current != null && current > round) "Previous round" else "Next round")
            // The button ignores a second press within 500 ms.
            idle(1_000)
        }
        c.selectTable(table)
        c.waitForText("hands done.", substring = true, timeoutMs = 60_000)
    }

    private fun cell(handId: Int, field: String) = c.onNode(hasTestTag("hand-$handId-$field"), useUnmergedTree = true)

    private fun tapCell(handId: Int, field: String) {
        cell(handId, field).apply { runCatching { performScrollTo() } }.performTouchInput { click(center) }
        c.waitForIdle()
    }

    private fun scoreField(handId: Int) =
        c.onAllNodes(hasSetTextAction() and hasAnyAncestor(hasTestTag("hand-$handId-score")), useUnmergedTree = true).onFirst()

    /** Picks "-" in the menu of a player cell: the open menu is the last one in the tree. */
    private fun clearPlayer(handId: Int, field: String) {
        tapCell(handId, field)
        idle(1_000)
        c.onAllNodes(hasText("-") and hasClickAction()).onLast().performClick()
        c.waitForIdle()
        closeOpenMenu()
    }

    /**
     * A menu of a player cell that stays open takes the touches of the screen: no tap reaches the editor. The menu is a
     * window of its own, so there is more than one root, and the back key closes it without leaving the editor.
     */
    private fun closeOpenMenu() {
        repeat(3) {
            if (c.onAllNodes(isRoot()).fetchSemanticsNodes().size <= 1 || tableUpdatedShown()) return
            shell("input keyevent KEYCODE_BACK")
            idle(1_000)
        }
    }

    /**
     * A done hand is locked: Done off opens its fields. A tap can land while the table is drawn again after a poll and
     * do nothing, so the editor looks at the score field and taps again.
     */
    private fun unlockHand(handId: Int) {
        val open = hasSetTextAction() and hasAnyAncestor(hasTestTag("hand-$handId-score")) and isEnabled()
        repeat(3) {
            if (c.onAllNodes(open, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()) return
            tapCell(handId, "done")
            runCatching { c.waitUntil(5_000) { c.onAllNodes(open, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() } }
        }
        check(c.onAllNodes(open, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()) { "Hand $handId stays locked after Done off" }
    }

    /** The hand no longer counts: Done off, no winner, no loser, no score. */
    private fun deleteHand(handId: Int) {
        unlockHand(handId)
        scoreField(handId).performTextReplacement("")
        clearPlayer(handId, "winner")
        clearPlayer(handId, "loser")
    }

    /** Another score that is valid for any hand, also a chicken hand (8 to 12). */
    private fun editHand(handId: Int, before: JSONObject) {
        val score = if (before.optString("handScore") == "11") "10" else "11"
        unlockHand(handId)
        scoreField(handId).performTextReplacement(score)
        tapCell(handId, "done")
    }

    /** A save of a player while the editor works shows "Table updated", or "Tables updated" when other tables changed too. */
    private fun tableUpdatedShown(): Boolean = c.isOnScreen("Table updated") || c.isOnScreen("Tables updated")

    private fun dismissTableUpdated() {
        repeat(3) {
            if (!tableUpdatedShown()) return
            c.clickLastButton("OK")
            c.waitUntil(10_000) { !tableUpdatedShown() }
        }
    }

    private fun closeTheEditor() {
        dismissTableUpdated()
        c.waitForText("Players & results", timeoutMs = 60_000)
    }

    private fun tournamentIdOf(name: String): String {
        val list = E2eApi.call("GET", "/public/tournaments").json().getJSONArray("tournaments")
        return (0 until list.length()).map { list.getJSONObject(it) }.first { it.getString("name") == name }.getString("id")
    }

    private fun openTables(tournamentId: String, round: Int): Int {
        val reply = runCatching { E2eApi.call("GET", "/public/tournaments/$tournamentId/rounds/$round/tables-with-hands") }.getOrNull()
        if (reply == null || reply.status != 200) return Int.MAX_VALUE
        val tables = reply.json().getJSONArray("tables")
        return (0 until tables.length()).count { !tables.getJSONObject(it).getJSONObject("table").optBoolean("isCompleted") }
    }

    /** Lets the app run in real time while the organizer waits. */
    private fun idle(ms: Long) {
        val until = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < until) {
            runCatching {
                c.mainClock.advanceTimeBy(500)
                c.waitForIdle()
            }
            Thread.sleep(500)
        }
    }

    /** Play on the Timer of the tournament, as in TournamentJourneyTest. The Timer opens in its own window. */
    private fun startRoundOnTheTimer(tournamentId: String, round: Int) {
        c.clickButton("Timer")
        c.waitForAnyText("Start timer", "Pause timer", timeoutMs = 60_000)
        val deadline = System.currentTimeMillis() + 60_000
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
        c.clickButton("Start timer")
        c.waitForText("Pause timer")
        val clocks = E2eApi.call("GET", "/public/tournaments/$tournamentId/round-clocks").json().getJSONArray("roundClocks")
        check((0 until clocks.length()).any { clocks.getJSONObject(it).getInt("roundId") == round }) { "Round $round has no clock" }
        // The organizer closes the Timer window. Its back key is not reliable on the emulator: it can leave the Timer
        // alive behind the app, where its texts still show to the test.
        closeTheTimerWindow()
        backToTheTournament()
    }

    /**
     * Back to the tournament screen. A back key can be used up by a keyboard or lost while a window takes the focus, and
     * a tap meanwhile is not injected: each try is a back key, then the Back of the top bar when there is one.
     */
    private fun backToTheTournament() {
        repeat(4) {
            if (c.isOnScreen("Players & results") && mainActivityResumed()) return
            // The tree of a window in the background is still readable, but its back keys go to the window in front.
            bringTheAppToTheFront()
            if (c.isOnScreen("Players & results")) return
            log("back to the tournament, attempt ${it + 1}: resumed ${resumedActivities()}, ${timerActivities().size} timers")
            shell("input keyevent KEYCODE_BACK")
            runCatching { c.waitForText("Players & results", timeoutMs = 5_000) }
            if (c.isOnScreen("Players & results")) return
            // The Timer window has no Back of its own: the Back under it would leave the tournament.
            // When its back key is lost, the test closes the Timer as its back key would.
            if (timerActivities().isNotEmpty()) {
                closeTheTimerWindow()
            } else {
                runCatching { c.onAllNodes(hasContentDescription("Back") and hasClickAction()).onFirst().performClick() }
            }
            runCatching { c.waitForText("Players & results", timeoutMs = 15_000) }
        }
        c.waitForText("Players & results", timeoutMs = 60_000)
    }

    private fun resumedActivities(): List<String> {
        var names = emptyList<String>()
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync {
            names = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)
                .map { it.javaClass.simpleName }
        }
        return names
    }

    private fun mainActivityResumed() = "MainActivity" in resumedActivities()

    /**
     * The Timer and the rankings open in tasks of their own. When one of them closes, the task of the app is not
     * always the one in front. FLAG_ACTIVITY_REORDER_TO_FRONT (with NEW_TASK, 0x10020000) brings the running
     * MainActivity back as it is. A launcher intent would not: the test started the app with another intent, so the
     * launcher intent makes a second MainActivity, with its own graph of the admin part.
     */
    private fun bringTheAppToTheFront() {
        if (mainActivityResumed()) return
        val packageName = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.packageName
        log("the app is not in front (resumed: ${resumedActivities()}), so the test brings it back")
        shell("am start -f 0x10020000 -n $packageName/com.etologic.mahjongtournamentsuite.MainActivity")
        val until = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < until && !mainActivityResumed()) Thread.sleep(500)
        runCatching { c.waitForIdle() }
    }

    /** The Timer activities that are alive, in front or behind the app. */
    private fun timerActivities(): List<android.app.Activity> {
        var found = emptyList<android.app.Activity>()
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val monitor = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
            found = listOf(
                androidx.test.runner.lifecycle.Stage.RESUMED,
                androidx.test.runner.lifecycle.Stage.PAUSED,
                androidx.test.runner.lifecycle.Stage.STOPPED,
            ).flatMap { monitor.getActivitiesInStage(it) }.filter { it.javaClass.simpleName == "TimerActivity" }
        }
        return found
    }

    /** Finishes the Timer activity, which is what its back key does. */
    private fun closeTheTimerWindow() {
        val timers = timerActivities()
        if (timers.isEmpty()) return
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync { timers.forEach { it.finish() } }
        val until = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < until && timerActivities().isNotEmpty()) Thread.sleep(500)
        log("the test closed the Timer window (${timers.size})")
        Thread.sleep(1_000)
        bringTheAppToTheFront()
    }

    /** Round control warns of open tables in ended rounds. The review closes them all, as the organizer at the end. */
    private fun closeOpenTablesOfEndedRounds() {
        c.clickButton("Round control")
        c.waitForText("Round control", timeoutMs = 60_000)
        idle(5_000)
        if (c.isOnScreen("Review")) {
            c.clickButton("Review")
            c.waitForText("Open tables in ended rounds")
            // The checkbox of the header selects every row.
            val boxes = c.onAllNodes(androidx.compose.ui.test.isToggleable())
            boxes[0].performClick()
            c.clickButton("Close selected")
            c.waitForNoText("Open tables in ended rounds", timeoutMs = 120_000)
            log("the organizer closed the open tables of the ended rounds")
        }
        shell("input keyevent KEYCODE_BACK")
        c.waitForText("Players & results", timeoutMs = 60_000)
    }

    private companion object {
        const val TAG = "SCENARIO"
    }
}
