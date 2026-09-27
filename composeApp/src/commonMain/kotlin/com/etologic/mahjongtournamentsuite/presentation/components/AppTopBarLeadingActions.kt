package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import com.etologic.mahjongtournamentsuite.presentation.theme.LocalThemeController
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import com.etologic.mahjongtournamentsuite.presentation.theme.ThemeController
import com.etologic.mahjongtournamentsuite.presentation.theme.ThemePreference

@Composable
fun AppTopBarLeadingActions(
    showThemeToggle: Boolean = false,
    onTimer: (() -> Unit)? = null,
    onRanking: (() -> Unit)? = null,
    onExport: (() -> Unit)? = null,
    onUsers: (() -> Unit)? = null,
    timerFocusRequester: FocusRequester? = null,
    rankingFocusRequester: FocusRequester? = null,
    exportFocusRequester: FocusRequester? = null,
    usersFocusRequester: FocusRequester? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        onUsers?.let { AppTopBarButton("Users", it, usersFocusRequester) }
        if (showThemeToggle) { ThemeModeToggleButton() }
        onTimer?.let { AppTopBarButton("Timer", it, timerFocusRequester) }
        onRanking?.let { AppTopBarButton("Ranking", it, rankingFocusRequester) }
        onExport?.let { AppTopBarButton("Export", it, exportFocusRequester) }
    }
}

@Composable
private fun ThemeModeToggleButton() {
    val themeController = LocalThemeController.current
    val label = "Theme: ${themeController.preference.name}"

    AppTextButton(onClick = themeController.onTogglePreference) {
        Text(
            text = label,
            color = Color.White,
        )
    }
}

@Preview(device = Devices.DESKTOP)
@Composable
private fun AppTopBarLeadingActionsPreview() {
    val themeController = ThemeController(
        preference = ThemePreference.Light,
        isDarkTheme = false,
        onTogglePreference = {},
    )

    CompositionLocalProvider(LocalThemeController provides themeController) {
        MtsTheme(useDarkTheme = false) {
            AppTopBarLeadingActions(
                showThemeToggle = true,
                onTimer = {},
                onRanking = {},
            )
        }
    }
}
