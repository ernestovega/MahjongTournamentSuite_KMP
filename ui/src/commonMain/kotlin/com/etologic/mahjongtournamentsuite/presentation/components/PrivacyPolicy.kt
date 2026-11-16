package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler

/** The public privacy policy, in every language of the apps. Google Play links it too. */
const val PRIVACY_POLICY_URL = "https://mts.mahjongmadrid.com/privacy/"

/** Opens the privacy policy in the browser. */
@Composable
fun rememberOpenPrivacyPolicy(): () -> Unit {
    val uriHandler = LocalUriHandler.current
    return { runCatching { uriHandler.openUri(PRIVACY_POLICY_URL) } }
}

/** The page with the latest and the published version of the web, the backend and the Android app. */
const val VERSIONS_URL = "https://mts.mahjongmadrid.com/version"

/** Opens the versions page in the browser. */
@Composable
fun rememberOpenVersions(): () -> Unit {
    val uriHandler = LocalUriHandler.current
    return { runCatching { uriHandler.openUri(VERSIONS_URL) } }
}
