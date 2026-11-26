package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.domain.model.Organization
import com.etologic.mahjongtournamentsuite.domain.model.UserProfile
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

/** Small shared parts: the button row of a dialog, the profile button, the date field, the logo of an organization, the row menu, the DEV menu. */
@OptIn(ExperimentalTestApi::class)
class SmallComponentsUiTest {
    private fun rowOf(width: Int) = runComposeUiTest {
        setContent {
            MtsTheme(useDarkTheme = false) {
                DialogButtonRow(
                    start = { Text("Delete the whole tournament", Modifier.testTag("start")) },
                    modifier = Modifier.width(width.dp),
                ) {
                    Text("Cancel", Modifier.testTag("cancel"))
                    Text("Save", Modifier.testTag("save"))
                }
            }
        }
        val start = onNodeWithTag("start").getUnclippedBoundsInRoot()
        val save = onNodeWithTag("save").getUnclippedBoundsInRoot()
        if (width >= 400) {
            assertEquals(start.top, save.top, "One line: the buttons share the line.")
            assertTrue(save.right <= width.dp && save.left > start.right, "One line: the end buttons are at the right.")
        } else {
            assertTrue(save.top >= start.bottom, "Two lines: the end buttons go under the start button.")
        }
    }

    @Test
    fun theDialogButtonsShareOneLineWhenTheyFit() = rowOf(500)

    @Test
    fun theDialogButtonsGoToTwoLinesWhenTheyDoNotFit() = rowOf(160)

    @Test
    fun theLabeledProfileButtonShowsTheNameAndTheRoleAndOpensTheProfile() = runComposeUiTest {
        var clicks = 0
        setContent {
            MtsTheme(useDarkTheme = false) {
                ProfileTopBarButton(UserProfile("me", "me@example.test", "Me"), name = "Ana", clickLabel = "Open profile", onClick = { clicks++ }, secondLine = "Editor")
            }
        }
        onNodeWithText("Editor").assertExists()
        onNode(hasClickAction() and hasText("Ana")).performClick()
        waitForIdle()
        assertEquals(1, clicks)
    }

    @Test
    fun theProfileButtonWithoutARoleIsATopBarButton() = runComposeUiTest {
        var clicks = 0
        setContent {
            MtsTheme(useDarkTheme = false) {
                ProfileTopBarButton(UserProfile("me", "me@example.test", "Me"), name = "Ana", clickLabel = "Open profile", onClick = { clicks++ })
            }
        }
        onNode(hasClickAction() and (hasText("Ana") or hasContentDescription("Open profile"))).performClick()
        waitForIdle()
        assertEquals(1, clicks)
    }

    @Test
    fun theDateFieldClearsAndPicksADate() = runComposeUiTest {
        var value by mutableStateOf("2026-10-22")
        // The buttons ignore a second press within 500 ms: the test moves this clock before each press.
        val clock = TestTimeSource()
        setContent {
            CompositionLocalProvider(LocalPressTimeSource provides clock) {
                MtsTheme(useDarkTheme = false) { DateField("Start", value, onChange = { value = it }) }
            }
        }
        clock += 1.seconds
        onNode(hasClickAction() and hasContentDescription("Pick Start")).performClick()
        waitForIdle()
        clock += 1.seconds
        onNode(hasClickAction() and hasText("OK")).performClick()
        waitForIdle()
        assertEquals("2026-10-22", value)

        clock += 1.seconds
        onNode(hasClickAction() and hasContentDescription("Clear Start")).performClick()
        waitForIdle()
        assertEquals("", value)
        assertEquals(0, onAllNodes(hasContentDescription("Clear Start")).fetchSemanticsNodes().size)

        clock += 1.seconds
        onNode(hasClickAction() and hasContentDescription("Pick Start")).performClick()
        waitForIdle()
        onNode(hasClickAction() and hasText("OK")).assertIsNotEnabled()
        clock += 1.seconds
        onNode(hasClickAction() and hasText("Cancel")).performClick()
        waitForIdle()
        assertEquals("", value)
    }

    @Test
    fun aDateGoesToMillisAndBackAndAWrongTextIsNoDate() {
        listOf("2026-10-22", "2000-02-29", "1999-12-31", "2026-01-01").forEach { date ->
            assertEquals(date, epochMillisToIsoDate(isoDateToEpochMillis(date)!!))
        }
        assertEquals(0L, isoDateToEpochMillis("1970-01-01"))
        listOf("", "2026-13-01", "2026-10", "abc").forEach { assertEquals(null, isoDateToEpochMillis(it), it) }
    }

    @Test
    fun anOrganizationWithoutALogoShowsItsFirstLetter() = runComposeUiTest {
        setContent {
            MtsTheme(useDarkTheme = false) {
                Column {
                    OrganizationLogo(Organization("o1", "club madrid"))
                    OrganizationLogo(organization = null)
                }
            }
        }
        onNodeWithText("C").assertExists()
    }

    @Test
    fun theColorOfAnOrganizationComesFromItsTextOrIsTheDefaultGreen() {
        assertEquals(Color(0xFFFF0000), organizationColor(" #ff0000 "))
        assertEquals(Color(0xFF02B16B), organizationColor("red"))
        assertEquals(Color(0xFF02B16B), organizationColor("#12345"))
    }

    @Test
    fun theRowMenuListsItsActionsAndRunsTheChosenOne() = runComposeUiTest {
        val chosen = mutableListOf<String>()
        setContent {
            MtsTheme(useDarkTheme = false) {
                RowActionsMenu(
                    enabled = true,
                    items = listOf(
                        RowActionMenuItem("Rename") { chosen += "rename" },
                        RowActionMenuItem("Delete", enabled = false) { chosen += "delete" },
                    ),
                )
            }
        }
        onNode(hasClickAction() and hasContentDescription("Actions")).performClick()
        waitForIdle()
        onNode(hasClickAction() and hasText("Delete")).assertIsNotEnabled()
        onNode(hasClickAction() and hasText("Rename")).performClick()
        waitForIdle()
        assertEquals(listOf("rename"), chosen)
        assertEquals(0, onAllNodesWithText("Rename").fetchSemanticsNodes().size)
    }

    @Test
    fun enterOnATableRowRunsItsAction() = runComposeUiTest {
        var clicks = 0
        setContent {
            MtsTheme(useDarkTheme = false) {
                DataTableRow(modifier = Modifier.testTag("row"), onClick = { clicks++ }) { Text("Row") }
            }
        }
        onNodeWithTag("row").requestFocus()
        waitForIdle()
        onNodeWithTag("row").performKeyInput { pressKey(Key.Enter) }
        onNodeWithTag("row").performKeyInput { pressKey(Key.NumPadEnter) }
        onNodeWithTag("row").performKeyInput { pressKey(Key.A) }
        waitForIdle()
        assertEquals(2, clicks)
    }

    @Test
    fun theDevMenuOpensTheScreensOfTheTools() = runComposeUiTest {
        val opened = mutableListOf<String>()
        // The DEV button ignores a second press within 500 ms: the test moves this clock instead of waiting.
        val clock = TestTimeSource()
        setContent {
            CompositionLocalProvider(LocalPressTimeSource provides clock) {
                MtsTheme(useDarkTheme = false) {
                    DevMenuButton(DevMenuActions(onActivity = { opened += "activity" }, onAppErrors = { opened += "errors" }, onDesignSystem = { opened += "design" }))
                }
            }
        }
        listOf("Activity", "App errors", "Design system").forEach { entry ->
            clock += 1.seconds
            onNode(hasClickAction() and hasText("DEV")).performClick()
            waitForIdle()
            onNode(hasClickAction() and hasText(entry)).performClick()
            waitForIdle()
        }
        assertEquals(listOf("activity", "errors", "design"), opened)
    }

    @Test
    fun theBestHandMarkNamesItsPlace() = runComposeUiTest {
        setContent {
            MtsTheme(useDarkTheme = false) {
                Column {
                    BestHandMark(1)
                    BestHandMark(2)
                    BestHandMark(3)
                    BestHandIcon()
                }
            }
        }
        assertEquals(4, onAllNodes(hasContentDescription("Best hand")).fetchSemanticsNodes().size)
    }
}
