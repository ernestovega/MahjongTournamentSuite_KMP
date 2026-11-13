package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.runtime.Composable

/**
 * Runs [onBack] on the system back of Android while [enabled], instead of leaving the screen. The desktop and the web
 * have no system back, so there it does nothing: the back button of the top bar does the same.
 */
@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)
