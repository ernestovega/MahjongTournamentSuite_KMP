@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.etologic.mahjongtournamentsuite.e2e

import androidx.compose.ui.test.performTextInput
import com.etologic.mahjongtournamentsuite.data.platform.IosPlatform
import kotlin.test.Test

class SignInTest : AdminE2eTest() {
    @Test
    fun adminSignsInAndSeesTournaments() = e2e {
        start(E2eAccounts.ADMIN)
    }

    @Test
    fun wrongPasswordShowsMessage() = e2e {
        app.launch()
        c.signIn(E2eAccounts.ADMIN, password = "wrong-password")
        c.waitForText("The email or password is incorrect.")
    }

    @Test
    fun unknownEmailShowsTheSameMessage() = e2e {
        app.launch()
        c.signIn("nobody@e2e.test")
        c.waitForText("The email or password is incorrect.")
    }

    @Test
    fun emptyFieldsAskForEmailAndPassword() = e2e {
        app.launch()
        c.waitForText("Sign in")
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
    fun errorDialogClosesWithOk() = e2e {
        app.launch()
        c.signIn(E2eAccounts.ADMIN, password = "wrong-password")
        c.waitForText("The email or password is incorrect.")
        c.clickButton("OK")
        c.waitForNoText("The email or password is incorrect.")
    }

    @Test
    fun forgotPasswordNeedsAnEmailFirst() = e2e {
        app.launch()
        c.waitForText("Forgot password?")
        c.clickButton("Forgot password?")
        c.waitForText("Enter your email address first.")
    }

    @Test
    fun forgotPasswordConfirmsWithoutRevealingIfTheAccountExists() = e2e {
        app.launch()
        c.waitForText("Forgot password?")
        c.field("Email").performTextInput(E2eAccounts.ADMIN)
        c.clickButton("Forgot password?")
        // A dialog shows the email first, so the user sees where the link goes.
        c.waitForText("Reset your password")
        c.clickButton("Yes, send it")
        c.waitForText("If an account exists, we sent password reset instructions to your email.")
    }

    @Test
    fun sessionSurvivesRestartingTheApp() = e2e {
        start(E2eAccounts.ADMIN)
        app.close()
        app.launch()
        c.waitForText("Test - Live ongoing tournament")
    }

    @Test
    fun logoutReturnsToSignIn() = e2e {
        start(E2eAccounts.ADMIN)
        // The Logout button is in the profile, which the profile button of the top bar opens.
        c.clickButton(PROFILE_BUTTON)
        c.clickButton("Logout")
        c.waitForText("Do you want to sign out?")
        c.clickButton("Yes, sign out")
        // The player part takes over, without a session: its top bar offers the sign-in.
        c.waitForText("Sign in")
        c.assertNoText("Manager app")
        // The session is gone: the admin part sends a restart to the sign-in form.
        app.close()
        app.launch()
        // The sign-in form: "Need help?" is gone since the sign-in redesign, "Forgot password?" stays.
        c.waitForText("Forgot password?")
    }

    @Test
    fun unreachableBackendShowsNetworkMessage() = e2e {
        IosPlatform.backendUrlOverride = "http://127.0.0.1:5999/api"
        app.launchKeepingBackend()
        c.signIn(E2eAccounts.ADMIN)
        c.waitForText("Network request failed.")
    }
}
