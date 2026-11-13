package com.etologic.mahjongtournamentsuite.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import io.github.takahirom.roborazzi.captureRoboImage

/** The window sizes of the screenshots, in dp at density 1. Phone is compact (< 600 dp), tablet medium, desktop expanded. */
enum class ScreenSize(val width: Int, val height: Int) {
    Phone(412, 915),
    Tablet(800, 1280),
    Desktop(1440, 900),
}

/** One screenshot of a screen: a window size and a theme. */
data class Variant(val size: ScreenSize, val dark: Boolean) {
    val fileName: String get() = "${size.name.lowercase()}-${if (dark) "dark" else "light"}.png"
}

val allVariants: List<Variant> = ScreenSize.entries.flatMap { size -> listOf(Variant(size, false), Variant(size, true)) }

/** Small differences of antialiasing between machines do not fail a test; a changed color or a moved text does. */
@OptIn(ExperimentalRoborazziApi::class)
private val options = RoborazziOptions(
    compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.001f),
)

/**
 * Takes a screenshot of [content] in every [Variant] and saves it in `screenshots/<name>/<size>-<theme>.png` of the module.
 * [content] gets the variant and must set the theme itself (see [screenshotsInMtsTheme]). [ready] waits until the screen
 * shows its data, for example with `waitUntil { onAllNodesWithText("...").fetchSemanticsNodes().isNotEmpty() }`.
 * With `recordRoborazziJvm` it writes the reference images, with `verifyRoborazziJvm` it compares against them, and with a
 * plain `jvmTest` it only checks that the screen renders. All the variants run even if one fails.
 */
@OptIn(ExperimentalTestApi::class)
fun screenshots(
    name: String,
    variants: List<Variant> = allVariants,
    ready: ComposeUiTest.() -> Unit = {},
    content: @Composable (Variant) -> Unit,
) {
    val failures = mutableListOf<Throwable>()
    for (variant in variants) {
        try {
            runDesktopComposeUiTest(variant.size.width, variant.size.height) {
                setContent { content(variant) }
                waitForIdle()
                ready()
                waitForIdle()
                onRoot().captureRoboImage("screenshots/$name/${variant.fileName}", roborazziOptions = options)
            }
        } catch (e: Throwable) {
            failures += AssertionError("$name ${variant.fileName}: ${e.message}", e)
        }
    }
    if (failures.isNotEmpty()) {
        val error = AssertionError("${failures.size} screenshot(s) of $name failed:\n" + failures.joinToString("\n") { it.message.orEmpty() })
        failures.forEach(error::addSuppressed)
        throw error
    }
}

/** [screenshots] with the content inside [MtsTheme], light or dark as the variant says. */
@OptIn(ExperimentalTestApi::class)
fun screenshotsInMtsTheme(
    name: String,
    variants: List<Variant> = allVariants,
    ready: ComposeUiTest.() -> Unit = {},
    content: @Composable () -> Unit,
) = screenshots(name, variants, ready) { variant -> MtsTheme(useDarkTheme = variant.dark) { content() } }
