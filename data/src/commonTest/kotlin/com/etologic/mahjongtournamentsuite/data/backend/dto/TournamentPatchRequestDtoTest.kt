package com.etologic.mahjongtournamentsuite.data.backend.dto

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class TournamentPatchRequestDtoTest {

    private val json = Json {
        explicitNulls = false
    }

    @Test
    fun serializesSparseTablePatchWithBooleanAndStringFields() {
        val payload = TablePatchRequestDto(
            useTotalsOnly = true,
            playerEastScore = "32000",
            playerSouthPoints = "2",
            manualPlayerEastScore = "33000",
            manualPlayerWestPoints = "1",
        )

        val encoded = json.encodeToString(payload)

        assertEquals(
            """{"useTotalsOnly":true,"playerEastScore":"32000","playerSouthPoints":"2","manualPlayerEastScore":"33000","manualPlayerWestPoints":"1"}""",
            encoded,
        )
    }

    @Test
    fun serializesSparseHandPatchWithBooleanAndStringFields() {
        val payload = HandPatchRequestDto(
            playerWinnerId = "12",
            isChickenHand = false,
            isDone = true,
            handScore = "16",
        )

        val encoded = json.encodeToString(payload)

        assertEquals(
            """{"playerWinnerId":"12","handScore":"16","isChickenHand":false,"isDone":true}""",
            encoded,
        )
    }

    @Test
    fun serializesTournamentSettingsWithEditableDates() {
        val payload = UpdateTournamentSettingsRequestDto(
            name = "European Championship",
            shortName = "EC2026",
            primaryColor = "#123456",
            eventStartDate = "2026-10-03",
            eventEndDate = "2026-10-04",
            hostCountry = "ESP",
            hostCity = "Madrid",
            roundSchedules = listOf(
                TournamentRoundScheduleDto(1, "2026-10-03", "09:30"),
                TournamentRoundScheduleDto(2),
            ),
            agendaItems = listOf(
                TournamentAgendaItemDto("Registration", "2026-10-03", "08:30", "09:00"),
            ),
        )

        val encoded = json.encodeToString(payload)

        assertEquals(
            """{"name":"European Championship","shortName":"EC2026","primaryColor":"#123456","eventStartDate":"2026-10-03","eventEndDate":"2026-10-04","hostCountry":"ESP","hostCity":"Madrid","roundSchedules":[{"roundId":1,"date":"2026-10-03","startTime":"09:30"},{"roundId":2}],"agendaItems":[{"title":"Registration","date":"2026-10-03","startTime":"08:30","endTime":"09:00"}]}""",
            encoded,
        )
    }

    @Test
    fun serializesReusableTournamentLogoSource() {
        val payload = UpdateTournamentSettingsRequestDto(
            name = "European Championship",
            shortName = "EC2026",
            primaryColor = "#123456",
            eventStartDate = "2026-10-03",
            eventEndDate = "2026-10-04",
            hostCountry = "ESP",
            hostCity = "Madrid",
            associationLogoSourceTournamentId = "source-tournament",
        )

        val encoded = json.encodeToString(payload)

        assertEquals(
            """{"name":"European Championship","shortName":"EC2026","primaryColor":"#123456","eventStartDate":"2026-10-03","eventEndDate":"2026-10-04","hostCountry":"ESP","hostCity":"Madrid","associationLogoSourceTournamentId":"source-tournament"}""",
            encoded,
        )
    }

    @Test
    fun serializesEmptyScheduleListsSoExistingValuesCanBeCleared() {
        val payload = UpdateTournamentSettingsRequestDto(
            name = "European Championship",
            shortName = "EC2026",
            primaryColor = "#123456",
            eventStartDate = "2026-10-03",
            eventEndDate = "2026-10-04",
            hostCountry = "ESP",
            hostCity = "Madrid",
            roundSchedules = emptyList(),
            agendaItems = emptyList(),
        )

        val encoded = json.encodeToString(payload)

        assertEquals(true, encoded.contains("\"roundSchedules\":[]"))
        assertEquals(true, encoded.contains("\"agendaItems\":[]"))
    }
}
