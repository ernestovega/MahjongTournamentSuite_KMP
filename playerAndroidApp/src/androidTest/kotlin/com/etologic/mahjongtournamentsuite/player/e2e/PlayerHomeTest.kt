package com.etologic.mahjongtournamentsuite.player.e2e

import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
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
        c.waitForText("Directions")
        c.waitForText("Contact")
    }

    @Test
    fun cardHasLinkToTheMap() {
        home(E2eAccounts.PLAYER)
        c.waitForText("Open directions in Google Maps")
    }

    /** The place of the player is a tag at the top right of the card, read out as "Position N". */
    @Test
    fun cardShowsThePlayerPlace() {
        home(E2eAccounts.PLAYER)
        c.waitForText("Position ", substring = true)
    }

    @Test
    fun scheduleCanBeShownAndHidden() {
        home(E2eAccounts.PLAYER)
        c.clickButton("Schedule")
        c.waitForText("Hide schedule")
        c.clickButton("Hide schedule")
        c.waitForText("Schedule")
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

    /** The theme is chosen in the profile, which opens from the Profile button of the top bar. */
    @Test
    fun themeChoiceInTheProfileChangesTheTheme() {
        home(E2eAccounts.PLAYER)
        c.clickButton("Profile")
        c.waitForText("Theme")
        val light = hasContentDescription("Light") and hasClickAction()
        c.waitUntil(5_000) { c.onAllNodes(light).fetchSemanticsNodes().isNotEmpty() }
        c.onAllNodes(light).onLast().assertIsNotSelected()
        c.onAllNodes(light).onLast().performClick()
        c.onAllNodes(light).onLast().assertIsSelected()
    }

    /** The Logout button is in the profile. */
    @Test
    fun signOutReturnsToSignIn() {
        home(E2eAccounts.PLAYER)
        c.clickButton("Profile")
        c.clickButton("Logout")
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
