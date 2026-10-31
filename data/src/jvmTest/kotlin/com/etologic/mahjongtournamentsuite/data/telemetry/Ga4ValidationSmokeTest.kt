package com.etologic.mahjongtournamentsuite.data.telemetry

import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryAction
import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryEvent
import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryOutcome
import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryParam
import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryProperty
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Asks Google if GA4 accepts every event of the app. It uses the validation address of the Measurement Protocol,
 * which checks a message and stores nothing. It needs the network and the settings of the data stream, so it runs
 * only when both variables are set:
 *
 *     MTS_GA4_MEASUREMENT_ID=G-... MTS_GA4_API_SECRET=... ./gradlew :data:jvmTest --tests '*Ga4ValidationSmokeTest*'
 *
 * Without the variables the test does nothing. A pass means that the format is valid. It does not mean that GA4
 * counted the event: only DebugView or the BigQuery export shows that.
 */
class Ga4ValidationSmokeTest {
    private val measurementId = System.getenv("MTS_GA4_MEASUREMENT_ID").orEmpty()
    private val apiSecret = System.getenv("MTS_GA4_API_SECRET").orEmpty()

    private val properties = mapOf(
        TelemetryProperty.APP to "admin",
        TelemetryProperty.PLATFORM to "desktop",
        TelemetryProperty.ENVIRONMENT to "dev",
        TelemetryProperty.ROLE to "admin",
        TelemetryProperty.LANGUAGE to "es",
    )

    /** A realistic set of parameters for each event of the app. */
    private val examples: Map<String, Map<String, Any>> = mapOf(
        TelemetryEvent.ADMIN_ACTION to mapOf(TelemetryParam.ACTION to TelemetryAction.TEAM_UPDATED, TelemetryParam.OUTCOME to TelemetryOutcome.FAILED),
        TelemetryEvent.ACTION_FAILED to mapOf(TelemetryParam.ACTION to "Updating tournament settings failed.", TelemetryParam.REASON to "network"),
        TelemetryEvent.APP_ERROR to mapOf(
            TelemetryParam.REPORT_ID to "abc123def456",
            TelemetryParam.ACTION to "Saving table state failed.",
            TelemetryParam.KIND to "IllegalStateException",
            TelemetryParam.REASON to "unexpected",
        ),
        TelemetryEvent.APP_CRASH to mapOf(TelemetryParam.REPORT_ID to "abc123def456", TelemetryParam.KIND to "OutOfMemoryError"),
        TelemetryEvent.TOURNAMENT_CREATED to mapOf(TelemetryParam.PLAYERS to 16, TelemetryParam.ROUNDS to 4, TelemetryParam.IS_TEAMS to false),
        TelemetryEvent.TABLE_SAVED to mapOf(TelemetryParam.HANDS to 8),
        TelemetryEvent.PLAYERS_IMPORTED to mapOf(TelemetryParam.FORMAT to "xlsx"),
        TelemetryEvent.PLAYERS_EXPORTED to mapOf(TelemetryParam.FORMAT to "csv"),
        TelemetryEvent.ACCOUNTS_SENT to mapOf(TelemetryParam.PLAYERS to 12),
        TelemetryEvent.STATISTICS_VIEWED to mapOf(TelemetryParam.SCOPE to "overview"),
        TelemetryEvent.DIALOG_VIEW to mapOf(TelemetryParam.DIALOG to "EditTournament", TelemetryParam.SCREEN to "Tournaments"),
        TelemetryEvent.TAB_VIEW to mapOf(TelemetryParam.TAB to "Players", TelemetryParam.SCREEN to "Statistics"),
        TelemetryEvent.VIEW_TIME to mapOf(
            TelemetryParam.KIND to "screen",
            TelemetryParam.VIEW to "Tables",
            TelemetryParam.SCREEN to "Tables",
            TelemetryParam.SECONDS to 42,
        ),
        TelemetryEvent.PASSWORD_RESET_REQUESTED to mapOf(TelemetryParam.KIND to "bulk", TelemetryParam.PLAYERS to 3),
        TelemetryEvent.PENALTY_ADDED to mapOf(TelemetryParam.HANDS to 2),
    )

    @Test
    fun googleAcceptsEveryEventOfTheApp() {
        if (measurementId.isBlank() || apiSecret.isBlank()) return
        val client = HttpClient.newHttpClient()
        // Every event of the app is checked, with its own parameters or with none.
        val problems = TelemetryEvent.all.mapNotNull { event ->
            val body = measurementBody("test-client", properties, System.currentTimeMillis() / 1000, event, examples[event].orEmpty())
            val request = HttpRequest.newBuilder(
                URI("https://www.google-analytics.com/debug/mp/collect?measurement_id=$measurementId&api_secret=$apiSecret"),
            ).POST(HttpRequest.BodyPublishers.ofString(body)).build()
            val answer = Json.parseToJsonElement(client.send(request, HttpResponse.BodyHandlers.ofString()).body()).jsonObject
            val messages = answer["validationMessages"]?.jsonArray.orEmpty()
            if (messages.isEmpty()) null else "$event: $messages"
        }
        assertEquals(emptyList(), problems)
    }
}
