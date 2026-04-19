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
            handScore = "16",
        )

        val encoded = json.encodeToString(payload)

        assertEquals(
            """{"playerWinnerId":"12","handScore":"16","isChickenHand":false}""",
            encoded,
        )
    }
}
