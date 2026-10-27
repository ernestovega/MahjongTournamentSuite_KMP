package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.runComposeUiTest
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class RankTrophyUiTest {
    @Test
    fun showsATrophyForTheFirstThreeRanksAndForTheOtherRanks() = runComposeUiTest {
        setContent {
            MtsTheme(useDarkTheme = false) {
                androidx.compose.foundation.layout.Column {
                    listOf(1, 2, 3, 4, 27).forEach { RankTrophy(it) }
                }
            }
        }

        listOf(1, 2, 3, 4, 27).forEach { onNodeWithContentDescription("Position $it").assertIsDisplayed() }
    }
}
