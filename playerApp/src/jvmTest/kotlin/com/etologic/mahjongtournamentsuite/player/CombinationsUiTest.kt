package com.etologic.mahjongtournamentsuite.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class CombinationsUiTest {
    private val original = Locale.getDefault()

    @BeforeTest
    fun english() = Locale.setDefault(Locale.ENGLISH)

    @AfterTest
    fun restore() = Locale.setDefault(original)

    @Test
    fun theSearchFiltersByNameAndTheArrowFoldsTheDescription() = runComposeUiTest {
        setContent { MtsTheme(useDarkTheme = false) { CombinationsScreen(rememberNavController()) } }
        onNodeWithTag(COMBINATIONS_SEARCH_TAG).performTextInput("edge")
        onAllNodesWithTag(COMBINATION_ROW_TAG).assertCountEquals(1)
        // The combinations start unfolded.
        onNodeWithText("Waiting only for a 3 to make 1-2-3, or only for a 7 to make 7-8-9. Not valid if waiting on more than one tile.").assertExists()
        onNodeWithContentDescription("Fold").performClick()
        onNodeWithText("Waiting only for a 3 to make 1-2-3, or only for a 7 to make 7-8-9. Not valid if waiting on more than one tile.").assertDoesNotExist()
        onNodeWithContentDescription("Unfold").assertExists()
    }

    @Test
    fun aFoldedRowShowsSmallTilesOnlyOnAWideScreen() = runComposeUiTest {
        var width by mutableStateOf(1000.dp)
        setContent {
            MtsTheme(useDarkTheme = false) { Box(Modifier.requiredWidth(width)) { CombinationsScreen(rememberNavController()) } }
        }
        onNodeWithTag(COMBINATIONS_SEARCH_TAG).performTextInput("short straight")
        // Unfolded, the tiles show in large below and not in small.
        onNodeWithTag(COMBINATION_MINI_TILES_TAG, useUnmergedTree = true).assertDoesNotExist()
        onNodeWithContentDescription("Fold").performClick()
        onNodeWithTag(COMBINATION_MINI_TILES_TAG, useUnmergedTree = true).assertExists()
        width = 400.dp
        onNodeWithTag(COMBINATION_MINI_TILES_TAG, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun aTapOnACombinationOpensItsDialog() = runComposeUiTest {
        setContent { MtsTheme(useDarkTheme = false) { CombinationsScreen(rememberNavController()) } }
        onNodeWithTag(COMBINATIONS_SEARCH_TAG).performTextInput("big four")
        onNodeWithText("Big four winds").performClick()
        onNodeWithTag(COMBINATION_DIALOG_TAG).assertExists()
        onNodeWithText("Does not combine with Big Three Winds", substring = true).assertExists()
        // The three examples of the Green Book, with what they combine with.
        onAllNodesWithTag(COMBINATION_EXAMPLE_TAG).assertCountEquals(3)
        onNodeWithText("Combined with Half Flush.").assertExists()
        onNodeWithText("Close").performClick()
        onNodeWithTag(COMBINATION_DIALOG_TAG).assertDoesNotExist()
    }

    @Test
    fun theDialogScrollsOnASmallScreen() = runDesktopComposeUiTest(width = 320, height = 480) {
        setContent { MtsTheme(useDarkTheme = false) { CombinationsScreen(rememberNavController()) } }
        onNodeWithTag(COMBINATIONS_SEARCH_TAG).performTextInput("big four")
        onNodeWithText("Big four winds").performClick()
        // The last example does not fit: it shows after a scroll, and the Close button stays on screen.
        onNodeWithText("Combined with Half Flush.").assertIsNotDisplayed()
        onNodeWithText("Combined with Half Flush.").performScrollTo().assertIsDisplayed()
        onNodeWithText("Close").assertIsDisplayed()
    }

    @Test
    fun theSearchFindsTheCombinationsOfSomePoints() = runComposeUiTest {
        setContent { MtsTheme(useDarkTheme = false) { CombinationsScreen(rememberNavController()) } }
        onNodeWithTag(COMBINATIONS_SEARCH_TAG).performTextInput("88")
        // Folded, so that all the rows fit on the screen.
        onNode(hasText("Fold all") or hasContentDescription("Fold all")).performClick()
        onAllNodesWithTag(COMBINATION_ROW_TAG).assertCountEquals(7)
        // One header for the value, above its seven combinations.
        onAllNodesWithTag(POINTS_HEADER_TAG).assertCountEquals(1)
        onNodeWithText("88 points").assertExists()
    }

    @Test
    fun aValueChipShowsOnlyTheCombinationsOfThatValue() = runComposeUiTest {
        setContent { MtsTheme(useDarkTheme = false) { CombinationsScreen(rememberNavController()) } }
        onNode(hasText("Fold all") or hasContentDescription("Fold all")).performClick()
        onNodeWithText("64 points (6)").performScrollTo().performClick()
        onAllNodesWithTag(COMBINATION_ROW_TAG).assertCountEquals(6)
        onAllNodesWithTag(POINTS_HEADER_TAG).assertCountEquals(1)
    }

    @Test
    fun theButtonShowsAndHidesAllCombinations() = runComposeUiTest {
        setContent { MtsTheme(useDarkTheme = false) { CombinationsScreen(rememberNavController()) } }
        onNodeWithTag(COMBINATIONS_SEARCH_TAG).performTextInput("self")
        onNodeWithText("Going out on a tile drawn from the wall.").assertExists()
        onNode(hasText("Fold all") or hasContentDescription("Fold all")).performClick()
        onNodeWithText("Going out on a tile drawn from the wall.").assertDoesNotExist()
    }
}
