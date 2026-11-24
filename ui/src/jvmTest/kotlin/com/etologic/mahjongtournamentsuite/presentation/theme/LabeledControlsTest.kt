package com.etologic.mahjongtournamentsuite.presentation.theme

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Every checkbox, switch and radio button of the apps changes when the user taps its text, not only the box
 * (Ernesto, 2026-11-24). The screens use MtsCheckboxRow, MtsSwitchRow and MtsRadioRow of :designsystem, where the
 * whole line is the control. This test reads the sources and fails when a screen calls Material's Checkbox,
 * TriStateCheckbox, Switch or RadioButton with its own click: then a tap on the text next to it does nothing.
 *
 * A Material control with `onCheckedChange = null` (or `onClick = null`) is allowed: its parent takes the tap, with
 * the text inside it. The few controls in [allowed] have no text of their own; each one says why.
 */
class LabeledControlsTest {
    /** File name to the number of bare controls it may have, and why. */
    private val allowed = mapOf(
        // The box that selects a row of the list of users: a tap on the row opens the user, so only the box selects.
        "UsersTable.kt" to 1,
        // The box that selects a row of the list of EMA players: a tap on the row opens the player.
        "PlayerBaseScreen.kt" to 1,
        // The boxes of the hand table: the box keeps the focus for the arrow keys; a tap on its name checks it too.
        "TableInputs.kt" to 1,
    )

    private val control = Regex("""(?<![\w.])(Checkbox|TriStateCheckbox|Switch|RadioButton)\(""")

    /** The main source sets of the apps. Gradle runs the tests with the module folder as the working directory. */
    private fun sourceFiles(): List<File> = listOf("ui", "adminApp", "playerApp", "androidApp")
        .map { File("../$it/src") }
        .filter { it.exists() }
        .flatMap { root ->
            root.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .filterNot { file -> file.path.split(File.separatorChar).any { it.endsWith("Test") || it == "test" } }
                .toList()
        }

    /** The text of the call that starts at [start], up to its closing parenthesis. */
    private fun callAt(code: String, start: Int): String {
        var depth = 0
        for (i in start until code.length) {
            when (code[i]) {
                '(' -> depth++
                ')' -> if (--depth == 0) return code.substring(start, i + 1)
            }
        }
        return code.substring(start)
    }

    private val withoutOwnClick = Regex("""\b(onCheckedChange|onClick)\s*=\s*null\b""")

    @Test
    fun theTestFindsTheSources() {
        assertTrue(sourceFiles().size > 100, "The scan found ${sourceFiles().size} files in ${File(".").absolutePath}.")
    }

    @Test
    fun everyCheckboxSwitchAndRadioTogglesFromItsText() {
        val bare = sourceFiles().flatMap { file ->
            val code = file.readText()
            control.findAll(code)
                .filterNot { withoutOwnClick.containsMatchIn(callAt(code, it.range.last)) }
                .map { match -> file to code.substring(0, match.range.first).count { it == '\n' } + 1 }
                .toList()
        }
        val problems = bare.groupBy({ it.first.name }, { "${it.first.path}:${it.second}" })
            .filter { (name, places) -> places.size > (allowed[name] ?: 0) }
        assertTrue(
            problems.isEmpty(),
            "These checkboxes, switches or radio buttons change only from the box, not from their text. " +
                "Use MtsCheckboxRow, MtsSwitchRow or MtsRadioRow (designsystem), or give the control no click " +
                "(onCheckedChange = null / onClick = null) inside a toggleable or selectable line:\n" +
                problems.values.flatten().joinToString("\n"),
        )
    }

    @Test
    fun theAllowedListIsNotStale() {
        val counts = sourceFiles().groupBy { it.name }.mapValues { (_, files) ->
            files.sumOf { file ->
                val code = file.readText()
                control.findAll(code).count { !withoutOwnClick.containsMatchIn(callAt(code, it.range.last)) }
            }
        }
        val stale = allowed.filter { (name, n) -> (counts[name] ?: 0) < n }
        assertTrue(stale.isEmpty(), "Lower or remove these entries of the allowed list: $stale")
    }
}
