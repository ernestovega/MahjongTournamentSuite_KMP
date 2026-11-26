package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.SupportMessage
import com.etologic.mahjongtournamentsuite.presentation.theme.LocalThemeController
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import com.etologic.mahjongtournamentsuite.presentation.theme.ThemeController
import com.etologic.mahjongtournamentsuite.presentation.theme.ThemePreference
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** The support form (missing fields, a failed send, a sent message) and the buttons at the left of the top bar. */
@OptIn(ExperimentalTestApi::class)
class SupportAndTopBarUiTest {
    private val original = Locale.getDefault()

    @BeforeTest
    fun english() = Locale.setDefault(Locale.ENGLISH)

    @AfterTest
    fun restore() = Locale.setDefault(original)

    private val sent = mutableListOf<SupportMessage>()
    private val events = mutableListOf<String>()

    private fun ComposeUiTest.showSupport(email: String, answer: AppResult<Unit>) = setContent {
        CompositionLocalProvider(LocalThemeController provides ThemeController(ThemePreference.Light, false, {})) {
            MtsTheme(useDarkTheme = false) {
                SupportForm(app = "player", initialEmail = email, onSend = { sent += it; answer }, onBack = { events += "back" })
            }
        }
    }

    private fun ComposeUiTest.send() {
        onNode(hasText("Send", ignoreCase = true) and hasClickAction()).performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.field(label: String) = onNode(hasSetTextAction() and hasText(label))

    @Test
    fun theFormAsksForTheEmailAndTheMessage() = runDesktopComposeUiTest {
        showSupport(email = "", answer = AppResult.Success(Unit))
        send()
        onNodeWithText("Enter your email, so we can answer you.").assertIsDisplayed()
        onNodeWithText("OK", ignoreCase = true).performClick()
        field("Email").performTextInput("ana@example.test")
        mainClock.advanceTimeBy(700)
        send()
        onNodeWithText("Write your message.").assertIsDisplayed()
        assertEquals(0, sent.size)
    }

    @Test
    fun aSentMessageSaysSoAndGoesBack() = runDesktopComposeUiTest {
        showSupport(email = "ana@example.test", answer = AppResult.Success(Unit))
        field("Message").performTextInput("Hello")
        send()
        onNodeWithText("Message sent. We will answer you by email as soon as we can.").assertIsDisplayed()
        onNodeWithText("OK", ignoreCase = true).performClick()
        assertEquals("player", sent.single().app)
        assertEquals(listOf("back"), events)
    }

    @Test
    fun aFailedSendShowsTheError() = runDesktopComposeUiTest {
        showSupport(email = "ana@example.test", answer = AppResult.Failure(AppError.Network))
        field("Message").performTextInput("Hello")
        send()
        assertEquals(1, sent.size)
        onNodeWithText("OK", ignoreCase = true).assertIsDisplayed()
    }

    @Test
    fun theLeadingButtonsOfTheTopBar() = runDesktopComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalThemeController provides ThemeController(ThemePreference.Light, false, {})) {
                MtsTheme(useDarkTheme = false) {
                  Column {
                    AppTopBarLeadingActions(
                        showThemeToggle = true,
                        onTimer = { events += "timer" },
                        onRanking = { events += "ranking" },
                        onActivity = { events += "activity" },
                        onAppUsers = { events += "users" },
                    )
                    AppTopBarTimerAction { events += "timer2" }
                  }
                }
            }
        }
        listOf("Users", "Activity", "Ranking").forEach { label ->
            onNode((hasText(label) or hasContentDescription(label)) and hasClickAction()).performClick()
            mainClock.advanceTimeBy(700)
        }
        assertEquals(listOf("users", "activity", "ranking"), events)
    }
}
