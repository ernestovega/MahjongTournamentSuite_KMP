package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.UserProfile
import com.etologic.mahjongtournamentsuite.presentation.theme.LocalThemeController
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import com.etologic.mahjongtournamentsuite.presentation.theme.ThemeController
import com.etologic.mahjongtournamentsuite.presentation.theme.ThemePreference
import kotlin.test.Test
import kotlin.test.assertEquals

/** The profile says who sees the alias and the photo, and lets the person hide the photo from the public. */
@OptIn(ExperimentalTestApi::class)
class ProfilePhotoVisibilityUiTest {
    private val player = UserProfile(uid = "u1", email = "ada@example.test", alias = "Ada Lovelace", emaId = "10001")
    private val switch = isToggleable() and hasText("Show my photo publicly")

    @Test
    fun theAliasAndThePhotoExplainWhoSeesThem() = runComposeUiTest {
        setContent { ProfileTheme { Dialog(player, onSetPublicPhoto = { AppResult.Success(player) }) } }

        onNodeWithContentDescription(
            "Your alias is private: only you and the organizers see it.\nIt helps tell apart people with the same name.\n" +
                "It never shows in rankings, statistics or other public places.",
        ).assertExists()
        onNodeWithContentDescription("Your photo shows publicly", substring = true).assertExists()
    }

    @Test
    fun theSwitchHidesThePhotoAndShowsItAgain() = runComposeUiTest {
        val asked = mutableListOf<Boolean>()
        val saved = mutableListOf<UserProfile>()
        setContent {
            ProfileTheme {
                Dialog(player, onSaved = { saved += it }, onSetPublicPhoto = { visible ->
                    asked += visible
                    AppResult.Success(player.copy(publicPhoto = visible))
                })
            }
        }

        onNode(switch).performScrollTo().assertIsOn().performClick()
        waitForIdle()
        onNode(switch).assertIsOff()
        onNode(switch).performClick()
        waitForIdle()
        onNode(switch).assertIsOn()
        assertEquals(listOf(false, true), asked)
        assertEquals(listOf(false, true), saved.map { it.publicPhoto })
    }

    @Test
    fun aFailedChangeKeepsTheSwitchAndShowsTheError() = runComposeUiTest {
        setContent { ProfileTheme { Dialog(player, onSetPublicPhoto = { AppResult.Failure(AppError.Network) }) } }

        onNode(switch).performScrollTo().performClick()
        waitForIdle()
        onNode(switch).assertIsOn()
        onNodeWithText("Network", substring = true, ignoreCase = true).assertExists()
    }

    @Test
    fun anAccountWithoutEmaNumberHasNoSwitch() = runComposeUiTest {
        setContent { ProfileTheme { Dialog(player.copy(emaId = null), onSetPublicPhoto = { error("unused") }) } }

        onNodeWithText("Profile").assertExists()
        onNode(switch).assertDoesNotExist()
    }
}

@Composable
private fun Dialog(
    profile: UserProfile,
    onSaved: (UserProfile) -> Unit = {},
    onSetPublicPhoto: suspend (Boolean) -> AppResult<UserProfile>,
) {
    ProfileDialog(
        profile = profile,
        onSaveAlias = { error("unused") },
        onSavePhoto = { _, _ -> error("unused") },
        onRemovePhoto = { error("unused") },
        onSaved = onSaved,
        onDismiss = {},
        onSetPublicPhoto = onSetPublicPhoto,
    )
}

@Composable
private fun ProfileTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalThemeController provides ThemeController(ThemePreference.Light, false, {})) {
        MtsTheme(useDarkTheme = false) { content() }
    }
}
