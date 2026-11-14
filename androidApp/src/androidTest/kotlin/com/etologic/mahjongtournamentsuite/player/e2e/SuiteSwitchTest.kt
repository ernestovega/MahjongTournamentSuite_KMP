package com.etologic.mahjongtournamentsuite.player.e2e

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import org.junit.Test

/** The one app of the suite holds the player part and the admin part. These tests switch between them. */
class SuiteSwitchTest : PlayerE2eTest() {
    private companion object {
        /** The Edit button of a tournament: only the admin part has it. On a phone "New tournament" is in a menu. */
        const val ADMIN_LIST = "Edit Test - Live ongoing tournament"
    }

    @Test
    fun adminChoosesManagementAfterTheSignIn() {
        app.launch()
        c.signIn(E2eAccounts.ADMIN)
        c.waitForText("What do you want to open?")
        c.clickButton("Management")
        // The admin part opens with the same session, without a second sign-in.
        c.waitForText(ADMIN_LIST)
        c.waitForText("Test - Live ongoing tournament")
    }

    @Test
    fun managementButtonOpensTheAdminPartAndBackReturns() {
        start(E2eAccounts.ADMIN)
        c.clickButton("Management")
        c.waitForText(ADMIN_LIST)
        // Back on the first screen of the admin part returns to the player part.
        Espresso.pressBack()
        c.waitForText("Management")
        c.assertNoText(ADMIN_LIST)
    }

    @Test
    fun playerViewButtonReturnsToThePlayerPart() {
        start(E2eAccounts.ADMIN)
        c.clickButton("Management")
        c.waitForText(ADMIN_LIST)
        c.clickButton("Player view")
        c.waitForText("Management")
        c.assertNoText(ADMIN_LIST)
    }

    @Test
    fun signOutInTheAdminPartLeavesThePlayerPartSignedOut() {
        start(E2eAccounts.ADMIN)
        c.clickButton("Management")
        c.waitForText(ADMIN_LIST)
        // The profile button of the admin part has no text, only the label of its click action.
        val profile = SemanticsMatcher("profile button") { it.config.getOrNull(SemanticsActions.OnClick)?.label == "Open profile" }
        c.waitUntil(20_000) { c.onAllNodes(profile).fetchSemanticsNodes().isNotEmpty() }
        c.onNode(profile).performClick()
        c.clickButton("Logout")
        c.clickButton("Yes, sign out")
        c.waitForText("Sign in")
        c.assertNoText("Management")
    }

    @Test
    fun playerHasNoManagementButton() {
        start(E2eAccounts.PLAYER, ready = "My tournaments")
        c.assertNoText("Management")
    }
}
