package com.etologic.mahjongtournamentsuite.presentation.util

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PlaceholderNamesTest {
    @AfterTest
    fun reset() {
        PlaceholderNames.playerTemplate = null
    }

    @Test
    fun aPlaceholderBecomesTheWordOfTheLanguage() {
        PlaceholderNames.playerTemplate = { "Jugador $it" }
        assertEquals("Jugador 7", PlaceholderNames.localize("Player 7"))
        assertEquals("Jugador 12", PlaceholderNames.localize(" Player 12 "))
    }

    @Test
    fun aRealNameStaysAsItIs() {
        PlaceholderNames.playerTemplate = { "Jugador $it" }
        assertEquals("Player Ruiz", PlaceholderNames.localize("Player Ruiz"))
        assertEquals("Ana Player 3", PlaceholderNames.localize("Ana Player 3"))
        assertEquals("Carla Ruiz", PlaceholderNames.localize("Carla Ruiz"))
    }

    @Test
    fun withoutALanguageYetTheNameStays() {
        PlaceholderNames.playerTemplate = null
        assertEquals("Player 7", PlaceholderNames.localize("Player 7"))
    }

    @Test
    fun aWholeMapIsConverted() {
        PlaceholderNames.playerTemplate = { "Jugador $it" }
        assertEquals(mapOf(1 to "Jugador 1", 2 to "Ana"), PlaceholderNames.localizeAll(mapOf(1 to "Player 1", 2 to "Ana")))
    }
}
