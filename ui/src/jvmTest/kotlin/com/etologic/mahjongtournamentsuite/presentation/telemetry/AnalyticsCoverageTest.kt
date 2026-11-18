package com.etologic.mahjongtournamentsuite.presentation.telemetry

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every screen, dialog, tab, write and link of both apps sends its analytics event (docs/telemetry.md). The test
 * reads the sources, so a new screen or action without an event fails here before it ships:
 *
 * - a route is added with the `screen<>` helper, which counts `screen_view`;
 * - a screen composable is shown inside a route or a `TrackedScreen`;
 * - a dialog or bottom sheet is inside a `TrackedDialog`;
 * - a tab is a `CompactTab` or has a `TrackedTab` beside it;
 * - a call that changes data on the server (a repository function that does not read) is in a function that counts it;
 * - a link outside the app opens with `rememberOpenLink`;
 * - every event, admin action and link kind is sent somewhere and is in docs/telemetry.md.
 *
 * Until the release it is not a check of CI (the build of this module leaves it out when `CI` is set). Run it with
 * `./gradlew :ui:jvmTest --tests '*AnalyticsCoverageTest*'`.
 */
class AnalyticsCoverageTest {
    /** The screens that cannot count: the consent screen shows before the user accepts the analytics. */
    private val screensWithoutEvent = setOf("ConsentScreen", "TrackedScreen")

    /** Writes that a function other than the one with the call counts, with the reason. Key: `File.kt:function`. */
    private val writesCountedElsewhere = mapOf(
        "RoundTimerController.kt:updateRoundClock" to "The press of a Timer button counts round_clock in perform(), before the request.",
    )

    /** The repository functions that only read. Every other function of a repository changes data. */
    private val readPrefixes = Regex("^(get|list|load|lookup|whoAmI|current|saved|refresh|prefetch|resolve|tournamentsOf|clearSaved|preview)")

    private val presentationModules = listOf("ui", "adminApp", "playerApp")

    /** The main source sets. Gradle runs the tests with the module folder as the working directory. */
    private fun sources(modules: List<String>): List<File> = modules
        .map { File("../$it/src") }
        .filter { it.exists() }
        .flatMap { root ->
            root.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .filterNot { file -> file.path.split(File.separatorChar).any { it.endsWith("Test") || it == "test" } }
                .toList()
        }

    private class Source(val file: File) {
        val text: String = file.readText()

        /** The text with strings and comments as spaces, so that braces and names in them do not count. */
        val code: String = maskStringsAndComments(text)
        fun line(offset: Int) = code.substring(0, offset).count { it == '\n' } + 1
        fun where(offset: Int) = "${file.path}:${line(offset)}"
    }

    private val presentation by lazy { sources(presentationModules).map(::Source) }

    @Test
    fun theTestFindsTheSources() {
        assertTrue(presentation.size > 100, "The scan found ${presentation.size} files. The working directory is ${File(".").absolutePath}.")
    }

    @Test
    fun everyRouteCountsItsScreen() {
        val offenders = presentation.filter { it.file.name != "RoutePersistence.kt" }.flatMap { source ->
            Regex("""\bcomposable\s*[<(]""").findAll(source.code).map { source.where(it.range.first) }.toList()
        }
        assertEquals(emptyList(), offenders, "Add routes with the screen<> helper of RoutePersistence.kt: it counts screen_view.")
    }

    @Test
    fun everyScreenIsShownInsideATrackedScreen() {
        val definition = Regex("""\bfun\s+([A-Z]\w*Screen)\s*\(""")
        val screens = presentation.flatMap { source -> definition.findAll(source.code).map { it.groupValues[1] }.toList() }.toSet() - screensWithoutEvent
        assertTrue(screens.size > 10, "The scan found only $screens")
        val tracked = presentation.flatMap { source ->
            val spans = lambdaSpans(source.code, Regex("""\bscreen\s*<[^>]*>\s*""")) + lambdaSpans(source.code, Regex("""\bTrackedScreen\s*\("""))
            spans.flatMap { span -> Regex("""\b([A-Z]\w*Screen)\s*\(""").findAll(source.code.substring(span)).map { it.groupValues[1] }.toList() }
        }.toSet()
        assertEquals(emptySet(), screens - tracked, "Show these screens from a route (screen<>) or inside TrackedScreen, so they count screen_view.")
    }

    @Test
    fun everyDialogIsInsideATrackedDialog() {
        val dialog = Regex("""(?<![\w.])(?:androidx\.compose\.material3\.)?(AlertDialog|BasicAlertDialog|Dialog|ModalBottomSheet|DialogWindow)\s*\(""")
        val found = presentation.sumOf { source -> dialog.findAll(source.code).count() }
        assertTrue(found > 40, "The scan found only $found dialogs")
        val offenders = presentation.flatMap { source ->
            val spans = lambdaSpans(source.code, Regex("""\bTrackedDialog\s*\("""))
            dialog.findAll(source.code)
                .filterNot { source.code.substring(maxOf(0, it.range.first - 4), it.range.first).contains("fun ") }
                .filterNot { call -> spans.any { call.range.first in it } }
                .map { "${source.where(it.range.first)} ${it.groupValues[1]}" }
                .toList()
        }
        assertEquals(emptyList(), offenders, "Wrap every dialog in TrackedDialog(\"Name\") { ... }, so it counts dialog_view and view_time.")
    }

    @Test
    fun everyTabCountsItsView() {
        val tab = Regex("""(?<![\w.])(Tab|MtsTab|LeadingIconTab)\s*\(""")
        val found = presentation.sumOf { source -> tab.findAll(source.code).count() }
        assertTrue(found > 2, "The scan found only $found tabs")
        val offenders = presentation.filter { it.file.name != "CompactTab.kt" }.flatMap { source ->
            tab.findAll(source.code)
                .filterNot { source.code.substring(maxOf(0, it.range.first - 4), it.range.first).contains("fun ") }
                .filterNot { call -> source.code.substring(maxOf(0, call.range.first - 300), call.range.first).contains("TrackedTab(") }
                .map { source.where(it.range.first) }
                .toList()
        }
        assertEquals(emptyList(), offenders, "Use CompactTab, or call TrackedTab beside the tab, so it counts tab_view.")
    }

    @Test
    fun everyWriteToTheServerIsCounted() {
        val repositories = File("../domain/src/commonMain/kotlin/com/etologic/mahjongtournamentsuite/domain/repository")
        val writes = repositories.listFiles().orEmpty().filter { it.extension == "kt" }.flatMap { file ->
            Regex("""\bfun\s+(\w+)\s*\(""").findAll(maskStringsAndComments(file.readText())).map { it.groupValues[1] }.toList()
        }.filterNot { readPrefixes.containsMatchIn(it) }.toSet()
        assertTrue(writes.size > 30, "The scan found only these writes: $writes")
        val call = Regex("""\b\w*[Rr]epository\w*\s*\.\s*(${writes.joinToString("|")})\s*\(""")
        val counts = Regex("""\btrack(Action)?\s*\(|\btelemetry\.\w+\s*\(""")
        val offenders = presentation.flatMap { source ->
            val functions = functionBodies(source.code)
            // A small function of the same file that counts, like profileUpdated(), counts for its callers.
            val helpers = functions.filter { counts.containsMatchIn(it.body) }.map { it.name }
            val countsHere = if (helpers.isEmpty()) counts else Regex(counts.pattern + """|\b(${helpers.joinToString("|")})\s*\(""")
            call.findAll(source.code).mapNotNull { match ->
                val key = "${source.file.name}:${match.groupValues[1]}"
                val body = functions.lastOrNull { match.range.first in it.range }?.body ?: return@mapNotNull "${source.where(match.range.first)} ${match.value}"
                if (countsHere.containsMatchIn(body) || key in writesCountedElsewhere) null else "${source.where(match.range.first)} ${match.groupValues[1]}"
            }.toList()
        }
        assertEquals(
            emptyList(),
            offenders,
            "Count these writes: .track(telemetry, TelemetryEvent.X), .trackAction(telemetry, TelemetryAction.X) or telemetry.event(...)." +
                " A write counted in another function goes in writesCountedElsewhere, with the reason.",
        )
    }

    @Test
    fun everyLinkOutsideTheAppIsCounted() {
        val offenders = presentation.filter { "LocalUriHandler" in it.code }.filterNot { source ->
            source.file.name == "TrackedLinks.kt" ||
                // Android keeps a copy of the Green Book and opens it with a PDF app; the browser is only the fallback.
                (source.file.name == "GreenBook.android.kt" && "linkOpened(TelemetryLink.GREEN_BOOK)" in source.code)
        }.map { it.file.path }
        assertEquals(emptyList(), offenders, "Open links with rememberOpenLink(TelemetryLink.X), so they count link_opened.")
    }

    @Test
    fun everyEventAndWordIsSentAndDocumented() {
        val definitions = File("../domain/src/commonMain/kotlin/com/etologic/mahjongtournamentsuite/domain/telemetry/TelemetryEvent.kt")
        val definitionText = definitions.readText()
        // The functions at the end of the definitions file, like trackAction(), send events too. Their objects do not count.
        val helpers = Regex("""(?s)\nobject \w+ \{.*?\n}\n""").replace(maskStringsAndComments(definitionText), "\n")
        val all = sources(presentationModules + listOf("data", "domain")).filter { it.name != definitions.name }
            .joinToString("\n") { maskStringsAndComments(it.readText()) } + helpers
        val docs = File("../docs/telemetry.md").readText()
        val notSent = mutableListOf<String>()
        val notDocumented = mutableListOf<String>()
        listOf("TelemetryEvent", "TelemetryAction", "TelemetryLink", "TelemetryProfileAction", "TelemetryPreference", "TelemetryClockAction").forEach { objectName ->
            val body = definitionText.substringAfter("object $objectName {").substringBefore("\n}\n")
            Regex("""const val (\w+) = "([^"]+)"""").findAll(body).forEach { match ->
                val (constant, value) = match.destructured
                if (!Regex("""\b$objectName\.$constant\b""").containsMatchIn(all)) notSent += "$objectName.$constant"
                if ("`$value`" !in docs) notDocumented += value
            }
        }
        assertEquals(emptyList(), notSent, "These events or words are never sent. Send them or remove them.")
        assertEquals(emptyList(), notDocumented, "Describe these events or words in docs/telemetry.md.")
    }

    private class FunctionBody(val name: String, val range: IntRange, val body: String)

    /** Each function of the file, from its `fun` to the next `fun`. It is enough to find what a function calls. */
    private fun functionBodies(code: String): List<FunctionBody> {
        val starts = Regex("""\bfun\s+(?:<[^>]*>\s*)?(?:[\w.<>]+\.)?(\w+)\s*\(""").findAll(code).toList()
        return starts.mapIndexed { index, match ->
            val end = if (index + 1 < starts.size) starts[index + 1].range.first else code.length
            FunctionBody(match.groupValues[1], match.range.first until end, code.substring(match.range.first, end))
        }
    }

    /**
     * The ranges of the trailing lambdas of the calls that [call] finds: from the `{` after the arguments to its `}`.
     * [call] ends at the opening parenthesis of the arguments, or before the lambda for a call without arguments.
     */
    private fun lambdaSpans(code: String, call: Regex): List<IntRange> = call.findAll(code).mapNotNull { match ->
        var index = match.range.last + 1
        if (code.getOrNull(match.range.last) == '(') index = closing(code, match.range.last) + 1
        while (index < code.length && code[index].isWhitespace()) index++
        if (code.getOrNull(index) != '{') return@mapNotNull null
        index..closing(code, index)
    }.toList()

    private companion object {
        /** The offset of the bracket that closes the one at [open]. */
        fun closing(code: String, open: Int): Int {
            val opening = code[open]
            val close = if (opening == '(') ')' else '}'
            var depth = 0
            for (i in open until code.length) {
                when (code[i]) {
                    opening -> depth++
                    close -> if (--depth == 0) return i
                }
            }
            return code.length - 1
        }

        /** Replaces strings, characters and comments with spaces and keeps the line breaks. Templates `${...}` stay code. */
        fun maskStringsAndComments(text: String): String = Masker(text).run()
    }

    private class Masker(private val text: String) {
        private val out = StringBuilder(text)

        fun run(): String {
            code(0, untilBrace = false)
            return out.toString()
        }

        private fun blank(from: Int, to: Int) {
            for (k in from until minOf(to, text.length)) if (out[k] != '\n') out[k] = ' '
        }

        /** Masks one string from its opening quote and answers the offset after it. Templates are scanned as code. */
        private fun string(start: Int, raw: Boolean): Int {
            val quote = if (raw) "\"\"\"" else "\""
            var j = start + quote.length
            var segment = start
            while (j < text.length) {
                if (!raw && text[j] == '\\') {
                    j += 2
                    continue
                }
                if (text.startsWith(quote, j)) {
                    blank(segment, j + quote.length)
                    return j + quote.length
                }
                if (text.startsWith("\${", j)) {
                    blank(segment, j)
                    j = code(j + 2, untilBrace = true)
                    segment = j
                    continue
                }
                j++
            }
            blank(segment, j)
            return j
        }

        /** Scans code from [start]. With [untilBrace] it stops after the `}` that closes a template. */
        private fun code(start: Int, untilBrace: Boolean): Int {
            var j = start
            var depth = 0
            while (j < text.length) {
                when {
                    text.startsWith("//", j) -> {
                        val end = text.indexOf('\n', j).let { if (it < 0) text.length else it }
                        blank(j, end)
                        j = end
                    }
                    text.startsWith("/*", j) -> {
                        val end = text.indexOf("*/", j + 2).let { if (it < 0) text.length else it + 2 }
                        blank(j, end)
                        j = end
                    }
                    text.startsWith("\"\"\"", j) -> j = string(j, raw = true)
                    text[j] == '"' -> j = string(j, raw = false)
                    text[j] == '\'' -> {
                        var k = j + 1
                        while (k < text.length && text[k] != '\'') k += if (text[k] == '\\') 2 else 1
                        blank(j, k + 1)
                        j = k + 1
                    }
                    untilBrace && text[j] == '{' -> {
                        depth++
                        j++
                    }
                    untilBrace && text[j] == '}' -> {
                        if (depth == 0) return j + 1
                        depth--
                        j++
                    }
                    else -> j++
                }
            }
            return j
        }
    }
}
