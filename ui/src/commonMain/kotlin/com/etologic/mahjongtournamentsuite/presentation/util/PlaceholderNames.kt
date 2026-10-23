package com.etologic.mahjongtournamentsuite.presentation.util

/**
 * A slot without a person has the name "Player 7" in the data. This name is an English placeholder, not a name.
 * [localize] turns it into the word of the language of the app. Any other name stays as it is.
 */
object PlaceholderNames {
    private val pattern = Regex("^Player (\\d+)$")

    /** Gives the text for the number of a player, or null while the language is not known yet. */
    var playerTemplate: ((Int) -> String)? = null

    fun localize(name: String): String {
        val number = pattern.matchEntire(name.trim())?.groupValues?.get(1)?.toIntOrNull() ?: return name
        return playerTemplate?.invoke(number) ?: name
    }

    fun localizeAll(names: Map<Int, String>): Map<Int, String> = names.mapValues { localize(it.value) }
}
