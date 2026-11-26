package com.etologic.mahjongtournamentsuite.presentation.theme

import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.etologic.mahjongtournamentsuite.presentation.components.MtsCheckboxRow
import com.etologic.mahjongtournamentsuite.presentation.components.MtsSwitchRow
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The theme follows the system without a choice, and a checkbox or switch row without a change only shows its value. */
@OptIn(ExperimentalTestApi::class)
class MtsThemeDefaultsTest {
    @Test
    fun withoutAChoiceTheThemeFollowsTheSystem() = runComposeUiTest {
        var width: MtsWindowWidth? = null
        setContent {
            MtsTheme {
                width = MtsTheme.windowWidth
                Text("Themed")
            }
        }

        onNodeWithText("Themed").assertExists()
        assertTrue(width != null)
    }

    @Test
    fun aRowWithoutAChangeOnlyShowsItsValue() = runComposeUiTest {
        setContent {
            MtsTheme(useDarkTheme = false) {
                MtsCheckboxRow(text = "Paid", checked = true, onCheckedChange = null)
                MtsSwitchRow(checked = true, onCheckedChange = null) { Text("Public") }
            }
        }

        onNodeWithText("Paid").performClick()
        onNodeWithText("Public").performClick()
        onNodeWithText("Paid").assertExists()
    }

    @Test
    fun onlyThePhoneWidthIsCompact() {
        assertTrue(MtsWindowWidth.Compact.isCompact)
        assertFalse(MtsWindowWidth.Medium.isCompact)
        assertFalse(MtsWindowWidth.Expanded.isCompact)
    }
}
