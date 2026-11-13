package com.etologic.mahjongtournamentsuite.presentation.screen

import com.etologic.mahjongtournamentsuite.domain.model.TableHand
import com.etologic.mahjongtournamentsuite.domain.model.TablePoints
import com.etologic.mahjongtournamentsuite.domain.model.TableState
import com.etologic.mahjongtournamentsuite.domain.model.seatPoints
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
 * Checks the shared scoring cases in testdata/scoring-cases.json against the table editor. The server tests
 * check the same file, so the scoring of the apps and of the server cannot drift apart.
 */
class ScoringCasesTest {
    private val cases: JsonObject = Json.parseToJsonElement(casesFile().readText()).jsonObject
    private val seats = cases.getValue("seats").jsonArray.map { it.jsonPrimitive.content }
    private val mismatches = mutableListOf<String>()

    /** Collects every case that differs, so one run lists all of them. */
    private fun check(expected: Any?, actual: Any?, name: String) {
        if (expected != actual) mismatches += "$name: expected $expected but was $actual"
    }

    private fun assertNoMismatches() = assertEquals(emptyList(), mismatches, mismatches.joinToString("\n"))

    @Test
    fun handCases() {
        for (entry in cases.getValue("hands").jsonArray.map { it.jsonObject }) {
            val name = entry.text("name")
            val hand = entry.getValue("hand").jsonObject
            check(entry.seatInts("scores"), handResult(hand, withPenalties = true), name)
            val result = if ("result" in entry) entry.seatInts("result") else entry.seatInts("scores")
            check(result, handResult(hand, withPenalties = false), "$name (without penalties)")
        }
        assertNoMismatches()
    }

    @Test
    fun tableCases() {
        for (entry in cases.getValue("tables").jsonArray.map { it.jsonObject }) {
            val name = entry.text("name")
            val state = tableState(entry.getValue("table").jsonObject)
            val hands = entry.getValue("hands").jsonArray.mapIndexed { index, hand -> tableHand(index + 1, hand.jsonObject, true) }
            val editor = TableManagerEditorState.from(state, hands)
            val scores = listOf(editor.displayEastScore, editor.displaySouthScore, editor.displayWestScore, editor.displayNorthScore)
            val points = listOf(editor.displayEastPoints, editor.displaySouthPoints, editor.displayWestPoints, editor.displayNorthPoints)
            check(entry.texts("scores"), scores, "$name: scores")
            check(entry.texts("points"), points, "$name: points")
            if (state.useTotalsOnly) check(entry.texts("points"), state.seatPoints, "$name: saved table points")
        }
        assertNoMismatches()
    }

    @Test
    fun pointsCases() {
        for (entry in cases.getValue("points").jsonArray.map { it.jsonObject }) {
            val scores = entry.texts("scores")!!.map { it.trim().toIntOrNull() }
            val forced = entry["forcedZeroSeat"]?.jsonPrimitive?.int
            check(entry.texts("points"), TablePoints.fromScores(scores, forced), entry.text("name"))
        }
        assertNoMismatches()
    }

    private fun handResult(hand: JsonObject, withPenalties: Boolean): List<Int>? {
        val editor = TableManagerEditorState.from(
            tableState(JsonObject(mapOf("seats" to cases.getValue("seats")))),
            listOf(tableHand(1, hand, withPenalties)),
        )
        val values = editor.handResultScores.single()
        if (values == SeatTextValues.EMPTY) return null
        return listOf(values.east, values.south, values.west, values.north).map { it.toInt() }
    }

    private fun tableState(table: JsonObject): TableState {
        val tableSeats = table.getValue("seats").jsonArray.map { it.jsonPrimitive.content }
        val manual = table.texts("manualScores") ?: List(4) { "" }
        val saved = table.texts("savedScores") ?: List(4) { "" }
        return TableState(
            roundId = 1,
            tableId = 1,
            playerIds = seats.map { it.toInt() },
            playerEastId = tableSeats[0],
            playerSouthId = tableSeats[1],
            playerWestId = tableSeats[2],
            playerNorthId = tableSeats[3],
            playerEastScore = saved[0],
            playerSouthScore = saved[1],
            playerWestScore = saved[2],
            playerNorthScore = saved[3],
            manualPlayerEastScore = manual[0],
            manualPlayerSouthScore = manual[1],
            manualPlayerWestScore = manual[2],
            manualPlayerNorthScore = manual[3],
            isCompleted = table.flag("completed"),
            useTotalsOnly = table.flag("useTotalsOnly"),
            forcedZeroPlayerId = table["forcedZero"]?.jsonPrimitive?.content.orEmpty(),
        )
    }

    private fun tableHand(handId: Int, hand: JsonObject, withPenalties: Boolean): TableHand {
        val penalties = hand.texts("penalties")?.takeIf { withPenalties } ?: List(4) { "" }
        return TableHand(
            handId = handId,
            playerWinnerId = hand["winner"]?.jsonPrimitive?.content.orEmpty(),
            playerLooserId = hand["loser"]?.jsonPrimitive?.content.orEmpty(),
            handScore = hand["score"]?.jsonPrimitive?.content.orEmpty(),
            isChickenHand = hand.flag("chicken"),
            isDone = hand["done"]?.jsonPrimitive?.boolean ?: true,
            playerEastPenalty = penalties[0],
            playerSouthPenalty = penalties[1],
            playerWestPenalty = penalties[2],
            playerNorthPenalty = penalties[3],
        )
    }
}

/** The file sits at the root of the repository. Tests run from the module folder. */
internal fun casesFile(): File = generateSequence(File("").absoluteFile) { it.parentFile }
    .map { File(it, "testdata/scoring-cases.json") }
    .first { it.exists() }

private fun JsonObject.text(key: String): String = getValue(key).jsonPrimitive.content

private fun JsonObject.flag(key: String): Boolean = this[key]?.jsonPrimitive?.boolean ?: false

private fun JsonObject.texts(key: String): List<String>? = this[key]?.takeUnless { it is JsonNull }
    ?.jsonArray?.map { it.jsonPrimitive.content }

private fun JsonObject.seatInts(key: String): List<Int>? = this[key]?.takeUnless { it is JsonNull }
    ?.jsonArray?.map { it.jsonPrimitive.int }
