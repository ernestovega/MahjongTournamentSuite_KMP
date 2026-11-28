package com.etologic.mahjongtournamentsuite.data.network

import com.etologic.mahjongtournamentsuite.data.platform.IosPlatform
import com.etologic.mahjongtournamentsuite.data.platform.platformCpuCount
import com.etologic.mahjongtournamentsuite.data.session.PlatformCredentialStore
import com.etologic.mahjongtournamentsuite.domain.model.SavedCredentials
import com.etologic.mahjongtournamentsuite.domain.AppVersion
import io.ktor.client.engine.darwin.Darwin
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** What the iOS app tells the network: the backend of its build, App Check, its language and its HTTP engine. */
class IosNetworkTest {
    @AfterTest
    fun resetAppCheck() {
        IosPlatform.appCheckToken = null
        IosPlatform.appCheckState = "off"
    }

    @Test
    fun withoutABackendInTheInfoPlistTheAppUsesProduction() {
        // The test binary has no MTSBackendUrl, like a production build.
        assertNull(IosPlatform.backendUrl)
        assertFalse(IosPlatform.isDebug)
        assertEquals(PublicSite.PRODUCTION_FUNCTIONS, functionsDirectUrlPlatform())
        assertEquals(PublicSite.apiThroughHosting(PublicSite.PRODUCTION_FUNCTIONS), functionsBaseUrlPlatform())
        assertEquals(PublicSite.ofBackend(PublicSite.PRODUCTION_FUNCTIONS), publicSiteUrlPlatform())
    }

    @Test
    fun withoutAppCheckThereIsNoTokenAndTheServerIsTold() = runTest {
        assertNull(currentAppCheckToken())
        assertEquals("ios/${AppVersion.admin}; appcheck=off", currentClientTag())
    }

    @Test
    fun theTokenComesFromTheSwiftSide() = runTest {
        IosPlatform.appCheckToken = { "attest-token" }
        IosPlatform.appCheckState = "ok"
        assertEquals("attest-token", currentAppCheckToken())
        assertEquals("ios/${AppVersion.admin}; appcheck=ok", currentClientTag())
    }

    @Test
    fun theRequestsUseTheEngineOfApple() {
        assertSame(Darwin, httpClientEngineFactory())
    }

    @Test
    fun theLanguageOfTheRequestsIsThePreferredOne() {
        assertTrue(currentLanguageTag().orEmpty().isNotBlank())
    }

    @Test
    fun theDrawUsesTheProcessorsOfTheDevice() {
        assertTrue(platformCpuCount() >= 1)
    }

    @Test
    fun theAppKeepsTheSessionNotThePassword() = runTest {
        val store = PlatformCredentialStore()
        store.save(SavedCredentials("ana@example.test", "secret"))
        assertNull(store.load())
        store.clear()
        assertNull(store.load())
    }
}
