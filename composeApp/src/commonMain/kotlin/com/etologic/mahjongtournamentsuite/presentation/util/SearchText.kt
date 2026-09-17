package com.etologic.mahjongtournamentsuite.presentation.util

/** Returns lower-case text with common Latin accents folded to their base letters. */
fun normalizeSearchText(value: String): String = buildString(value.length) {
    value.lowercase().forEach { character ->
        append(
            when (character) {
                'á', 'à', 'â', 'ä', 'ã', 'å' -> 'a'
                'ç' -> 'c'
                'é', 'è', 'ê', 'ë' -> 'e'
                'í', 'ì', 'î', 'ï' -> 'i'
                'ñ' -> 'n'
                'ó', 'ò', 'ô', 'ö', 'õ', 'ø' -> 'o'
                'ú', 'ù', 'û', 'ü' -> 'u'
                'ý', 'ÿ' -> 'y'
                'š' -> 's'
                'ž' -> 'z'
                'ł' -> 'l'
                'ð' -> 'd'
                'þ' -> 't'
                'æ' -> "ae"
                'œ' -> "oe"
                'ß' -> "ss"
                else -> character.toString()
            },
        )
    }
}
