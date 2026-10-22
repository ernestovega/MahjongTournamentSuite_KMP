package com.etologic.mahjongtournamentsuite.player.e2e

import org.junit.Test

class PlayerSignInTest : PlayerE2eTest() {
    @Test
    fun playerSignsInAndSeesOwnTournament() {
        app.launch()
        c.signIn(E2eAccounts.PLAYER)
        c.waitForText("My tournaments")
        c.waitForText("Test - Live ongoing tournament")
    }

    @Test
    fun wrongPasswordShowsMessage() {
        app.launch()
        c.signIn(E2eAccounts.PLAYER, password = "wrong-password")
        c.waitForText("The email or password is incorrect.")
    }

    @Test
    fun emptyFieldsAskForEmailAndPassword() {
        app.launch()
        c.waitForText("Sign in")
        c.clickButton("Sign in")
        c.waitForText("Enter your email and password.")
    }

    @Test
    fun disabledAccountIsRefused() {
        app.launch()
        c.signIn(E2eAccounts.DISABLED)
        c.waitForText("This account is disabled. Ask an administrator for help.")
    }

    @Test
    fun forgotPasswordNeedsAnEmailFirst() {
        app.launch()
        c.waitForText("Forgot password?")
        c.clickButton("Forgot password?")
        c.waitForText("Enter your email address first.")
    }

    @Test
    fun sessionSurvivesRestartingTheApp() {
        start(E2eAccounts.PLAYER, ready = "My tournaments")
        app.close()
        app.launch()
        c.waitForText("My tournaments")
    }
}
