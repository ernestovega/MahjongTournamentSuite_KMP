package com.etologic.mahjongtournamentsuite.presentation.language

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Every language of every module has the same texts and the same placeholders as the English base. */
class LocalizationParityTest {
    private val modules = listOf("ui", "playerApp", "adminApp")

    private fun resources(module: String): File {
        val here = File(System.getProperty("user.dir"))
        val root = generateSequence(here) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() }
        return File(root, "$module/src/commonMain/composeResources")
    }

    private fun keysOf(file: File) =
        Regex("""<(?:string|plurals) name="([^"]+)"""").findAll(file.readText()).map { it.groupValues[1] }.toSet()

    private fun placeholdersOf(file: File) =
        Regex("""<string name="([^"]+)">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL).findAll(file.readText())
            .associate { it.groupValues[1] to Regex("""%\d\$[sd]""").findAll(it.groupValues[2]).map { m -> m.value }.sorted().toList() }

    @Test
    fun everyLanguageHasTheKeysOfTheEnglishBase() {
        modules.forEach { module ->
            val base = resources(module)
            val english = keysOf(File(base, "values/strings.xml"))
            assertTrue(english.isNotEmpty(), "$module has no English texts")
            base.listFiles { f -> f.isDirectory && f.name.startsWith("values-") }.orEmpty().forEach { folder ->
                val keys = keysOf(File(folder, "strings.xml"))
                assertEquals(emptySet(), english - keys, "$module/${folder.name} misses texts")
                assertEquals(emptySet(), keys - english, "$module/${folder.name} has extra texts")
            }
        }
    }

    @Test
    fun everyLanguageUsesTheSamePlaceholdersAsTheEnglishBase() {
        modules.forEach { module ->
            val base = resources(module)
            val english = placeholdersOf(File(base, "values/strings.xml"))
            base.listFiles { f -> f.isDirectory && f.name.startsWith("values-") }.orEmpty().forEach { folder ->
                placeholdersOf(File(folder, "strings.xml")).forEach { (key, found) ->
                    english[key]?.let { assertEquals(it, found, "$module/${folder.name}: placeholders of $key") }
                }
            }
        }
    }

    @Test
    fun noTextHasADoublePercentSign() {
        modules.forEach { module ->
            val base = resources(module)
            base.walkTopDown().filter { it.name == "strings.xml" }.forEach {
                assertTrue("%%" !in it.readText(), "${it.path} has a double percent sign")
            }
        }
    }

    @Test
    fun noSpanishTextIsEmpty() {
        modules.forEach { module ->
            val file = File(resources(module), "values-es/strings.xml")
            assertTrue(Regex("""<string name="[^"]+">\s*</string>""").findAll(file.readText()).none(), "$module has an empty Spanish text")
        }
    }
}
