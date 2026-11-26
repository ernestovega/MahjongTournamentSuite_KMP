package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.runtime.Composable

/** iOS has no back button: the screens go back with the arrow of their top bar. */
@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit
