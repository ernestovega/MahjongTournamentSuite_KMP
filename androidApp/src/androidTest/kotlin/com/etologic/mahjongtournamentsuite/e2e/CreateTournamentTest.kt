package com.etologic.mahjongtournamentsuite.e2e

import org.junit.Test

class CreateTournamentTest : AdminE2eTest() {
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
        c.waitForText("Tournament schedule")
        c.waitForText("Round 7")
    }

    @Test
    fun emptyNameIsRejected() {
        openForm()
        c.clickButton("Create")
        c.waitForText("* Name is required.")
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
        c.waitForText("* Select a host country.")
    }

    @Test
    fun errorDialogClosesWithOk() {
        openForm()
        c.clickButton("Create")
        c.waitForText("* Name is required.")
        c.clickButton("OK")
        c.waitForNoText("* Name is required.")
    }

    @Test
    fun editorHasNoNewTournamentButton() {
        start(E2eAccounts.EDITOR)
        c.waitForText("EMA players")
        c.assertNoText("New tournament")
    }
}
