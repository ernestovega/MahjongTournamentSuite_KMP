package com.etologic.mahjongtournamentsuite.data.repository

import com.etologic.mahjongtournamentsuite.data.backend.dto.DataVersionsManifestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.RoundControlDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.TableControlDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.TournamentDto
import com.etologic.mahjongtournamentsuite.domain.model.ControlMode
import com.etologic.mahjongtournamentsuite.domain.model.OrganizationType
import com.etologic.mahjongtournamentsuite.domain.model.RegistrationPhase
import com.etologic.mahjongtournamentsuite.domain.model.RoundClockAction
import com.etologic.mahjongtournamentsuite.domain.model.RoundControlChange
import com.etologic.mahjongtournamentsuite.domain.model.TableKey
import com.etologic.mahjongtournamentsuite.domain.model.TournamentManifest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** How a tournament, its organizers, its round controls and the clock buttons go between the server and the app. */
class TournamentMappersTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun theOrganizersKeepTheirTypeAndABlankLogoIsNone() {
        val tournament = json.decodeFromString<TournamentDto>(
            """{"id":"t1","name":"Madrid Open","shortName":"MO","isTeams":false,"numPlayers":4,"numRounds":1,"hostCountry":"ES",
            "organizers":[{"id":"f","name":"Federation","type":"FEDERATION","logoUrl":" https://logo "},
                          {"id":"c","name":"Club","type":"SOMETHING_NEW","logoUrl":"  "}],
            "registration":{"status":"CLOSED","slug":"mo"}}""",
        ).toDomain()

        assertEquals(listOf(OrganizationType.FEDERATION, OrganizationType.CLUB), tournament.organizers.map { it.type })
        assertEquals("https://logo", tournament.organizers[0].logoUrl)
        assertNull(tournament.organizers[1].logoUrl)
        assertEquals(RegistrationPhase.CLOSED, tournament.registration?.status)
    }

    @Test
    fun aTournamentWithoutRegistrationsHasNone() {
        val tournament = json.decodeFromString<TournamentDto>(
            """{"id":"t1","name":"Madrid Open","shortName":"MO","isTeams":false,"numPlayers":4,"numRounds":1,"hostCountry":"ES"}""",
        ).toDomain()

        assertNull(tournament.registration)
    }

    @Test
    fun theControlsOfARoundReadEachMode() {
        val control = RoundControlDto(
            roundId = 2,
            hands = "open",
            seats = "closed",
            tables = listOf(TableControlDto(4, "auto"), TableControlDto(5, "from the future")),
        ).toDomain()

        assertEquals(ControlMode.OPEN, control.hands)
        assertEquals(ControlMode.CLOSED, control.seats)
        assertEquals(mapOf(4 to ControlMode.AUTO, 5 to ControlMode.AUTO), control.tables, "an unknown mode is automatic")
    }

    @Test
    fun aControlChangeSendsOnlyWhatChanges() {
        val one = RoundControlChange(table = 4 to ControlMode.CLOSED, reopenTables = listOf(5)).toDto()
        assertNull(one.hands)
        assertEquals(TableControlDto(4, "closed"), one.table)
        assertEquals(listOf(5), one.reopenTables)
        assertNull(one.completeTables)

        val none = RoundControlChange(seats = ControlMode.OPEN).toDto()
        assertEquals("open", none.seats)
        assertNull(none.table)
        assertNull(none.reopenTables, "no list is sent instead of an empty one")
    }

    @Test
    fun eachClockButtonHasItsAction() {
        assertEquals("start", RoundClockAction.Start(agoSeconds = 5).toDto().action)
        assertNull(RoundClockAction.Start().toDto().closePrevious, "closing the round before is sent only when asked")
        assertEquals(true, RoundClockAction.Start(closePrevious = true).toDto().closePrevious)
        assertEquals(30L, RoundClockAction.Pause(agoSeconds = 30).toDto().agoSeconds)
        assertEquals("resume", RoundClockAction.Resume().toDto().action)
        assertEquals(true, RoundClockAction.Reset(force = true).toDto().force)
        assertEquals(600L, RoundClockAction.SetRemaining(600).toDto().remainingSeconds)
        assertEquals("stop", RoundClockAction.Stop.toDto().action)
    }

    @Test
    fun aManifestOfAnOldServerHasEveryRevisionAtZeroAndSkipsUnknownTableKeys() {
        val manifest = DataVersionsManifestDto(tableVersions = mapOf("1_2" to 3L, "not-a-table" to 9L)).toDomain()

        assertEquals(TournamentManifest(tablesRevision = 0, tableVersions = mapOf(TableKey(1, 2) to 3L)), manifest)
        assertEquals(0L, DataVersionsManifestDto().drawRevision())
    }
}
