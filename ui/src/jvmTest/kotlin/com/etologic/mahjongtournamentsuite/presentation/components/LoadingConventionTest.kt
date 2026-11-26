package com.etologic.mahjongtournamentsuite.presentation.components

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Every screen loads with the logo and the progress below it, never with a text such as "Loading…"
 * (Ernesto, 2026-11-26). A screen passes `contentLoading` to [AppScaffold] on its first load. A dialog or a part of
 * a screen that loads by itself shows [AppLoadingCompact].
 */
class LoadingConventionTest {
    /** Texts that say how far a load is, not that it loads: they show next to the content that already loaded. */
    private val allowedNames = setOf("errors_loading_count")
    private val loadingText = Regex("""^\s*(Loading|Cargando)\b""", RegexOption.IGNORE_CASE)
    private val entry = Regex("""<(string|plurals) name="([^"]+)">\s*([^<]*)""")
    private val item = Regex("""<item quantity="[^"]+">\s*([^<]*)""")

    private val modules = listOf("designsystem", "ui", "adminApp", "playerApp", "androidApp")

    private fun englishStrings(): List<File> = modules.map { File("../$it/src") }.filter { it.exists() }.flatMap { root ->
        root.walkTopDown().filter { it.isFile && it.name == "strings.xml" && it.parentFile.name == "values" }.toList()
    }

    private fun sourceFiles(): List<File> = modules.map { File("../$it/src") }.filter { it.exists() }.flatMap { root ->
        root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filterNot { file -> file.path.split(File.separatorChar).any { it.endsWith("Test") || it == "test" } }
            .toList()
    }

    @Test
    fun noStringSaysLoading() {
        val offenders = englishStrings().flatMap { file ->
            var name: String? = null
            file.readLines().mapIndexedNotNull { index, line ->
                val text = entry.find(line)?.let { match ->
                    name = match.groupValues[2]
                    match.groupValues[3]
                } ?: item.find(line)?.groupValues?.get(1)
                if (text != null && name !in allowedNames && loadingText.containsMatchIn(text)) "${file.path}:${index + 1}: ${line.trim()}" else null
            }
        }
        assertEquals(emptyList(), offenders, "A screen loads with the logo and a progress, not a text: contentLoading of AppScaffold, or AppLoadingCompact.")
    }

    @Test
    fun noCodeSaysLoading() {
        // A text on the screen, not a line of the log.
        val literal = Regex("""Text\(\s*"(Loading|Cargando)\b""")
        val offenders = sourceFiles().flatMap { file ->
            file.readLines().mapIndexedNotNull { index, line ->
                val code = line.substringBefore("//")
                if (code.trim().startsWith("*") || !literal.containsMatchIn(code)) null else "${file.path}:${index + 1}: ${line.trim()}"
            }
        }
        assertEquals(emptyList(), offenders, "A screen loads with the logo and a progress, not a text: contentLoading of AppScaffold, or AppLoadingCompact.")
    }
}
