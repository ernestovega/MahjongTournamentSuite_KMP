package com.etologic.mahjongtournamentsuite.player.e2e

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import org.junit.Test

/**
 * The one app of the suite holds the player part and the admin part. These tests switch between them on a phone.
 * The sign-out in the admin part, which goes back to the player part, is in SignInTest of the admin tests (tablet).
 */
class SuiteSwitchTest : PlayerE2eTest() {
    private companion object {
        /** The Edit button of a tournament: only the admin part has it. */
        const val ADMIN_LIST = "Edit Test - Live ongoing tournament"
    }

    @Test
    fun adminChoosesManagementAfterTheSignIn() {
        app.launch()
        c.signIn(E2eAccounts.ADMIN)
        c.waitForText("What do you want to open?")
        c.clickButton("Manager app")
        // The admin part opens with the same session, without a second sign-in.
        c.waitForText(ADMIN_LIST)
        c.waitForText("Test - Live ongoing tournament")
    }

    @Test
    fun managementButtonOpensTheAdminPartAndBackReturns() {
        start(E2eAccounts.ADMIN)
        // On a phone the buttons at the start of the top bar (Profile, Manager app) are in the first menu.
        c.clickButton("Menu")
        // The hidden top bar button is also in the tree with the same label. The item of the open menu is the last one.
        c.waitForText("Manager app")
        c.onAllNodes(hasText("Manager app") and hasClickAction()).onLast().performClick()
        c.waitForText(ADMIN_LIST)
        // Back on the first screen of the admin part returns to the player part.
        Espresso.pressBack()
        c.waitForText("Manager app")
        c.assertNoText(ADMIN_LIST)
    }

    @Test
    fun playerViewButtonReturnsToThePlayerPart() {
        start(E2eAccounts.ADMIN)
        // On a phone the buttons at the start of the top bar (Profile, Manager app) are in the first menu.
        c.clickButton("Menu")
        // The hidden top bar button is also in the tree with the same label. The item of the open menu is the last one.
        c.waitForText("Manager app")
        c.onAllNodes(hasText("Manager app") and hasClickAction()).onLast().performClick()
        c.waitForText(ADMIN_LIST)
        // On a phone the buttons at the start of the top bar are in the first menu.
        c.clickButton("Menu")
        c.clickButton("Player app")
        c.waitForText("Manager app")
        c.assertNoText(ADMIN_LIST)
    }

    @Test
    fun playerHasNoManagementButton() {
        start(E2eAccounts.PLAYER, ready = "My tournaments")
        c.assertNoText("Manager app")
    }
}
