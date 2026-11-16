package com.etologic.mahjongtournamentsuite.player

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
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
    fun theSearchFiltersByNameAndTheArrowShowsTheDescription() = runComposeUiTest {
        setContent { MtsTheme(useDarkTheme = false) { CombinationsScreen(rememberNavController()) } }
        onNodeWithTag(COMBINATIONS_SEARCH_TAG).performTextInput("edge")
        onAllNodesWithTag(COMBINATION_ROW_TAG).assertCountEquals(1)
        onNodeWithText("Waiting only for a 3 to make 1-2-3, or only for a 7 to make 7-8-9. Not valid if waiting on more than one tile.").assertDoesNotExist()
        onNodeWithContentDescription("Unfold").performClick()
        onNodeWithText("Waiting only for a 3 to make 1-2-3, or only for a 7 to make 7-8-9. Not valid if waiting on more than one tile.").assertExists()
        onNodeWithContentDescription("Fold").assertExists()
    }

    @Test
    fun aTapOnACombinationOpensItsDialog() = runComposeUiTest {
        setContent { MtsTheme(useDarkTheme = false) { CombinationsScreen(rememberNavController()) } }
        onNodeWithTag(COMBINATIONS_SEARCH_TAG).performTextInput("big four")
        onNodeWithText("Big four winds").performClick()
        onNodeWithTag(COMBINATION_DIALOG_TAG).assertExists()
        onNodeWithText("Does not combine with Big Three Winds", substring = true).assertExists()
        onNodeWithText("Close").performClick()
        onNodeWithTag(COMBINATION_DIALOG_TAG).assertDoesNotExist()
    }

    @Test
    fun theSearchFindsTheCombinationsOfSomePoints() = runComposeUiTest {
        setContent { MtsTheme(useDarkTheme = false) { CombinationsScreen(rememberNavController()) } }
        onNodeWithTag(COMBINATIONS_SEARCH_TAG).performTextInput("88")
        onAllNodesWithTag(COMBINATION_ROW_TAG).assertCountEquals(7)
        // One header for the value, above its seven combinations.
        onAllNodesWithTag(POINTS_HEADER_TAG).assertCountEquals(1)
        onNodeWithText("88 points").assertExists()
    }

    @Test
    fun aValueChipShowsOnlyTheCombinationsOfThatValue() = runComposeUiTest {
        setContent { MtsTheme(useDarkTheme = false) { CombinationsScreen(rememberNavController()) } }
        onNodeWithText("64 points (6)").performScrollTo().performClick()
        onAllNodesWithTag(COMBINATION_ROW_TAG).assertCountEquals(6)
        onAllNodesWithTag(POINTS_HEADER_TAG).assertCountEquals(1)
    }

    @Test
    fun theButtonShowsAndHidesAllCombinations() = runComposeUiTest {
        setContent { MtsTheme(useDarkTheme = false) { CombinationsScreen(rememberNavController()) } }
        onNodeWithTag(COMBINATIONS_SEARCH_TAG).performTextInput("self")
        onNodeWithText("Going out on a tile drawn from the wall.").assertDoesNotExist()
        onNode(hasText("Unfold all") or hasContentDescription("Unfold all")).performClick()
        onNodeWithText("Going out on a tile drawn from the wall.").assertExists()
    }
}
