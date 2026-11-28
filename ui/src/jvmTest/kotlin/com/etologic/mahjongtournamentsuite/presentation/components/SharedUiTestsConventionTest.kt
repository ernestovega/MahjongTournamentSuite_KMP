package com.etologic.mahjongtournamentsuite.presentation.components

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The UI tests in `src/uiTest` run on the desktop and on the iOS simulator alike (docs/ios-release.md), so they can
 * use only common Kotlin. A test that needs Java (`java.util.Locale`, `java.lang.reflect.Proxy`, `::class.java`,
 * `"%08d".format(…)`) or the desktop test runner (`runDesktopComposeUiTest`) goes into `src/jvmTest` instead. Without this check the
 * workflow Build · iOS fails to compile the tests, and only on macOS.
 */
class SharedUiTestsConventionTest {
    private val javaOnly = Regex("""^\s*import java\.|runDesktopComposeUiTest|java\.lang\.reflect|::class\.java\b|"\.format\(""")

    @Test
    fun sharedUiTestsUseOnlyCommonKotlin() {
        val offenders = listOf("designsystem", "ui", "adminApp", "playerApp", "suite")
            .map { File("../$it/src/uiTest") }
            .filter { it.exists() }
            .flatMap { root -> root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    if (javaOnly.containsMatchIn(line.substringBefore("//"))) "${file.path}:${index + 1}: ${line.trim()}" else null
                }
            }
        assertEquals(emptyList(), offenders, "Move these tests to src/jvmTest, or use common Kotlin only")
    }

    /**
     * A test hook (`LocalXxxForTests` in commonMain) replaces a system chooser that a test cannot use. The shared UI
     * tests run on the desktop and on the iOS simulator, so both pickers must read it: an iOS picker that does not
     * opens UIKit in the test process, and the test fails or crashes only there.
     */
    @Test
    fun everyTestHookIsReadOnTheDesktopAndOnIos() {
        val modules = listOf("designsystem", "ui", "adminApp", "playerApp", "suite")
        fun kotlinIn(sourceSet: String) = modules.map { File("../$it/src/$sourceSet") }.filter { it.exists() }
            .flatMap { root -> root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }
            .joinToString("\n") { it.readText() }
        val hooks = Regex("""val (Local\w+ForTests)\b""").findAll(kotlinIn("commonMain")).map { it.groupValues[1] }.toSet()
        val missing = listOf("jvmMain", "iosMain").flatMap { sourceSet ->
            val code = kotlinIn(sourceSet)
            hooks.filterNot { "$it.current" in code }.map { "$it is not read in $sourceSet" }
        }
        assertEquals(emptyList(), missing)
    }
}
