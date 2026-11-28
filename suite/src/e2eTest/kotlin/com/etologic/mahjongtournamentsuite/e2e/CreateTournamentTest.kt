@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.etologic.mahjongtournamentsuite.e2e

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import kotlin.test.AfterTest
import kotlin.test.Test

class CreateTournamentTest : AdminE2eTest() {
    @AfterTest
    fun deleteCreatedTournaments() = onlyWithBackend { E2eApi.deleteTournamentsNamed("E2E created") }

    /** The form shows its fields in tabs: General, Venue, Organizers and Schedule. */
    private fun openTab(name: String) {
        c.clickLastButton(name)
    }

    private fun fillValidForm(players: String, rounds: String) {
        c.field("Name").performTextInputText("E2E created")
        c.field("Short name").performTextInputText("E2E")
        c.field("Players").performTextReplacementText(players)
        c.field("Rounds").performTextReplacementText(rounds)
        openTab("Venue")
        c.chooseInSelectField("Country", "Spain")
        c.field("City").performTextInputText("Madrid")
        c.field("Venue name").performTextInputText("E2E club")
        c.field("Address").performTextInputText("Calle Mayor 1, Madrid")
        c.field("Contact email").performTextInputText("e2e@example.com")
        rounds.toIntOrNull()?.takeIf { it > 0 }?.let { fillRoundSchedules(it) }
        // A tournament needs a main organizer, which gives it its logo and color.
        openTab("Organizers")
        c.chooseInSelectField("Main organizer", ORGANIZER)
        openTab("General")
    }

    /** Every round needs a date and a start time. The rounds play today, one hour apart. */
    private fun fillRoundSchedules(rounds: Int, skipStartTimeOfRound: Int? = null) {
        openTab("Schedule")
        c.waitForText("Round $rounds")
        val today = todayDdMmYyyy()
        val dates = c.onAllNodes(hasSetTextAction() and hasText("Date"))
        val times = c.onAllNodes(hasSetTextAction() and hasText("Start"))
        repeat(rounds) { index ->
            dates[index].performTextReplacement(today)
            times[index].performTextReplacement(if (index + 1 == skipStartTimeOfRound) "" else "${10 + index % 12}:00")
        }
    }

    /** The switch and its label are one control (MtsSwitchRow), so the toggleable node holds the label. */
    private fun toggleFinishTheHand() {
        val switch = c.onAllNodes(isToggleable() and hasText("Finish the hand in play")).onLast()
        switch.performScrollTo()
        switch.performClick()
    }

    /** The form shows the zone of the device when its list has it, and "UTC" otherwise, for example for "Etc/UTC". */
    private fun expectedTimeZone(): String = E2ePlatform.timeZoneId().let { zone ->
        if (zone == "UTC" || (zone.contains('/') && !zone.startsWith("Etc/") && !zone.startsWith("SystemV/"))) zone else "UTC"
    }

    private fun openForm() {
        start(E2eAccounts.ADMIN, ready = "New tournament")
        c.clickButton("New tournament")
        c.waitForText("Creates rounds, tables, and players automatically.")
    }

    @Test
    fun formShowsDefaults() = e2e {
        openForm()
        c.waitForText("60")
        c.waitForText("7")
        c.clickButton("Schedule")
        c.waitForText("Tournament schedule")
        c.waitForText("Round 7")
    }

    @Test
    fun emptyNameIsRejected() = e2e {
        openForm()
        c.clickButton("Create")
        c.waitForText("Name is required.")
    }

    @Test
    fun shortNameFieldStopsAtTenCharacters() = e2e {
        openForm()
        c.field("Short name").performTextInputText("ABCDEFGHIJK")
        c.waitForText("ABCDEFGHIJ")
        c.assertNoText("ABCDEFGHIJK")
    }

    @Test
    fun hostCountryIsRequired() = e2e {
        openForm()
        c.field("Name").performTextInputText("Created by a test")
        c.field("Short name").performTextInputText("E2E")
        c.clickButton("Create")
        c.waitForText("Select a host country.")
    }

    @Test
    fun venueIsRequired() = e2e {
        openForm()
        c.clickButton("Create")
        openTab("Venue")
        c.waitForText("Add a Google Maps link, or type the venue name and the address.")
    }

    @Test
    fun contactEmailIsRequired() = e2e {
        openForm()
        c.clickButton("Create")
        openTab("Venue")
        c.waitForText("Contact email is required.")
    }

    @Test
    fun contactEmailMustBeValid() = e2e {
        openForm()
        openTab("Venue")
        c.field("Contact email").performTextInputText("not an email")
        c.clickButton("Create")
        openTab("Venue")
        c.waitForText("Use a valid email address.")
    }

    @Test
    fun fieldErrorGoesAwayWhenTheFieldChanges() = e2e {
        openForm()
        c.clickButton("Create")
        c.waitForText("Name is required.")
        c.field("Name").performTextInputText("E2E created")
        c.waitForNoText("Name is required.")
    }

    @Test
    fun editorHasNoNewTournamentButton() = e2e {
        start(E2eAccounts.EDITOR)
        c.waitForText("EMA players")
        c.assertNoText("New tournament")
    }

    @Test
    fun playerCountMustBeAMultipleOfFour() = e2e {
        openForm()
        fillValidForm(players = "18", rounds = "2")
        c.clickButton("Create")
        c.waitForText("Must be a multiple of 4 and at least 16.")
    }

    @Test
    fun playerCountBelowSixteenIsRejected() = e2e {
        openForm()
        fillValidForm(players = "12", rounds = "2")
        c.clickButton("Create")
        c.waitForText("Must be a multiple of 4 and at least 16.")
    }

    @Test
    fun roundsMustBeAPositiveNumber() = e2e {
        openForm()
        fillValidForm(players = "16", rounds = "0")
        c.clickButton("Create")
        c.waitForText("Must be a positive whole number.")
    }

    @Test
    fun everyRoundNeedsAStartTime() = e2e {
        openForm()
        fillValidForm(players = "16", rounds = "2")
        fillRoundSchedules(rounds = 2, skipStartTimeOfRound = 2)
        c.clickButton("Create")
        c.waitForText("Round 2 needs a start time.", substring = true)
    }

    @Test
    fun validFormCreatesTheTournamentAndOpensItsPlayers() = e2e {
        openForm()
        fillValidForm(players = "16", rounds = "2")
        c.clickButton("Create")
        // The app draws the seating, and then it opens the players of the new tournament.
        c.waitForText("Tournament players", timeoutMs = 90_000)
        c.waitForText("Not assigned")
        val list = E2eApi.call("GET", "/tournaments", E2eApi.signIn(E2eAccounts.ADMIN)).json().getJSONArray("tournaments")
        check((0 until list.length()).any { list.getJSONObject(it).getString("name") == "E2E created" }) { "The tournament was not created" }
    }

    private fun tournamentJson(name: String) = E2eApi.call("GET", "/tournaments", E2eApi.signIn(E2eAccounts.ADMIN))
        .json().getJSONArray("tournaments").let { list -> (0 until list.length()).map { list.getJSONObject(it) }.first { it.getString("name") == name } }

    @Test
    fun matchTimeFieldsShowTheDefaults() = e2e {
        openForm()
        listOf("Match duration (minutes)", "120", "Finish the hand in play").forEach { c.waitForText(it) }
        c.assertNoText("Extra time (minutes)")
        openTab("Venue")
        c.waitForText("Time zone")
        // The field shows the offset and the name, for example "UTC +0 · UTC".
        c.waitForText(expectedTimeZone(), substring = true)
    }

    @Test
    fun finishTheHandSwitchShowsTheExtraTimeField() = e2e {
        openForm()
        toggleFinishTheHand()
        c.waitForText("Extra time (minutes)")
        toggleFinishTheHand()
        c.waitForNoText("Extra time (minutes)")
    }

    @Test
    fun matchDurationZeroIsRejected() = e2e {
        openForm()
        fillValidForm(players = "16", rounds = "2")
        c.field("Match duration (minutes)").performTextReplacementText("0")
        c.clickButton("Create")
        c.waitForText("Use a whole number of minutes from 1 to 600.")
    }

    @Test
    fun matchDurationAboveSixHundredIsRejected() = e2e {
        openForm()
        fillValidForm(players = "16", rounds = "2")
        c.field("Match duration (minutes)").performTextReplacementText("601")
        c.clickButton("Create")
        c.waitForText("Use a whole number of minutes from 1 to 600.")
    }

    @Test
    fun extraTimeZeroIsRejectedWhenTheHandIsFinished() = e2e {
        openForm()
        fillValidForm(players = "16", rounds = "2")
        toggleFinishTheHand()
        c.field("Extra time (minutes)").performTextReplacementText("0")
        c.clickButton("Create")
        c.waitForText("Use a whole number of minutes from 1 to 120.")
    }

    @Test
    fun matchTimeSettingsAreSavedWithTheNewTournament() = e2e {
        openForm()
        fillValidForm(players = "16", rounds = "2")
        c.field("Match duration (minutes)").performTextReplacementText("90")
        toggleFinishTheHand()
        c.field("Extra time (minutes)").performTextReplacementText("15")
        c.clickButton("Create")
        c.waitForText("Tournament players", timeoutMs = 90_000)
        val tournament = tournamentJson("E2E created")
        check(tournament.getInt("matchDurationMinutes") == 90) { "Duration: ${tournament.opt("matchDurationMinutes")}" }
        check(tournament.getString("matchEndMode") == "FINISH_HAND") { "End mode: ${tournament.opt("matchEndMode")}" }
        check(tournament.getInt("matchExtraMinutes") == 15) { "Extra time: ${tournament.opt("matchExtraMinutes")}" }
        check(tournament.getString("timeZone") == expectedTimeZone()) { "Time zone: ${tournament.opt("timeZone")}" }
    }

    private companion object {
        /** The test club of the seed. */
        const val ORGANIZER = "Test Mahjong Club"
    }
}
