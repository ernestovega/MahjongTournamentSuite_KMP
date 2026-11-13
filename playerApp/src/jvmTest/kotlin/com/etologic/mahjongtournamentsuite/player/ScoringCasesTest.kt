package com.etologic.mahjongtournamentsuite.player

import com.etologic.mahjongtournamentsuite.domain.model.TableHand
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Checks the hand details of the player app against the shared scoring cases in testdata/scoring-cases.json.
 * The server and the admin app check the same file. The details show the result of a hand apart from its penalties.
 */
class ScoringCasesTest {
    @Test
    fun handResultsMatchTheSharedCases() {
        val cases = Json.parseToJsonElement(casesFile().readText()).jsonObject
        val seats = cases.getValue("seats").jsonArray.map { it.jsonPrimitive.int }
        val mismatches = mutableListOf<String>()
        for (entry in cases.getValue("hands").jsonArray.map { it.jsonObject }) {
            val hand = entry.getValue("hand").jsonObject
            val expected = (if ("result" in entry) entry["result"] else entry["scores"])
                ?.takeUnless { it is JsonNull }?.jsonArray?.map { it.jsonPrimitive.int }
            val actual = tableHand(hand).resultChange(seats)
            if (expected != actual) mismatches += "${entry.getValue("name").jsonPrimitive.content}: expected $expected but was $actual"
        }
        assertEquals(emptyList(), mismatches, mismatches.joinToString("\n"))
    }

    private fun tableHand(hand: JsonObject): TableHand {
        val penalties = hand["penalties"]?.jsonArray?.map { it.jsonPrimitive.content } ?: List(4) { "" }
        return TableHand(
            handId = 1,
            playerWinnerId = hand["winner"]?.jsonPrimitive?.content.orEmpty(),
            playerLooserId = hand["loser"]?.jsonPrimitive?.content.orEmpty(),
            handScore = hand["score"]?.jsonPrimitive?.content.orEmpty(),
            isChickenHand = hand["chicken"]?.jsonPrimitive?.boolean ?: false,
            isDone = true,
            playerEastPenalty = penalties[0],
            playerSouthPenalty = penalties[1],
            playerWestPenalty = penalties[2],
            playerNorthPenalty = penalties[3],
        )
    }

    /** The file sits at the root of the repository. Tests run from the module folder. */
    private fun casesFile(): File = generateSequence(File("").absoluteFile) { it.parentFile }
        .map { File(it, "testdata/scoring-cases.json") }
        .first { it.exists() }
}
