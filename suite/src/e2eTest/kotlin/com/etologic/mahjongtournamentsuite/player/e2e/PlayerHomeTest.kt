@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.etologic.mahjongtournamentsuite.player.e2e

import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import com.etologic.mahjongtournamentsuite.e2e.E2ePlatform
import kotlin.test.Test

/** The buttons of the app ignore a second press within 500 ms. See DebouncedButtons.kt. */
private const val BUTTON_PRESS_THRESHOLD_MS = 700L

class PlayerHomeTest : PlayerE2eTest() {
    private fun home(email: String) = start(email, ready = "My tournaments")

    @Test
    fun tournamentCardShowsVenueAndState() = e2e {
        home(E2eAccounts.PLAYER)
        c.waitForText("Test - Live ongoing tournament")
        c.waitForText("Madrid")
        c.waitForText("In progress")
        // The card shows only the city and the flag. The address is behind the Directions icon and in the Info tab.
        c.assertNoText("Eskalerria - Calle Cipriano Sancho 36, Madrid")
        c.waitForText("Contact")
    }

    /**
     * The back leaves the app only from the first tab of the start screen, In play here because a tournament is in
     * play. From Stats or All it opens In play (Ernesto 27-nov). A back that left the app would fail pressBack.
     */
    @Test
    fun backFromAnotherTabOpensTheFirstTabAndStaysInTheApp() = e2e {
        home(E2eAccounts.PLAYER)
        val inPlaySelected = hasText("In play", ignoreCase = true) and isSelected()
        c.waitUntil(timeoutMillis = 10_000) { c.onAllNodes(inPlaySelected).fetchSemanticsNodes().isNotEmpty() }
        c.clickButton("Stats")
        c.waitForText("Overall")
        c.pressBack()
        c.waitUntil(timeoutMillis = 10_000) { c.onAllNodes(inPlaySelected).fetchSemanticsNodes().isNotEmpty() }
        c.waitForText("Test - Live ongoing tournament")

        c.clickButton("All")
        c.waitUntil(timeoutMillis = 10_000) { c.onAllNodes(inPlaySelected).fetchSemanticsNodes().isEmpty() }
        c.pressBack()
        c.waitUntil(timeoutMillis = 10_000) { c.onAllNodes(inPlaySelected).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun cardHasLinkToTheMap() = e2e {
        home(E2eAccounts.PLAYER)
        c.waitForText("Open directions in Google Maps")
    }

    /**
     * The places of the player are lines at the top right of the card: the number and the icon of the stat, here the
     * ranking. A tap shows the details in a tooltip (checked by the screenshot test tournament-standings-tooltip). This
     * player has a live table, and a tap here can end on that table, so the test does not tap.
     */
    @Test
    fun cardShowsThePlayerPlace() = e2e {
        home(E2eAccounts.PLAYER)
        c.waitForText("Show the details")
        c.waitForText("Ranking")
    }

    /** The buttons ignore a second press within 500 ms, so the test waits a moment before it hides the schedule. */
    @Test
    fun scheduleCanBeShownAndHidden() = e2e {
        home(E2eAccounts.PLAYER)
        c.clickButton("Schedule")
        c.waitForText("Hide schedule")
        E2ePlatform.sleep(BUTTON_PRESS_THRESHOLD_MS)
        c.clickButton("Hide schedule")
        c.waitForNoText("Hide schedule")
        c.waitForText("Schedule")
    }

    /**
     * Every tournament is public, so the list shows them all. The "My tournaments" filter leaves none for an account
     * that does not play in any, and the list says that no tournament matches.
     */
    @Test
    fun playerWithoutTournamentsSeesAMessage() = e2e {
        home(E2eAccounts.STRANGER)
        c.waitForText("Test - Live ongoing tournament")
        c.clickButton("My tournaments")
        c.waitForText("No tournament matches the filters.")
        c.waitForNoText("Test - Live ongoing tournament")
    }

    @Test
    fun seatedPlayerSeesTheTournament() = e2e {
        home(E2eAccounts.SEATED)
        c.waitForText("Test - Live ongoing tournament")
    }

    @Test
    fun waitingPlayerSeesTheTournament() = e2e {
        home(E2eAccounts.WAITING)
        c.waitForText("Test - Live ongoing tournament")
    }

    @Test
    fun playerWithAFinishedTableSeesTheTournament() = e2e {
        home(E2eAccounts.DONE)
        c.waitForText("Test - Live ongoing tournament")
    }

    @Test
    fun playerDoesNotSeeOtherTournaments() = e2e {
        home(E2eAccounts.PLAYER)
        c.assertNoText("Test - Large team tournament")
        c.assertNoText("Test - Minimum 16 players")
    }

    /** The theme is chosen in the profile, which opens from the Profile button of the top bar. */
    @Test
    fun themeChoiceInTheProfileChangesTheTheme() = e2e {
        home(E2eAccounts.PLAYER)
        c.clickButton("Profile")
        c.waitForText("Theme")
        // The theme buttons show their name next to the icon.
        val light = hasText("Light") and hasClickAction()
        c.waitUntil(timeoutMillis = 5_000) { c.onAllNodes(light).fetchSemanticsNodes().isNotEmpty() }
        c.onAllNodes(light).onLast().assertIsNotSelected()
        c.onAllNodes(light).onLast().performClick()
        c.onAllNodes(light).onLast().assertIsSelected()
    }

    /**
     * The Logout button is in the profile. The public tournament list stays on screen, without the data of the
     * account, and the top bar shows the Sign in button again, which opens the sign-in form.
     */
    @Test
    fun signOutReturnsToSignIn() = e2e {
        home(E2eAccounts.PLAYER)
        // The stamp of an own tournament (20-nov): Playing while it is in play, Played once it ends. It shows in capitals.
        c.waitForAnyText("PLAYING", "PLAYED")
        c.clickButton("Profile")
        c.clickButton("Logout")
        c.waitForText("Do you want to sign out?")
        c.clickButton("Yes, sign out")
        c.waitForText("Sign in")
        c.waitForNoText("Profile")
        c.waitForNoText("My tournaments")
        c.waitForNoText("Playing")
        c.waitForNoText("Played")
        c.waitForText("Test - Live ongoing tournament")
        c.openSignIn()
        c.waitForText("Password")
    }

    @Test
    fun statisticsShortcutOpensPlayerStatistics() = e2e {
        home(E2eAccounts.PLAYER)
        c.clickButton("Stats")
        c.waitForText("Stats")
    }
}
