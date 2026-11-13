package com.etologic.mahjongtournamentsuite.presentation.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.domain.model.PlayerLevel

/*
 * The design tokens of the suite. Material 3 stays the base for color, typography and shapes. These tokens add
 * what Material does not have: a spacing scale, adaptive layout values, the colors of the domain (gain, medals,
 * levels...) and a few text roles. Read them through [MtsTheme], like MaterialTheme:
 *
 *     Modifier.padding(MtsTheme.layout.cardPadding)
 *     Text(score, style = MtsTheme.typography.score, color = MtsTheme.colors.gain)
 *
 * Phase 0 of the design system: the values are the ones the screens use today, so adopting a token changes no pixel.
 * Proposal and migration plan: docs/design-system.md.
 */

/** The spacing scale. Use it for padding and for the gaps between items. Sizes of things (icons, widths) are not spacing. */
@Immutable
object MtsSpacing {
    val none: Dp = 0.dp
    val xxs: Dp = 2.dp
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 24.dp
    val xxl: Dp = 32.dp
    val xxxl: Dp = 48.dp
}

/**
 * The width classes of the window. Compact ends at 600 dp, like in Material. Medium ends at 900 dp (840 in Material),
 * the width under which the adaptive top bar already uses the three-dots menu.
 */
enum class MtsWindowWidth {
    /** Under 600 dp: phones in portrait. */
    Compact,

    /** From 600 dp to 899 dp: phones in landscape, small tablets, narrow browser windows. */
    Medium,

    /** From 900 dp: desktop, tablets in landscape, wide browser windows. */
    Expanded,
    ;

    val isCompact: Boolean get() = this == Compact

    companion object {
        const val MEDIUM_MIN_DP = 600
        const val EXPANDED_MIN_DP = 900

        fun fromWidth(width: Dp): MtsWindowWidth = when {
            width.value < MEDIUM_MIN_DP -> Compact
            width.value < EXPANDED_MIN_DP -> Medium
            else -> Expanded
        }
    }
}

/** The layout values that change with the width of the window. */
@Immutable
data class MtsLayout(
    /** The space between the content and the left and right edges of the screen. */
    val screenGutter: Dp,
    /** The padding inside a card. */
    val cardPadding: Dp,
    /** The space between the cards of a list. */
    val listGap: Dp,
    /** The space between the sections of a screen. */
    val sectionGap: Dp,
) {
    companion object {
        fun forWidth(width: MtsWindowWidth): MtsLayout = when (width) {
            MtsWindowWidth.Compact -> MtsLayout(screenGutter = 12.dp, cardPadding = 12.dp, listGap = 8.dp, sectionGap = 12.dp)
            MtsWindowWidth.Medium, MtsWindowWidth.Expanded ->
                MtsLayout(screenGutter = 24.dp, cardPadding = 16.dp, listGap = 8.dp, sectionGap = 16.dp)
        }
    }
}

/** The maximum widths of the content. They replace the many `widthIn(max = ...)` values of the screens. */
@Immutable
object MtsContentWidth {
    /** Forms and dialogs. */
    val form: Dp = 520.dp

    /** Text and detail screens. */
    val reading: Dp = 720.dp

    /** The default of [com.etologic.mahjongtournamentsuite.presentation.components.ScreenColumn]. */
    val screen: Dp = 900.dp

    /** Wide tables. */
    val wide: Dp = 1200.dp
}

/** The background, border and content colors of a level medal. */
@Immutable
data class MtsLevelColors(val fill: Color, val rim: Color, val content: Color)

/** The colors of the domain, which the Material color scheme has no role for. */
@Immutable
data class MtsColors(
    /** A positive score or the winner of a hand. */
    val gain: Color,
    /** A table whose scores add up. */
    val scoreOk: Color,
    /** A table whose scores do not add up yet. */
    val scoreWarning: Color,
    /** A value that an import changes. */
    val changed: Color,
    /** Something the user should check. */
    val warning: Color,
    /** Neutral information, like an upcoming tournament. */
    val info: Color,
    val onInfo: Color,
    /** The hands won by self-pick, and the draws. A discard uses the error color. */
    val selfPick: Color,
    val draw: Color,
    val medalGold: Color,
    val medalSilver: Color,
    val medalBronze: Color,
    /** Text and icons on the medal colors. */
    val onMedal: Color,
    /** The background of the first place in a ranking. */
    val firstPlaceHighlight: Color,
    /** Text and icons on the green header (top bar, tabs). */
    val onHeader: Color,
    val onHeaderMuted: Color,
    val levels: Map<PlayerLevel, MtsLevelColors>,
) {
    fun level(level: PlayerLevel): MtsLevelColors = levels.getValue(level)

    /** The color of a position at a table: the medals, and the error red for the fourth. */
    fun position(position: Int, error: Color): Color = when (position) {
        1 -> medalGold
        2 -> medalSilver
        3 -> medalBronze
        else -> error
    }
}

// The palette of the domain colors, unified by Ernesto on 2026-11-13: the greens are the primary green of the theme,
// the greys are the silver of the medals, and the text on all of them is white.
private val MedalGold = Color(0xFFFFAC33)
private val MedalSilver = Color(0xFF8A99A8)
private val MedalBronze = Color(0xFFA9632F)
private val Warning = Color(0xFFB26A00)

private val LevelColorsByLevel = mapOf(
    PlayerLevel.BEGINNER to MtsLevelColors(MedalSilver, Color(0xFF5F6E7D), Color.White),
    PlayerLevel.INTERMEDIATE to MtsLevelColors(MahjongGreen, MahjongGreenDark, Color.White),
    PlayerLevel.ADVANCED to MtsLevelColors(Color(0xFFF2692E), Color(0xFFB53B10), Color.White),
    PlayerLevel.EXPERT to MtsLevelColors(Color(0xFF8E5CD9), Color(0xFF6538AD), Color.White),
    PlayerLevel.MASTER to MtsLevelColors(Color(0xFF5B616E), Color(0xFF15181E), Color.White),
)

internal val LightMtsColors = MtsColors(
    gain = MahjongGreen,
    scoreOk = MahjongGreen,
    scoreWarning = Warning,
    changed = MahjongGreenDark,
    warning = Warning,
    info = Color(0xFF1565C0),
    onInfo = Color.White,
    selfPick = MahjongGreen,
    draw = MedalSilver,
    medalGold = MedalGold,
    medalSilver = MedalSilver,
    medalBronze = MedalBronze,
    onMedal = Color.White,
    firstPlaceHighlight = MedalGold,
    onHeader = Color.White,
    onHeaderMuted = Color.White.copy(alpha = 0.85f),
    levels = LevelColorsByLevel,
)

/** The same palette; only the colors that follow a container of the dark scheme change. */
internal val DarkMtsColors = LightMtsColors.copy(
    changed = MahjongGreenDarker,
    info = Color(0xFF4D9BFF),
)

/** Text roles on top of the Material type scale. */
@Immutable
data class MtsTypography(
    /** Scores in tables and lists: bold, with digits of the same width so the columns line up. */
    val score: TextStyle,
    /** Big scores and clocks. */
    val scoreLarge: TextStyle,
    /** A body text that must stand out. */
    val emphasis: TextStyle,
) {
    companion object {
        fun from(material: Typography): MtsTypography = MtsTypography(
            score = material.bodyMedium.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum", textAlign = TextAlign.End),
            scoreLarge = material.headlineMedium.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
            emphasis = material.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
        )
    }
}

/** The shapes on top of `MaterialTheme.shapes`. */
@Immutable
object MtsShapes {
    /** Chips, badges and pill buttons. */
    val pill: Shape = RoundedCornerShape(percent = 50)
}

/** The Material shapes, written out so that the scale is visible here. They are the Material defaults. */
internal val MtsMaterialShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

internal val LocalMtsColors = staticCompositionLocalOf { LightMtsColors }
internal val LocalMtsTypography = staticCompositionLocalOf<MtsTypography?> { null }

/** The width class of the window. [MtsTheme] sets it from the size of the window. */
val LocalMtsWindowWidth = staticCompositionLocalOf { MtsWindowWidth.Expanded }

/** The tokens of the suite. See the top of this file. */
object MtsTheme {
    val spacing: MtsSpacing get() = MtsSpacing

    val contentWidth: MtsContentWidth get() = MtsContentWidth

    val shapes: MtsShapes get() = MtsShapes

    val colors: MtsColors
        @Composable @ReadOnlyComposable
        get() = LocalMtsColors.current

    val typography: MtsTypography
        @Composable @ReadOnlyComposable
        get() = LocalMtsTypography.current
            ?: MtsTypography.from(androidx.compose.material3.MaterialTheme.typography)

    val windowWidth: MtsWindowWidth
        @Composable @ReadOnlyComposable
        get() = LocalMtsWindowWidth.current

    val layout: MtsLayout
        @Composable @ReadOnlyComposable
        get() = MtsLayout.forWidth(LocalMtsWindowWidth.current)
}
