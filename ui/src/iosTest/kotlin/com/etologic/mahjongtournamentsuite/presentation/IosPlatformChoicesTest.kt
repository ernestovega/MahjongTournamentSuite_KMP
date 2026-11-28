package com.etologic.mahjongtournamentsuite.presentation

import com.etologic.mahjongtournamentsuite.presentation.components.showsLoadingBackgroundLogo
import com.etologic.mahjongtournamentsuite.presentation.platform.SuiteApp
import com.etologic.mahjongtournamentsuite.presentation.platform.SuiteAppHost
import com.etologic.mahjongtournamentsuite.presentation.platform.appSwitchLeavesPage
import com.etologic.mahjongtournamentsuite.presentation.platform.leaveAdminForPlayerApp
import com.etologic.mahjongtournamentsuite.presentation.platform.loadBrowserCredentials
import com.etologic.mahjongtournamentsuite.presentation.platform.preventBrowserAutoSignIn
import com.etologic.mahjongtournamentsuite.presentation.platform.saveBrowserCredentials
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** How the iOS app differs from the web: one app with both parts, no browser, and the logo behind the loading. */
class IosPlatformChoicesTest {
    @AfterTest
    fun noHost() {
        SuiteAppHost.switchTo = null
    }

    @Test
    fun theAdminPartOpensThePlayerPartInTheSameApp() {
        val switches = mutableListOf<Pair<SuiteApp, Boolean>>()
        SuiteAppHost.switchTo = { app, signIn -> switches += app to signIn }
        assertTrue(leaveAdminForPlayerApp(signIn = true))
        assertTrue(leaveAdminForPlayerApp(signIn = false))
        assertEquals(listOf(SuiteApp.PLAYER to true, SuiteApp.PLAYER to false), switches)
        assertFalse(appSwitchLeavesPage)
    }

    @Test
    fun beforeTheAppStartsThereIsNoPartToOpen() {
        assertFalse(leaveAdminForPlayerApp(signIn = false))
    }

    @Test
    fun theBrowserPasswordsDoNotExistOnIos() {
        var loaded = false
        saveBrowserCredentials("ana@example.test", "secret")
        loadBrowserCredentials { _, _ -> loaded = true }
        preventBrowserAutoSignIn()
        assertFalse(loaded)
    }

    @Test
    fun theLoadingShowsTheLogoBehind() {
        assertTrue(showsLoadingBackgroundLogo)
    }
}
