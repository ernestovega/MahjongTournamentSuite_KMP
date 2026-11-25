package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Every button shows its text in capitals (Ernesto, 25-nov-2026). The content of a button uses [ButtonText], never a
 * plain `Text`. The scan reads the content lambda of every button call of the sources, aliases included.
 */
@OptIn(ExperimentalTestApi::class)
class ButtonTextConventionTest {
    private val buttons = listOf(
        "DebouncedButton", "DebouncedOutlinedButton", "DebouncedTextButton", "DebouncedIconButton",
        "DebouncedExtendedFloatingActionButton", "FocusedButton", "FocusedOutlinedButton", "FocusedTextButton",
        "FocusedIconButton", "AppTextButton",
    )
    private val allowed = setOf("KeyboardFocus.kt", "DebouncedButtons.kt", "MtsButtonText.kt")
    private val plainText = Regex("""(?<![\w.])Text\(""")

    private fun sourceFiles(): List<File> =
        listOf("designsystem", "ui", "adminApp", "playerApp", "androidApp")
            .map { File("../$it/src") }
            .filter { it.exists() }
            .flatMap { root ->
                root.walkTopDown()
                    .filter { it.isFile && it.extension == "kt" && it.name !in allowed }
                    .filterNot { file -> file.path.split(File.separatorChar).any { it.endsWith("Test") || it == "test" } }
                    .toList()
            }

    /** The index of the bracket that closes the one at [start], skipping strings and line comments. */
    private fun closing(source: String, start: Int, open: Char, close: Char): Int {
        var depth = 0
        var i = start
        while (i < source.length) {
            when {
                source.startsWith("\"\"\"", i) -> { i = source.indexOf("\"\"\"", i + 3) + 3; continue }
                source[i] == '"' -> {
                    var j = i + 1
                    while (j < source.length && source[j] != '"') { if (source[j] == '\\') j++; j++ }
                    i = j + 1
                    continue
                }
                source.startsWith("//", i) -> { i = source.indexOf('\n', i).let { if (it < 0) source.length else it }; continue }
                source[i] == open -> depth++
                source[i] == close -> { depth--; if (depth == 0) return i }
            }
            i++
        }
        return source.length - 1
    }

    private fun offenders(file: File): List<String> {
        val source = file.readText()
        val aliases = Regex("""import com\.etologic\.mahjongtournamentsuite\.presentation\.components\.(\w+) as (\w+)""")
            .findAll(source).filter { it.groupValues[1] in buttons }.map { it.groupValues[2] }
        val names = (buttons + aliases).distinct().sortedByDescending { it.length }
        val call = Regex("""(?<![\w.])(${names.joinToString("|")})\(""")
        return call.findAll(source).flatMap { match ->
            val before = source.substring(maxOf(0, match.range.first - 7), match.range.first)
            if (before.endsWith("fun ") || before.endsWith("import ")) return@flatMap emptySequence()
            val argsEnd = closing(source, match.range.last, '(', ')')
            val lambdas = buildList {
                Regex("""^\s*\{""").find(source.substring(argsEnd + 1))?.let {
                    val start = argsEnd + 1 + it.range.last
                    add(start..closing(source, start, '{', '}'))
                }
                source.indexOf("content = {", match.range.last).takeIf { it in 0 until argsEnd }?.let {
                    val start = it + "content = ".length
                    add(start..closing(source, start, '{', '}'))
                }
            }
            lambdas.asSequence().flatMap { range -> plainText.findAll(source.substring(range)) }.map { text ->
                val offset = source.substring(0, source.indexOf(text.value, match.range.first))
                "${file.path}:${offset.count { it == '\n' } + 1}"
            }
        }.distinct().toList()
    }

    @Test
    fun theScanFindsTheButtons() {
        val withButtonText = sourceFiles().count { it.readText().contains("ButtonText(") }
        assert(withButtonText > 50) { "Only $withButtonText files use ButtonText. The working directory is ${File(".").absolutePath}." }
    }

    @Test
    fun everyButtonShowsItsTextInCapitals() {
        assertEquals(emptyList(), sourceFiles().flatMap(::offenders), "Use ButtonText in the content of a button, so its text is in capitals.")
    }

    @Test
    fun buttonTextDrawsCapitalsButKeepsTheTextForScreenReaders() = runComposeUiTest {
        setContent { ButtonText("Save · straße", Modifier.testTag("label")) }
        onNodeWithTag("label").assertTextEquals("Save · straße")
        onNodeWithText("Save · straße").assertExists()
    }

    @Test
    fun capitalsAreTheSameInEveryLanguage() {
        assertEquals("ÉPINGLER · ÄNDERN · OMGÅNG · СОХРАНИТЬ · SS", buttonCaps("épingler · ändern · omgång · сохранить · ß"))
        assertEquals("FINISH", buttonCaps("finish"))
    }
}
