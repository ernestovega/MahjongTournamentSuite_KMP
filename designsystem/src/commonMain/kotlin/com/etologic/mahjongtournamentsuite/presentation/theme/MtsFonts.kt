package com.etologic.mahjongtournamentsuite.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.intl.Locale
import mahjongtournamentsuite.designsystem.generated.resources.Res
import mahjongtournamentsuite.designsystem.generated.resources.go3v2
import mahjongtournamentsuite.designsystem.generated.resources.mts_cyrillic_caps
import org.jetbrains.compose.resources.Font

/** The font of titles and labels for a language. */
internal enum class TitleFont {
    /** Gang of Three, all in capitals. */
    Decorative,

    /** A capitals-only font with Cyrillic letters (MTS Cyrillic Caps, from Russo One), so it looks like Gang of Three. */
    CyrillicCaps,

    /** The text font: for letters that neither title font has. */
    Text,
}

/** Languages written in Cyrillic: Gang of Three has no Cyrillic letters. */
private val CyrillicLanguages = setOf("ru", "uk", "be", "bg", "sr", "mk")

/**
 * Languages with letters that no title font has: Greek and CJK, and the ø of Danish and Norwegian (the å and Å of
 * Swedish were added to Gang of Three). The web and the desktop draw nothing for a missing letter, so titles and buttons
 * would lose letters: these languages use the text font everywhere.
 */
private val LanguagesWithoutTitleFont = setOf("el", "zh", "ja", "ko", "da", "nb", "nn", "no")

internal fun titleFontFor(language: String): TitleFont = when (language) {
    in CyrillicLanguages -> TitleFont.CyrillicCaps
    in LanguagesWithoutTitleFont -> TitleFont.Text
    else -> TitleFont.Decorative
}

/** The font of titles and labels in the language of the app: Gang of Three, its Cyrillic look-alike or the text font. */
@Composable
fun GangOfThreeFontFamily(): FontFamily {
    val decorative = Font(Res.font.go3v2)
    val cyrillic = Font(Res.font.mts_cyrillic_caps)
    val titleFont = titleFontFor(Locale.current.language)
    return remember(decorative, cyrillic, titleFont) {
        when (titleFont) {
            TitleFont.Decorative -> FontFamily(decorative)
            TitleFont.CyrillicCaps -> FontFamily(cyrillic)
            TitleFont.Text -> FontFamily.Default
        }
    }
}

/**
 * The same style in the text font. Titles and labels use the decorative font, which is hard to read when small and
 * takes more width. Player names and numbers use this instead, so long names fit and stay easy to read.
 */
fun TextStyle.inTextFont(): TextStyle = copy(fontFamily = FontFamily.Default)
