package com.etologic.mahjongtournamentsuite.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AppDownloadsTest {
    @Test
    fun findsTheSystemOfTheBrowser() {
        assertEquals(AppPlatform.ANDROID, appPlatformOfUserAgent("Mozilla/5.0 (Linux; Android 15; Pixel 9) AppleWebKit/537.36 Chrome/131.0 Mobile Safari/537.36"))
        assertEquals(AppPlatform.IOS, appPlatformOfUserAgent("Mozilla/5.0 (iPhone; CPU iPhone OS 18_1 like Mac OS X) AppleWebKit/605.1.15 Version/18.1 Mobile/15E148 Safari/604.1"))
        assertEquals(AppPlatform.MACOS, appPlatformOfUserAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 Version/18.1 Safari/605.1.15"))
        assertEquals(AppPlatform.WINDOWS, appPlatformOfUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/131.0 Safari/537.36"))
        assertEquals(AppPlatform.LINUX, appPlatformOfUserAgent("Mozilla/5.0 (X11; Linux x86_64) Gecko/20100101 Firefox/133.0"))
        assertNull(appPlatformOfUserAgent("Mozilla/5.0 (X11; CrOS x86_64 14541.0.0) AppleWebKit/537.36 Chrome/131.0 Safari/537.36"))
    }

    @Test
    fun anIpadThatSaysItIsAMacIsAnIpad() {
        val ipad = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 Version/18.1 Safari/605.1.15"
        assertEquals(AppPlatform.IOS, appPlatformOfUserAgent(ipad, touchPoints = 5))
    }

    @Test
    fun theSystemOfTheBrowserComesFirst() {
        assertEquals(
            listOf(AppPlatform.WINDOWS, AppPlatform.ANDROID, AppPlatform.IOS, AppPlatform.MACOS, AppPlatform.LINUX),
            appsInOrder(AppPlatform.WINDOWS),
        )
        assertEquals(AppPlatform.entries, appsInOrder(null))
    }

    @Test
    fun theDesktopLinksPointToTheFixedNamesOfTheLatestRelease() {
        AppDownloads.url(AppPlatform.MACOS)?.let { assertEquals(true, it.endsWith("/releases/latest/download/MTS-macos-arm64.dmg")) }
    }
}
