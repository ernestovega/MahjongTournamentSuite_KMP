package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.ui.graphics.Color
import com.etologic.mahjongtournamentsuite.domain.model.TournamentVenue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** The rules of the fields of a tournament (create and edit): venue, contact email, color and the size of a logo. */
class TournamentFormRulesTest {
    @Test
    fun theVenueNeedsAMapLinkOrANameAndAnAddress() {
        assertNotNull(venueRequiredError(TournamentVenue()))
        assertNotNull(venueRequiredError(TournamentVenue(name = "Hotel")))
        assertNotNull(venueRequiredError(TournamentVenue(address = "Calle Mayor 1")))
        assertNotNull(venueRequiredError(TournamentVenue(name = "  ", address = "Calle Mayor 1")))
        assertNull(venueRequiredError(TournamentVenue(name = "Hotel", address = "Calle Mayor 1")))
        assertNull(venueRequiredError(TournamentVenue(mapUrl = "https://maps.app.goo.gl/abc")))
    }

    @Test
    fun theContactEmailIsRequiredAndMustLookLikeAnEmail() {
        assertNotNull(contactEmailRequiredError(""))
        assertNotNull(contactEmailRequiredError("   "))
        assertNotNull(contactEmailRequiredError("organizer"))
        assertNotNull(contactEmailRequiredError("organizer@club"))
        assertNotNull(contactEmailRequiredError("orga nizer@club.es"))
        assertNull(contactEmailRequiredError("organizer@club.es"))
        assertNull(contactEmailRequiredError("  organizer@club.es  "), "the spaces around are not part of the email")
        // The required and the invalid errors are different texts.
        assertEquals(false, contactEmailRequiredError("") == contactEmailRequiredError("organizer"))
    }

    @Test
    fun theSizeOfALogoReadsInBytesKilobytesOrMegabytes() {
        assertEquals("0 B", formatByteSize(0))
        assertEquals("1023 B", formatByteSize(1_023))
        assertEquals("1 KB", formatByteSize(1_024))
        assertEquals("2 KB", formatByteSize(1_536), "rounded to the nearest kilobyte")
        assertEquals("1024 KB", formatByteSize(1_048_575))
        assertEquals("1.0 MB", formatByteSize(1_048_576))
        assertEquals("1.5 MB", formatByteSize(1_572_864))
        assertEquals("8.0 MB", formatByteSize(8L * 1_048_576))
    }

    @Test
    fun theColorOfATournamentIsAHexColorOrNothing() {
        assertEquals(Color(0xFF123456), "#123456".toTournamentColorOrNull())
        assertNull("".toTournamentColorOrNull())
        assertNull("blue".toTournamentColorOrNull())
        assertNull("#12345".toTournamentColorOrNull())
    }
}
