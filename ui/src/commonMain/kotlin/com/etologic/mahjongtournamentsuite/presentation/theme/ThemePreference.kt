package com.etologic.mahjongtournamentsuite.presentation.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.vector.ImageVector

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

/** A square with a border. The left half is filled. It shows that the theme follows the system. */
private val SystemThemeIcon: ImageVector by lazy {
    materialIcon(name = "SystemTheme") {
        materialPath(pathFillType = PathFillType.EvenOdd) {
            moveTo(3f, 3f)
            horizontalLineTo(21f)
            verticalLineTo(21f)
            horizontalLineTo(3f)
            close()
            moveTo(5f, 5f)
            verticalLineTo(19f)
            horizontalLineTo(19f)
            verticalLineTo(5f)
            close()
            moveTo(5f, 5f)
            horizontalLineTo(12f)
            verticalLineTo(19f)
            horizontalLineTo(5f)
            close()
        }
    }
}

val ThemePreference.icon: ImageVector
    get() = when (this) {
        ThemePreference.System -> SystemThemeIcon
        ThemePreference.Light -> Icons.Default.LightMode
        ThemePreference.Dark -> Icons.Default.DarkMode
    }

val ThemePreference.contentDescription: String
    get() = "Theme: $name"

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
    val useDarkTheme = isDarkTheme(preference)

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

/** A theme controller for one screen. It starts from the app preference and does not save changes. */
@Composable
fun rememberScreenThemeController(): ThemeController {
    val initialPreference = rememberThemeController().preference
    var preferenceName by rememberSaveable { mutableStateOf(initialPreference.name) }
    val preference = remember(preferenceName) {
        runCatching { ThemePreference.valueOf(preferenceName) }.getOrDefault(ThemePreference.System)
    }
    val useDarkTheme = isDarkTheme(preference)

    return remember(preference, useDarkTheme) {
        ThemeController(
            preference = preference,
            isDarkTheme = useDarkTheme,
            onTogglePreference = { preferenceName = preference.next().name },
        )
    }
}

@Composable
private fun isDarkTheme(preference: ThemePreference): Boolean = when (preference) {
    ThemePreference.System -> isPlatformDarkTheme()
    ThemePreference.Light -> false
    ThemePreference.Dark -> true
}

internal expect object ThemePreferenceStorage {
    fun load(): String?
    fun save(value: String)
}

@Composable
internal expect fun isPlatformDarkTheme(): Boolean
