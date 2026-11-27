package com.etologic.mahjongtournamentsuite.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppDownloadsTest {
    @Test
    fun findsTheSystemOfTheBrowser() {
        assertEquals(AppPlatform.ANDROID, appPlatformOfUserAgent("Mozilla/5.0 (Linux; Android 15; Pixel 9) AppleWebKit/537.36 Chrome/131.0 Mobile Safari/537.36"))
        assertEquals(AppPlatform.IOS, appPlatformOfUserAgent("Mozilla/5.0 (iPhone; CPU iPhone OS 18_1 like Mac OS X) AppleWebKit/605.1.15 Version/18.1 Mobile/15E148 Safari/604.1"))
        assertEquals(AppPlatform.MACOS, appPlatformOfUserAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 Version/18.1 Safari/605.1.15"))
        assertEquals(AppPlatform.WINDOWS, appPlatformOfUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/131.0 Safari/537.36"))
        assertEquals(AppPlatform.LINUX, appPlatformOfUserAgent("Mozilla/5.0 (X11; Linux x86_64) Gecko/20100101 Firefox/133.0"))
        assertNull(appPlatformOfUserAgent("Mozilla/5.0 (X11; CrOS x86_64 14541.0.0) AppleWebKit/537.36 Chrome/131.0 Safari/537.36"))
        assertNull(appPlatformOfUserAgent("curl/8.5.0"), "an agent of no known system")
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

    @Test
    fun everySystemHasItsLinkOnceItIsPublished() {
        AppPlatform.entries.forEach { platform ->
            val url = AppDownloads.url(platform)
            when (platform) {
                AppPlatform.ANDROID -> assertEquals(AppDownloads.android, url)
                AppPlatform.IOS -> assertEquals(AppDownloads.ios, url)
                else -> assertEquals(AppDownloads.desktop, url != null)
            }
        }
        assertTrue(AppDownloads.anyAvailable, "the Android app is in Google Play")
    }

    @Test
    fun theNoticeOfTheAppsClosesForThisVisitOrForever() {
        var hidden = false
        var hides = 0
        val hide = {
            hides++
            hidden = true
        }
        val apps = WebApps(device = AppPlatform.ANDROID, noticeHidden = { hidden }, hideNotice = hide)
        assertTrue(apps.showsNotice)

        apps.closeNotice(forever = false)
        assertFalse(apps.showsNotice)
        assertEquals(0, hides)

        val again = WebApps(device = null, noticeHidden = { hidden }, hideNotice = hide)
        again.closeNotice(forever = true)
        assertEquals(1, hides)
        assertFalse(WebApps(device = null, noticeHidden = { hidden }, hideNotice = {}).showsNotice, "a hidden notice stays hidden")
    }
}
