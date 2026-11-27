package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.runtime.Composable

/** The dialog itself closes on the back of the system. */
@Composable
actual fun DialogBackHandler(dismissOnBack: Boolean, onDismiss: () -> Unit) = Unit
