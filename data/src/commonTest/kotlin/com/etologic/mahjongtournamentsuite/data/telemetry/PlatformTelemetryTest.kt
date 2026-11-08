package com.etologic.mahjongtournamentsuite.data.telemetry

import com.etologic.mahjongtournamentsuite.domain.AppVersion
import com.etologic.mahjongtournamentsuite.domain.telemetry.NoTelemetry
import kotlin.test.Test
import kotlin.test.assertEquals

class PlatformTelemetryTest {
    private fun platform(app: String, platform: String) = PlatformTelemetry(NoTelemetry, app, platform, "prod", "en", null)

    @Test
    fun eachProductReportsItsOwnVersion() {
        assertEquals(AppVersion.web, platform("admin", "web").appVersion)
        assertEquals(AppVersion.web, platform("player", "web").appVersion)
        assertEquals(AppVersion.admin, platform("admin", "android").appVersion)
        assertEquals(AppVersion.admin, platform("admin", "desktop").appVersion)
        assertEquals(AppVersion.player, platform("player", "android").appVersion)
    }
}
