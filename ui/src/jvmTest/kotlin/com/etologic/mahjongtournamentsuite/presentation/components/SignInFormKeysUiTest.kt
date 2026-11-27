package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.test.withKeyDown
import com.etologic.mahjongtournamentsuite.presentation.theme.LocalThemeController
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import com.etologic.mahjongtournamentsuite.presentation.theme.ThemeController
import com.etologic.mahjongtournamentsuite.presentation.theme.ThemePreference
import com.etologic.mahjongtournamentsuite.presentation.util.UiText
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** The keys of the sign-in form, the password that shows and hides, the messages, the loading state and the app link. */
@OptIn(ExperimentalTestApi::class)
class SignInFormKeysUiTest {
    private val original = Locale.getDefault()

    @BeforeTest
    fun english() = Locale.setDefault(Locale.ENGLISH)

    @AfterTest
    fun restore() = Locale.setDefault(original)

    private val events = mutableListOf<String>()
    private var loading by mutableStateOf(false)

    private fun ComposeUiTest.showForm(info: UiText? = null, error: UiText? = null) = setContent {
        CompositionLocalProvider(LocalThemeController provides ThemeController(ThemePreference.Light, false, {})) {
          MtsTheme(useDarkTheme = false) {
            SignInForm(
                email = "ana@example.test",
                onEmailChange = {},
                password = "secret",
                onPasswordChange = {},
                isLoading = loading,
                errorMessage = error,
                infoMessage = info,
                emailRequester = FocusRequester(),
                onSubmit = { events += "submit" },
                onForgotPassword = { events += "forgot" },
                onErrorDismiss = { events += "dismiss" },
                onNeedHelp = { events += "help" },
                onGetTheApp = { events += "app" },
            )
          }
        }
    }

    private fun ComposeUiTest.field(label: String) = onNode(hasSetTextAction() and hasText(label))

    private fun ComposeUiTest.press(label: String, key: Key, shift: Boolean = false) {
        field(label).performKeyInput { if (shift) withKeyDown(Key.ShiftLeft) { pressKey(key) } else pressKey(key) }
        waitForIdle()
    }

    @Test
    fun tabAndEnterMoveBetweenTheFieldsAndSignIn() = runDesktopComposeUiTest {
        showForm()
        field("Email").requestFocus()
        press("Email", Key.Tab)
        field("Password").assertIsFocused()
        press("Password", Key.Tab, shift = true)
        field("Email").assertIsFocused()
        press("Email", Key.Enter)
        field("Password").assertIsFocused()
        press("Password", Key.A)
        press("Password", Key.Tab)
        field("Password").requestFocus()
        press("Password", Key.Enter)
        assertEquals(listOf("submit"), events)
        field("Email").requestFocus()
        press("Email", Key.Tab, shift = true)
        press("Email", Key.A)
    }

    @Test
    fun thePasswordShowsAndHides() = runDesktopComposeUiTest {
        showForm()
        onNodeWithContentDescription("Show password").performClick()
        onNodeWithContentDescription("Hide password").assertIsDisplayed()
    }

    @Test
    fun theButtonSignsInAndTheLinksAnswer() = runDesktopComposeUiTest {
        showForm(info = UiText.Plain("Check your email"))
        onNodeWithText("Check your email").assertIsDisplayed()
        onNode(hasText("Sign in") and hasClickAction()).performClick()
        onNodeWithText("Get the app", ignoreCase = true).performClick()
        assertEquals(listOf("submit", "app"), events)
    }

    @Test
    fun anErrorShowsInADialog() = runDesktopComposeUiTest {
        showForm(error = UiText.Plain("Wrong password"))
        onNodeWithText("Wrong password").assertIsDisplayed()
    }

    @Test
    fun whileLoadingTheButtonShowsAProgress() = runDesktopComposeUiTest {
        loading = true
        showForm()
        onNodeWithText("Email").assertIsDisplayed()
    }
}
