package com.etologic.mahjongtournamentsuite.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import mahjongtournamentsuite.designsystem.generated.resources.Res
import mahjongtournamentsuite.designsystem.generated.resources.go3v2
import org.jetbrains.compose.resources.Font

@Composable
fun GangOfThreeFontFamily(): FontFamily {
    val font = Font(Res.font.go3v2)
    return remember(font) { FontFamily(font) }
}

/**
 * The same style in the text font. Titles and labels use the decorative font, which is hard to read when small and
 * takes more width. Player names and numbers use this instead, so long names fit and stay easy to read.
 */
fun TextStyle.inTextFont(): TextStyle = copy(fontFamily = FontFamily.Default)
