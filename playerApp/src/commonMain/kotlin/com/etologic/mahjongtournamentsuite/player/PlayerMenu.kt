package com.etologic.mahjongtournamentsuite.player

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.Icon
import com.etologic.mahjongtournamentsuite.presentation.components.StatisticsIcon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.etologic.mahjongtournamentsuite.presentation.theme.LocalThemeController
import com.etologic.mahjongtournamentsuite.presentation.theme.contentDescription
import com.etologic.mahjongtournamentsuite.presentation.theme.icon

/** The top bar button that switches the theme. It shows the icon of the current preference. */
@Composable
fun ThemeAction() {
    val theme = LocalThemeController.current
    IconButton(onClick = theme.onTogglePreference) {
        Icon(
            theme.preference.icon,
            contentDescription = theme.preference.contentDescription,
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
