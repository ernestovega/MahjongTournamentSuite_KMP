package com.etologic.mahjongtournamentsuite.presentation.components

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Keeps the fading edges and the scrollbar on every list (Ernesto, 2026-11-19).
 * A plain `verticalScroll(` / `horizontalScroll(` must be `verticalScrollWithIndicators(` /
 * `horizontalScrollWithIndicators(`, or come right after a `scrollIndicators(` of the same state. A lazy list
 * (`LazyColumn(`, `LazyRow(`, a lazy grid) must have `scrollIndicators(` within its first 12 lines.
 */
class ScrollIndicatorsConventionTest {
    private val allowed = setOf("MtsScrollIndicators.kt")
    private val lazy = Regex("""\b(LazyColumn|LazyRow|LazyVerticalGrid|LazyHorizontalGrid|LazyVerticalStaggeredGrid)\(""")
    private val plainScroll = Regex("""\.(verticalScroll|horizontalScroll)\(""")

    private fun sourceFiles(): List<File> {
        val roots = listOf("designsystem", "ui", "adminApp", "playerApp", "androidApp")
            .map { File("../$it/src") }
            .filter { it.exists() }
        return roots.flatMap { root ->
            root.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .filterNot { file -> file.path.split(File.separatorChar).any { it.endsWith("Test") || it == "test" } }
                .toList()
        }
    }

    @Test
    fun everyScrollHasItsIndicators() {
        val offenders = sourceFiles().filterNot { it.name in allowed }.flatMap { file ->
            val lines = file.readLines()
            lines.mapIndexedNotNull { index, line ->
                val code = line.substringBefore("//")
                if (code.trim().startsWith("import ") || code.trim().startsWith("*")) return@mapIndexedNotNull null
                val missing = when {
                    plainScroll.containsMatchIn(code) ->
                        // The indicators are on the same line or on one of the two lines before (a modifier chain).
                        lines.subList((index - 2).coerceAtLeast(0), index + 1).none { "scrollIndicators(" in it }
                    lazy.containsMatchIn(code) && !code.contains("fun ") ->
                        lines.subList(index, (index + 12).coerceAtMost(lines.size)).none { "scrollIndicators(" in it }
                    else -> false
                }
                if (missing) "${file.path}:${index + 1}: ${line.trim()}" else null
            }
        }
        assertEquals(
            emptyList(),
            offenders,
            "Every list needs its fading edges and scrollbar: Modifier.scrollIndicators(state), or " +
                "verticalScrollWithIndicators() / horizontalScrollWithIndicators() (docs/design-system.md).",
        )
    }
}
