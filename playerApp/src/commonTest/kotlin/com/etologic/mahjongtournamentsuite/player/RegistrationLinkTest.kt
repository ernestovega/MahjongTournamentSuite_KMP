package com.etologic.mahjongtournamentsuite.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RegistrationLinkTest {
    @Test
    fun theShortLinkOfATournamentIsItsSlugAtTheRoot() {
        assertEquals(RegistrationLink(slug = "6thmmc"), registrationLinkOf("/6thmmc", emptyMap()))
        assertEquals(RegistrationLink(slug = "6thmmc"), registrationLinkOf("/6thMMC/", emptyMap()))
        assertEquals(RegistrationLink(slug = "bmo-2027"), registrationLinkOf("/bmo-2027", emptyMap()))
    }

    @Test
    fun theOldLinksStillWork() {
        assertEquals(RegistrationLink(slug = "6th-mmc-2027"), registrationLinkOf("/inscripcion/6th-mmc-2027", emptyMap()))
        assertEquals(
            RegistrationLink(tournamentId = "t1", token = "abc"),
            registrationLinkOf("/inscripcion/gestionar", mapOf("t" to "t1", "k" to "abc")),
        )
    }

    @Test
    fun theLinkOfTheEmailsHasTheTournamentAndTheToken() {
        assertEquals(RegistrationLink(tournamentId = "t1", token = "abc"), registrationLinkOf("/registration", mapOf("t" to "t1", "k" to "abc")))
        assertEquals(RegistrationLink(tournamentId = "t1"), registrationLinkOf("/registration", mapOf("t" to "t1")))
        assertNull(registrationLinkOf("/registration", emptyMap()))
    }

    @Test
    fun thePagesOfTheSiteAndOtherAddressesAreNotLinks() {
        for (path in listOf("/", "", "/admin", "/admin/", "/support", "/privacy", "/green-book", "/api-docs", "/t/abc", "/a/b/c", "/ab", "/index.html", "/x_y")) {
            assertNull(registrationLinkOf(path, emptyMap()), path)
        }
    }
}
