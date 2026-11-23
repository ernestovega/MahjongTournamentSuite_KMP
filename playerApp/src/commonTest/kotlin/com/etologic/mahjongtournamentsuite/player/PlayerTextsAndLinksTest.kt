package com.etologic.mahjongtournamentsuite.player

import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.TournamentAgendaItem
import com.etologic.mahjongtournamentsuite.domain.model.TournamentRoundSchedule
import com.etologic.mahjongtournamentsuite.domain.model.TournamentVenue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Small texts and links of the player app: the image of My results, the clock of a round, the map of the venue. */
class PlayerTextsAndLinksTest {
    private val tournament = Tournament(id = "t", name = "Open", isTeams = false, numPlayers = 16, numRounds = 4)

    @Test
    fun theImageOfTheResultsIsNamedAfterTheTournamentAndThePlayerWithSafeCharacters() {
        assertEquals("Madrid-Open-2026-Lucía-Martín.png", summaryFileName("Madrid Open 2026", "Lucía Martín"))
        // Slashes, dots and quotes never reach the file system, and the dashes never repeat or lead.
        assertEquals("6th-MMC-O-Brien-J.png", summaryFileName("6th MMC", "O'Brien, J."))
        assertEquals("results.png", summaryFileName("", "  "))
        assertEquals("results.png", summaryFileName("///", "..."))
        // A long name is cut, so the share sheet and the download keep it.
        assertEquals(80 + ".png".length, summaryFileName("A".repeat(60), "B".repeat(60)).length)
    }

    @Test
    fun theClockOfARoundRoundsUpAndNeverGoesBelowZero() {
        assertEquals("0:00:00", formatClock(0))
        assertEquals("0:00:00", formatClock(-5_000))
        assertEquals("0:00:01", formatClock(1))
        assertEquals("0:00:01", formatClock(1_000))
        assertEquals("0:01:00", formatClock(59_001))
        assertEquals("2:00:00", formatClock(2 * 3_600_000L))
        assertEquals("1:05:09", formatClock((3_600 + 5 * 60 + 9) * 1_000L))
    }

    @Test
    fun theVenueOpensTheMapOfTheOrganizersOrDirectionsToItsNameAndAddress() {
        val withMap = tournament.copy(venue = TournamentVenue(name = "Hotel", mapUrl = "https://maps.app.goo.gl/abc"))
        assertEquals("https://maps.app.goo.gl/abc", venueMapLink(withMap))

        val withAddress = tournament.copy(hostCity = "Madrid", venue = TournamentVenue(name = "Hotel Ñ & Co", address = "Calle Mayor 1"))
        val expected = "https://www.google.com/maps/dir/?api=1&destination=Hotel%20%C3%91%20%26%20Co%2C%20Calle%20Mayor%201%2C%20Madrid"
        assertEquals(expected, venueMapLink(withAddress))
        // The Directions button always asks for directions, even when the organizers gave a map link.
        assertEquals(
            "https://www.google.com/maps/dir/?api=1&destination=Hotel",
            venueDirectionsLink(withMap),
        )
        assertEquals(expected, venueDirectionsLink(withAddress))
    }

    @Test
    fun aVenueWithoutNameAndAddressHasNoLinkEvenWithACity() {
        val onlyCity = tournament.copy(hostCity = "Madrid")
        assertNull(venueMapLink(onlyCity))
        assertNull(venueDirectionsLink(onlyCity))
    }

    @Test
    fun theScheduleShowsWhenARoundHasADateOrATimeOrThereIsAnEvent() {
        assertFalse(hasSchedule(tournament))
        assertFalse(hasSchedule(tournament.copy(roundSchedules = listOf(TournamentRoundSchedule(roundId = 1)))))
        assertTrue(hasSchedule(tournament.copy(roundSchedules = listOf(TournamentRoundSchedule(roundId = 1, startTime = "10:00")))))
        assertTrue(hasSchedule(tournament.copy(roundSchedules = listOf(TournamentRoundSchedule(roundId = 2, date = "2027-02-06")))))
        assertTrue(hasSchedule(tournament.copy(agendaItems = listOf(TournamentAgendaItem("Dinner")))))
    }

    @Test
    fun theStatusOfATournamentComesFromItsDates() {
        assertNull(statusOf(tournament), "no dates, no status")
        assertEquals(TournamentStatus.FINISHED, statusOf(tournament.copy(isCompleted = true)))
        assertEquals(TournamentStatus.UPCOMING, statusOf(tournament.copy(eventStartDate = "2099-01-01", eventEndDate = "2099-01-02")))
        assertEquals(TournamentStatus.FINISHED, statusOf(tournament.copy(eventStartDate = "2001-01-01", eventEndDate = "2001-01-02")))
        assertEquals(TournamentStatus.IN_PROGRESS, statusOf(tournament.copy(eventStartDate = "2001-01-01", eventEndDate = "2099-01-02")))
        // Without an end date, the tournament lasts its first day: a past start is finished.
        assertEquals(TournamentStatus.FINISHED, statusOf(tournament.copy(eventStartDate = "2001-01-01")))
        // A date that does not parse is no date.
        assertNull(statusOf(tournament.copy(eventStartDate = "soon")))
    }
}
