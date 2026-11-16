package com.etologic.mahjongtournamentsuite.presentation.language

import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.runtime.Composable
import mahjongtournamentsuite.ui.generated.resources.Res
import mahjongtournamentsuite.ui.generated.resources.sign_out_message
import org.jetbrains.compose.resources.stringResource
import com.etologic.mahjongtournamentsuite.presentation.util.formatLocalizedDate
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

@Composable
private fun Sample() = Text(stringResource(Res.string.sign_out_message))

private class FakeLanguageStore(
    var saved: String? = null,
    val system: List<String> = emptyList(),
    var noticeSeen: Boolean = true,
) : LanguageStore {
    val applied = mutableListOf<String>()
    override fun systemTags(): List<String> = system
    override fun aiNoticeSeen(): Boolean = noticeSeen
    override fun saveAiNoticeSeen() {
        noticeSeen = true
    }
    override fun load(): String? = saved
    override fun save(tag: String) {
        saved = tag
    }
    override fun apply(tag: String) {
        applied += tag
        Locale.setDefault(Locale.forLanguageTag(tag))
    }
}

/** The texts load in the background, so the test waits until the text shows. */
@OptIn(ExperimentalTestApi::class)
private fun androidx.compose.ui.test.ComposeUiTest.awaitText(text: String) = waitUntil(timeoutMillis = 5_000) { onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

/** Opens the menu of the selector with a tap on the field, which shows the [current] language, and taps the option [name]. */
@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.chooseLanguage(current: String, name: String) {
    onAllNodesWithText(current).onFirst().performClick()
    waitUntil(timeoutMillis = 5_000) { onAllNodesWithText(name).fetchSemanticsNodes().isNotEmpty() }
    onAllNodesWithText(name).onLast().performClick()
}

@OptIn(ExperimentalTestApi::class)
class AppLanguageTest {
    private val original = Locale.getDefault()

    @AfterTest
    fun restoreLocale() = Locale.setDefault(original)

    @Test
    fun aKnownTagGivesItsLanguage() {
        assertEquals(AppLanguage.Spanish, AppLanguage.fromTag("es"))
        assertEquals(AppLanguage.French, AppLanguage.fromTag("fr"))
        assertEquals(AppLanguage.Italian, AppLanguage.fromTag("it"))
        assertEquals(AppLanguage.German, AppLanguage.fromTag("de"))
        assertEquals(AppLanguage.Swedish, AppLanguage.fromTag("sv"))
        assertEquals(AppLanguage.Dutch, AppLanguage.fromTag("nl"))
        assertEquals(AppLanguage.Russian, AppLanguage.fromTag("ru"))
        assertEquals(AppLanguage.English, AppLanguage.fromTag("en"))
    }

    @Test
    fun anEmptyOrUnknownTagGivesEnglish() {
        assertEquals(AppLanguage.English, AppLanguage.fromTag(null))
        assertEquals(AppLanguage.English, AppLanguage.fromTag(""))
        assertEquals(AppLanguage.English, AppLanguage.fromTag("xx"))
    }

    @Test
    fun theLanguagesOfTheDeviceGiveTheFirstOneThatTheAppHas() {
        assertEquals(AppLanguage.Spanish, AppLanguage.fromSystemTags(listOf("es-ES")))
        assertEquals(AppLanguage.Spanish, AppLanguage.fromSystemTags(listOf("es_MX")))
        assertEquals(AppLanguage.French, AppLanguage.fromSystemTags(listOf("xx-YY", "fr-CA", "it")))
        assertEquals(AppLanguage.Italian, AppLanguage.fromSystemTags(listOf("IT")))
        assertEquals(AppLanguage.German, AppLanguage.fromSystemTags(listOf("de-CH")))
        assertEquals(AppLanguage.Swedish, AppLanguage.fromSystemTags(listOf("sv-SE")))
        assertEquals(AppLanguage.Dutch, AppLanguage.fromSystemTags(listOf("nl-BE")))
        assertEquals(AppLanguage.Russian, AppLanguage.fromSystemTags(listOf("ru-RU")))
        assertEquals(AppLanguage.English, AppLanguage.fromSystemTags(listOf("ja-JP")))
        assertEquals(AppLanguage.English, AppLanguage.fromSystemTags(emptyList()))
    }

    @Test
    fun withoutASavedLanguageTheAppUsesTheLanguageOfTheDeviceAndDoesNotSaveIt() = runComposeUiTest {
        val store = FakeLanguageStore(saved = null, system = listOf("es-ES"))
        setContent { ProvideAppLanguage(store) { Sample() } }
        awaitText("¿Quieres cerrar la sesión?")
        assertEquals(listOf("es"), store.applied)
        assertEquals(null, store.saved)
    }

    @Test
    fun theSavedLanguageWinsOverTheLanguageOfTheDevice() = runComposeUiTest {
        val store = FakeLanguageStore(saved = "en", system = listOf("es-ES"))
        setContent { ProvideAppLanguage(store) { Sample() } }
        awaitText("Do you want to sign out?")
    }

    @Test
    fun aLanguageTranslatedWithAiShowsTheNoticeOneTime() = runComposeUiTest {
        val store = FakeLanguageStore(saved = "es", noticeSeen = false)
        setContent { ProvideAppLanguage(store) { AiTranslationNotice(onContact = {}) } }
        awaitText("Traducción con IA")
        onNodeWithText("Aceptar").performClick()
        waitForIdle()
        assertEquals(true, store.noticeSeen)
        onAllNodesWithText("Traducción con IA").assertCountEquals(0)
    }

    @Test
    fun theContactOfTheNoticeOpensTheSupport() = runComposeUiTest {
        val store = FakeLanguageStore(saved = "fr", noticeSeen = false)
        var contacted = false
        setContent { ProvideAppLanguage(store) { AiTranslationNotice(onContact = { contacted = true }) } }
        awaitText("Contact")
        onNodeWithText("Contact").performClick()
        waitForIdle()
        assertEquals(true, contacted)
        assertEquals(true, store.noticeSeen)
    }

    @Test
    fun englishDoesNotShowTheNotice() = runComposeUiTest {
        val store = FakeLanguageStore(saved = "en", noticeSeen = false)
        setContent {
            ProvideAppLanguage(store) {
                AiTranslationNotice(onContact = {})
                Sample()
            }
        }
        awaitText("Do you want to sign out?")
        onAllNodesWithText("Translated with AI").assertCountEquals(0)
    }

    @Test
    fun everyLanguageHasADifferentTag() {
        val tags = AppLanguage.entries.mapNotNull { it.tag }
        assertEquals(tags.size, tags.toSet().size)
    }

    @Test
    fun theSavedLanguageIsAppliedBeforeTheContentDraws() = runComposeUiTest {
        val store = FakeLanguageStore(saved = "es")
        setContent { ProvideAppLanguage(store) { Sample() } }
        awaitText("¿Quieres cerrar la sesión?")
        assertEquals(listOf("es"), store.applied)
    }

    @Test
    fun withoutASavedLanguageEnglishIsTheDefault() = runComposeUiTest {
        val store = FakeLanguageStore(saved = null)
        setContent { ProvideAppLanguage(store) { Sample() } }
        awaitText("Do you want to sign out?")
        assertEquals(listOf("en"), store.applied)
    }

    @Test
    fun theSelectorChangesTheLanguageAndSavesIt() = runComposeUiTest {
        val store = FakeLanguageStore(saved = null)
        setContent {
            ProvideAppLanguage(store) {
                LanguageSelector()
                Sample()
            }
        }
        awaitText("Do you want to sign out?")

        chooseLanguage("English", "Español")
        awaitText("¿Quieres cerrar la sesión?")
        assertEquals("es", store.saved)

        chooseLanguage("Español", "English")
        awaitText("Do you want to sign out?")
        assertEquals("en", store.saved)
    }

    @Test
    fun choosingEnglishSavesIt() = runComposeUiTest {
        val store = FakeLanguageStore(saved = "es")
        setContent { ProvideAppLanguage(store) { LanguageSelector() } }
        awaitText("Español")
        chooseLanguage("Español", "English")
        awaitText("English")
        assertEquals("en", store.saved)
    }

    @Test
    fun theSelectorShowsEachLanguageInItsOwnName() = runComposeUiTest {
        setContent { ProvideAppLanguage(FakeLanguageStore()) { LanguageSelector() } }
        onNodeWithText("English").assertExists()
        onNodeWithText("Language").assertExists()
        onNodeWithText("English").performClick()
        awaitText("Español")
        awaitText("Français")
        awaitText("Italiano")
        awaitText("Deutsch")
        awaitText("Svenska")
        awaitText("Nederlands")
        awaitText("Русский")
    }

    @Test
    fun theStateOfTheContentStartsNewWhenTheLanguageChanges() = runComposeUiTest {
        val store = FakeLanguageStore()
        var created = 0
        setContent {
            ProvideAppLanguage(store) {
                androidx.compose.runtime.remember { created++ }
                LanguageSelector()
                Text("content")
            }
        }
        assertEquals(1, created)
        chooseLanguage("English", "Español")
        waitForIdle()
        assertEquals(2, created)
    }

    @Test
    fun datesFollowTheLanguage() {
        Locale.setDefault(Locale.forLanguageTag("es"))
        val spanish = formatLocalizedDate("2026-10-12")
        Locale.setDefault(Locale.ENGLISH)
        val english = formatLocalizedDate("2026-10-12")
        assertNotEquals(english, spanish)
        assertEquals(true, spanish.contains("oct", ignoreCase = true))
    }
}

@OptIn(ExperimentalTestApi::class)
class PlaceholderLanguageTest {
    private val original = Locale.getDefault()

    @AfterTest
    fun restore() {
        Locale.setDefault(original)
        com.etologic.mahjongtournamentsuite.presentation.util.PlaceholderNames.playerTemplate = null
    }

    @Test
    fun theLanguageHostGivesTheNameOfAnEmptySlotInTheLanguageOfTheApp() = runComposeUiTest {
        val store = FakeLanguageStore(saved = "es")
        setContent { ProvideAppLanguage(store) { Text("x") } }
        waitUntil(timeoutMillis = 5_000) {
            com.etologic.mahjongtournamentsuite.presentation.util.PlaceholderNames.localize("Player 4") == "Jugador 4"
        }
    }
}
