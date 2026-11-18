package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.runtime.Composable

/**
 * Whether the person asked the device for less motion: the animator scale set to off on Android, or
 * "prefers-reduced-motion" in the browser. Screens skip their decorative animations then.
 */
@Composable
expect fun rememberReduceMotion(): Boolean
