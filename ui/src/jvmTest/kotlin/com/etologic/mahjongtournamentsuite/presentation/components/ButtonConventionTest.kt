package com.etologic.mahjongtournamentsuite.presentation.components

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Keeps the press threshold on every button. A source file must not use a raw Material button, because it has no
 * threshold. It uses `Debounced*` (same look) or `Focused*` (with the focus halo). The two files that define the
 * wrappers are the only ones that may use the raw buttons.
 */
class ButtonConventionTest {
    private val rawButtons = listOf(
        "Button", "TextButton", "OutlinedButton", "IconButton", "ExtendedFloatingActionButton", "FloatingActionButton",
        "SmallFloatingActionButton", "LargeFloatingActionButton", "FilledTonalButton", "ElevatedButton",
        "FilledIconButton", "FilledTonalIconButton", "OutlinedIconButton", "IconToggleButton",
    )
    private val allowed = setOf("KeyboardFocus.kt", "DebouncedButtons.kt")

    /** The main source sets of every module. Gradle runs the tests with the module folder as the working directory. */
    private fun sourceFiles(): List<File> {
        val roots = listOf("ui", "adminApp", "playerApp", "androidApp", "playerAndroidApp")
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
    fun noSourceFileUsesARawMaterialButton() {
        val offenders = sourceFiles().filterNot { it.name in allowed }.flatMap { file ->
            file.readLines().mapIndexedNotNull { index, line ->
                val raw = rawButtons.any { name ->
                    line.trim() == "import androidx.compose.material3.$name" || line.contains("material3.$name(")
                } || line.trim() == "import androidx.compose.material3.*"
                if (raw) "${file.path}:${index + 1}: ${line.trim()}" else null
            }
        }
        assertEquals(emptyList(), offenders, "Use Debounced* or Focused* buttons, so every button has the press threshold.")
    }
}
