package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.PlayerLevel
import com.etologic.mahjongtournamentsuite.domain.model.PlayerStatistics
import com.etologic.mahjongtournamentsuite.domain.model.PlayerSubstitution
import com.etologic.mahjongtournamentsuite.domain.model.SubstitutionReason
import com.etologic.mahjongtournamentsuite.domain.model.UserProfile
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import com.etologic.mahjongtournamentsuite.presentation.util.UiText
import com.etologic.mahjongtournamentsuite.presentation.util.toUiText
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.Test

/** The components draw their texts in Spanish when the language is Spanish, and in English for the base language. */
@OptIn(ExperimentalTestApi::class)
class SpanishScreensUiTest {
    private val original = Locale.getDefault()

    @AfterTest
    fun restoreLocale() = Locale.setDefault(original)

    private fun ComposeUiTest.show(language: String, content: @Composable () -> Unit) {
        Locale.setDefault(Locale.forLanguageTag(language))
        setContent { MtsTheme(useDarkTheme = false) { content() } }
    }

    /** The texts load in the background, so the test waits until the text shows. */
    private fun ComposeUiTest.awaitText(text: String) =
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun theProfileDialogIsInSpanish() = runComposeUiTest {
        val profile = UserProfile(uid = "u1", email = "ada@example.test", alias = "Ada", emaId = "10001")
        show("es") {
            ProfileDialog(profile, onSaveAlias = { error("unused") }, onSavePhoto = { _, _ -> error("unused") }, onRemovePhoto = { error("unused") }, onSaved = {}, onDismiss = {})
        }
        awaitText("Perfil")
        onNodeWithText("Número EMA").assertExists()
        onNodeWithText("Correo electrónico").assertExists()
        onNodeWithText("Guardar").assertExists()
        onNodeWithText("Cerrar").assertExists()
        onNodeWithText("Idioma").assertExists()
    }

    @Test
    fun theProfileDialogIsInEnglishByDefault() = runComposeUiTest {
        val profile = UserProfile(uid = "u1", email = "ada@example.test", alias = "Ada", emaId = null)
        show("en") {
            ProfileDialog(profile, onSaveAlias = { error("unused") }, onSavePhoto = { _, _ -> error("unused") }, onRemovePhoto = { error("unused") }, onSaved = {}, onDismiss = {})
        }
        awaitText("Profile")
        onNodeWithText("Language").assertExists()
        onNodeWithText("Español").assertExists()
        onNodeWithText("English").assertExists()
    }

    @Test
    fun theSignInFormIsInSpanish() = runComposeUiTest {
        show("es") {
            androidx.compose.runtime.CompositionLocalProvider(
                com.etologic.mahjongtournamentsuite.presentation.theme.LocalThemeController provides
                    com.etologic.mahjongtournamentsuite.presentation.theme.ThemeController(
                        com.etologic.mahjongtournamentsuite.presentation.theme.ThemePreference.System, false, {},
                    ),
            ) {
            SignInForm(
                email = "", onEmailChange = {}, password = "", onPasswordChange = {}, isLoading = false,
                errorMessage = null, infoMessage = null, backendVersion = null, emailRequester = FocusRequester(),
                onSubmit = {}, onForgotPassword = {}, onErrorDismiss = {},
            )
            }
        }
        awaitText("Contraseña")
        onNodeWithText("Correo electrónico").assertExists()
        onNodeWithText("¿Has olvidado la contraseña?").assertExists()
        onNodeWithText("Usa tu correo electrónico para iniciar sesión. Las cuentas son solo por invitación.").assertExists()
        onNodeWithContentDescription("Mostrar contraseña").assertExists()
    }

    @Test
    fun anErrorShowsItsMessageInTheLanguageOfTheApp() = runComposeUiTest {
        show("es") { AppErrorDialog(message = AppError.Network.toUiText(), onDismiss = {}) }
        awaitText("La petición de red ha fallado.")
        onNodeWithText("Error").assertExists()
        onNodeWithText("Aceptar").assertExists()
    }

    @Test
    fun aServerMessageWithoutATranslationShowsTheGenericSpanishMessage() = runComposeUiTest {
        show("es") { AppErrorDialog(message = AppError.Unexpected("boom").toUiText(), onDismiss = {}) }
        awaitText("Algo ha ido mal. Inténtalo de nuevo.")
    }

    @Test
    fun aMessageOfAPersonStaysAsWritten() = runComposeUiTest {
        show("es") { AppErrorDialog(message = UiText.Plain("Hola Ana"), onDismiss = {}) }
        awaitText("Hola Ana")
    }

    @Test
    fun theTableShowsTheDifferencesSwitchInSpanish() = runComposeUiTest {
        val seats = listOf(
            SeatDisplay("Ana", "32", "4.0", slotId = 1), SeatDisplay("Bruno", "-8", "1.0", slotId = 2),
            SeatDisplay("Carla", "12", "2.0", slotId = 3), SeatDisplay("David", "-36", "0.0", slotId = 4),
        )
        show("es") { TableSeatsView(seats = seats, handNumber = 3, centerLabel = "Mano 3", showPoints = true) }
        awaitText("Diferencias")
        onNodeWithContentDescription("Sobre las diferencias").assertExists()
    }

    @Test
    fun anEmptyStatisticsPanelIsInSpanish() = runComposeUiTest {
        show("es") { PlayerStatisticsPanel(PlayerStatistics(playerId = 1, results = emptyList())) }
        awaitText("Aún no hay estadísticas")
        onNodeWithText("Las estadísticas aparecen cuando se completa la primera mesa.").assertExists()
    }

    @Test
    fun levelsAndWindsHaveSpanishNames() = runComposeUiTest {
        show("es") {
            Column {
                PlayerLevel.entries.forEach { Text(it.label()) }
                SeatWind.entries.forEach { Text(it.label()) }
                Text(PlayerSubstitution(SubstitutionReason.ILLNESS, fromRound = 3, excludedFromEma = true).label())
            }
        }
        awaitText("Principiante")
        listOf("Intermedio", "Avanzado", "Experto", "Maestro", "Este", "Sur", "Oeste", "Norte").forEach {
            onNodeWithText(it).assertExists()
        }
        onNodeWithText("enfermo, sustituido desde la ronda 3, excluido de la clasificación EMA").assertExists()
    }

    @Test
    fun theDateFieldButtonsAreInSpanish() = runComposeUiTest {
        show("es") { DateField(label = "Inicio", value = "2026-10-12", onChange = {}) }
        awaitText("Inicio")
        onNodeWithContentDescription("Borrar Inicio").assertExists()
        onNodeWithContentDescription("Elegir Inicio").assertExists()
    }

    @Test
    fun theSignOutDialogIsInEnglishForAnotherLanguage() = runComposeUiTest {
        show("fr") { SignOutConfirmDialog(onConfirm = {}, onDismiss = {}) }
        awaitText("Do you want to sign out?")
    }
}
