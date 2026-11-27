package com.etologic.mahjongtournamentsuite.presentation.theme

import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.runComposeUiTest
import java.util.Locale
import java.util.prefs.Preferences
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * The theme preference: the order of the toggle, the texts, the app controller that saves the choice and the screen
 * controller that does not.
 */
@OptIn(ExperimentalTestApi::class)
class ThemeControllerUiTest {
    private val preferences = Preferences.userNodeForPackage(ThemePreferenceStorage::class.java)
    private var saved: String? = null
    private val originalLocale = Locale.getDefault()

    @BeforeTest
    fun keepTheStoredPreference() {
        saved = preferences.get("themePreference", null)
        Locale.setDefault(Locale.ENGLISH)
    }

    @AfterTest
    fun restoreTheStoredPreference() {
        saved?.let { preferences.put("themePreference", it) } ?: preferences.remove("themePreference")
        Locale.setDefault(originalLocale)
    }

    @Test
    fun theToggleGoesLightDarkSystem() {
        assertEquals(ThemePreference.Dark, ThemePreference.Light.next())
        assertEquals(ThemePreference.System, ThemePreference.Dark.next())
        assertEquals(ThemePreference.Light, ThemePreference.System.next())
    }

    @Test
    fun aStoredValueThatIsNotAPreferenceIsIgnored() {
        preferences.put("themePreference", "Sepia")
        assertEquals(null, storedThemePreference())
        preferences.put("themePreference", "Dark")
        assertEquals(ThemePreference.Dark, storedThemePreference())
    }

    @Test
    fun eachPreferenceHasItsText() = runComposeUiTest {
        setContent { ThemePreference.entries.forEach { Text(it.contentDescription) } }
        listOf("Theme: System", "Theme: Light", "Theme: Dark").forEach { onNodeWithText(it).assertIsDisplayed() }
    }

    @Test
    fun theAppControllerSavesTheChoice() = runComposeUiTest {
        preferences.put("themePreference", "Light")
        lateinit var controller: ThemeController
        setContent { controller = rememberThemeController() }
        waitForIdle()
        assertEquals(ThemePreference.Light, controller.preference)

        controller.onTogglePreference()
        waitForIdle()
        assertEquals(ThemePreference.Dark, controller.preference)
        assertEquals("Dark", preferences.get("themePreference", null))

        controller.onSelectPreference(ThemePreference.System)
        waitForIdle()
        assertEquals(ThemePreference.System, controller.preference)
        assertEquals("System", preferences.get("themePreference", null))
    }

    @Test
    fun theScreenControllerChangesOnlyItsScreen() = runComposeUiTest {
        preferences.put("themePreference", "Light")
        lateinit var controller: ThemeController
        setContent { controller = rememberScreenThemeController() }
        waitForIdle()
        controller.onTogglePreference()
        waitForIdle()
        assertEquals(ThemePreference.Dark, controller.preference)
        assertEquals(true, controller.isDarkTheme)
        controller.onSelectPreference(ThemePreference.Light)
        waitForIdle()
        assertEquals(ThemePreference.Light, controller.preference)
        assertEquals("Light", preferences.get("themePreference", null))
    }

    @Test
    fun aScreenWithoutAControllerFails() = runComposeUiTest {
        assertFailsWith<IllegalStateException> {
            setContent { LocalThemeController.current }
        }
    }
}
