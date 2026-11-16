package com.etologic.mahjongtournamentsuite.player

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
    fun theSearchFiltersByNameAndATapShowsTheDescription() = runComposeUiTest {
        setContent { MtsTheme(useDarkTheme = false) { CombinationsScreen(rememberNavController()) } }
        onNodeWithTag(COMBINATIONS_SEARCH_TAG).performTextInput("edge")
        onAllNodesWithTag(COMBINATION_ROW_TAG).assertCountEquals(1)
        onNodeWithText("Edge wait").performClick()
        onNodeWithText("Win with 3 or 7 having 1–2 or 8–9").assertExists()
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
    fun theTopBarShowsAndHidesTheTilesOfAllCombinations() = runComposeUiTest {
        setContent { MtsTheme(useDarkTheme = false) { CombinationsScreen(rememberNavController()) } }
        onNodeWithTag(COMBINATIONS_SEARCH_TAG).performTextInput("self")
        onNodeWithText("Win with a tile from the wall").assertDoesNotExist()
        onNodeWithContentDescription("Show all tiles").performClick()
        onNodeWithText("Win with a tile from the wall").assertExists()
    }
}
