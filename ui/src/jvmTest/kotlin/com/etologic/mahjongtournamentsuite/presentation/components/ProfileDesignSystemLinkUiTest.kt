package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.etologic.mahjongtournamentsuite.domain.model.UserProfile
import com.etologic.mahjongtournamentsuite.presentation.theme.LocalThemeController
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import com.etologic.mahjongtournamentsuite.presentation.theme.ThemeController
import com.etologic.mahjongtournamentsuite.presentation.theme.ThemePreference
import kotlin.test.Test
import kotlin.test.assertEquals

/** The profile shows the link to the catalog of the design system only when the screen gives it (administrators). */
@OptIn(ExperimentalTestApi::class)
class ProfileDesignSystemLinkUiTest {
    private val profile = UserProfile(uid = "u1", email = "ada@example.test", alias = "Ada Lovelace", emaId = "10001")

    @Test
    fun theLinkShowsOnlyWhenTheScreenGivesIt() = runComposeUiTest {
        var opened = 0
        var admin by mutableStateOf(false)
        setContent {
            TestTheme {
                ProfileDialog(
                    profile = profile,
                    onSaveAlias = { error("unused") },
                    onSavePhoto = { _, _ -> error("unused") },
                    onRemovePhoto = { error("unused") },
                    onSaved = {},
                    onDismiss = {},
                    onOpenDesignSystem = if (admin) ({ opened++ }) else null,
                )
            }
        }

        onNodeWithText("Design system").assertDoesNotExist()
        admin = true
        onNodeWithText("Design system").performClick()
        assertEquals(1, opened)
    }
}

@Composable
private fun TestTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalThemeController provides ThemeController(ThemePreference.Light, false, {}),
    ) { MtsTheme(useDarkTheme = false) { content() } }
}
