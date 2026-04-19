package com.etologic.mahjongtournamentsuite.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle

@Composable
fun MtsTheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val fontFamily = GangOfThreeFontFamily()
    val typography = remember(fontFamily) { gangOfThreeTypography(fontFamily) }

    MaterialTheme(
        colorScheme = if (useDarkTheme) DarkColors else LightColors,
        typography = typography,
        content = content,
    )
}

private val MahjongGreen = Color(0xFF00B16A)
private val MahjongGreenLight = Color(0xFF3DE6A3)
private val MahjongGreenDark = Color(0xFF00934C)
private val MahjongGreenDarker = Color(0xFF00752E)
private val MahjongRed = Color(0xFFE00000)
private val MahjongAccent = Color(0xFFFF00A7)
private val AppDarkGray = Color(0xFF414141)
private val AppDarkGraySurface = Color(0xFF363636)
private val AppDarkGrayVariant = Color(0xFF4D4D4D)

private val LightColors = lightColorScheme(
    primary = MahjongGreen,
    onPrimary = Color.White,
    primaryContainer = MahjongGreenDark,
    onPrimaryContainer = Color.White,
    secondary = MahjongGreenDark,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCDEADD),
    onSecondaryContainer = Color(0xFF062114),
    tertiary = MahjongAccent,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFA5EFCF),
    onTertiaryContainer = Color(0xFF002115),
    error = MahjongRed,
    onError = Color(0xFFFFDAD6),
    errorContainer = MahjongRed,
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFFF2FFFA),
    onBackground = Color(0xFF002115),
    surface = Color(0xFFF2FFFA),
    onSurface = Color(0xFF002115),
    surfaceVariant = Color(0xFFD6E8DE),
    onSurfaceVariant = Color(0xFF3E4C45),
    outline = Color(0xFF6F8078),
    outlineVariant = Color(0xFFBECBC3),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFF0F1F18),
    inverseOnSurface = Color(0xFFE2F9EE),
    inversePrimary = MahjongGreenLight,
    surfaceTint = MahjongGreen,
)

private val DarkColors = darkColorScheme(
    primary = MahjongGreen,
    onPrimary = Color.White,
    primaryContainer = MahjongGreenDarker,
    onPrimaryContainer = Color.White,
    secondary = MahjongGreenLight,
    onSecondary = Color(0xFF003821),
    secondaryContainer = Color(0xFF0C5E3A),
    onSecondaryContainer = Color(0xFFCDEADD),
    tertiary = MahjongAccent,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF004A32),
    onTertiaryContainer = Color(0xFFB8F5DA),
    error = MahjongRed,
    onError = Color(0xFFFFDAD6),
    errorContainer = MahjongRed,
    onErrorContainer = Color(0xFFFFDAD6),
    background = AppDarkGray,
    onBackground = Color.White,
    surface = AppDarkGraySurface,
    onSurface = Color.White,
    surfaceVariant = AppDarkGrayVariant,
    onSurfaceVariant = Color(0xFFE0E0E0),
    outline = Color(0xFF7B7B7B),
    outlineVariant = Color(0xFF5A5A5A),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFFB8F5DA),
    inverseOnSurface = Color(0xFF002115),
    inversePrimary = MahjongGreen,
    surfaceTint = MahjongGreen,
)

private fun gangOfThreeTypography(fontFamily: androidx.compose.ui.text.font.FontFamily): Typography {
    val base = Typography()

    fun TextStyle.withFont() = copy(fontFamily = fontFamily)

    return Typography(
        displayLarge = base.displayLarge.withFont(),
        displayMedium = base.displayMedium.withFont(),
        displaySmall = base.displaySmall.withFont(),
        headlineLarge = base.headlineLarge.withFont(),
        headlineMedium = base.headlineMedium.withFont(),
        headlineSmall = base.headlineSmall.withFont(),
        titleLarge = base.titleLarge.withFont(),
        titleMedium = base.titleMedium.withFont(),
        titleSmall = base.titleSmall.withFont(),
        bodyLarge = base.bodyLarge,
        bodyMedium = base.bodyMedium,
        bodySmall = base.bodySmall,
        labelLarge = base.labelLarge.withFont(),
        labelMedium = base.labelMedium.withFont(),
        labelSmall = base.labelSmall,
    )
}
