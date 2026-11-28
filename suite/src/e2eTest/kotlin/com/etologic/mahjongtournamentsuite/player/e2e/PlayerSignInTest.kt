@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.etologic.mahjongtournamentsuite.player.e2e

import kotlin.test.Test

class PlayerSignInTest : PlayerE2eTest() {
    @Test
    fun playerSignsInAndSeesOwnTournament() = e2e {
        app.launch()
        c.signIn(E2eAccounts.PLAYER)
        c.waitForText("My tournaments")
        c.waitForText("Test - Live ongoing tournament")
    }

    @Test
    fun editorChoosesThePlayerAppAfterTheSignIn() = e2e {
        app.launch()
        c.signIn(E2eAccounts.EDITOR)
        c.waitForText("What do you want to open?")
        c.clickButton("Player app")
        c.waitForText("Test - Live ongoing tournament")
        c.assertNoText("What do you want to open?")
    }

    @Test
    fun playerIsNotAskedWhichAppToOpen() = e2e {
        app.launch()
        c.signIn(E2eAccounts.PLAYER)
        c.waitForText("Test - Live ongoing tournament")
        c.assertNoText("What do you want to open?")
        c.assertNoText("Manager app")
    }

    @Test
    fun wrongPasswordShowsMessage() = e2e {
        app.launch()
        c.signIn(E2eAccounts.PLAYER, password = "wrong-password")
        c.waitForText("The email or password is incorrect.")
    }

    @Test
    fun emptyFieldsAskForEmailAndPassword() = e2e {
        app.launch()
        c.openSignIn()
        c.clickButton("Sign in")
        c.waitForText("Enter your email and password.")
    }

    @Test
    fun disabledAccountIsRefused() = e2e {
        app.launch()
        c.signIn(E2eAccounts.DISABLED)
        c.waitForText("This account is disabled. Ask an administrator for help.")
    }

    @Test
    fun forgotPasswordNeedsAnEmailFirst() = e2e {
        app.launch()
        c.openSignIn()
        c.waitForText("Forgot password?")
        c.clickButton("Forgot password?")
        c.waitForText("Enter your email address first.")
    }

    @Test
    fun sessionSurvivesRestartingTheApp() = e2e {
        start(E2eAccounts.PLAYER, ready = "My tournaments")
        app.close()
        app.launch()
        c.waitForText("My tournaments")
    }
}
