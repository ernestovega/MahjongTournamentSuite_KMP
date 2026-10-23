package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class SpanishTextsUiTest {
    private val original = Locale.getDefault()

    @AfterTest
    fun restoreLocale() = Locale.setDefault(original)

    @Test
    fun theDialogUsesTheSpanishTextsWhenTheLanguageIsSpanish() = runComposeUiTest {
        Locale.setDefault(Locale.forLanguageTag("es"))
        setContent { SignOutConfirmDialog(onConfirm = {}, onDismiss = {}) }
        onNodeWithText("¿Quieres cerrar la sesión?").assertExists()
    }

    @Test
    fun theDialogUsesTheEnglishTextsForAnyOtherLanguage() = runComposeUiTest {
        Locale.setDefault(Locale.forLanguageTag("xx"))
        setContent { SignOutConfirmDialog(onConfirm = {}, onDismiss = {}) }
        onNodeWithText("Do you want to sign out?").assertExists()
    }
}
