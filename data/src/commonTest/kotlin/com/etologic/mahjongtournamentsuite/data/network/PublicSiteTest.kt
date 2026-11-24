package com.etologic.mahjongtournamentsuite.data.network

import kotlin.test.Test
import kotlin.test.assertEquals

class PublicSiteTest {
    @Test
    fun productionGoesThroughItsOwnDomain() {
        assertEquals("https://mts.mahjongmadrid.com/api", PublicSite.apiThroughHosting(PublicSite.PRODUCTION_FUNCTIONS))
        assertEquals(PublicSite.PRODUCTION, PublicSite.ofBackend(PublicSite.PRODUCTION_FUNCTIONS))
    }

    @Test
    fun stagingGoesThroughItsFirebaseSite() {
        val staging = "https://europe-southwest1-mts---staging.cloudfunctions.net/api"
        assertEquals("https://mts---staging.web.app/api", PublicSite.apiThroughHosting(staging))
    }

    @Test
    fun aLocalBackendOfTheTestsStaysAsItIs() {
        assertEquals("http://10.0.2.2:5999/api", PublicSite.apiThroughHosting("http://10.0.2.2:5999/api"))
    }
}
