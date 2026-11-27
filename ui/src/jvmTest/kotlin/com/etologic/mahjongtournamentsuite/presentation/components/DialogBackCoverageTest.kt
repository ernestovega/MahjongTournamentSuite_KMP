package com.etologic.mahjongtournamentsuite.presentation.components

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The back closes only the open dialog, on every platform (Ernesto 27-nov). Android, iOS and the desktop do it by
 * themselves; on the web each dialog must add its entry to the browser history with [DialogBackHandler]. The test
 * reads the sources of both apps, so a new dialog without it fails here:
 *
 * - an `AlertDialog` is the one of the app (DialogBack.kt), never the one of Material 3;
 * - any other dialog or bottom sheet has a `DialogBackHandler` call right before it.
 */
class DialogBackCoverageTest {
    private val modules = listOf("ui", "adminApp", "playerApp", "suite")

    /** The main source sets. Gradle runs the tests with the module folder as the working directory. */
    private val sources: List<File> by lazy {
        modules.map { File("../$it/src") }.filter { it.exists() }.flatMap { root ->
            root.walkTopDown()
                .filter { it.isFile && it.extension == "kt" && it.name != "DialogBack.kt" }
                .filterNot { file -> file.path.split(File.separatorChar).any { it.endsWith("Test") || it == "test" } }
                .toList()
        }
    }

    private fun File.where(offset: Int) = "$path:${readText().substring(0, offset).count { it == '\n' } + 1}"

    @Test
    fun theTestFindsTheSources() {
        assertTrue(sources.size > 100, "The scan found ${sources.size} files. The working directory is ${File(".").absolutePath}.")
    }

    @Test
    fun everyAlertDialogIsTheOneOfTheApp() {
        val material = Regex("""import androidx\.compose\.material3\.AlertDialog\s*$|androidx\.compose\.material3\.AlertDialog\s*\(""", RegexOption.MULTILINE)
        val offenders = sources.flatMap { file -> material.findAll(file.readText()).map { file.where(it.range.first) }.toList() }
        assertEquals(emptyList(), offenders, "Use the AlertDialog of presentation.components, so the browser back closes it on the web.")
    }

    @Test
    fun everyOtherDialogClosesWithTheBack() {
        val dialog = Regex("""(?<![\w.])(BasicAlertDialog|Dialog|DatePickerDialog|ModalBottomSheet)\s*\(""")
        val offenders = sources.flatMap { file ->
            val text = file.readText()
            dialog.findAll(text)
                .filterNot { text.substring(maxOf(0, it.range.first - 4), it.range.first).contains("fun ") }
                .filterNot { text.substring(maxOf(0, it.range.first - 200), it.range.first).contains("DialogBackHandler(") }
                .map { "${file.where(it.range.first)} ${it.groupValues[1]}" }
                .toList()
        }
        assertEquals(emptyList(), offenders, "Call DialogBackHandler(onDismiss = ...) right before the dialog, so the browser back closes it on the web.")
    }
}
