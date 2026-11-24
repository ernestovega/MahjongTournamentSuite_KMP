package com.etologic.mahjongtournamentsuite.domain.guard

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The common code of every module also builds for the web (Wasm), where the JVM library does not exist. The JVM tests
 * still compile a JVM-only call in commonMain, so they pass while the web build fails: on 2026-11-24 `toSortedMap`
 * (domain) and `toSortedSet` (adminApp) broke the Deploy · Production runs for an hour.
 *
 * This test reads the commonMain sources of all the modules and fails on the JVM-only calls that are easy to write
 * by mistake. It is a quick warning, not a full check: `./gradlew compileKotlinWasmJs` is the full one.
 */
class CommonCodeIsMultiplatformTest {
    /** A JVM-only call and what to use in common code instead. */
    private val jvmOnly = listOf(
        Regex("""\.toSortedMap\(""") to "toSortedMap: use entries.sortedBy { it.key }.associate { it.toPair() }",
        Regex("""\.toSortedSet\(""") to "toSortedSet: use distinct().sorted()",
        Regex("""(?m)^import java\.""") to "java.* imports: use a Kotlin common API or an expect/actual",
        Regex("""(?<![\w.])String\.format\(""") to "String.format: build the text with a template or padStart",
        Regex("""(?<![\w.])synchronized\(|@Synchronized""") to "synchronized: use a Mutex of kotlinx.coroutines",
        Regex("""(?<![\w.])System\.""") to "System.*: use kotlin.time / kotlinx-datetime Clock",
        Regex("""(?<![\w.])Thread\.""") to "Thread.*: use coroutines (delay)",
        Regex("""(?<![\w.])Collections\.""") to "java.util.Collections: use the Kotlin collection functions",
        Regex("""\.(computeIfAbsent|putIfAbsent|removeIf)\(""") to "JVM Map/List methods: use getOrPut, removeAll",
        Regex("""\.stream\(\)""") to "Java streams: use the Kotlin collection functions or Sequence",
        Regex("""(?<![\w.])Comparator\.comparing""") to "Comparator.comparing: use compareBy",
        Regex("""(?<![\w.])Math\.[a-z]""") to "java.lang.Math: use kotlin.math",
        Regex("""(?<![\w.])(Character|Integer)\.|(?<![\w.])(Long|Double)\.(parse\w*|valueOf)\(""") to
            "java.lang boxes: use toInt(), toLongOrNull(), Char.isDigit(), Int.MAX_VALUE",
        Regex("""\.(toLowerCase|toUpperCase)\(\)""") to "toLowerCase/toUpperCase: use lowercase()/uppercase()",
        Regex("""\.getBytes\(""") to "getBytes: use encodeToByteArray()",
    )

    /** Gradle runs the tests with the module folder as the working directory. */
    private fun commonSources(): List<File> = File("..").listFiles().orEmpty()
        .map { File(it, "src/commonMain") }
        .filter { it.isDirectory }
        .flatMap { root -> root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }

    @Test
    fun theTestFindsTheCommonSourcesOfEveryModule() {
        val modules = commonSources().map { it.relativeTo(File("..")).path.substringBefore(File.separatorChar) }.toSet()
        assertTrue(
            modules.containsAll(listOf("domain", "data", "designsystem", "ui", "adminApp", "playerApp")),
            "The scan found the modules $modules in ${File("..").absolutePath}.",
        )
    }

    @Test
    fun commonCodeCallsNoJvmOnlyApi() {
        val problems = commonSources().flatMap { file ->
            val code = file.readText()
            jvmOnly.flatMap { (pattern, advice) ->
                pattern.findAll(code).map { match ->
                    val line = code.substring(0, match.range.first).count { it == '\n' } + 1
                    "${file.relativeTo(File("..")).path}:$line  ${match.value.trim()}  ($advice)"
                }.toList()
            }
        }
        assertTrue(
            problems.isEmpty(),
            "These calls exist only on the JVM, so the web (Wasm) build fails on them. Move them to jvmMain or " +
                "use the common API:\n" + problems.joinToString("\n"),
        )
    }
}
