package com.etologic.mahjongtournamentsuite.data.telemetry

import com.etologic.mahjongtournamentsuite.domain.telemetry.Telemetry

/**
 * The telemetry of this platform. It sends nothing until the Firebase settings of the platform are filled in.
 * See docs/telemetry.md.
 */
expect fun providePlatformTelemetry(): Telemetry
