package com.etologic.mahjongtournamentsuite.presentation.util

import kotlinx.datetime.TimeZone
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class DataRevisionTest {
    private val utc = TimeZone.UTC
    // 2026-10-11 15:30:12 UTC
    private val moment = Instant.parse("2026-10-11T15:30:12Z")

    @Test
    fun theRevisionIsTheSumOfAllTableVersions() {
        assertEquals(0, tournamentDataRevision(emptyList()))
        assertEquals(10, tournamentDataRevision(listOf(0L, 3L, 7L)))
    }

    @Test
    fun everySaveRaisesTheRevisionByOne() {
        assertEquals(tournamentDataRevision(listOf(2L, 5L)) + 1, tournamentDataRevision(listOf(2L, 6L)))
    }

    @Test
    fun theLabelHasAnRAndHandlesAnUnknownRevision() {
        assertEquals("r123", dataRevisionLabel(123))
        assertEquals("r0", dataRevisionLabel(0))
        assertEquals("", dataRevisionLabel(null))
    }

    @Test
    fun theFileStampHasNoSeparatorThatAFileSystemRefuses() {
        assertEquals("20261011-1530", fileStamp(moment, utc))
    }

    @Test
    fun theFileStampPadsSingleDigitsAndSortsInTimeOrder() {
        val early = Instant.parse("2026-01-02T03:04:05Z")
        assertEquals("20260102-0304", fileStamp(early, utc))
        assertTrue(fileStamp(early, utc) < fileStamp(moment, utc))
    }

    @Test
    fun theFileStampUsesTheGivenTimeZone() {
        assertEquals("20261011-1730", fileStamp(moment, TimeZone.of("UTC+02:00")))
    }

    @Test
    fun theClockLabelShowsSeconds() {
        assertEquals("15:30:12", clockLabel(moment, utc))
        assertEquals("03:04:05", clockLabel(Instant.parse("2026-01-02T03:04:05Z"), utc))
    }

    @Test
    fun theStatusLabelShowsTheRevisionAndTheTime() = runTest {
        assertEquals("Results r42 · checked 15:30:12", dataStatus(42, moment, zone = utc).render())
        assertEquals("Results r42 · loaded 15:30:12", dataStatus(42, moment, loaded = true, zone = utc).render())
    }

    @Test
    fun theStatusLabelSkipsWhatIsUnknown() = runTest {
        assertEquals("Results r42", dataStatus(42, null, zone = utc).render())
        assertEquals("checked 15:30:12", dataStatus(null, moment, zone = utc).render())
        assertEquals("", dataStatus(null, null, zone = utc).render())
    }

    @Test
    fun aReportNameHasTheKindTheRevisionAndTheTime() {
        assertEquals(
            "Madrid-Open-ema-report-r123-20261011-1530.xlsx",
            generatedFileName("Madrid Open", "ema-report", "xlsx", moment, revision = 123, zone = utc),
        )
    }

    @Test
    fun aFileThatDoesNotDependOnTheResultsHasNoRevision() {
        assertEquals(
            "MO-2026-id-cards-20261011-1530.pdf",
            generatedFileName("MO", "id-cards", "pdf", moment, year = "2026", zone = utc),
        )
    }

    @Test
    fun unsafeCharactersBecomeDashes() {
        assertEquals(
            "A-B-C-ema-report-r1-20261011-1530.xlsx",
            generatedFileName("A/B: C?", "ema-report", "xlsx", moment, revision = 1, zone = utc),
        )
    }

    @Test
    fun anEmptyNameFallsBackToTournament() {
        assertEquals("tournament-id-list-20261011-1530.xlsx", generatedFileName("///", "id-list", "xlsx", moment, zone = utc))
        assertEquals("tournament-id-list-20261011-1530.xlsx", generatedFileName("", "id-list", "xlsx", moment, zone = utc))
    }

    @Test
    fun twoReportsOfTheSameDataDifferOnlyByTime() {
        val first = generatedFileName("Open", "ema-report", "xlsx", moment, revision = 9, zone = utc)
        val second = generatedFileName("Open", "ema-report", "xlsx", Instant.parse("2026-10-11T16:00:00Z"), revision = 9, zone = utc)

        assertTrue(first != second)
        assertTrue(first.contains("-r9-") && second.contains("-r9-"))
    }
}
