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

    @Test
    fun theQrOfATournamentOpensMyTable() {
        assertEquals(RegistrationLink(slug = "6thmmc", myTable = true), registrationLinkOf("/6thmmc/my-table", emptyMap()))
        assertEquals(RegistrationLink(slug = "6thmmc", myTable = true), registrationLinkOf("/6thMMC/mesa/", emptyMap()))
        assertEquals(RegistrationLink(tournamentId = "AbC123", myTable = true), registrationLinkOf("/t/AbC123/my-table", emptyMap()))
        // The first links had the number of a table. They open the same.
        assertEquals(RegistrationLink(slug = "6thmmc", myTable = true), registrationLinkOf("/6thmmc/table/5", emptyMap()))
        assertEquals(RegistrationLink(tournamentId = "AbC123", myTable = true), registrationLinkOf("/t/AbC123/table/3", emptyMap()))
        for (path in listOf("/admin/my-table", "/t/my-table", "/a/b/my-table", "/ab/my-table", "/6thmmc/other")) {
            assertNull(registrationLinkOf(path, emptyMap()), path)
        }
    }
}
