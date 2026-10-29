package com.etologic.mahjongtournamentsuite.data.telemetry

import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryParam
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

/** The parameters of the `app_error` event: the area of the action and the type of the error. */
internal fun errorParams(throwable: Throwable, area: String): Map<String, Any> = mapOf(
    TelemetryParam.ACTION to area.take(MAX_VALUE_LENGTH),
    TelemetryParam.KIND to (throwable::class.simpleName ?: "Error"),
)
