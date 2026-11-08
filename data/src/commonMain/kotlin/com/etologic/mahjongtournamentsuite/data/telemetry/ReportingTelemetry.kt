package com.etologic.mahjongtournamentsuite.data.telemetry

import com.etologic.mahjongtournamentsuite.domain.telemetry.Telemetry
import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryProperty

/**
 * The telemetry that the app uses. It passes everything to the Analytics of the platform, and it sends each handled
 * error also to the error log of the API through [reporter].
 */
class ReportingTelemetry(
    private val analytics: Telemetry,
    private val reporter: ErrorReporter,
) : Telemetry by analytics {
    override fun error(throwable: Throwable, area: String) = reporter.reportHandled(throwable, area)

    override fun userProperty(name: String, value: String?) {
        analytics.userProperty(name, value)
        if (name == TelemetryProperty.ROLE) reporter.setRole(value)
    }

    override fun setConsent(granted: Boolean) {
        analytics.setConsent(granted)
        if (granted) reporter.flushPending()
    }
}
