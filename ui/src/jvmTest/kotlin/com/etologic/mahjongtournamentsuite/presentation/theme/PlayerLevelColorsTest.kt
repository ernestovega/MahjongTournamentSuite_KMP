package com.etologic.mahjongtournamentsuite.presentation.theme

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import com.etologic.mahjongtournamentsuite.domain.model.PlayerLevel
import kotlin.test.Test
import kotlin.test.assertEquals

/** The design system keeps the level colors in order, without the domain; ui maps each PlayerLevel to one of them. */
@OptIn(ExperimentalTestApi::class)
class PlayerLevelColorsTest {
    @Test
    fun everyPlayerLevelHasItsColorsInBothThemes() = runComposeUiTest {
        val counts = mutableListOf<Int>()
        setContent {
            MtsTheme(useDarkTheme = false) { counts += MtsTheme.colors.levels.size }
            MtsTheme(useDarkTheme = true) { counts += MtsTheme.colors.levels.size }
        }
        waitForIdle()
        assertEquals(listOf(PlayerLevel.entries.size, PlayerLevel.entries.size), counts.take(2))
    }
}
