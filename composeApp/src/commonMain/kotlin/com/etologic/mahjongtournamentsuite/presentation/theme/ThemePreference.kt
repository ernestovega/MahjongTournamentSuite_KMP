package com.etologic.mahjongtournamentsuite.presentation.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

enum class ThemePreference {
    Light,
    Dark,
    ;

    fun next(): ThemePreference = when (this) {
        Light -> Dark
        Dark -> Light
    }
}

@Immutable
data class ThemeController(
    val preference: ThemePreference,
    val isDarkTheme: Boolean,
    val onTogglePreference: () -> Unit,
)

val LocalThemeController = staticCompositionLocalOf<ThemeController> {
    error("ThemeController not provided")
}
