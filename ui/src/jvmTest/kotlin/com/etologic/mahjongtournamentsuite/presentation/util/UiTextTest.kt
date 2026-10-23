package com.etologic.mahjongtournamentsuite.presentation.util

import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.runComposeUiTest
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import mahjongtournamentsuite.ui.generated.resources.Res
import mahjongtournamentsuite.ui.generated.resources.date_field_pick
import mahjongtournamentsuite.ui.generated.resources.rival_player
import mahjongtournamentsuite.ui.generated.resources.sign_out_title

@OptIn(ExperimentalTestApi::class)
class UiTextTest {
    private val original = Locale.getDefault()

    @AfterTest
    fun restoreLocale() = Locale.setDefault(original)

    private fun androidx.compose.ui.test.ComposeUiTest.awaitText(text: String) =
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithTextCount(text) > 0 }

    private fun androidx.compose.ui.test.ComposeUiTest.onAllNodesWithTextCount(text: String) =
        onAllNodesWithText(text).fetchSemanticsNodes().size

    private fun ComposeUiTestShow(language: String, text: UiText, test: androidx.compose.ui.test.ComposeUiTest) {
        Locale.setDefault(Locale.forLanguageTag(language))
        test.setContent { Text(text.asString()) }
    }

    @Test
    fun aPlainTextStaysAsItIs() = runComposeUiTest {
        ComposeUiTestShow("es", UiText.Plain("Ada & Co"), this)
        awaitText("Ada & Co")
    }

    @Test
    fun aResourceTextFollowsTheLanguage() = runComposeUiTest {
        ComposeUiTestShow("es", UiText.of(Res.string.sign_out_title), this)
        awaitText("Cerrar sesión")
    }

    @Test
    fun aResourceTextFillsItsPlaceholders() = runComposeUiTest {
        ComposeUiTestShow("es", UiText.of(Res.string.rival_player, 7), this)
        awaitText("Jugador 7")
    }

    @Test
    fun aTextCanHoldOtherTexts() = runComposeUiTest {
        ComposeUiTestShow("es", UiText.of(Res.string.date_field_pick, UiText.of(Res.string.sign_out_title)), this)
        awaitText("Elegir Cerrar sesión")
    }

    @Test
    fun joinedTextsAreWrittenOneAfterTheOther() = runComposeUiTest {
        val joined = UiText.Joined(listOf(UiText.Plain("a"), UiText.of(Res.string.sign_out_title), UiText.Plain("c")), separator = " | ")
        ComposeUiTestShow("en", joined, this)
        awaitText("a | Sign out | c")
    }

    @Test
    fun twoTextsWithTheSameResourceAndArgumentsAreEqual() {
        assertEquals(UiText.of(Res.string.rival_player, 3), UiText.of(Res.string.rival_player, 3))
        assertEquals(UiText.Plain("x"), UiText.Plain("x"))
    }
}
