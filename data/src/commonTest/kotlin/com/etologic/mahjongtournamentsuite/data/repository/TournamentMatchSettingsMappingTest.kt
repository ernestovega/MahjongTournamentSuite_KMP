package com.etologic.mahjongtournamentsuite.data.repository

import com.etologic.mahjongtournamentsuite.data.backend.dto.CreateTournamentRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.TournamentDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.UpdateTournamentSettingsRequestDto
import com.etologic.mahjongtournamentsuite.domain.model.MatchEndMode
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TournamentMatchSettingsMappingTest {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private fun dto(
        minutes: Int = 120,
        mode: String = "STOP",
        extra: Int = 15,
        zone: String? = null,
    ) = TournamentDto(
        id = "t", name = "T", isTeams = false, numPlayers = 4, numRounds = 1,
        matchDurationMinutes = minutes, matchEndMode = mode, matchExtraMinutes = extra, timeZone = zone,
    )

    @Test
    fun mapsTheMatchSettingsToTheDomain() {
        val tournament = dto(minutes = 115, mode = "FINISH_HAND", extra = 10, zone = " Europe/Madrid ").toDomain()

        assertEquals(115, tournament.matchDurationMinutes)
        assertEquals(MatchEndMode.FINISH_HAND, tournament.matchEndMode)
        assertEquals(10, tournament.matchExtraMinutes)
        assertEquals("Europe/Madrid", tournament.timeZone)
    }

    @Test
    fun anUnknownModeAndInvalidMinutesFallBackToTheDefaults() {
        val tournament = dto(minutes = 0, mode = "LATER", extra = -3, zone = "  ").toDomain()

        assertEquals(120, tournament.matchDurationMinutes)
        assertEquals(MatchEndMode.STOP, tournament.matchEndMode)
        assertEquals(15, tournament.matchExtraMinutes)
        assertNull(tournament.timeZone)
    }

    @Test
    fun aResponseFromAnOldServerGetsTheDefaults() {
        val old = json.decodeFromString<TournamentDto>(
            """{"id":"t","name":"T","isTeams":false,"numPlayers":4,"numRounds":1}""",
        ).toDomain()

        assertEquals(120, old.matchDurationMinutes)
        assertEquals(MatchEndMode.STOP, old.matchEndMode)
        assertEquals(15, old.matchExtraMinutes)
        assertNull(old.timeZone)
    }

    @Test
    fun theUpdateRequestSendsOnlyTheMatchSettingsThatItHas() {
        fun update(minutes: Int?, mode: String?, extra: Int?, zone: String?) = UpdateTournamentSettingsRequestDto(
            name = "T", shortName = "T", primaryColor = "#123456", eventStartDate = "2026-10-03", eventEndDate = "2026-10-04",
            hostCountry = "ES", hostCity = "Madrid",
            matchDurationMinutes = minutes, matchEndMode = mode, matchExtraMinutes = extra, timeZone = zone,
        )

        val sparse = json.encodeToString(update(null, null, null, null))
        val full = json.encodeToString(update(100, "FINISH_HAND", 12, "Europe/Lisbon"))

        assertEquals(false, sparse.contains("match"))
        assertEquals(false, sparse.contains("timeZone"))
        assertEquals(true, full.contains(""""matchDurationMinutes":100"""))
        assertEquals(true, full.contains(""""matchEndMode":"FINISH_HAND""""))
        assertEquals(true, full.contains(""""matchExtraMinutes":12"""))
        assertEquals(true, full.contains(""""timeZone":"Europe/Lisbon""""))
    }

    @Test
    fun theCreateRequestKeepsTheDefaultsWhenTheyAreNotSent() {
        val request = CreateTournamentRequestDto(
            name = "T", eventStartDate = "2026-10-03", eventEndDate = "2026-10-04", isTeams = false, numPlayers = 4,
            numRounds = 1, numTries = 0, players = emptyList(), tables = emptyList(), shortName = "T",
            primaryColor = "#123456", hostCountry = "ES", hostCity = "Madrid",
        )

        assertEquals(120, request.matchDurationMinutes)
        assertEquals("STOP", request.matchEndMode)
        assertEquals(15, request.matchExtraMinutes)
        assertNull(request.timeZone)
    }
}
