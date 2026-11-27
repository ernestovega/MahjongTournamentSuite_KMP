package com.etologic.mahjongtournamentsuite.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.TextStyle

@Composable
fun MtsTheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val fontFamily = GangOfThreeFontFamily()
    val typography = remember(fontFamily) { gangOfThreeTypography(fontFamily) }

    val mtsTypography = remember(typography) { MtsTypography.from(typography) }
    // The width class comes from the window, not from the layout, so reading it costs no extra measure pass.
    // A window of width 0 (some tests) counts as Expanded, the layout the screens had before the classes existed.
    val windowPx = LocalWindowInfo.current.containerSize.width
    val windowWidth = if (windowPx > 0) {
        MtsWindowWidth.fromWidth(with(LocalDensity.current) { windowPx.toDp() })
    } else {
        MtsWindowWidth.Expanded
    }

    MaterialTheme(
        colorScheme = if (useDarkTheme) DarkColors else LightColors,
        typography = typography,
        shapes = MtsMaterialShapes,
    ) {
        CompositionLocalProvider(
            LocalMtsColors provides if (useDarkTheme) DarkMtsColors else LightMtsColors,
            LocalMtsTypography provides mtsTypography,
            LocalMtsWindowWidth provides windowWidth,
            LocalRippleConfiguration provides MtsRippleConfiguration,
            content = content,
        )
    }
}

/**
 * The hover layer of every control is stronger than the Material default (8 %), so a hovered green button changes
 * visibly. The focus layer is off: the keyboard focus shows as the pink ring of the `ui` components, and only while the
 * keyboard is in use (see [MtsInputModality]). So a control lights up in one place only (AGENTS.md, One Lit Area Per
 * Control), and a button clicked with the mouse does not stay lit.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
val MtsRippleConfiguration = RippleConfiguration(
    rippleAlpha = RippleAlpha(draggedAlpha = 0.24f, focusedAlpha = 0f, hoveredAlpha = 0.16f, pressedAlpha = 0.20f),
)

internal val MahjongGreen = Color(0xFF00B16A)
internal val MahjongGreenLight = Color(0xFF3DE6A3)
internal val MahjongGreenDark = Color(0xFF00934C)
internal val MahjongGreenDarker = Color(0xFF00752E)
private val MahjongRed = Color(0xFFFA6868)

/** The red of error texts and icons: darker than the red of the fills in light mode, lighter in dark mode (4.5:1). */
private val ErrorTextLight = Color(0xFFC62828)
private val ErrorTextDark = Color(0xFFFF8A80)
private val MahjongAccent = Color(0xFFFF00A7)
/** The grey of the light pages, and the tooltips of the dark theme. */
internal val AppLightGray = Color(0xFFE9EBEE)

/**
 * The light containers (selected chips and segments, notices, placeholders, tracks, stat tiles) since 2026-11-27,
 * where a pale green was before (Ernesto: the pale green lowered the contrast). A little darker than [AppLightGray],
 * as dark as the old green, so a container still shows when it sits on the grey page and not on a white card.
 */
internal val AppLightGrayContainer = Color(0xFFD5D9DE)
private val AppDarkGray = Color(0xFF414141)
internal val AppDarkGraySurface = Color(0xFF363636)
private val AppDarkGrayVariant = Color(0xFF4D4D4D)
private val LightColors = lightColorScheme(
    primary = MahjongGreen,
    onPrimary = Color.White,
    primaryContainer = MahjongGreenDark,
    onPrimaryContainer = Color.White,
    secondary = MahjongGreenDark,
    onSecondary = Color.White,
    secondaryContainer = AppLightGrayContainer,
    onSecondaryContainer = Color(0xFF062114),
    tertiary = MahjongAccent,
    onTertiary = Color.White,
    tertiaryContainer = AppLightGrayContainer,
    onTertiaryContainer = Color(0xFF002115),
    // Error texts and icons. Fills of destructive buttons use errorContainer, the red of the brand.
    error = ErrorTextLight,
    onError = Color.White,
    errorContainer = MahjongRed,
    onErrorContainer = Color.White,
    // Light grey behind white cards (Ernesto, 24-nov-2026: option G2), so a card stands out from the page.
    background = AppLightGray,
    onBackground = Color(0xFF002115),
    surface = AppLightGray,
    onSurface = Color(0xFF002115),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F8F9),
    surfaceContainer = Color(0xFFF4F5F7),
    surfaceContainerHigh = Color(0xFFFFFFFF),
    surfaceContainerHighest = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFDDE0E4),
    surfaceVariant = AppLightGrayContainer,
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
    error = ErrorTextDark,
    onError = Color.White,
    errorContainer = MahjongRed,
    onErrorContainer = Color.White,
    background = AppDarkGray,
    onBackground = Color.White,
    surface = AppDarkGraySurface,
    onSurface = Color.White,
    surfaceVariant = AppDarkGrayVariant,
    onSurfaceVariant = Color(0xFFE0E0E0),
    // The border of fields: 3:1 on the dark surfaces.
    outline = Color(0xFF9E9E9E),
    outlineVariant = Color(0xFF5A5A5A),
    scrim = Color(0xFF000000),
    inverseSurface = AppLightGray,
    inverseOnSurface = Color(0xFF002115),
    inversePrimary = MahjongGreen,
    surfaceTint = MahjongGreen,
)

/**
 * Gang of Three only where it reads well and looks good: the big titles of screens, dialogs and sections (display,
 * headline, titleLarge and titleMedium). Content and actions are in the text font (Ernesto, 2026-11-25): small titles,
 * buttons, chips, tabs, fields, numbers and names. A title style that shows data, like a score, uses [inTextFont].
 */
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
        titleSmall = base.titleSmall,
        bodyLarge = base.bodyLarge,
        bodyMedium = base.bodyMedium,
        bodySmall = base.bodySmall,
        labelLarge = base.labelLarge,
        labelMedium = base.labelMedium,
        labelSmall = base.labelSmall,
    )
}
