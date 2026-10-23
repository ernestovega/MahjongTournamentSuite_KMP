package com.etologic.mahjongtournamentsuite.presentation.language

import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
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

private class FakeLanguageStore(var saved: String? = null) : LanguageStore {
    val applied = mutableListOf<String?>()
    override fun load(): String? = saved
    override fun save(tag: String?) { saved = tag }
    override fun apply(tag: String?) {
        applied += tag
        Locale.setDefault(tag?.let(Locale::forLanguageTag) ?: Locale.ENGLISH)
    }
}

/** The texts load in the background, so the test waits until the text shows. */
@OptIn(ExperimentalTestApi::class)
private fun androidx.compose.ui.test.ComposeUiTest.awaitText(text: String) =
    waitUntil(timeoutMillis = 5_000) { onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

@OptIn(ExperimentalTestApi::class)
class AppLanguageTest {
    private val original = Locale.getDefault()

    @AfterTest
    fun restoreLocale() = Locale.setDefault(original)

    @Test
    fun aKnownTagGivesItsLanguage() {
        assertEquals(AppLanguage.Spanish, AppLanguage.fromTag("es"))
        assertEquals(AppLanguage.English, AppLanguage.fromTag("en"))
    }

    @Test
    fun anEmptyOrUnknownTagGivesTheLanguageOfTheDevice() {
        assertEquals(AppLanguage.System, AppLanguage.fromTag(null))
        assertEquals(AppLanguage.System, AppLanguage.fromTag(""))
        assertEquals(AppLanguage.System, AppLanguage.fromTag("xx"))
    }

    @Test
    fun everyLanguageHasADifferentTag() {
        val tags = AppLanguage.entries.mapNotNull { it.tag }
        assertEquals(tags.size, tags.toSet().size)
        assertEquals(null, AppLanguage.System.endonym)
    }

    @Test
    fun theSavedLanguageIsAppliedBeforeTheContentDraws() = runComposeUiTest {
        val store = FakeLanguageStore(saved = "es")
        setContent { ProvideAppLanguage(store) { Sample() } }
        awaitText("¿Quieres cerrar la sesión?")
        assertEquals(listOf<String?>("es"), store.applied)
    }

    @Test
    fun withoutASavedLanguageTheDeviceLanguageStays() = runComposeUiTest {
        val store = FakeLanguageStore(saved = null)
        setContent { ProvideAppLanguage(store) { Sample() } }
        awaitText("Do you want to sign out?")
        assertEquals(listOf<String?>(null), store.applied)
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

        onNodeWithText("Español").performClick()
        awaitText("¿Quieres cerrar la sesión?")
        assertEquals("es", store.saved)

        onNodeWithText("English").performClick()
        awaitText("Do you want to sign out?")
        assertEquals("en", store.saved)
    }

    @Test
    fun choosingTheDeviceLanguageClearsTheSavedChoice() = runComposeUiTest {
        val store = FakeLanguageStore(saved = "es")
        setContent { ProvideAppLanguage(store) { LanguageSelector() } }
        awaitText("Igual que el dispositivo")
        onNodeWithText("Igual que el dispositivo").performClick()
        awaitText("Same as the device")
        assertEquals(null, store.saved)
    }

    @Test
    fun theSelectorShowsEachLanguageInItsOwnName() = runComposeUiTest {
        setContent { ProvideAppLanguage(FakeLanguageStore()) { LanguageSelector() } }
        onNodeWithText("English").assertExists()
        onNodeWithText("Español").assertExists()
        onNodeWithText("Language").assertExists()
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
        onNodeWithText("Español").performClick()
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
