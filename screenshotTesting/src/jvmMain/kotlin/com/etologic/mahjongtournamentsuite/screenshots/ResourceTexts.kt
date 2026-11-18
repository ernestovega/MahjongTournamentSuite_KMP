package com.etologic.mahjongtournamentsuite.screenshots

import java.io.File
import java.util.Locale

/**
 * The text [key] of the strings.xml of [module] (for example "ui") in the language of the screenshot, with its `%1$d`
 * placeholders filled with [args]. A `ready` step uses it to wait for or click a text of another module in every language,
 * because the `Res` class of a module is internal. In its own module a test uses `getString(Res.string.key)` instead.
 */
fun resourceText(module: String, key: String, vararg args: Any): String {
    val root = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").exists() }
    val resources = File(root, "$module/src/commonMain/composeResources")
    val language = Locale.getDefault().language
    val folders = listOf("values-$language", "values").distinct()
    val text = folders.firstNotNullOfOrNull { folder ->
        File(resources, folder).listFiles { f -> f.extension == "xml" }.orEmpty().firstNotNullOfOrNull { file ->
            Regex("""<string name="${Regex.escape(key)}">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL).find(file.readText())?.groupValues?.get(1)
        }
    } ?: error("$module has no text $key")
    val plain = text.replace("\\n", "\n").replace("\\'", "'").replace("\\\"", "\"").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
    return args.foldIndexed(plain) { index, acc, arg -> acc.replace(Regex("""%${index + 1}\$[sd]"""), arg.toString()) }
}
