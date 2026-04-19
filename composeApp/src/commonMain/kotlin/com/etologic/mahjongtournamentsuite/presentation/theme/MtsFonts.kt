package com.etologic.mahjongtournamentsuite.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily
import mahjongtournamentsuite.composeapp.generated.resources.Res
import mahjongtournamentsuite.composeapp.generated.resources.go3v2
import org.jetbrains.compose.resources.Font

@Composable
fun GangOfThreeFontFamily(): FontFamily {
    val font = Font(Res.font.go3v2)
    return remember(font) { FontFamily(font) }
}
