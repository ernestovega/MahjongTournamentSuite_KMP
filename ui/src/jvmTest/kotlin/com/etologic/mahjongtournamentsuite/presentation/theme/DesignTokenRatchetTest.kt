package com.etologic.mahjongtournamentsuite.presentation.theme

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The ratchet of the design system (docs/design-system.md). The screens still have values that should be tokens:
 * raw colors, raw corner shapes, and spacings outside the scale of [MtsSpacing]. This test counts them and fails when
 * a count goes up, so new code uses the tokens. When a migration lowers a count, lower its baseline here in the same
 * commit: the ratchet only turns one way.
 *
 * The theme package is not counted: it is where the raw values are defined.
 */
class DesignTokenRatchetTest {
    /** The counts on main when the ratchet started (2026-11-13). Lower them as the screens move to the tokens. */
    private val baselineRawColors = 31
    private val baselineRawCornerShapes = 27
    private val baselineOffScaleSpacings = 77

    private val rawColor = Regex("""Color\(0x[0-9A-Fa-f]{6,8}\)""")
    private val rawCornerShape = Regex("""RoundedCornerShape\(""")
    private val offScaleSpacing = Regex("""\b(6|10|14|18|20|22|28)\.dp""")

    /** The main source sets of every module. Gradle runs the tests with the module folder as the working directory. */
    private fun sourceFiles(): List<File> = listOf("designsystem", "ui", "adminApp", "playerApp", "androidApp", "playerAndroidApp")
        .map { File("../$it/src") }
        .filter { it.exists() }
        .flatMap { root ->
            root.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .filterNot { file -> file.path.split(File.separatorChar).any { it.endsWith("Test") || it == "test" } }
                .filterNot { file -> file.invariantSeparatorsPath.contains("presentation/theme/") }
                .toList()
        }

    private fun count(lineFilter: (String) -> Boolean = { true }, regex: Regex): Pair<Int, List<String>> {
        val hits = sourceFiles().flatMap { file ->
            file.readLines().mapIndexedNotNull { index, line ->
                val n = if (lineFilter(line)) regex.findAll(line).count() else 0
                if (n > 0) "${file.path}:${index + 1}" to n else null
            }
        }
        return hits.sumOf { it.second } to hits.map { it.first }
    }

    private fun check(what: String, token: String, baseline: Int, result: Pair<Int, List<String>>) {
        val (total, places) = result
        assertTrue(
            total <= baseline,
            "There are $total $what, more than the $baseline of the baseline. Use $token instead.\n" +
                places.joinToString("\n"),
        )
        if (total < baseline) println("DesignTokenRatchetTest: $what went down to $total. Lower the baseline from $baseline.")
    }

    @Test
    fun theTestFindsTheSources() {
        assertTrue(sourceFiles().size > 100, "The scan found ${sourceFiles().size} files in ${File(".").absolutePath}.")
    }

    @Test
    fun rawColorsDoNotGrow() = check(
        "raw colors Color(0x...)",
        "MaterialTheme.colorScheme or MtsTheme.colors",
        baselineRawColors,
        count(regex = rawColor),
    )

    @Test
    fun rawCornerShapesDoNotGrow() = check(
        "raw RoundedCornerShape(...)",
        "MaterialTheme.shapes or MtsTheme.shapes",
        baselineRawCornerShapes,
        count(regex = rawCornerShape),
    )

    @Test
    fun offScaleSpacingsDoNotGrow() = check(
        "paddings or gaps outside the spacing scale (6, 10, 14, 18, 20, 22 or 28 dp)",
        "MtsTheme.spacing or MtsTheme.layout",
        baselineOffScaleSpacings,
        count(lineFilter = { "padding(" in it || "spacedBy(" in it }, regex = offScaleSpacing),
    )
}
