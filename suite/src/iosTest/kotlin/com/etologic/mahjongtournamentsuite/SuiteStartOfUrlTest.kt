package com.etologic.mahjongtournamentsuite

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The links of the site that open the iOS app (universal links), read with NSURLComponents. */
class SuiteStartOfUrlTest {
    private val site = "https://mahjongmadrid.com"

    @Test
    fun theHomeOpensThePlayerPart() {
        assertEquals(SuiteStart(SuiteSection.PLAYER), suiteStartOfUrl("$site/"))
        assertEquals(SuiteStart(SuiteSection.PLAYER), suiteStartOfUrl(site))
    }

    @Test
    fun adminOpensTheAdminPart() {
        assertEquals(SuiteStart(SuiteSection.ADMIN), suiteStartOfUrl("$site/admin/"))
        assertEquals(SuiteStart(SuiteSection.ADMIN), suiteStartOfUrl("$site/admin/tournaments/x"))
    }

    @Test
    fun supportOpensTheForm() {
        assertEquals(SuiteStart(SuiteSection.PLAYER, support = true), suiteStartOfUrl("$site/support"))
    }

    @Test
    fun aTournamentOpensItWithTheIdDecoded() {
        assertEquals(SuiteStart(SuiteSection.PLAYER, tournamentId = "madrid-open-2026"), suiteStartOfUrl("$site/t/madrid-open-2026"))
        assertEquals(
            SuiteStart(SuiteSection.PLAYER, tournamentId = "madrid open"),
            suiteStartOfUrl("$site/?tournament=madrid%20open"),
        )
    }

    @Test
    fun theTableQrOpensTheTableOfThePlayer() {
        val start = suiteStartOfUrl("$site/madrid-open-2026/my-table")
        assertTrue(start?.link != null, "$start")
        assertEquals(SuiteSection.PLAYER, start?.section)
    }
}
