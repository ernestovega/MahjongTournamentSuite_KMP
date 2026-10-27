package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.etologic.mahjongtournamentsuite.domain.model.PlayerLevel
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import mahjongtournamentsuite.ui.generated.resources.Res
import mahjongtournamentsuite.ui.generated.resources.level_description
import mahjongtournamentsuite.ui.generated.resources.level_end
import mahjongtournamentsuite.ui.generated.resources.level_provisional_suffix
import mahjongtournamentsuite.ui.generated.resources.level_skill_score_suffix
import mahjongtournamentsuite.ui.generated.resources.skill_score_down
import mahjongtournamentsuite.ui.generated.resources.skill_score_up
import org.jetbrains.compose.resources.stringResource

/** The colors of a level. [content] is the color of the icon and the text on the level color. */
internal class LevelColors(val main: Color, val dark: Color, val content: Color)

internal fun PlayerLevel.colors(): LevelColors = when (this) {
    PlayerLevel.BEGINNER -> LevelColors(Color(0xFF8A99A8), Color(0xFF5F6E7D), Color.White)
    PlayerLevel.INTERMEDIATE -> LevelColors(Color(0xFF02B16B), Color(0xFF017A4A), Color.White)
    PlayerLevel.ADVANCED -> LevelColors(Color(0xFFF2692E), Color(0xFFB53B10), Color.White)
    PlayerLevel.EXPERT -> LevelColors(Color(0xFF8E5CD9), Color(0xFF6538AD), Color.White)
    PlayerLevel.MASTER -> LevelColors(Color(0xFF5B616E), Color(0xFF15181E), Color.White)
}

/** A shape of an icon on a 24 by 24 grid. A stroke width above zero draws a line, else the shape is filled. */
private class IconShape(
    val path: String,
    val detail: Boolean = false,
    val stroke: Float = 0f,
    /** A fixed color. Without it the shape uses the content color or the detail color of the level. */
    val tint: Color? = null,
    /** Degrees around the center of the icon. */
    val rotation: Float = 0f,
)

private val LevelIcons: Map<PlayerLevel, List<IconShape>> = mapOf(
    // A sprout.
    PlayerLevel.BEGINNER to listOf(
        IconShape("M12 22 V12", stroke = 2.2f),
        IconShape("M12 14 C12 8.5 8 6 3.5 7 C3.5 11.5 7 14.5 12 14 Z"),
        IconShape("M12 11.5 C12 6 15.5 3 20.5 3.5 C20.5 9 17 12.5 12 11.5 Z"),
    ),
    // A tree.
    PlayerLevel.INTERMEDIATE to listOf(
        IconShape("M10.6 14 H13.4 V22 H10.6 Z"),
        IconShape("M12 2 A7.6 7.6 0 1 1 11.99 2 Z"),
        IconShape("M12 18 L8.6 13.6 M12 16 L15.4 11.4", detail = true, stroke = 1.4f),
    ),
    // A flame.
    PlayerLevel.ADVANCED to listOf(
        IconShape("M12.5 2 C13 6 19 8.5 19 14.5 A7 7 0 0 1 5 14.5 C5 11.5 7 9.5 8 7 C9.6 8 10.4 9.2 10.8 10.6 C11.9 8 11.6 4.6 12.5 2 Z"),
        IconShape("M12 21 A3.4 3.4 0 0 1 8.6 17.6 C8.6 15.8 10 15 11 13.4 C12.2 14.6 15.4 15.8 15.4 17.8 A3.4 3.4 0 0 1 12 21 Z", detail = true),
    ),
    // A star.
    PlayerLevel.EXPERT to listOf(
        IconShape("M12 1.8 L14.9 8.5 L22 9.2 L16.6 14 L18.2 21.2 L12 17.4 L5.8 21.2 L7.4 14 L2 9.2 L9.1 8.5 Z"),
    ),
    // A black hole: a light streak, a glowing ring and a black center.
    PlayerLevel.MASTER to listOf(
        IconShape("M1.8 12 H22.2", stroke = 1.5f, tint = Color.White.copy(alpha = 0.85f)),
        IconShape("M12 2.4 A9.6 9.6 0 1 1 11.99 2.4 Z", stroke = 1.2f, tint = Color.White.copy(alpha = 0.45f)),
        IconShape("M12 4.6 A7.4 7.4 0 1 1 11.99 4.6 Z", stroke = 2.8f, tint = Color.White),
        IconShape("M12 6.4 A5.6 5.6 0 1 1 11.99 6.4 Z", tint = Color(0xFF0B0B12)),
    ),
)

private val ParsedIcons: Map<PlayerLevel, List<Pair<IconShape, Path>>> = LevelIcons.mapValues { (_, shapes) ->
    shapes.map { it to PathParser().parsePathString(it.path).toPath() }
}

/** The number of lit segments of the ring of a level: one for Beginner, five for Master. */
private fun PlayerLevel.step(): Int = ordinal + 1

/**
 * The round medal of a level: a colored disc, a ring of five segments, and the icon of the level.
 * The lit segments of the ring show the step of the level. Master also has a laurel when [laurel] is true.
 */
@Composable
fun LevelMedal(level: PlayerLevel, size: Dp, modifier: Modifier = Modifier, laurel: Boolean = false) {
    val colors = level.colors()
    Canvas(modifier.size(size)) {
        val scale = this.size.minDimension / 72f
        withTransform({
            scale(scale, scale, Offset.Zero)
            translate(4f, 4f)
        }) {
            drawMedal(level, colors, laurel && level == PlayerLevel.MASTER)
        }
    }
}

private fun DrawScope.drawMedal(level: PlayerLevel, colors: LevelColors, laurel: Boolean) {
    if (laurel) {
        for (side in listOf(-1, 1)) {
            for (k in 0 until 7) {
                val degrees = 100 + k * 24
                val radians = degrees * kotlin.math.PI / 180.0
                val x = (32 + side * 29 * cos(radians)).toFloat()
                val y = (32 + 29 * sin(radians)).toFloat()
                rotate((side * (degrees - 180)).toFloat(), Offset(x, y)) {
                    drawOval(colors.main, Offset(x - 2f, y - 4f), Size(4f, 8f))
                }
            }
        }
    }
    drawCircle(Brush.verticalGradient(listOf(colors.main, colors.dark), startY = 2f, endY = 62f), radius = 30f, center = Offset(32f, 32f))
    drawCircle(Color.White.copy(alpha = 0.85f), radius = 30f, center = Offset(32f, 32f), style = Stroke(2f))
    // A soft highlight on the upper half makes the disc look glossy.
    val gloss = if (level == PlayerLevel.MASTER) 0.34f else 0.16f
    drawOval(
        Brush.verticalGradient(listOf(Color.White.copy(alpha = gloss), Color.Transparent), startY = 5f, endY = 32f),
        topLeft = Offset(11f, 5f),
        size = Size(42f, 27f),
    )
    val lit = colors.content
    val unlit = colors.content.copy(alpha = 0.3f)
    val radius = 24.5f
    val gap = 14f
    val span = 72f - gap
    for (k in 0 until 5) {
        drawArc(
            color = if (k < level.step()) lit else unlit,
            startAngle = -90f + k * 72f + gap / 2f,
            sweepAngle = span,
            useCenter = false,
            topLeft = Offset(32f - radius, 32f - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(3f, cap = StrokeCap.Round),
        )
    }
    withTransform({
        translate(32f, 32f)
        scale(0.78f, 0.78f, Offset.Zero)
        translate(-12f, -12f)
    }) {
        ParsedIcons.getValue(level).forEach { (shape, path) ->
            val color = shape.tint ?: if (shape.detail) colors.dark else colors.content
            rotate(shape.rotation, Offset(12f, 12f)) {
                if (shape.stroke > 0f) {
                    drawPath(path, color, style = Stroke(shape.stroke, cap = StrokeCap.Round))
                } else {
                    drawPath(path, color)
                }
            }
        }
    }
}

/** The medal with a ribbon under it that has the name of the level. Use it where there is room. */
@Composable
fun LevelRibbonBadge(level: PlayerLevel, modifier: Modifier = Modifier, medalSize: Dp = 96.dp) {
    val levelDescription = stringResource(Res.string.level_description, level.label())
    val colors = level.colors()
    val tapeHeight = 28.dp
    val overlap = 14.dp
    val shape = remember {
        GenericShape { size, _ ->
            val notch = size.height * 0.4f
            moveTo(0f, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width - notch, size.height / 2f)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            lineTo(notch, size.height / 2f)
            close()
        }
    }
    Box(
        modifier.semantics { contentDescription = levelDescription },
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            Modifier.padding(top = medalSize - overlap)
                .height(tapeHeight)
                .background(Brush.verticalGradient(listOf(colors.main, colors.dark)), shape)
                .padding(horizontal = 26.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                level.label().uppercase(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.6.sp,
                color = colors.content,
                maxLines = 1,
            )
        }
        LevelMedal(level, medalSize, laurel = true)
    }
}

/** A small medal with a tooltip that names the level. It fits next to the name of a player. */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun LevelMedalTag(
    level: PlayerLevel,
    skillScore: Double?,
    provisional: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 26.dp,
) {
    val levelDescription = stringResource(Res.string.level_description, level.label())
    val skillScoreText = skillScore?.let { stringResource(Res.string.level_skill_score_suffix, it.roundToInt()) }.orEmpty()
    val provisionalText = if (provisional) "\n" + stringResource(Res.string.level_provisional_suffix) else ""
    val text = levelDescription + skillScoreText + stringResource(Res.string.level_end) + provisionalText
    TooltipBox(
        positionProvider = rememberAppTooltipPositionProvider(),
        tooltip = { AppPlainTooltip { Text(text) } },
        state = rememberTooltipState(),
        modifier = modifier,
    ) {
        LevelMedal(level, size, Modifier.semantics { contentDescription = levelDescription })
    }
}

private val GainGreen = Color(0xFF00934C)

/** The change of the skill score caused by a table: a green arrow up, a red arrow down, or a flat value. */
@Composable
fun LevelChangeTag(change: Double, modifier: Modifier = Modifier) {
    val rounded = (change * 10).roundToInt() / 10.0
    val flat = abs(rounded) < 0.05
    val color = when {
        flat -> MaterialTheme.colorScheme.onSurfaceVariant
        change > 0 -> GainGreen
        else -> MaterialTheme.colorScheme.error
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (!flat) {
            Icon(
                if (change > 0) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                contentDescription = stringResource(if (change > 0) Res.string.skill_score_up else Res.string.skill_score_down),
                modifier = Modifier.size(20.dp),
                tint = color,
            )
        }
        Text(
            abs(rounded).toString(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = color,
        )
    }
}
