package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.runtime.Composable

/**
 * Keeps [com.etologic.mahjongtournamentsuite.presentation.theme.MtsInputModality] up to date: a key press turns the
 * keyboard on, a mouse move or a press turns it off. On the desktop and the web it listens to the whole window, also
 * the dialogs. It is cheap to call from many places: the platform listeners are added once.
 */
@Composable
internal expect fun InputModalityTracker()
