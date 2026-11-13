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
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import com.etologic.mahjongtournamentsuite.presentation.theme.level
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

@Composable
internal fun PlayerLevel.colors(): LevelColors = MtsTheme.colors.level(this).let { LevelColors(it.fill, it.rim, it.content) }

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
        IconShape("M12 20.1 V12.1", stroke = 2.2f),
        IconShape("M12 13.7 C12 9.3 8.8 7.3 5.2 8.1 C5.2 11.7 8 14.1 12 13.7 Z"),
        IconShape("M12 11.7 C12 7.3 14.8 4.9 18.8 5.3 C18.8 9.7 16 12.5 12 11.7 Z"),
    ),
    // A tree with a round crown and a white trunk.
    PlayerLevel.INTERMEDIATE to listOf(
        IconShape("M10.8 13 H13.2 V22.6 H10.8 Z"),
        IconShape("M12 2 A5.4 5.4 0 1 1 11.99 2.0 Z M7.4 6.8 A4 4 0 1 1 7.39 6.8 Z M16.6 6.8 A4 4 0 1 1 16.59 6.8 Z M12 7.6 A4.4 4.4 0 1 1 11.99 7.6 Z"),
    ),
    // A flame.
    PlayerLevel.ADVANCED to listOf(
        IconShape("M12.5 2 C13 6 19 8.5 19 14.5 A7 7 0 0 1 5 14.5 C5 11.5 7 9.5 8 7 C9.6 8 10.4 9.2 10.8 10.6 C11.9 8 11.6 4.6 12.5 2 Z"),
        IconShape("M12 21 A3.4 3.4 0 0 1 8.6 17.6 C8.6 15.8 10 15 11 13.4 C12.2 14.6 15.4 15.8 15.4 17.8 A3.4 3.4 0 0 1 12 21 Z", detail = true),
    ),
    // A rocket that flies up to the right, with a window, two fins, a flame and a white trail.
    PlayerLevel.EXPERT to listOf(
        IconShape("M12 18.5 V23.2", stroke = 1.5f, tint = Color.White.copy(alpha = 0.85f), rotation = 45f),
        IconShape("M9.56 18.03 V22.44", stroke = 1.1f, tint = Color.White.copy(alpha = 0.55f), rotation = 45f),
        IconShape("M14.44 18.03 V22.44", stroke = 1.1f, tint = Color.White.copy(alpha = 0.55f), rotation = 45f),
        IconShape("M10.07 11.81 H13.93 L12 16.83 Z", detail = true, rotation = 45f),
        IconShape("M8.91 8.31 L6.32 11.49 V13.48 L8.91 11.65 ZM15.09 8.31 L17.68 11.49 V13.48 L15.09 11.65 Z", rotation = 45f),
        IconShape("M12 -0.88 C14.51 1.13 15.26 4.47 15.26 7.64 V11.65 H8.74 V7.64 C8.74 4.47 9.49 1.13 12 -0.88 Z", rotation = 45f),
        IconShape("M12 3.64 A1.67 1.67 0 1 1 11.99 3.64 Z", detail = true, rotation = 45f),
    ),
    // A black hole: a glowing ring, a black center and a tilted disc around it, like the rings of Saturn.
    // The back half of the disc goes behind the center and the front half over it.
    PlayerLevel.MASTER to listOf(
        IconShape("M1.2 12 A10.8 4 0 0 1 22.8 12", stroke = 3f, tint = Color.White.copy(alpha = 0.6f), rotation = -18f),
        IconShape("M12 4.6 A7.4 7.4 0 1 1 11.99 4.6 Z", stroke = 2.8f, tint = Color.White),
        IconShape("M12 6.4 A5.6 5.6 0 1 1 11.99 6.4 Z", tint = Color(0xFF0B0B12)),
        IconShape("M1.2 12 A10.8 4 0 0 0 22.8 12", stroke = 3f, tint = Color.White, rotation = -18f),
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
    AppTooltipBox(text, modifier) {
        LevelMedal(level, size, Modifier.semantics { contentDescription = levelDescription })
    }
}

/** The change of the skill score caused by a table: a green arrow up, a red arrow down, or a flat value. */
@Composable
fun LevelChangeTag(change: Double, modifier: Modifier = Modifier) {
    val rounded = (change * 10).roundToInt() / 10.0
    val flat = abs(rounded) < 0.05
    val color = when {
        flat -> MaterialTheme.colorScheme.onSurfaceVariant
        change > 0 -> MtsTheme.colors.gain
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
