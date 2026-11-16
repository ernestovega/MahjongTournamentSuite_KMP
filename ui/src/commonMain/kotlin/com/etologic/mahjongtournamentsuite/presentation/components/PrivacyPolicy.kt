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
