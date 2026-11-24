package com.etologic.mahjongtournamentsuite.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import io.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO

/** The window sizes of the screenshots, in dp at density 1. Phone is compact (< 600 dp), tablet medium, desktop expanded. */
enum class ScreenSize(val width: Int, val height: Int) {
    Phone(412, 915),

    /** A small phone. With the texts of the other languages, often longer than the English ones, it shows what does not fit. */
    Narrow(360, 760),
    Tablet(800, 1280),
    Desktop(1440, 900),
}

/** The languages of the apps besides English. A screen and a dialog get a screenshot in each one, see [allVariants]. */
val otherLanguages: List<String> = listOf("de", "es", "fr", "it", "nl", "ru", "sv")

/**
 * One screenshot of a screen: a window size, a theme, a language (English when [language] is null) and the size of the
 * text that the user chose in the system settings ([fontScale], 1 is the normal size).
 */
data class Variant(val size: ScreenSize, val dark: Boolean, val language: String? = null, val fontScale: Float = 1f) {
    val fileName: String get() =
        "${size.name.lowercase()}-${if (dark) "dark" else "light"}${language?.let { "-$it" }.orEmpty()}" +
            (if (fontScale == 1f) "" else "-font${(fontScale * 100).toInt()}") + ".png"
}

/**
 * A phone, light, with the large text of the accessibility settings (Android allows up to 2×): the texts that are cut or
 * overlap when a player needs bigger letters (audit 2026-11-24, item 10).
 */
val phoneWithLargeText: List<Variant> = listOf(1.5f, 2f).map { Variant(ScreenSize.Phone, dark = false, fontScale = it) }

/** A small phone, light, in each language besides English: the texts that do not fit or break the layout of a dialog. */
val narrowInEveryLanguage: List<Variant> = otherLanguages.map { Variant(ScreenSize.Narrow, dark = false, language = it) }

/** A phone, light, in each language besides English: the same window as `phone-light.png`, to compare them side by side. */
val phoneInEveryLanguage: List<Variant> = otherLanguages.map { Variant(ScreenSize.Phone, dark = false, language = it) }

/** Components: phone width, light and dark. Their size does not depend on the window. */
val componentVariants: List<Variant> = listOf(Variant(ScreenSize.Phone, false), Variant(ScreenSize.Phone, true))

/**
 * Dialogs: phone and desktop, light and dark, and a small phone in every other language. A dialog keeps its width on a
 * tablet, so the tablet adds nothing. On a phone a dialog takes the width of the window, so the small phone catches the
 * texts that do not fit.
 */
val dialogVariants: List<Variant> =
    listOf(ScreenSize.Phone, ScreenSize.Desktop).flatMap { listOf(Variant(it, false), Variant(it, true)) } + narrowInEveryLanguage +
        phoneWithLargeText

/** A component that is a dialog: [componentVariants] and the small phone in every other language. */
val componentDialogVariants: List<Variant> = componentVariants + narrowInEveryLanguage

/** Screens: phone, tablet and desktop, light and dark, the phone in every other language and the phone with large text. */
val allVariants: List<Variant> =
    listOf(ScreenSize.Phone, ScreenSize.Tablet, ScreenSize.Desktop).flatMap { size -> listOf(Variant(size, false), Variant(size, true)) } +
        phoneInEveryLanguage + phoneWithLargeText

/** Small differences of antialiasing between machines do not fail a test; a changed color or a moved text does. */
@OptIn(ExperimentalRoborazziApi::class)
private val options = RoborazziOptions(
    compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.001f),
)

/**
 * Takes a screenshot of [content] in every [Variant] and saves it in `screenshots/<name>/<size>-<theme>[-<language>].png` of the module.
 * [content] gets the variant and must set the theme itself (see [screenshotsInMtsTheme]). [ready] waits until the screen
 * shows its data, for example with `waitUntil { onAllNodesWithText("...").fetchSemanticsNodes().isNotEmpty() }`.
 * With `recordRoborazziJvm` it writes the reference images, with `verifyRoborazziJvm` it compares against them, and with a
 * plain `jvmTest` it only checks that the screen renders. All the variants run even if one fails.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
fun screenshots(
    name: String,
    variants: List<Variant> = allVariants,
    ready: ComposeUiTest.() -> Unit = {},
    content: @Composable (Variant) -> Unit,
) {
    val failures = mutableListOf<Throwable>()
    // Some screens switch to Dispatchers.Main after a request. The JVM tests have no Main, so it runs in place.
    Dispatchers.setMain(Dispatchers.Unconfined)
    val locale = Locale.getDefault()
    for (variant in variants) {
        // The texts of the apps follow the language of the system.
        Locale.setDefault(variant.language?.let(Locale::forLanguageTag) ?: locale)
        try {
            runDesktopComposeUiTest(variant.size.width, variant.size.height) {
                setContent {
                    val density = LocalDensity.current
                    CompositionLocalProvider(LocalDensity provides Density(density.density, variant.fontScale)) { content(variant) }
                }
                waitForIdle()
                try {
                    ready()
                } catch (e: Throwable) {
                    // With large text the awaited text can be pushed below the window: the screen is ready, it shows less.
                    if (variant.fontScale != 1f) return@runDesktopComposeUiTest captureLarge(name, variant)
                    // The image of a screen that never got ready shows why: an error, an empty list, another text.
                    val file = File("build/outputs/roborazzi/not-ready/$name/${variant.fileName}").apply { parentFile.mkdirs() }
                    ImageIO.write(onAllNodes(isRoot()).onFirst().captureToImage().toAwtImage(), "png", file)
                    throw AssertionError("Not ready, see ${file.path}: ${e.message}", e)
                }
                waitForIdle()
                onAllNodes(isRoot()).onFirst().captureRoboImage("screenshots/$name/${variant.fileName}", roborazziOptions = options)
            }
        } catch (e: Throwable) {
            failures += AssertionError("$name ${variant.fileName}: ${e.message}", e)
        }
    }
    Locale.setDefault(locale)
    Dispatchers.resetMain()
    if (failures.isNotEmpty()) {
        val error = AssertionError("${failures.size} screenshot(s) of $name failed:\n" + failures.joinToString("\n") { it.message.orEmpty() })
        failures.forEach(error::addSuppressed)
        throw error
    }
}

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.captureLarge(name: String, variant: Variant) {
    waitForIdle()
    onAllNodes(isRoot()).onFirst().captureRoboImage("screenshots/$name/${variant.fileName}", roborazziOptions = options)
}

/** [screenshots] with the content inside [MtsTheme], light or dark as the variant says. */
@OptIn(ExperimentalTestApi::class)
fun screenshotsInMtsTheme(
    name: String,
    variants: List<Variant> = allVariants,
    ready: ComposeUiTest.() -> Unit = {},
    content: @Composable () -> Unit,
) = screenshots(name, variants, ready) { variant -> MtsTheme(useDarkTheme = variant.dark) { content() } }
