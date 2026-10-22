package com.etologic.mahjongtournamentsuite.e2e

import androidx.compose.ui.test.performTextInput
import com.etologic.mahjongtournamentsuite.data.network.BackendUrlOverride
import org.junit.Test

class SignInTest : AdminE2eTest() {
    @Test
    fun adminSignsInAndSeesTournaments() {
        start(E2eAccounts.ADMIN)
    }

    @Test
    fun wrongPasswordShowsMessage() {
        app.launch()
        c.signIn(E2eAccounts.ADMIN, password = "wrong-password")
        c.waitForText("The email or password is incorrect.")
    }

    @Test
    fun unknownEmailShowsTheSameMessage() {
        app.launch()
        c.signIn("nobody@e2e.test")
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
    fun errorDialogClosesWithOk() {
        app.launch()
        c.signIn(E2eAccounts.ADMIN, password = "wrong-password")
        c.waitForText("The email or password is incorrect.")
        c.clickButton("OK")
        c.waitForNoText("The email or password is incorrect.")
    }

    @Test
    fun forgotPasswordNeedsAnEmailFirst() {
        app.launch()
        c.waitForText("Forgot password?")
        c.clickButton("Forgot password?")
        c.waitForText("Enter your email address first.")
    }

    @Test
    fun forgotPasswordConfirmsWithoutRevealingIfTheAccountExists() {
        app.launch()
        c.waitForText("Forgot password?")
        c.field("Email").performTextInput(E2eAccounts.ADMIN)
        c.clickButton("Forgot password?")
        c.waitForText("If an account exists, we sent password reset instructions to your email.")
    }

    @Test
    fun signInScreenShowsTheBackendVersion() {
        app.launch()
        c.waitForText("Backend 1.0.0", substring = true)
    }

    @Test
    fun sessionSurvivesRestartingTheApp() {
        start(E2eAccounts.ADMIN)
        app.close()
        app.launch()
        c.waitForText("Test - Live ongoing tournament")
    }

    @Test
    fun logoutReturnsToSignIn() {
        start(E2eAccounts.ADMIN)
        c.clickButton("Logout")
        c.waitForText("Do you want to sign out?")
        c.clickButton("Yes, sign out")
        c.waitForText("Use your email to sign in. Accounts are invitation-only.")
        // The session is gone: a restart shows the sign-in screen again.
        app.close()
        app.launch()
        c.waitForText("Use your email to sign in. Accounts are invitation-only.")
    }

    @Test
    fun unreachableBackendShowsNetworkMessage() {
        BackendUrlOverride.functionsBaseUrl = "http://10.0.2.2:5999/api"
        app.launchKeepingBackend()
        c.signIn(E2eAccounts.ADMIN)
        c.waitForText("Network request failed.")
    }
}
