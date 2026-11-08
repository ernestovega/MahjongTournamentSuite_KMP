package com.etologic.mahjongtournamentsuite.data.telemetry

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private const val MAX_VALUE_LENGTH = 100

/** The parameters of an event as a JSON object. The web and the desktop send them as JSON. */
internal fun Map<String, Any>.toJsonObject(): JsonObject = JsonObject(
    mapValues { (_, value) ->
        when (value) {
            is Number -> JsonPrimitive(value)
            is Boolean -> JsonPrimitive(value.toString())
            else -> JsonPrimitive(value.toString().take(MAX_VALUE_LENGTH))
        }
    },
)
