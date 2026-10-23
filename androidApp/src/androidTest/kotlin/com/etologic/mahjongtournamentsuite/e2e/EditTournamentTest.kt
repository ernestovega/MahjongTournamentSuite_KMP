package com.etologic.mahjongtournamentsuite.e2e

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import org.junit.After
import org.junit.Test

class EditTournamentTest : AdminE2eTest() {
    @After
    fun restoreData() = E2eApi.reset("test-live-ongoing", "test-no-extras")

    private fun openDialog(name: String = "Test - Live ongoing tournament") {
        start(E2eAccounts.ADMIN, ready = "Logout")
        c.openTournament(name)
        c.waitForText("Players & results")
        c.clickButton("Edit tournament")
        c.waitForText("Branding")
    }

    @Test
    fun dialogShowsStoredValues() {
        openDialog()
        listOf("Eskalerria", "test-live@example.com", "Madrid").forEach { c.waitForText(it) }
        c.clickButton("Branding")
        c.waitForText("#D81B60")
        c.clickButton("Schedule")
        listOf("Tournament schedule", "Player agenda").forEach { c.waitForText(it) }
    }

    @Test
    fun dialogShowsReadOnlyTournamentData() {
        openDialog()
        c.clickButton("Data")
        listOf("test-live-ongoing", "Created by UID", "Created by name", "Created at", "Updated at", "Tries").forEach { c.waitForText(it) }
    }

    @Test
    fun dialogListsOneRowPerRoundAndPerAgendaItem() {
        openDialog()
        c.clickButton("Schedule")
        c.waitForText("Round 6")
        c.assertNoText("Round 7")
        c.waitForText("Registration and breakfast")
        c.waitForText("Lunch")
        c.waitForText("Awards ceremony")
    }

    @Test
    fun cancelClosesWithoutSaving() {
        openDialog()
        c.clickButton("Cancel")
        c.waitForNoText("Branding")
        c.waitForText("Round 4")
    }

    @Test
    fun saveRenamesTheTournament() {
        openDialog()
        c.field("Tournament name").performTextReplacementText("Live renamed by a test")
        c.clickButton("Save")
        c.waitForNoText("Branding")
        c.waitForText("Live renamed by a test")
    }

    @Test
    fun tournamentWithoutAgendaOpensTheDialog() {
        openDialog("Test - Without best hands, chicken hands or dates")
        c.waitForText("Branding")
        c.clickButton("Schedule")
        c.waitForText("Add activity")
    }

    @Test
    fun addActivityAddsAnAgendaRow() {
        openDialog()
        c.clickButton("Schedule")
        c.clickButton("Add activity")
        c.waitForText("Choose activity 4 date")
    }

    @Test
    fun removeActivityRemovesAnAgendaRow() {
        openDialog()
        c.clickButton("Schedule")
        c.clickButton("Remove")
        c.waitForNoText("Choose activity 3 date")
    }

    @Test
    fun editorSaveIsRefusedByTheServer() {
        start(E2eAccounts.EDITOR, ready = "Logout")
        c.openTournament("Test - Live ongoing tournament")
        c.waitForText("Players & results")
        c.clickButton("Edit tournament")
        c.waitForText("Branding")
        c.field("Tournament name").performTextReplacementText("Renamed by an editor")
        c.clickButton("Save")
        c.waitForText("You do not have permission to perform this action.")
    }

    /** The edit dialog and the confirmation both have a "Delete" button. The confirmation is the last one. */
    private fun pressDeleteInConfirmation() {
        c.onAllNodes(hasText("Delete") and hasClickAction()).onLast().performClick()
    }

    @Test
    fun deleteAsksForConfirmation() {
        openDialog()
        c.clickButton("Delete")
        c.waitForText("Delete tournament")
        c.waitForText("This will permanently delete \"Test - Live ongoing tournament\" (ID: test-live-ongoing).")
    }

    @Test
    fun cancelDeleteKeepsTheTournament() {
        openDialog()
        c.clickButton("Delete")
        c.waitForText("Delete tournament")
        c.onAllNodes(hasText("Cancel") and hasClickAction()).onLast().performClick()
        c.waitForNoText("Delete tournament")
        c.waitForText("Branding")
    }

    @Test
    fun confirmedDeleteRemovesTheTournamentFromTheList() {
        val name = "Test - Without best hands, chicken hands or dates"
        openDialog(name)
        c.clickButton("Delete")
        c.waitForText("Delete tournament")
        pressDeleteInConfirmation()
        c.waitForText("Logout")
        c.waitForNoText(name)
    }

    private fun liveTournamentJson() = E2eApi.call("GET", "/tournaments", E2eApi.signIn(E2eAccounts.ADMIN))
        .json().getJSONArray("tournaments").let { list -> (0 until list.length()).map { list.getJSONObject(it) }.first { it.getString("id") == "test-live-ongoing" } }

    @Test
    fun dialogShowsTheMatchTimeSettings() {
        openDialog()
        c.clickButton("Match")
        // The live tournament has no stored time zone, so the form suggests the zone of the device.
        listOf("Match duration (minutes)", "120", "Time zone", java.util.TimeZone.getDefault().id, "Finish the hand in play")
            .forEach { c.waitForText(it) }
        c.assertNoText("Extra time (minutes)")
    }

    @Test
    fun matchDurationZeroIsRefusedOnSave() {
        openDialog()
        c.clickButton("Match")
        c.field("Match duration (minutes)").performTextReplacementText("0")
        c.clickButton("Save")
        c.waitForText("Match duration must be a whole number of minutes from 1 to 600.")
        c.waitForText("Branding")
    }

    @Test
    fun timeZoneWithoutARegionIsRefusedOnSave() {
        openDialog()
        c.clickButton("Match")
        c.field("Time zone").performTextReplacementText("Madrid")
        c.clickButton("Save")
        c.waitForText("Time zone must be a name like Europe/Madrid.")
    }

    /** The Match tab ends with three switches: finish the hand, best hands and chicken hands. The dialog is last in the tree. */
    private fun toggleFinishTheHand() {
        val switches = c.onAllNodes(isToggleable())
        switches[switches.fetchSemanticsNodes().size - 3].performClick()
    }

    @Test
    fun extraTimeZeroIsRefusedWhenTheHandIsFinished() {
        openDialog()
        c.clickButton("Match")
        toggleFinishTheHand()
        c.field("Extra time (minutes)").performTextReplacementText("0")
        c.clickButton("Save")
        c.waitForText("Extra time must be a whole number of minutes from 1 to 120.")
    }

    @Test
    fun savedMatchTimeSettingsReachTheServer() {
        openDialog()
        c.clickButton("Match")
        c.field("Match duration (minutes)").performTextReplacementText("95")
        toggleFinishTheHand()
        c.field("Extra time (minutes)").performTextReplacementText("20")
        c.clickButton("Save")
        c.waitForNoText("Branding")
        val tournament = liveTournamentJson()
        check(tournament.getInt("matchDurationMinutes") == 95) { "Duration: ${tournament.opt("matchDurationMinutes")}" }
        check(tournament.getString("matchEndMode") == "FINISH_HAND") { "End mode: ${tournament.opt("matchEndMode")}" }
        check(tournament.getInt("matchExtraMinutes") == 20) { "Extra time: ${tournament.opt("matchExtraMinutes")}" }
    }
}
