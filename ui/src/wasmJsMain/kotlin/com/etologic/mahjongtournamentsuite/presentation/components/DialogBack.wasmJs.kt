package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.runtime.Composable
import com.etologic.mahjongtournamentsuite.presentation.navigation.BackLayerEffect

/** The dialog is an entry of the browser history. A dialog that cannot be closed keeps its entry. */
@Composable
actual fun DialogBackHandler(dismissOnBack: Boolean, onDismiss: () -> Unit) = BackLayerEffect(enabled = true, onBack = { if (dismissOnBack) onDismiss() })
