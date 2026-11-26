package com.etologic.mahjongtournamentsuite.player

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.runComposeUiTest
import com.etologic.mahjongtournamentsuite.presentation.language.AppLanguage
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The glow of a new or corrected hand, what the screen reader says about a change, and the editions of the Green Book. */
@OptIn(ExperimentalTestApi::class)
class HandGlowAndGreenBookUiTest {
    private val original = Locale.getDefault()

    @BeforeTest
    fun english() = Locale.setDefault(Locale.ENGLISH)

    @AfterTest
    fun restore() = Locale.setDefault(original)

    @Test
    fun aNewHandGlowsAndFadesAndACorrectedOneGlowsTwice() = runComposeUiTest {
        mainClock.autoAdvance = false
        var change by mutableStateOf<HandListChange?>(null)
        val glows = mutableListOf<Pair<Float, Float>>()
        setContent {
            MtsTheme(useDarkTheme = false) {
                val added = rememberHandGlow(3, change)
                val edited = rememberHandGlow(1, change)
                handGlowColor(3, change)
                handGlowColor(1, change)
                glows += added to edited
                Column { HandChangeAnnouncement(change) }
            }
        }
        mainClock.advanceTimeBy(100)
        change = HandListChange(added = setOf(3), edited = setOf(1), removed = emptyList(), serial = 1)
        mainClock.advanceTimeBy(800)
        assertTrue(glows.any { it.first == 1f }, glows.toString())
        assertTrue(glows.any { it.second > 0f && it.second < 1f }, glows.toString())
        mainClock.advanceTimeBy(6_000)
        assertEquals(0f to 0f, glows.last())
        onNodeWithContentDescription("New hand 3. Hand 1 corrected").assertExists()
    }

    @Test
    fun theScreenReaderSaysTheDeletedHands() = runComposeUiTest {
        val removed = listOf(
            com.etologic.mahjongtournamentsuite.domain.model.TableHand(
                handId = 6, playerWinnerId = "1", playerLooserId = "2", handScore = "8", isChickenHand = false, isDone = true,
                playerEastPenalty = "", playerSouthPenalty = "", playerWestPenalty = "", playerNorthPenalty = "",
            ),
        )
        var text = ""
        setContent { text = handChangeAnnouncement(HandListChange(emptySet(), emptySet(), removed, serial = 2)) }
        waitForIdle()
        assertEquals("Hand 6 deleted", text)
    }

    @Test
    fun eachLanguageHasItsGreenBook() {
        assertEquals("ES", greenBookEdition(AppLanguage.Spanish))
        assertEquals("RU", greenBookEdition(AppLanguage.Russian))
        assertEquals("EN", greenBookEdition(AppLanguage.French))
        assertEquals("/green-book/GreenBook_EN.pdf", greenBookAddress("EN"))
        assertTrue(greenBookAddress("RU").startsWith("https://www.mahjong.ru/"))
    }
}
