package com.etologic.mahjongtournamentsuite.player

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.Icon
import com.etologic.mahjongtournamentsuite.presentation.components.StatisticsIcon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.etologic.mahjongtournamentsuite.presentation.theme.LocalThemeController

/** The top bar button that switches the theme. It shows the mode that a tap switches to. */
@Composable
fun ThemeAction() {
    val theme = LocalThemeController.current
    IconButton(onClick = theme.onTogglePreference) {
        Icon(
            if (theme.isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
            contentDescription = "Theme: ${theme.preference.name}",
            tint = Color.White,
        )
    }
}

/** The top bar button that signs the player out. It sits on the left of the top bar. */
@Composable
fun SignOutAction(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Sign out", tint = Color.White)
    }
}

/** The top bar button that opens the statistics of the screen. */
@Composable
fun StatisticsAction(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(StatisticsIcon, contentDescription = "Statistics", tint = Color.White)
    }
}
