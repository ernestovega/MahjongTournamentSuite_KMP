package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.runtime.Composable
import com.etologic.mahjongtournamentsuite.presentation.navigation.BackLayerEffect

/** The browser back and the back gesture of a phone go through the browser history, where this adds an entry. */
@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = BackLayerEffect(enabled, onBack)
