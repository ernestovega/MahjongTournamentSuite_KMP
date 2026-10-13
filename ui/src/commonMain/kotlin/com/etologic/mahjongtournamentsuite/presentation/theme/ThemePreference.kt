package com.etologic.mahjongtournamentsuite.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

enum class ThemePreference {
    System,
    Light,
    Dark,
    ;

    fun next(): ThemePreference = when (this) {
        Light -> Dark
        Dark -> System
        System -> Light
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

@Composable
fun rememberThemeController(): ThemeController {
    val initialPreference = remember {
        ThemePreferenceStorage.load()
            ?.let { stored -> runCatching { ThemePreference.valueOf(stored) }.getOrNull() }
            ?: ThemePreference.System
    }
    var preferenceName by rememberSaveable { mutableStateOf(initialPreference.name) }
    val preference = remember(preferenceName) {
        runCatching { ThemePreference.valueOf(preferenceName) }.getOrDefault(ThemePreference.System)
    }
    val systemDarkTheme = if (preference == ThemePreference.System) isPlatformDarkTheme() else false
    val useDarkTheme = when (preference) {
        ThemePreference.System -> systemDarkTheme
        ThemePreference.Light -> false
        ThemePreference.Dark -> true
    }

    return remember(preference, useDarkTheme) {
        ThemeController(
            preference = preference,
            isDarkTheme = useDarkTheme,
            onTogglePreference = {
                val next = preference.next()
                preferenceName = next.name
                ThemePreferenceStorage.save(next.name)
            },
        )
    }
}

internal expect object ThemePreferenceStorage {
    fun load(): String?
    fun save(value: String)
}

@Composable
internal expect fun isPlatformDarkTheme(): Boolean
