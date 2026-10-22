package com.etologic.mahjongtournamentsuite.player.e2e

import org.junit.Test

class PlayerHomeTest : PlayerE2eTest() {
    private fun home(email: String) = start(email, ready = "My tournaments")

    @Test
    fun tournamentCardShowsVenueAndState() {
        home(E2eAccounts.PLAYER)
        c.waitForText("Test - Live ongoing tournament")
        c.waitForText("Madrid")
        c.waitForText("In progress")
        c.waitForText("Eskalerria - Calle Cipriano Sancho 36, Madrid")
        c.waitForText("test-live@example.com")
    }

    @Test
    fun cardHasLinkToTheMap() {
        home(E2eAccounts.PLAYER)
        c.waitForText("Open directions in Google Maps")
    }

    @Test
    fun cardShowsThePlayerPlace() {
        home(E2eAccounts.PLAYER)
        c.waitForText("Place")
    }

    @Test
    fun scheduleCanBeShownAndHidden() {
        home(E2eAccounts.PLAYER)
        c.clickButton("Show schedule")
        c.waitForText("Hide schedule")
        c.clickButton("Hide schedule")
        c.waitForText("Show schedule")
    }

    @Test
    fun playerWithoutTournamentsSeesAMessage() {
        home(E2eAccounts.STRANGER)
        c.waitForText("You are not registered in any tournament yet.")
    }

    @Test
    fun seatedPlayerSeesTheTournament() {
        home(E2eAccounts.SEATED)
        c.waitForText("Test - Live ongoing tournament")
    }

    @Test
    fun waitingPlayerSeesTheTournament() {
        home(E2eAccounts.WAITING)
        c.waitForText("Test - Live ongoing tournament")
    }

    @Test
    fun playerWithAFinishedTableSeesTheTournament() {
        home(E2eAccounts.DONE)
        c.waitForText("Test - Live ongoing tournament")
    }

    @Test
    fun playerDoesNotSeeOtherTournaments() {
        home(E2eAccounts.PLAYER)
        c.assertNoText("Test - Large team tournament")
        c.assertNoText("Test - Minimum 16 players")
    }

    @Test
    fun themeButtonChangesTheTheme() {
        home(E2eAccounts.PLAYER)
        c.waitForText("Theme: System")
        c.clickButton("Theme: System")
        c.waitForText("Theme: Light")
    }

    @Test
    fun signOutReturnsToSignIn() {
        home(E2eAccounts.PLAYER)
        c.clickButton("Sign out")
        c.waitForText("Do you want to sign out?")
        c.clickButton("Yes, sign out")
        c.waitForText("Sign in")
        c.waitForText("Email")
    }

    @Test
    fun statisticsShortcutOpensPlayerStatistics() {
        home(E2eAccounts.PLAYER)
        c.clickButton("Statistics")
        c.waitForText("Statistics")
    }
}
