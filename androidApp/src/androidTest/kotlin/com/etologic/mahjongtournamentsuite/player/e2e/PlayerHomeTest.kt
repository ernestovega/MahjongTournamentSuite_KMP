package com.etologic.mahjongtournamentsuite.player.e2e

import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import org.junit.Test

/** The buttons of the app ignore a second press within 500 ms. See DebouncedButtons.kt. */
private const val BUTTON_PRESS_THRESHOLD_MS = 700L

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

    /** The buttons ignore a second press within 500 ms, so the test waits a moment before it hides the schedule. */
    @Test
    fun scheduleCanBeShownAndHidden() {
        home(E2eAccounts.PLAYER)
        c.clickButton("Schedule")
        c.waitForText("Hide schedule")
        Thread.sleep(BUTTON_PRESS_THRESHOLD_MS)
        c.clickButton("Hide schedule")
        c.waitForNoText("Hide schedule")
        c.waitForText("Schedule")
    }

    /**
     * Every tournament is public, so the list shows them all. The "My tournaments" filter leaves none for an account
     * that does not play in any, and the list says that no tournament matches.
     */
    @Test
    fun playerWithoutTournamentsSeesAMessage() {
        home(E2eAccounts.STRANGER)
        c.waitForText("Test - Live ongoing tournament")
        c.clickButton("My tournaments")
        c.waitForText("No tournament matches the filters.")
        c.waitForNoText("Test - Live ongoing tournament")
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

    /**
     * The Logout button is in the profile. The public tournament list stays on screen, without the data of the
     * account, and the top bar shows the Sign in button again, which opens the sign-in form.
     */
    @Test
    fun signOutReturnsToSignIn() {
        home(E2eAccounts.PLAYER)
        c.waitForText("You play in this tournament")
        c.clickButton("Profile")
        c.clickButton("Logout")
        c.waitForText("Do you want to sign out?")
        c.clickButton("Yes, sign out")
        c.waitForText("Sign in")
        c.waitForNoText("Profile")
        c.waitForNoText("My tournaments")
        c.waitForNoText("You play in this tournament")
        c.waitForText("Test - Live ongoing tournament")
        c.openSignIn()
        c.waitForText("Password")
    }

    @Test
    fun statisticsShortcutOpensPlayerStatistics() {
        home(E2eAccounts.PLAYER)
        c.clickButton("Statistics")
        c.waitForText("Statistics")
    }
}
