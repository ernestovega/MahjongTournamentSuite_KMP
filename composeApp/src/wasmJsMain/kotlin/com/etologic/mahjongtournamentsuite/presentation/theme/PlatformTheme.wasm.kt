package com.etologic.mahjongtournamentsuite.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable

@Composable
internal actual fun isPlatformDarkTheme(): Boolean = isSystemInDarkTheme()
