package com.etologic.mahjongtournamentsuite.data.telemetry

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class TelemetryJsonTest {
    @Test
    fun numbersStayNumbersAndTheRestBecomesText() {
        val json = mapOf<String, Any>("seconds" to 12, "ratio" to 0.5, "ok" to true, "action" to "save_table").toJsonObject()

        assertEquals(JsonPrimitive(12), json["seconds"])
        assertEquals(JsonPrimitive(0.5), json["ratio"])
        // GA4 has no boolean parameters, so a boolean goes as the text "true" or "false".
        assertEquals(JsonPrimitive("true"), json["ok"])
        assertEquals(JsonPrimitive("save_table"), json["action"])
    }

    @Test
    fun longTextIsCutToTheLimitOfGa4() {
        val json = mapOf<String, Any>("message" to "x".repeat(150)).toJsonObject()

        assertEquals(100, json["message"]!!.jsonPrimitive.content.length)
    }
}
