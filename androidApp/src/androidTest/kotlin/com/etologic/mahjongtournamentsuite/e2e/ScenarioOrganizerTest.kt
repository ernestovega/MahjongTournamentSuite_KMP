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
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
            else -> error("Unknown phase $phase")
        }
    }

    private fun setup() {
        log("${plan.getString("name")}: ${slots.size} players")
        app.launch()
        c.signIn(email, password)
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
            c.clickButton("Users")
            c.waitForText("New user", timeoutMs = 60_000)
        }
        c.clickButton("New user")
        c.waitForText("Add account")
        c.field("Email").performTextInput(slot.email)
        c.field("Confirm email").performTextInput(slot.email)
        chooseInSelect("Global role", "Player")
        c.field("EMA ID").performTextInput(slot.emaId)
        c.waitForText(slot.lastName, substring = true, timeoutMs = 30_000)
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
        c.waitForText("The EMA registry has no player with the number 99999999.", timeoutMs = 60_000)
        c.waitForText("is not valid.", substring = true)
        log("the file with mistakes shows its errors")
        pickNext("players.csv")
        c.clickButton("Choose another file")
        val imported = slots.count { it.via == "import" }
        c.waitForText("Import ($imported)", timeoutMs = 60_000)
        c.clickButton("Import ($imported)")
        c.waitForText("Replace all players?")
        c.clickButton("Replace")
        c.waitForText("Import finished", timeoutMs = 120_000)
        c.waitForText("Players saved: $imported")
        c.clickLastButton("Close")
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
        val buttons = c.onAllNodes(hasText("Assign") and hasClickAction())
        c.waitUntil(20_000) { buttons.fetchSemanticsNodes().isNotEmpty() }
        // The free slots are the last ones, so the first Assign button left is the slot of the plan.
        buttons[0].apply { runCatching { performScrollTo() } }.performClick()
        c.waitForText(title)
    }

    // The invitations: one email to every player. The players without an account get one, the others a reminder.

    private fun sendTheInvitations() {
        c.clickButton("Invitations")
        c.waitForText("Invitations and reminders", timeoutMs = 60_000)
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
        c.waitForText("Accounts created and invitations sent: $invited", substring = true, timeoutMs = 60_000)
        log("invitations sent: $invited new accounts, ${slots.size - invited} reminders")
    }

    private companion object {
        const val TAG = "SCENARIO"
    }
}
