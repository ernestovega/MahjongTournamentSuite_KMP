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

    /**
     * The text files of the English base. strings.xml must be in every language. Another file, such as the reference
     * texts of the player app, may be missing in a language that has no translation yet: the English texts then show.
     */
    private fun textFiles(base: File) = File(base, "values").listFiles { f -> f.extension == "xml" }.orEmpty().sortedBy { it.name }

    private fun languages(base: File) = base.listFiles { f -> f.isDirectory && f.name.startsWith("values-") }.orEmpty()

    @Test
    fun everyLanguageHasTheKeysOfTheEnglishBase() {
        modules.forEach { module ->
            val base = resources(module)
            assertTrue(keysOf(File(base, "values/strings.xml")).isNotEmpty(), "$module has no English texts")
            textFiles(base).forEach { englishFile ->
                val english = keysOf(englishFile)
                languages(base).forEach { folder ->
                    val file = File(folder, englishFile.name)
                    if (file.exists() || englishFile.name == "strings.xml") {
                        val keys = keysOf(file)
                        assertEquals(emptySet(), english - keys, "$module/${folder.name}/${file.name} misses texts")
                        assertEquals(emptySet(), keys - english, "$module/${folder.name}/${file.name} has extra texts")
                    }
                }
            }
        }
    }

    @Test
    fun everyLanguageUsesTheSamePlaceholdersAsTheEnglishBase() {
        modules.forEach { module ->
            val base = resources(module)
            textFiles(base).forEach { englishFile ->
                val english = placeholdersOf(englishFile)
                languages(base).map { File(it, englishFile.name) }.filter { it.exists() }.forEach { file ->
                    placeholdersOf(file).forEach { (key, found) ->
                        english[key]?.let { assertEquals(it, found, "$module/${file.parentFile.name}: placeholders of $key") }
                    }
                }
            }
        }
    }

    @Test
    fun noTextHasADoublePercentSign() {
        modules.forEach { module ->
            val base = resources(module)
            base.walkTopDown().filter { it.extension == "xml" && it.parentFile.name.startsWith("values") }.forEach {
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
