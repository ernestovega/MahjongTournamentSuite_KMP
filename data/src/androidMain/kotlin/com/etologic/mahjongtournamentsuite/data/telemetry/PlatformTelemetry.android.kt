package com.etologic.mahjongtournamentsuite.data.telemetry

import com.etologic.mahjongtournamentsuite.data.platform.AndroidPlatformContext
import com.etologic.mahjongtournamentsuite.domain.telemetry.Telemetry

actual fun providePlatformTelemetry(): Telemetry = createAndroidTelemetry(AndroidPlatformContext.requireContext())
