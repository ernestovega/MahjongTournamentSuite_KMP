package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.runtime.Composable

/**
 * Runs [onBack] on the back while [enabled], instead of leaving the screen: the system back of Android, and on the web
 * the browser back and the back gesture of a phone (see `BackLayerEffect`). The desktop and iOS have no system back, so
 * there it does nothing: the back button of the top bar does the same.
 */
@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)
