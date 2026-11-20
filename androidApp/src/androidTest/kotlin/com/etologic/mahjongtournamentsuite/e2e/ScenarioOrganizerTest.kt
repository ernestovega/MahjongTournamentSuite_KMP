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
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
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
        /** A player of an earlier game day, who comes back with that account. */
        val returning: Boolean,
    )

    private val slots: List<Slot> by lazy {
        val list = plan.getJSONArray("slots")
        (0 until list.length()).map { index ->
            list.getJSONObject(index).let {
                Slot(
                    it.getInt("slot"), it.getString("emaId"), it.getString("name"), it.getString("lastName"), it.getString("email"),
                    it.getString("via"), it.getBoolean("accountBefore"), it.optBoolean("returning"),
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
        // The players of earlier game days have their account already.
        slots.filter { it.accountBefore && !it.returning }.forEach { createAccountInUsers(it) }
        ensureTheOrganization()
        if (plan.has("byHand")) registrationsByHand()
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

    // Organizations: every tournament has a main organizer. The game days are organized by one club, which the first
    // game day creates in Organizations and the next ones reuse.

    private fun ensureTheOrganization(name: String = ORGANIZATION) {
        // Organizations opens from the list of tournaments, not from Users.
        if (c.isOnScreen("New user")) c.clickButton("Back")
        c.waitForText("New tournament", timeoutMs = 60_000)
        repeat(3) {
            if (!c.isOnScreen("New organization")) runCatching { c.settle(); c.clickButton("Organizations") }
            runCatching { c.waitForText("New organization", timeoutMs = 20_000) }
        }
        c.waitForText("New organization", timeoutMs = 30_000)
        runCatching { c.waitForAnyText(name, "There are no organizations yet.", timeoutMs = 30_000) }
        if (!c.isOnScreen(name)) {
            c.clickButton("New organization")
            c.waitForText("Club premises")
            c.field("Name").performTextInput(name)
            // A federation has no premises, so the dialog asks for less.
            c.onAllNodes(hasText("Federation") and hasClickAction()).onLast().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
            c.waitForNoText("Club premises")
            chooseCountry("Spain")
            c.clickLastButton("Save")
            c.waitUntil(60_000) { c.onAllNodes(hasText("Save") and hasClickAction()).fetchSemanticsNodes().isEmpty() }
            c.waitForText(name, timeoutMs = 30_000)
            log("organization $name created")
        }
        c.clickButton("Back")
        c.waitForText("New tournament", timeoutMs = 60_000)
    }

    // Registrations, by hand in the screens (corners only, plan.byHand): the club admin runs a tournament of its club
    // with registrations from its creation to its draw, and a player registers and cancels in the player app.

    private val byHand by lazy { plan.getJSONObject("byHand") }
    private fun accountEmail(suffix: String) = email.replace("+mts-botadmin@", "+mts-$suffix@")

    private fun registrationsByHand() {
        ensureTheOrganization(CO_ORGANIZATION)
        makeTheClubAdmin()
        signOut()
        signInToTheAdmin(accountEmail("botclub"))
        createTheRegistrationTournament()
        importTheGoogleForm()
        addAPersonByHand(byHand.getString("added"))
        signOut()
        registerAndCancelInThePlayerApp(accountEmail("cal01"))
        signOutOfThePlayerApp()
        signInToTheAdmin(accountEmail("botclub"))
        c.openTournament(byHand.getString("name"))
        c.waitForText("Registration settings", timeoutMs = 60_000)
        removeAndGiveThePlace(byHand.getString("removed"), byHand.getString("added"))
        closeAndMakeTheDraw()
        signOut()
        signInToTheAdmin(email)
        log("registrations by hand done")
    }

    private fun signInToTheAdmin(account: String) {
        c.signIn(account, password)
        c.waitForAnyText("Management", PROFILE_BUTTON, timeoutMs = 60_000)
        if (c.isOnScreen("What do you want to open?")) c.clickButton("Management")
        c.waitForText(PROFILE_BUTTON, timeoutMs = 60_000)
        c.waitForText("New tournament", timeoutMs = 60_000)
    }

    /** The profile of the admin part: Logout, and the confirmation. */
    private fun signOut(profile: String = PROFILE_BUTTON) {
        if (c.isOnScreen("Back")) runCatching { c.clickButton("Back") }
        c.waitForText(profile, timeoutMs = 60_000)
        c.clickButton(profile)
        c.waitForText("Logout")
        c.clickButton("Logout")
        c.waitForText("Yes, sign out")
        c.clickButton("Yes, sign out")
        c.waitForText("Sign in", timeoutMs = 60_000)
    }

    private fun signOutOfThePlayerApp() = signOut("Profile")

    /** Users: the bot admin makes the plain account of the club admin the Club Admin of the club of the game days. */
    private fun makeTheClubAdmin() {
        repeat(3) {
            if (!c.isOnScreen("New user")) runCatching { c.settle(); c.clickButton("Users") }
            runCatching { c.waitForText("New user", timeoutMs = 20_000) }
        }
        c.waitForText("New user", timeoutMs = 30_000)
        c.onAllNodes(hasSetTextAction() and hasText("Search")).onFirst().performTextInput(CLUB_ADMIN)
        // The row of the account, not the search field that has the same text.
        val row = hasClickAction() and !hasSetTextAction() and hasText(CLUB_ADMIN)
        c.waitUntil(30_000) { c.onAllNodes(row).fetchSemanticsNodes().isNotEmpty() }
        // While the list loads, a row does not open: the organizer taps again.
        for (attempt in 1..6) {
            if (c.isOnScreen("Edit account")) break
            c.settle()
            runCatching { c.onAllNodes(row).onFirst().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) }
            runCatching { c.waitForText("Edit account", timeoutMs = 10_000) }
        }
        c.waitForText("Edit account", timeoutMs = 10_000)
        chooseInSelect("Global role", "Club Admin")
        c.waitForText("Add an organization", timeoutMs = 10_000)
        // The chip of the organization shows once the account is of the club; the field adds it.
        if (c.onAllNodes(hasContentDescription("Remove $ORGANIZATION", substring = true)).fetchSemanticsNodes().isEmpty()) {
            val choice = hasText(ORGANIZATION) and hasClickAction() and !hasSetTextAction() and !isDropdownList()
            val behind = c.onAllNodes(choice).fetchSemanticsNodes().size
            for (attempt in 1..4) {
                c.settle()
                runCatching { c.selectField("Add an organization").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) }
                if (runCatching { c.waitUntil(10_000) { c.onAllNodes(choice).fetchSemanticsNodes().size > behind } }.isSuccess) break
            }
            c.waitUntil(10_000) { c.onAllNodes(choice).fetchSemanticsNodes().size > behind }
            c.onAllNodes(choice).onLast().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
            c.waitUntil(10_000) { c.onAllNodes(hasContentDescription("Remove $ORGANIZATION", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        }
        c.clickLastButton("Save")
        c.waitForNoText("Edit account", timeoutMs = 60_000)
        c.clickButton("Back")
        c.waitForText("New tournament", timeoutMs = 60_000)
        log("the club admin is Club Admin of $ORGANIZATION")
    }

    /** The club admin creates the tournament of its club with registrations: 16 places, a co-organizer, 3 rounds. */
    private fun createTheRegistrationTournament() {
        c.clickButton("New tournament")
        c.waitForText("Creates rounds, tables, and players automatically.")
        c.field("Name").performTextInput(byHand.getString("name"))
        c.field("Short name").performTextInput(byHand.getString("shortName"))
        setSwitch("Open registrations", true)
        c.waitForText("Places (optional)", timeoutMs = 10_000)
        c.field("Places (optional)").performTextInput(byHand.getInt("places").toString())
        c.field("Rounds").performTextReplacement(byHand.getInt("rounds").toString())
        setSwitch("Teams", false)
        c.field("Match duration (minutes)").performTextReplacement(plan.getInt("matchDurationMinutes").toString())
        c.clickLastButton("Organizers")
        // A club admin of one organization has it as main organizer already.
        c.waitForText(ORGANIZATION, timeoutMs = 30_000)
        c.selectField("Add a co-organizer").tap()
        val choice = hasText(CO_ORGANIZATION) and hasClickAction() and !hasSetTextAction() and !isDropdownList()
        c.waitUntil(10_000) { c.onAllNodes(choice).fetchSemanticsNodes().isNotEmpty() }
        c.onAllNodes(choice).onLast().tap()
        c.waitUntil(10_000) { c.onAllNodes(hasContentDescription("Remove $CO_ORGANIZATION", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        c.clickLastButton("Venue")
        chooseCountry("Spain")
        c.field("City").performTextInput("Madrid")
        c.field("Venue name").performTextInput("Game day club")
        c.field("Address").performTextInput("Calle Mayor 1, Madrid")
        c.field("Contact email").performTextInput(slots.first().email.replace(Regex("\\+[^@]*"), ""))
        c.clickLastButton("Schedule")
        val rounds = byHand.getInt("rounds")
        c.waitForText("Round $rounds")
        val schedule = plan.getJSONArray("roundSchedules").getJSONObject(0)
        val (year, month, day) = schedule.getString("date").split("-")
        val dates = c.onAllNodes(hasSetTextAction() and hasText("Date"))
        val times = c.onAllNodes(hasSetTextAction() and hasText("Start"))
        repeat(rounds) { index ->
            dates[index].performTextReplacement("$day/$month/$year")
            times[index].performTextReplacement(schedule.getString("startTime"))
        }
        c.clickLastButton("General")
        c.clickButton("Create")
        // A tournament with registrations opens on its registrations, without players.
        c.waitForText("Nobody has registered yet", timeoutMs = 120_000)
        log("tournament with registrations created by the club admin, with $CO_ORGANIZATION as co-organizer")
    }

    private fun importTheGoogleForm() {
        val count = byHand.getJSONArray("form").length()
        pickNext("registrations-form.csv")
        c.clickButton("Import Google Form")
        c.waitForText("Import the Google Form", timeoutMs = 30_000)
        c.clickButton("Choose the file")
        c.waitForText("$count of $count rows can be imported.", timeoutMs = 60_000)
        c.clickButton("Import $count")
        c.waitForText("$count people were imported.", timeoutMs = 60_000)
        // The result stays until OK closes it: the organizer taps OK again while it shows.
        for (attempt in 1..4) {
            if (!c.isOnScreen("$count people were imported.")) break
            c.settle()
            runCatching {
                c.onAllNodes(hasLabel("OK") and hasClickAction()).onLast()
                    .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
            }
            runCatching { c.waitForNoText("$count people were imported.", timeoutMs = 10_000) }
        }
        c.waitForNoText("$count people were imported.", timeoutMs = 10_000)
        c.waitForText("With a place ($count)", timeoutMs = 60_000)
        log("$count people imported from the Google Form")
    }

    private fun registrationCard(emaId: String): SemanticsMatcher = hasTestTag("registration-$emaId")

    /** The list is lazy: it scrolls to the card of the person. */
    private fun showCard(emaId: String) {
        c.waitUntil(30_000) {
            if (c.onAllNodes(registrationCard(emaId)).fetchSemanticsNodes().isEmpty()) {
                runCatching { c.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(registrationCard(emaId)) }
            }
            c.onAllNodes(registrationCard(emaId)).fetchSemanticsNodes().isNotEmpty()
        }
        runCatching { c.onNode(registrationCard(emaId)).performScrollTo() }
    }

    private fun cardMenu(emaId: String, action: String) {
        showCard(emaId)
        c.onNode(hasContentDescription("More actions for", substring = true) and hasAnyAncestor(registrationCard(emaId))).tap()
        val item = hasText(action) and hasClickAction()
        c.waitUntil(10_000) { c.onAllNodes(item).fetchSemanticsNodes().isNotEmpty() }
        c.onAllNodes(item).onLast().tap()
    }

    /** Add a person: the EMA player from the registry. The places are full, so the person goes to the waiting list. */
    private fun addAPersonByHand(emaId: String) {
        c.clickButton("Add a person")
        c.waitForText("Choose the EMA player", timeoutMs = 30_000)
        c.clickButton("Choose the EMA player")
        c.waitUntil(30_000) { c.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
        c.onAllNodes(hasSetTextAction()).onLast().performTextInput(emaId)
        val row = hasClickAction() and hasText(emaId, substring = true) and !hasSetTextAction()
        c.waitUntil(30_000) { c.onAllNodes(row).fetchSemanticsNodes().isNotEmpty() }
        c.onAllNodes(row).onLast().tap()
        c.waitForText(emaId, substring = true)
        c.clickLastButton("Save")
        // The summary at the top counts the waiting list: its section is further down the lazy list.
        waitForCounts(byHand.getJSONArray("form").length(), 1)
        log("a person added by hand goes to the waiting list: ${counts()}")
    }

    /** The player part: the player opens the tournament, registers (number 2 of the waiting list) and cancels. */
    private fun registerAndCancelInThePlayerApp(account: String) {
        c.signIn(account, password)
        val name = byHand.getString("name")
        c.scrollListTo(name)
        val open = hasText("Open") and hasClickAction() and hasAnyAncestor(hasText(name))
        c.waitUntil(60_000) { c.onAllNodes(open).fetchSemanticsNodes().isNotEmpty() }
        runCatching { c.onNode(open).performScrollTo() }
        c.onNode(open).tap()
        c.waitForText("Registrations are open", timeoutMs = 60_000)
        c.clickButton("Register")
        c.waitUntil(30_000) { c.onAllNodes(hasTestTag("registration-submit") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        // The result of the registration, not the summary of the places ("1 on the waiting list") behind the form.
        val done = hasText("You are number", substring = true)
        for (attempt in 1..4) {
            if (c.onAllNodes(done).fetchSemanticsNodes().isNotEmpty()) break
            c.settle()
            runCatching {
                c.onNode(hasTestTag("registration-submit") and isEnabled())
                    .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
            }
            runCatching { c.waitUntil(20_000) { c.onAllNodes(done).fetchSemanticsNodes().isNotEmpty() } }
        }
        c.waitUntil(30_000) { c.onAllNodes(done).fetchSemanticsNodes().isNotEmpty() }
        log("the player registered in the player app and is on the waiting list: ${counts()}")
        c.clickLastButton("Close")
        c.waitForText("My registration", timeoutMs = 30_000)
        c.clickButton("My registration")
        c.waitUntil(30_000) { c.onAllNodes(hasTestTag("registration-cancel")).fetchSemanticsNodes().isNotEmpty() }
        c.onNode(hasTestTag("registration-cancel")).tap()
        c.waitForText("Cancel your registration?")
        c.clickButton("Cancel registration")
        c.waitForText("Register", timeoutMs = 60_000)
        c.waitForNoText("My registration", timeoutMs = 60_000)
        log("the player cancelled the registration in the player app: ${counts()}")
        c.clickButton("Back")
    }

    /** The counts at the top of the registrations ("16 with a place of 16 · 1 on the waiting list"), for the log. */
    private fun counts(): String = c.onAllNodes(hasText("with a place of", substring = true)).fetchSemanticsNodes()
        .joinToString(" | ") { node -> node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text } ?: "" }
        .replace("\n", " ")

    /** Waits for the counts at the top: the headers of the lazy list can be scrolled out of the screen. */
    private fun waitForCounts(withAPlace: Int, waiting: Int) {
        val text = "$withAPlace with a place of ${byHand.getInt("places")}"
        c.waitUntil(60_000) {
            val now = counts()
            now.contains(text) && now.contains("$waiting on the waiting list")
        }
    }

    /** A person with a place leaves: the club admin removes it, and gives the place to the first of the waiting list. */
    private fun removeAndGiveThePlace(removed: String, added: String) {
        val count = byHand.getJSONArray("form").length()
        c.waitUntil(60_000) { counts().contains("with a place of") }
        log("the club admin opens the registrations again: ${counts()}")
        waitForCounts(count, 1)
        cardMenu(removed, "Remove")
        c.waitForText("Remove the registration?")
        c.clickLastButton("Remove")
        waitForCounts(count - 1, 1)
        cardMenu(added, "Give a place")
        waitForCounts(count, 0)
        log("a person removed, and the place given to the waiting list: ${counts()}")
    }

    private fun closeAndMakeTheDraw() {
        c.clickButton("Registration settings")
        c.waitForText("Name of the public link", timeoutMs = 30_000)
        setSwitch("Registrations open", false)
        c.clickLastButton("Save")
        c.waitForText("Registrations closed", timeoutMs = 60_000)
        c.clickLastButton("Make the draw")
        c.waitForText("Make the draw?", timeoutMs = 30_000)
        c.clickLastButton("Make the draw")
        // The app computes the draw, the server writes it, and the tournament opens on its players.
        c.waitForAnyText("Tournament players", "Players & results", timeoutMs = 300_000)
        log("registrations closed and draw made by the club admin")
    }

    private fun isDropdownList() = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.DropdownList)

    /**
     * Opens the select by its click action and picks the option of its menu. The screen behind can have a node with
     * the same text (the filter chip "Club Admin" of Users), so the option is one that the open menu added.
     */
    private fun chooseInSelect(label: String, option: String) {
        val choice = hasText(option) and hasClickAction() and !hasSetTextAction() and !isDropdownList()
        val behind = c.onAllNodes(choice).fetchSemanticsNodes().size
        for (attempt in 1..4) {
            c.settle()
            runCatching { c.selectField(label).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) }
            val opened = runCatching { c.waitUntil(10_000) { c.onAllNodes(choice).fetchSemanticsNodes().size > behind } }.isSuccess
            if (opened) break
        }
        c.waitUntil(10_000) { c.onAllNodes(choice).fetchSemanticsNodes().size > behind }
        c.onAllNodes(choice).onLast().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
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
        // By its click action: after the Timer window a tap can be refused ("Failed to inject touch input").
        if (isOn != on) {
            c.onAllNodes(matcher).onLast().apply { runCatching { performScrollTo() } }
                .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        }
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
        // The extra time shows once the switch is on. The form has more switches now (Open registrations): the switch
        // is set to on by its state, not tapped blindly.
        setSwitch("Finish the hand in play", true)
        c.waitForText("Extra time (minutes)", timeoutMs = 10_000)
        c.field("Extra time (minutes)").performTextReplacement(plan.getInt("matchExtraMinutes").toString())
        c.clickLastButton("Organizers")
        chooseInSelect("Main organizer", ORGANIZATION)
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
        c.selectField("Country").tap()
        // The menu lists every country and composes only the first ones. Its search field finds the country.
        c.waitUntil(10_000) { c.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
        c.onAllNodes(hasSetTextAction()).onLast().performTextInput(name)
        val option = hasText(name) and hasClickAction() and !hasSetTextAction() and !isDropdownList()
        c.waitUntil(5_000) { c.onAllNodes(option).fetchSemanticsNodes().isNotEmpty() }
        c.onAllNodes(option).onLast().tap()
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
        c.onAllNodes(row).onLast().tap()
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
            buttons[0].tap()
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
                val field = hasSetTextAction() and hasText("${slot.slot} · ", substring = true)
                // The list of the dialog is lazy: a field further down exists once the list scrolls to it, and a scroll
                // can compose it again as a new node. The editor looks for it again and types the whole email again.
                var typed: Result<Unit> = Result.failure(IllegalStateException("not typed"))
                for (attempt in 1..5) {
                    if (c.onAllNodes(field).fetchSemanticsNodes().isEmpty()) {
                        runCatching { c.onAllNodes(hasScrollToNodeAction()).onLast().performScrollToNode(field) }
                        c.waitForIdle()
                    }
                    typed = runCatching {
                        c.onAllNodes(field).onLast().apply { runCatching { performScrollTo() } }
                        c.waitForIdle()
                        c.onAllNodes(field).onLast().performTextReplacement(slot.email)
                    }
                    if (typed.isSuccess) break
                    log("the email field of slot ${slot.slot} moved while typing, attempt $attempt")
                }
                typed.getOrThrow()
            }
            c.clickLastButton("Send (${manual.size})")
        }
        c.waitForText("Done", timeoutMs = 300_000)
        val invited = slots.count { !it.accountBefore }
        // The summary has one section for each outcome: its title, then its count.
        // With no free EMA player, every player comes back with an account and the summary has no Invited section.
        if (invited > 0) {
            c.waitForText("Invited", timeoutMs = 60_000)
            check(c.onAllNodes(hasText("$invited")).fetchSemanticsNodes().isNotEmpty()) { "The summary does not count $invited invited players." }
        }
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
            typeTheManualTotals(tournamentId, round)
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
            // A correction changes the score of a won hand: a draw has no score to correct (with one it is no longer a
            // valid hand, and the player writes it again). The last hand is waited for until it is a won hand, and an
            // older one is the nearest won hand before it.
            val won = { hand: JSONObject -> hand.optString("playerWinnerId").isNotBlank() }
            if (item.getString("kind") == "edit" && item.getString("which") == "last") {
                while (!won(hands.maxBy { it.getInt("handId") }) && System.currentTimeMillis() < deadline) {
                    idle(5_000)
                    hands = doneHands(tournamentId, round, table)
                }
            }
            val newest = hands.maxOf { it.getInt("handId") }
            val target = if (item.getString("which") == "last") newest else newest - item.optInt("back", 3)
            val handId = if (item.getString("kind") != "edit") {
                target
            } else {
                hands.filter { it.getInt("handId") <= target && won(it) }.maxOfOrNull { it.getInt("handId") } ?: target
            }
            if (handId != target) log("table $round-$table: hand $target is a draw, the editor corrects hand $handId")
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
                // Save sends every table of the round with changes: the done hands of the other tables must stay.
                val othersBefore = doneHandsOfTheRound(tournamentId, round, except = table)
                // The message can also open just as Save is tapped: the tap goes nowhere, and the editor does it again.
                val saved = runCatching { c.clickButton("Save") }
                // The app asks before saving a hand with a result and Done off: here that is a mistake, so back.
                if (c.isOnScreen("Hands without Done")) {
                    log("table $round-$table: the app asked about hands without Done, attempt $attempt")
                    c.clickLastButton("Cancel")
                    continue
                }
                if (tableUpdatedShown()) continue
                if (saved.isFailure) {
                    c.waitForIdle()
                    if (tableUpdatedShown()) continue
                    closeOpenMenu()
                    c.clickButton("Save")
                }
                idle(3_000)
                dismissTableUpdated()
                val othersAfter = doneHandsOfTheRound(tournamentId, round, except = table)
                othersBefore.forEach { (where, hand) ->
                    val now = othersAfter[where]
                    if (now != hand) log("table $round-$table: the save of the editor changed hand $where: $hand -> ${now ?: "not done"}")
                }
                // The table on the server has the change. A deleted hand can already be written again by the player.
                val after = handOf(tournamentId, round, table, handId)
                reached = if (item.getString("kind") == "edit") {
                    // A correction is a done hand with the new score. A hand left not done would no longer count:
                    // the phones would see it deleted (docs/scenarios.md).
                    after != null && after.optBoolean("isDone") && after.optString("handScore") == newScoreOf(before) &&
                        after.optString("playerWinnerId") == before.optString("playerWinnerId")
                } else {
                    after == null || !after.optBoolean("isDone") ||
                        after.optString("handScore") != before.optString("handScore") ||
                        after.optString("playerWinnerId") != before.optString("playerWinnerId")
                }
                if (!reached && after != null) {
                    log("table $round-$table: after the ${item.getString("kind")} the server has hand $handId done ${after.optBoolean("isDone")}, score ${after.optString("handScore")}")
                }
                if (reached) break
                log("table $round-$table: the ${item.getString("kind")} did not reach the server, attempt $attempt")
            }
            check(reached) { "The ${item.getString("kind")} of hand $handId at table $round-$table did not reach the server" }
            log("table $round-$table: ${item.getString("kind")} of hand $handId (${item.getString("which")}, newest $newest)")
            closeTheEditor()
        }
    }

    /**
     * A table that plays on paper: its players save the seats and leave, and at the end of the round they bring the
     * sheet. The editor turns Manual scores on in the table editor, types the four totals and completes the table. The
     * totals of a table with a player of the tie are 0, so the tie stays (docs/scenarios.md).
     */
    private fun typeTheManualTotals(tournamentId: String, round: Int) {
        val cases = plan.optJSONArray("cornerCases") ?: return
        val all = (0 until cases.length()).map { cases.getJSONObject(it) }
        val item = all.firstOrNull { it.getString("kind") == "manual-totals" && it.getInt("round") == round } ?: return
        val table = item.getInt("table")
        val seatsOf = { data: JSONObject? ->
            val row = data?.optJSONObject("table")
            listOf("East", "South", "West", "North").map { row?.optString("player${it}Id").orEmpty() }
        }
        val deadline = System.currentTimeMillis() + 5 * 60_000L
        while (seatsOf(tableData(tournamentId, round, table)).any { it.isBlank() } && System.currentTimeMillis() < deadline) idle(5_000)
        val seats = seatsOf(tableData(tournamentId, round, table))
        check(seats.none { it.isBlank() }) { "Table $round-$table has no seats for the manual totals" }
        val tie = all.firstOrNull { it.getString("kind") == "tie" }
        val tied = tie?.let { seatsOf(tableData(tournamentId, it.getInt("round"), it.getInt("table"))) }.orEmpty()
        val planned = item.getJSONArray("scores").let { list -> (0 until list.length()).map { list.getString(it) } }
        val scores = if (seats.any { it in tied }) List(4) { "0" } else planned
        backToTheTournament()
        openTable(round, table)
        var saved = false
        for (attempt in 1..3) {
            dismissTableUpdated()
            val typed = runCatching {
                setSwitch("Manual scores", true)
                c.waitUntil(10_000) { c.onAllNodes(hasTestTag("table-total-0")).fetchSemanticsNodes().isNotEmpty() }
                scores.forEachIndexed { seat, score ->
                    c.onNode(hasTestTag("table-total-$seat")).apply { runCatching { performScrollTo() } }.performTextReplacement(score)
                }
                setSwitch("Completed", true)
                val save = hasLabel("Save") and hasClickAction() and isEnabled()
                c.waitUntil(10_000) { c.onAllNodes(save).fetchSemanticsNodes().isNotEmpty() }
                c.onAllNodes(save).onFirst().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
            }
            idle(3_000)
            if (tableUpdatedShown()) continue
            typed.getOrThrow()
            val after = tableData(tournamentId, round, table)?.optJSONObject("table")
            saved = after?.optBoolean("useTotalsOnly") == true && after.optBoolean("isCompleted")
            if (saved) break
            log("table $round-$table: the manual totals did not reach the server, attempt $attempt")
        }
        check(saved) { "The manual totals of table $round-$table did not reach the server" }
        log("table $round-$table: manual totals ${scores.joinToString(" / ")}")
        closeTheEditor()
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
        // The menu of a player has Substitute once a table of the tournament has a hand, and the Players screen reads
        // the tables when it opens: it is opened once the first hand of the round is saved, as an organizer sees it.
        val tableIds = (0 until list.length()).map { list.getJSONObject(it).getInt("tableId") }
        val deadline = System.currentTimeMillis() + 600_000
        while (tableIds.none { doneHands(tournamentId, round, it).isNotEmpty() }) {
            if (System.currentTimeMillis() > deadline) {
                log("round $round has no hand after 10 minutes")
                break
            }
            idle(15_000)
        }
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
            c.onAllNodes(hasContentDescription("Player actions") and hasClickAction()).onFirst().tap()
            if (runCatching { c.waitForText("Substitute", timeoutMs = 10_000) }.isSuccess) break
            log("the menu of slot $slotId has no Substitute yet, attempt $attempt")
            closeOpenMenu()
            idle(10_000)
        }
        c.waitForText("Substitute")
        c.clickLastButton("Substitute")
        c.waitForText("A substitute plays for this player from the chosen round.", substring = true)
        c.onAllNodes(hasText("The player came late.")).onLast().tap()
        c.onAllNodes(hasText("$round") and hasClickAction()).onLast().tap()
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

    /** The done hands of the other tables of the round, "table#hand" to "winner/score". A player only adds hands. */
    private fun doneHandsOfTheRound(tournamentId: String, round: Int, except: Int): Map<String, String> {
        val reply = runCatching { E2eApi.call("GET", "/public/tournaments/$tournamentId/rounds/$round/tables-with-hands") }.getOrNull()
        if (reply?.status != 200) return emptyMap()
        val tables = reply.json().getJSONArray("tables")
        return buildMap {
            for (index in 0 until tables.length()) {
                val item = tables.getJSONObject(index)
                val tableId = item.getJSONObject("table").optInt("tableId")
                if (tableId == except) continue
                val hands = item.optJSONArray("hands") ?: continue
                for (h in 0 until hands.length()) {
                    val hand = hands.getJSONObject(h)
                    if (!hand.optBoolean("isDone")) continue
                    put("$tableId#${hand.optInt("handId")}", "${hand.optString("playerWinnerId")}/${hand.optString("handScore")}")
                }
            }
        }
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
        c.onAllNodes(hasText("-") and hasClickAction()).onLast().tap()
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
            setDone(handId, false)
            runCatching { c.waitUntil(5_000) { c.onAllNodes(open, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() } }
        }
        check(c.onAllNodes(open, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()) { "Hand $handId stays locked after Done off" }
    }

    /**
     * Opens the hand and writes its score. The poll of the table can draw it again between the look and the typing: the
     * field is then a new node, so the editor looks for it and types again.
     */
    private fun writeScore(handId: Int, score: String) {
        for (attempt in 1..5) {
            unlockHand(handId)
            val typed = runCatching { scoreField(handId).performTextReplacement(score) }
            if (typed.isSuccess) return
            log("the score of hand $handId was drawn again while typing, attempt $attempt")
            c.waitForIdle()
        }
        unlockHand(handId)
        scoreField(handId).performTextReplacement(score)
    }

    /** The hand no longer counts: Done off, no winner, no loser, no score. */
    private fun deleteHand(handId: Int) {
        writeScore(handId, "")
        clearPlayer(handId, "winner")
        clearPlayer(handId, "loser")
    }

    /** Another score that is valid for any hand, also a chicken hand (8 to 12). */
    private fun newScoreOf(before: JSONObject) = if (before.optString("handScore") == "11") "10" else "11"

    private fun editHand(handId: Int, before: JSONObject) {
        val score = newScoreOf(before)
        writeScore(handId, score)
        setDone(handId, true)
        // What Save sends: the new score in a hand that is done. Anything else is typed again.
        // A done hand is locked: its field has no text action, so the score is read from the cell.
        val shown = c.onAllNodes(hasAnyAncestor(hasTestTag("hand-$handId-score")), useUnmergedTree = true).fetchSemanticsNodes()
            .firstNotNullOfOrNull { it.config.getOrNull(SemanticsProperties.EditableText)?.text }
        check(shown == score && isDone(handId) == true) { "Hand $handId shows score $shown and done ${isDone(handId)} before Save" }
    }

    /** The Done box of a hand, by its toggle state: a touch at the centre of its cell can land on the sticky header. */
    private fun doneBox(handId: Int) =
        c.onAllNodes(isToggleable() and hasAnyAncestor(hasTestTag("hand-$handId-done")), useUnmergedTree = true)

    private fun isDone(handId: Int): Boolean? = doneBox(handId).fetchSemanticsNodes().firstOrNull()
        ?.config?.getOrNull(SemanticsProperties.ToggleableState)?.let { it == androidx.compose.ui.state.ToggleableState.On }

    /** Turns Done of a hand on or off. The app refuses Done for a hand that is not valid: the error says so. */
    private fun setDone(handId: Int, on: Boolean) {
        runCatching { cell(handId, "done").performScrollTo() }
        repeat(3) {
            if (isDone(handId) == on) return
            runCatching { doneBox(handId).onFirst().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) }
            runCatching { c.waitUntil(5_000) { isDone(handId) == on } }
        }
        check(isDone(handId) == on) { "Done of hand $handId stays ${isDone(handId)}, the editor wanted $on" }
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

    /**
     * A button of the Timer by its click action. A tap is injected into the window with the focus, and on the emulator
     * the focus can stay on another window (the launcher) while the Timer shows: the taps are then refused.
     */
    private fun timerAction(text: String) {
        c.waitUntil(10_000) { c.onAllNodes(hasLabel(text) and hasClickAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        c.onAllNodes(hasLabel(text) and hasClickAction() and isEnabled()).onFirst()
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        c.waitForIdle()
    }

    /** Play on the Timer of the tournament, as in TournamentJourneyTest. The Timer opens in its own window. */
    private fun startRoundOnTheTimer(tournamentId: String, round: Int) {
        c.clickButton("Timer")
        c.waitForAnyText("Start timer", "Pause timer", timeoutMs = 60_000)
        val deadline = System.currentTimeMillis() + 180_000
        while (true) {
            c.settle()
            if (c.isOnScreen("Round $round") && c.isOnScreen("Start timer")) break
            check(System.currentTimeMillis() < deadline) { "The Timer did not reach round $round" }
            // A tap while the new Timer window takes the focus is refused ("Failed to inject touch input"): the loop
            // looks again and taps again until its deadline.
            val tapped = runCatching {
                when {
                    c.isOnScreen("Pause timer") -> {
                        timerAction("Pause timer")
                        c.waitForText("Start timer", timeoutMs = 10_000)
                    }
                    c.isOnScreen("Increase round") -> timerAction("Increase round")
                }
            }
            if (tapped.isFailure) {
                log("a tap on the Timer was refused (${tapped.exceptionOrNull()?.message?.take(80)}), it is tried again")
                Thread.sleep(1_000)
            }
        }
        for (attempt in 1..5) {
            if (runCatching { timerAction("Start timer") }.isSuccess) break
            log("the tap on Start timer was refused, attempt $attempt")
            Thread.sleep(1_000)
        }
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
            val focus = shell("dumpsys window").lines().firstOrNull { line -> "mCurrentFocus" in line }?.trim()?.take(120)
            log("back to the tournament, attempt ${it + 1}: resumed ${resumedActivities()}, ${timerActivities().size} timers, focus $focus")
            shell("input keyevent KEYCODE_BACK")
            runCatching { c.waitForText("Players & results", timeoutMs = 5_000) }
            if (c.isOnScreen("Players & results")) return
            // The Timer window has no Back of its own: the Back under it would leave the tournament.
            // When its back key is lost, the test closes the Timer as its back key would.
            if (timerActivities().isNotEmpty()) {
                closeTheTimerWindow()
            } else {
                // The Back of the top bar by its click action: a tap is injected into the window with the focus,
                // which is not always the app after the Timer window closes.
                // On a tablet the Back shows its label, so it is found by its text or its description. It is off while
                // the screen loads.
                val clicked = runCatching {
                    c.waitUntil(15_000) { c.onAllNodes(hasLabel("Back") and hasClickAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                    c.onAllNodes(hasLabel("Back") and hasClickAction() and isEnabled()).onFirst()
                        .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
                }
                if (clicked.isFailure) log("the Back of the top bar did not answer: ${clicked.exceptionOrNull()?.message?.take(80)}")
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
            boxes[0].tap()
            c.clickButton("Close selected")
            c.waitForNoText("Open tables in ended rounds", timeoutMs = 120_000)
            log("the organizer closed the open tables of the ended rounds")
        }
        shell("input keyevent KEYCODE_BACK")
        c.waitForText("Players & results", timeoutMs = 60_000)
    }

    private companion object {
        const val TAG = "SCENARIO"
        const val ORGANIZATION = "Game day club"
        const val CO_ORGANIZATION = "Game day co-club"
        const val CLUB_ADMIN = "Bot club admin"
    }
}
