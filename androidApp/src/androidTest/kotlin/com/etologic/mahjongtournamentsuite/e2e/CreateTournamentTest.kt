package com.etologic.mahjongtournamentsuite.e2e

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import org.junit.After
import org.junit.Test

class CreateTournamentTest : AdminE2eTest() {
    @After
    fun deleteCreatedTournaments() = E2eApi.deleteTournamentsNamed("E2E created")

    private fun chooseCountry(name: String) {
        // The list behind the form also has a "Country" header, so the form field is the last one.
        c.onAllNodes(hasText("Country")).onLast().performClick()
        c.onAllNodes(hasText(name) and hasClickAction()).onLast().performClick()
    }

    private fun fillValidForm(players: String, rounds: String) {
        c.field("Name").performTextInputText("E2E created")
        c.field("Short name").performTextInputText("E2E")
        chooseCountry("Spain")
        c.field("City").performTextInputText("Madrid")
        c.field("Venue name").performTextInputText("E2E club")
        c.field("Address").performTextInputText("Calle Mayor 1, Madrid")
        c.field("Contact email").performTextInputText("e2e@example.com")
        c.field("Players").performTextReplacementText(players)
        c.field("Rounds").performTextReplacementText(rounds)
    }

    private fun openForm() {
        start(E2eAccounts.ADMIN, ready = "New tournament")
        c.clickButton("New tournament")
        c.waitForText("Creates rounds, tables, and players automatically.")
    }

    @Test
    fun formShowsDefaults() {
        openForm()
        c.waitForText("60")
        c.waitForText("7")
        c.clickButton("Schedule")
        c.waitForText("Tournament schedule")
        c.waitForText("Round 7")
    }

    @Test
    fun emptyNameIsRejected() {
        openForm()
        c.clickButton("Create")
        c.waitForText("Name is required.")
    }

    @Test
    fun shortNameFieldStopsAtTenCharacters() {
        openForm()
        c.field("Short name").performTextInputText("ABCDEFGHIJK")
        c.waitForText("ABCDEFGHIJ")
        c.assertNoText("ABCDEFGHIJK")
    }

    @Test
    fun hostCountryIsRequired() {
        openForm()
        c.field("Name").performTextInputText("Created by a test")
        c.field("Short name").performTextInputText("E2E")
        c.clickButton("Create")
        c.waitForText("Select a host country.")
    }

    @Test
    fun venueIsRequired() {
        openForm()
        c.clickButton("Create")
        c.waitForText("Add a Google Maps link, or type the venue name and the address.")
    }

    @Test
    fun contactEmailIsRequired() {
        openForm()
        c.clickButton("Create")
        c.waitForText("Contact email is required.")
    }

    @Test
    fun contactEmailMustBeValid() {
        openForm()
        c.field("Contact email").performTextInputText("not an email")
        c.clickButton("Create")
        c.waitForText("Use a valid email address.")
    }

    @Test
    fun fieldErrorGoesAwayWhenTheFieldChanges() {
        openForm()
        c.clickButton("Create")
        c.waitForText("Name is required.")
        c.field("Name").performTextInputText("E2E created")
        c.waitForNoText("Name is required.")
    }

    @Test
    fun editorHasNoNewTournamentButton() {
        start(E2eAccounts.EDITOR)
        c.waitForText("EMA players")
        c.assertNoText("New tournament")
    }

    @Test
    fun playerCountMustBeAMultipleOfFour() {
        openForm()
        fillValidForm(players = "18", rounds = "2")
        c.clickButton("Create")
        c.waitForText("Must be a multiple of 4 and at least 16.")
    }

    @Test
    fun playerCountBelowSixteenIsRejected() {
        openForm()
        fillValidForm(players = "12", rounds = "2")
        c.clickButton("Create")
        c.waitForText("Must be a multiple of 4 and at least 16.")
    }

    @Test
    fun roundsMustBeAPositiveNumber() {
        openForm()
        fillValidForm(players = "16", rounds = "0")
        c.clickButton("Create")
        c.waitForText("Must be a positive whole number.")
    }

    @Test
    fun validFormCreatesTheTournamentAndOpensItsPlayers() {
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
    fun matchTimeFieldsShowTheDefaults() {
        openForm()
        c.clickButton("Match")
        listOf("Match duration (minutes)", "120", "Time zone", java.util.TimeZone.getDefault().id, "Finish the hand in play")
            .forEach { c.waitForText(it) }
        c.assertNoText("Extra time (minutes)")
    }

    @Test
    fun finishTheHandSwitchShowsTheExtraTimeField() {
        openForm()
        c.clickButton("Match")
        c.clickButton("Finish the hand in play")
        c.waitForText("Extra time (minutes)")
        c.clickButton("Finish the hand in play")
        c.waitForNoText("Extra time (minutes)")
    }

    @Test
    fun matchDurationZeroIsRejected() {
        openForm()
        fillValidForm(players = "16", rounds = "2")
        c.clickButton("Match")
        c.field("Match duration (minutes)").performTextReplacementText("0")
        c.clickButton("Create")
        c.waitForText("Use a whole number of minutes from 1 to 600.")
    }

    @Test
    fun matchDurationAboveSixHundredIsRejected() {
        openForm()
        fillValidForm(players = "16", rounds = "2")
        c.clickButton("Match")
        c.field("Match duration (minutes)").performTextReplacementText("601")
        c.clickButton("Create")
        c.waitForText("Use a whole number of minutes from 1 to 600.")
    }

    @Test
    fun extraTimeZeroIsRejectedWhenTheHandIsFinished() {
        openForm()
        fillValidForm(players = "16", rounds = "2")
        c.clickButton("Match")
        c.clickButton("Finish the hand in play")
        c.field("Extra time (minutes)").performTextReplacementText("0")
        c.clickButton("Create")
        c.waitForText("Use a whole number of minutes from 1 to 120.")
    }

    @Test
    fun matchTimeSettingsAreSavedWithTheNewTournament() {
        openForm()
        fillValidForm(players = "16", rounds = "2")
        c.clickButton("Match")
        c.field("Match duration (minutes)").performTextReplacementText("90")
        c.clickButton("Finish the hand in play")
        c.field("Extra time (minutes)").performTextReplacementText("15")
        c.clickButton("Create")
        c.waitForText("Tournament players", timeoutMs = 90_000)
        val tournament = tournamentJson("E2E created")
        check(tournament.getInt("matchDurationMinutes") == 90) { "Duration: ${tournament.opt("matchDurationMinutes")}" }
        check(tournament.getString("matchEndMode") == "FINISH_HAND") { "End mode: ${tournament.opt("matchEndMode")}" }
        check(tournament.getInt("matchExtraMinutes") == 15) { "Extra time: ${tournament.opt("matchExtraMinutes")}" }
        check(tournament.getString("timeZone") == java.util.TimeZone.getDefault().id) { "Time zone: ${tournament.opt("timeZone")}" }
    }
}
