package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import kotlin.test.Test

/** The headers of a section card: centred with side controls, centred on a phone, plain, and without a card. */
@OptIn(ExperimentalTestApi::class)
class SectionCardLayoutsUiTest {

    private fun ComposeUiTest.show(compact: Boolean, content: @Composable () -> Unit) = setContent {
        CompositionLocalProvider(LocalCompactLayout provides compact) {
            MtsTheme(useDarkTheme = false) { content() }
        }
    }

    private fun ComposeUiTest.seeAll(vararg texts: String) = texts.forEach { onNodeWithText(it).assertIsDisplayed() }

    @Composable
    private fun Card(centerTitle: Boolean, titleActionAtStart: Boolean, withStart: Boolean = true, container: Boolean = true) = SectionCard(
        title = "Title",
        subtitle = "Subtitle",
        centerTitle = centerTitle,
        titleActionAtStart = titleActionAtStart,
        titleAction = { Text("Help") },
        startContent = if (withStart) ({ Text("Back") }) else null,
        actions = { Text("Edit") },
        container = container,
    ) { Text("Body") }

    @Test
    fun aCentredTitleKeepsItsControlsAtTheSidesOnAWideScreen() = runDesktopComposeUiTest {
        show(compact = false) { Card(centerTitle = true, titleActionAtStart = false) }
        seeAll("Title", "Subtitle", "Help", "Back", "Edit", "Body")
    }

    @Test
    fun aCentredTitleCanHaveItsActionAtTheStart() = runDesktopComposeUiTest {
        show(compact = false) { Card(centerTitle = true, titleActionAtStart = true) }
        seeAll("Title", "Help", "Back", "Edit")
    }

    @Test
    fun onAPhoneTheControlsOfACentredTitleGoUnderIt() = runDesktopComposeUiTest {
        show(compact = true) { Card(centerTitle = true, titleActionAtStart = false) }
        seeAll("Title", "Subtitle", "Help", "Back", "Edit", "Body")
    }

    @Test
    fun onAPhoneTheActionCanGoAtTheStartOfTheRowUnderTheTitle() = runDesktopComposeUiTest {
        show(compact = true) { Card(centerTitle = true, titleActionAtStart = true, withStart = false) }
        seeAll("Title", "Help", "Edit")
    }

    @Test
    fun aPlainTitleAndASectionWithoutCard() = runDesktopComposeUiTest {
        show(compact = false) { Card(centerTitle = false, titleActionAtStart = false, container = false) }
        seeAll("Title", "Subtitle", "Help", "Edit", "Body")
    }
}
