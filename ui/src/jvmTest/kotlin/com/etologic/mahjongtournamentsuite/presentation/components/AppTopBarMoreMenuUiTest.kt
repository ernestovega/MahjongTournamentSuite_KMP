package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Timer
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The "More" menu of a top bar (the tournament of the admin app, 23-nov): the screens that are not used during the
 * rounds go in it, so the bar keeps only the tools of the rounds. On a phone the bar is already a three-dots menu, so
 * the items go into it with no second "More" menu.
 */
@OptIn(ExperimentalTestApi::class)
class AppTopBarMoreMenuUiTest {
    private val original = Locale.getDefault()

    @BeforeTest
    fun english() = Locale.setDefault(Locale.ENGLISH)

    @AfterTest
    fun restore() = Locale.setDefault(original)

    private val clicks = mutableListOf<String>()

    private fun ComposeUiTest.show() = setContent {
        CompositionLocalProvider(LocalAdaptiveTopBar provides true) {
            MtsTheme(useDarkTheme = false) {
                AppScaffold(
                    title = "Madrid Open",
                    onBack = {},
                    actions = {
                        AppTopBarButton(text = "Timer", icon = Icons.Default.Timer, onClick = { clicks += "Timer" })
                        AppTopBarMoreMenu {
                            AppTopBarButton(text = "Players", icon = Icons.Default.People, onClick = { clicks += "Players" })
                            AppTopBarButton(text = "Statistics", icon = Icons.Default.People, onClick = { clicks += "Statistics" })
                        }
                    },
                ) {}
            }
        }
    }

    private fun ComposeUiTest.button(label: String) = onNode(hasText(label) or hasContentDescription(label))

    @Test
    fun onAWideScreenTheItemsWaitInTheMoreMenuAndItClosesAfterAClick() = runDesktopComposeUiTest(width = 1200, height = 800) {
        show()

        button("Timer").assertExists()
        button("Players").assertDoesNotExist()

        button("More").performClick()
        waitForIdle()
        button("Players").assertExists()
        button("Statistics").performClick()
        waitForIdle()

        assertEquals(listOf("Statistics"), clicks)
        button("Players").assertDoesNotExist()
    }

    @Test
    fun onAPhoneTheItemsAreInTheThreeDotsMenuWithoutASecondMenu() = runDesktopComposeUiTest(width = 400, height = 800) {
        show()

        button("Players").assertDoesNotExist()
        onNodeWithContentDescription("Menu").performClick()
        waitForIdle()

        button("Timer").assertExists()
        button("Players").assertExists()
        button("Statistics").assertExists()
        button("More").assertDoesNotExist()
        button("Players").performClick()
        waitForIdle()
        assertEquals(listOf("Players"), clicks)
    }
}
