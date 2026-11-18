package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.runtime.Composable
import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryLink
import com.etologic.mahjongtournamentsuite.presentation.telemetry.rememberOpenLink

/** The public privacy policy, in every language of the apps. Google Play links it too. */
const val PRIVACY_POLICY_URL = "https://mts.mahjongmadrid.com/privacy/"

/** Opens the privacy policy in the browser. */
@Composable
fun rememberOpenPrivacyPolicy(): () -> Unit {
    val open = rememberOpenLink(TelemetryLink.PRIVACY_POLICY)
    return { open(PRIVACY_POLICY_URL) }
}

/** The page with the latest and the published version of the web, the backend and the Android app. */
const val VERSIONS_URL = "https://mts.mahjongmadrid.com/version"

/** Opens the versions page in the browser. */
@Composable
fun rememberOpenVersions(): () -> Unit {
    val open = rememberOpenLink(TelemetryLink.VERSIONS)
    return { open(VERSIONS_URL) }
}
