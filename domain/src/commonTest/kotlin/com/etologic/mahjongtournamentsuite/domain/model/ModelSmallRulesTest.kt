package com.etologic.mahjongtournamentsuite.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The edge cases of small model rules: empty values, missing limits and texts of another shape. */
class ModelSmallRulesTest {
    @Test
    fun aPlayerHasNoPermissionsByDefault() {
        assertEquals(emptySet(), Permission.defaultsOf(GlobalUserRole.PLAYER))
    }

    @Test
    fun aTableKeyNeedsTwoNumbers() {
        assertNull(TableKey.parse("x_2"))
        assertNull(TableKey.parse("1_y"))
        assertNull(TableKey.parse("1"))
        assertEquals(TableKey(1, 2), TableKey.parse("1_2"))
    }

    @Test
    fun withoutBestHandsOfTheTournamentNoHandOfATableIsAmongThem() {
        val table = TournamentTable(roundId = 1, tableId = 1, playerIds = listOf(1, 2, 3, 4), isCompleted = true, useTotalsOnly = false, hasProgress = true, bestHandScores = listOf(40))

        assertEquals(emptyList(), table.tournamentBestHandScores(emptyList()))
        assertEquals(listOf(40), table.tournamentBestHandScores(listOf(50, 40)))
    }

    @Test
    fun theQrLinkUsesThePublicLinkOrTheId() {
        val tournament = Tournament(id = "t1", name = "Open", isTeams = false, numPlayers = 16, numRounds = 4)

        assertEquals("https://site/t/t1/my-table", tournament.myTableQrLink("https://site/"))
        assertEquals("https://site/t/t1/my-table", tournament.copy(registration = RegistrationSettings(slug = "  ")).myTableQrLink("https://site"))
        assertEquals("https://site/open/my-table", tournament.copy(registration = RegistrationSettings(slug = "open")).myTableQrLink("https://site"))
    }

    @Test
    fun aMappedFailureStaysTheSameFailure() {
        val failure: AppResult<Int> = AppResult.Failure(AppError.Network)

        assertEquals(failure, failure.map { it + 1 })
        assertEquals(AppResult.Success(2), AppResult.Success(1).map { it + 1 })
    }

    @Test
    fun registrationsWithoutALimitAreNeverFull() {
        assertFalse(RegistrationSettings(maxPlayers = null, confirmed = 100).isFull)
        assertFalse(RegistrationSettings(maxPlayers = 16, confirmed = 15).isFull)
        assertTrue(RegistrationSettings(maxPlayers = 16, confirmed = 16).isFull)
    }

    @Test
    fun onlyADisqualifiedOrExcludedPlayerIsStruckThrough() {
        assertTrue(PlayerSubstitution(SubstitutionReason.DISQUALIFIED, 1).struckThrough)
        assertTrue(PlayerSubstitution(SubstitutionReason.LATE, 1, excludedFromEma = true).struckThrough)
        assertFalse(PlayerSubstitution(SubstitutionReason.LATE, 1).struckThrough)
    }

    @Test
    fun aVenueWithAnyFieldIsNotEmpty() {
        assertTrue(TournamentVenue().isEmpty)
        assertFalse(TournamentVenue(name = "Casa").isEmpty)
        assertFalse(TournamentVenue(address = "Calle 1").isEmpty)
        assertFalse(TournamentVenue(mapUrl = "https://maps").isEmpty)
        assertFalse(TournamentVenue(contactEmail = "a@b.es").isEmpty)
    }

    @Test
    fun aCardTooBigForTheSheetHasNoGridNorCell() {
        val huge = IdCardSheetLayout(scalePercent = 1000.0)

        assertNull(huge.grid())
        assertNull(huge.cell(0, back = false))
        // Wide enough for one column but too tall for one row.
        assertNull(IdCardSheetLayout(scalePercent = 400.0, landscape = true).grid())
    }

    @Test
    fun theRoundsWithoutRepeatsNeedFullTables() {
        assertEquals(0, RepeatFreeRounds.sure(0, isTeams = false))
        assertEquals(0, RepeatFreeRounds.sure(6, isTeams = false))
        assertEquals(0, RepeatFreeRounds.timesAtMost(16, 0, isTeams = false))
        assertEquals(0, RepeatFreeRounds.timesAtMost(6, 3, isTeams = false))
    }
}
