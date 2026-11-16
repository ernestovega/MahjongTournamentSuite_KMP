package com.etologic.mahjongtournamentsuite.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.intl.Locale
import mahjongtournamentsuite.designsystem.generated.resources.Res
import mahjongtournamentsuite.designsystem.generated.resources.go3v2
import org.jetbrains.compose.resources.Font

/**
 * Languages whose letters the decorative font does not have. The web draws no fallback for a missing letter, so their
 * titles and buttons would be empty: these languages use the text font everywhere.
 */
internal val LanguagesWithoutDecorativeFont = setOf("ru", "uk", "be", "bg", "sr", "mk", "el", "zh", "ja", "ko")

/** The decorative font of titles and labels, or the text font when the language of the app has letters it lacks. */
@Composable
fun GangOfThreeFontFamily(): FontFamily {
    val font = Font(Res.font.go3v2)
    val language = Locale.current.language
    return remember(font, language) { if (language in LanguagesWithoutDecorativeFont) FontFamily.Default else FontFamily(font) }
}

/**
 * The same style in the text font. Titles and labels use the decorative font, which is hard to read when small and
 * takes more width. Player names and numbers use this instead, so long names fit and stay easy to read.
 */
fun TextStyle.inTextFont(): TextStyle = copy(fontFamily = FontFamily.Default)
