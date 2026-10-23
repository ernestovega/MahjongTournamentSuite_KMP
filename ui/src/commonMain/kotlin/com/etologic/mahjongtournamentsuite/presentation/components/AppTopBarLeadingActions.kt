package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import com.etologic.mahjongtournamentsuite.presentation.theme.LocalThemeController
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import com.etologic.mahjongtournamentsuite.presentation.theme.ThemeController
import com.etologic.mahjongtournamentsuite.presentation.theme.ThemePreference
import com.etologic.mahjongtournamentsuite.presentation.theme.icon
import mahjongtournamentsuite.ui.generated.resources.Res
import mahjongtournamentsuite.ui.generated.resources.top_bar_app_usage
import mahjongtournamentsuite.ui.generated.resources.top_bar_app_users
import mahjongtournamentsuite.ui.generated.resources.top_bar_ranking
import mahjongtournamentsuite.ui.generated.resources.top_bar_timer
import org.jetbrains.compose.resources.stringResource

@Composable
fun AppTopBarLeadingActions(
    showThemeToggle: Boolean = false,
    onTimer: (() -> Unit)? = null,
    onRanking: (() -> Unit)? = null,
    onUsage: (() -> Unit)? = null,
    onAppUsers: (() -> Unit)? = null,
    timerFocusRequester: FocusRequester? = null,
    rankingFocusRequester: FocusRequester? = null,
    usageFocusRequester: FocusRequester? = null,
    usersFocusRequester: FocusRequester? = null,
) {
    TopBarButtonsLayout {
        onAppUsers?.let { AppTopBarButton(stringResource(Res.string.top_bar_app_users), Icons.Default.Person, it, usersFocusRequester) }
        if (showThemeToggle) { ThemeModeToggleButton() }
        onUsage?.let { AppTopBarButton(stringResource(Res.string.top_bar_app_usage), StatisticsIcon, it, usageFocusRequester) }
        onTimer?.let { AppTopBarButton(stringResource(Res.string.top_bar_timer), Icons.Default.AccessTime, it, timerFocusRequester) }
        onRanking?.let { AppTopBarButton(stringResource(Res.string.top_bar_ranking), Icons.Default.Leaderboard, it, rankingFocusRequester) }
    }
}

@Composable
private fun ThemeModeToggleButton() {
    val themeController = LocalThemeController.current
    val label = themeController.preference.name

    AppTopBarButton(
        text = label,
        icon = themeController.preference.icon,
        onClick = themeController.onTogglePreference,
    )
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
