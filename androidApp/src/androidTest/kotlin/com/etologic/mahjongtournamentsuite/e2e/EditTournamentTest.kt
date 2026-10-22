package com.etologic.mahjongtournamentsuite.e2e

import org.junit.After
import org.junit.Test

class EditTournamentTest : AdminE2eTest() {
    @After
    fun restoreData() = E2eApi.reset("test-live-ongoing")

    private fun openDialog(name: String = "Test - Live ongoing tournament") {
        start(E2eAccounts.ADMIN, ready = "Logout")
        c.openTournament(name)
        c.waitForText("Players & results")
        c.clickButton("Edit tournament")
        c.waitForText("Tournament data")
    }

    @Test
    fun dialogShowsStoredValues() {
        openDialog()
        listOf("Eskalerria", "test-live@example.com", "Madrid", "#D81B60", "Tournament schedule", "Player agenda")
            .forEach { c.waitForText(it) }
    }

    @Test
    fun dialogShowsReadOnlyTournamentData() {
        openDialog()
        listOf("test-live-ongoing", "Created by UID", "Created by name", "Created at", "Updated at", "Tries").forEach { c.waitForText(it) }
    }

    @Test
    fun dialogListsOneRowPerRoundAndPerAgendaItem() {
        openDialog()
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
        c.waitForNoText("Tournament data")
        c.waitForText("Round 4")
    }

    @Test
    fun saveRenamesTheTournament() {
        openDialog()
        c.field("Tournament name").performTextReplacementText("Live renamed by a test")
        c.clickButton("Save")
        c.waitForNoText("Tournament data")
        c.waitForText("Live renamed by a test")
    }

    @Test
    fun tournamentWithoutVenueOrAgendaOpensTheDialog() {
        openDialog("Test - Without best hands, chicken hands, venue or dates")
        c.waitForText("Tournament data")
        c.waitForText("Add activity")
    }

    @Test
    fun addActivityAddsAnAgendaRow() {
        openDialog()
        c.clickButton("Add activity")
        c.waitForText("Choose activity 4 date")
    }

    @Test
    fun removeActivityRemovesAnAgendaRow() {
        openDialog()
        c.clickButton("Remove")
        c.waitForNoText("Choose activity 3 date")
    }

    @Test
    fun editorSaveIsRefusedByTheServer() {
        start(E2eAccounts.EDITOR, ready = "Logout")
        c.openTournament("Test - Live ongoing tournament")
        c.waitForText("Players & results")
        c.clickButton("Edit tournament")
        c.waitForText("Tournament data")
        c.field("Tournament name").performTextReplacementText("Renamed by an editor")
        c.clickButton("Save")
        c.waitForText("You do not have permission to perform this action.")
    }
}
