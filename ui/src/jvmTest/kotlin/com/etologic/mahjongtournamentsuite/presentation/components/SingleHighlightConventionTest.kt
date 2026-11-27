package com.etologic.mahjongtournamentsuite.presentation.components

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A control lights up in one place only, and in its own shape. Two ways broke that before:
 *
 * - A screen drew its own hover or focus fill around a button that already lights up (a rounded square behind the
 *   circle of an icon button). Hover and focus fills live only in [FocusHighlightContainer] and in the rows of
 *   `FlatDataTable`; elsewhere use `FocusHighlightContainer` or `Modifier.focusRing`.
 * - A card or surface took the click in its modifier (`Card(Modifier.clickable(...))`). The ripple and the hover were
 *   then a rectangle with square corners over a card with round ones. Use `Card(onClick = ...)` or
 *   `Surface(onClick = ...)`, or clip the modifier to the shape of the card before the click.
 *
 * The behavior itself is in [SingleHighlightUiTest].
 */
class SingleHighlightConventionTest {
    private val hoverFillAllowed = setOf("KeyboardFocus.kt", "FlatDataTable.kt")

    private fun sourceFiles(): List<File> {
        val roots = listOf("designsystem", "ui", "adminApp", "playerApp", "suite")
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
    fun theTestFindsTheSources() {
        assert(sourceFiles().size > 100) { "The scan found ${sourceFiles().size} files. The working directory is ${File(".").absolutePath}." }
    }

    @Test
    fun noScreenDrawsItsOwnHoverFill() {
        val offenders = sourceFiles().filterNot { it.name in hoverFillAllowed }.flatMap { file ->
            file.readLines().mapIndexedNotNull { index, line ->
                if (line.contains("collectIsHoveredAsState()")) "${file.path}:${index + 1}: ${line.trim()}" else null
            }
        }
        assertEquals(emptyList(), offenders, "Draw hover and focus with FocusHighlightContainer or Modifier.focusRing, so only one area lights up.")
    }

    @Test
    fun noCardTakesTheClickInItsModifierWithoutItsShape() {
        val opener = Regex("""\b(Card|Surface|ElevatedCard|OutlinedCard)\($""")
        val offenders = sourceFiles().flatMap { file ->
            val lines = file.readLines()
            lines.indices.mapNotNull { index ->
                if (!opener.containsMatchIn(lines[index].trim())) return@mapNotNull null
                // The modifier is the first argument: one line, or a chain of lines that start with a dot.
                val first = lines.getOrNull(index + 1)?.trim() ?: return@mapNotNull null
                if (!first.startsWith("Modifier") && !first.startsWith("modifier =") && !first.startsWith("if (")) return@mapNotNull null
                val chain = buildString {
                    append(first)
                    var next = index + 2
                    while (next < lines.size && lines[next].trim().startsWith(".")) append(lines[next++].trim())
                }
                // A click with `indication = null` draws nothing; its container lights it up.
                val clicks = (chain.contains(".clickable(") || chain.contains(".clickable {")) && !chain.contains("indication = null")
                if (clicks && !chain.substringBefore(".clickable").contains(".clip(")) "${file.path}:${index + 2}: $first" else null
            }
        }
        assertEquals(emptyList(), offenders, "Use Card(onClick = ...) or Surface(onClick = ...), so the hover follows the round corners.")
    }
}
