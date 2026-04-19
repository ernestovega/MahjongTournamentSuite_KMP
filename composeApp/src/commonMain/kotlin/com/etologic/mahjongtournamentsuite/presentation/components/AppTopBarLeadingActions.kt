package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.etologic.mahjongtournamentsuite.presentation.theme.LocalThemeController
import com.etologic.mahjongtournamentsuite.presentation.theme.ThemePreference

@Composable
fun AppTopBarLeadingActions(
    showThemeToggle: Boolean = false,
    onTimer: (() -> Unit)? = null,
    onRanking: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        if (showThemeToggle) { ThemeModeToggleButton() }
        onTimer?.let { AppTopBarButton("Timer", it) }
        onRanking?.let { AppTopBarButton("Ranking", it) }
    }
}

@Composable
private fun ThemeModeToggleButton() {
    val themeController = LocalThemeController.current
    val label = when (themeController.preference) {
        ThemePreference.Light -> "Light"
        ThemePreference.Dark -> "Dark"
    }

    AppTextButton(onClick = themeController.onTogglePreference) {
        Text(
            text = label,
            color = Color.White,
        )
    }
}