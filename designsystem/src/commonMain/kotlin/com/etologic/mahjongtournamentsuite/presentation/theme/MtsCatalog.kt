package com.etologic.mahjongtournamentsuite.presentation.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.presentation.components.MtsOutlinedTag
import com.etologic.mahjongtournamentsuite.presentation.components.signedColor
import com.etologic.mahjongtournamentsuite.presentation.components.signedText

/**
 * A page that shows every token of the design system: colors, text styles, spacing and shapes.
 * It is for review in the IDE previews and in the admin app (Profile > Design system).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MtsCatalog(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val colors = MtsTheme.colors
    Surface(modifier = modifier, color = scheme.background) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(MtsTheme.layout.screenGutter),
            verticalArrangement = Arrangement.spacedBy(MtsTheme.layout.sectionGap),
        ) {
            CatalogTitle("Window: ${MtsTheme.windowWidth}")

            CatalogTitle("Material color scheme")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(MtsSpacing.sm), verticalArrangement = Arrangement.spacedBy(MtsSpacing.sm)) {
                Swatch("primary", scheme.primary, scheme.onPrimary)
                Swatch("primaryContainer", scheme.primaryContainer, scheme.onPrimaryContainer)
                Swatch("secondary", scheme.secondary, scheme.onSecondary)
                Swatch("secondaryContainer", scheme.secondaryContainer, scheme.onSecondaryContainer)
                Swatch("tertiary", scheme.tertiary, scheme.onTertiary)
                Swatch("error", scheme.error, scheme.onError)
                Swatch("surface", scheme.surface, scheme.onSurface)
                Swatch("surfaceVariant", scheme.surfaceVariant, scheme.onSurfaceVariant)
                Swatch("surfaceContainerHigh", scheme.surfaceContainerHigh, scheme.onSurface)
            }

            CatalogTitle("Domain colors (MtsTheme.colors)")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(MtsSpacing.sm), verticalArrangement = Arrangement.spacedBy(MtsSpacing.sm)) {
                Swatch("gain", colors.gain, Color.White)
                Swatch("scoreOk", colors.scoreOk, Color.White)
                Swatch("scoreWarning", colors.scoreWarning, Color.White)
                Swatch("changed", colors.changed, Color.White)
                Swatch("warning", colors.warning, Color.White)
                Swatch("info", colors.info, colors.onInfo)
                Swatch("selfPick", colors.selfPick, Color.White)
                Swatch("draw", colors.draw, Color.White)
                Swatch("medalGold", colors.medalGold, colors.onMedal)
                Swatch("medalSilver", colors.medalSilver, colors.onMedal)
                Swatch("medalBronze", colors.medalBronze, colors.onMedal)
                Swatch("firstPlace", colors.firstPlaceHighlight, colors.onMedal)
                Swatch("onHeader", scheme.primary, colors.onHeader)
                colors.levels.forEachIndexed { index, c ->
                    Swatch("level ${index + 1}", c.fill, c.content, border = c.rim)
                }
            }

            CatalogTitle("Text styles")
            val type = MaterialTheme.typography
            Column(verticalArrangement = Arrangement.spacedBy(MtsSpacing.xs)) {
                Text("headlineMedium", style = type.headlineMedium)
                Text("titleLarge", style = type.titleLarge)
                Text("titleMedium", style = type.titleMedium)
                Text("bodyLarge", style = type.bodyLarge)
                Text("bodyMedium", style = type.bodyMedium)
                Text("emphasis", style = MtsTheme.typography.emphasis)
                Text("labelLarge", style = type.labelLarge)
                Text("bodySmall", style = type.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(MtsSpacing.lg)) {
                    Text("+1.250", style = MtsTheme.typography.score, color = colors.gain, modifier = Modifier.width(72.dp))
                    Text("-875", style = MtsTheme.typography.score, color = scheme.error, modifier = Modifier.width(72.dp))
                    Text("12:34", style = MtsTheme.typography.scoreLarge)
                }
            }

            CatalogTitle("Spacing (MtsTheme.spacing)")
            listOf(
                "xxs" to MtsSpacing.xxs,
                "xs" to MtsSpacing.xs,
                "sm" to MtsSpacing.sm,
                "md" to MtsSpacing.md,
                "lg" to MtsSpacing.lg,
                "xl" to MtsSpacing.xl,
                "xxl" to MtsSpacing.xxl,
                "xxxl" to MtsSpacing.xxxl,
            ).forEach { (name, value) -> SpacingBar(name, value) }

            CatalogTitle("Shapes")
            Row(horizontalArrangement = Arrangement.spacedBy(MtsSpacing.md)) {
                ShapeSample("xs 4", MaterialTheme.shapes.extraSmall)
                ShapeSample("s 8", MaterialTheme.shapes.small)
                ShapeSample("m 12", MaterialTheme.shapes.medium)
                ShapeSample("l 16", MaterialTheme.shapes.large)
                ShapeSample("xl 28", MaterialTheme.shapes.extraLarge)
                ShapeSample("pill", MtsTheme.shapes.pill)
                ShapeSample("tag", MtsTheme.shapes.tag)
            }

            CatalogTitle("Components")
            Row(horizontalArrangement = Arrangement.spacedBy(MtsSpacing.md), verticalAlignment = Alignment.CenterVertically) {
                MtsOutlinedTag(scheme.tertiary) { Text("In progress", style = type.labelMedium, color = scheme.tertiary) }
                MtsOutlinedTag(colors.info) { Text("Upcoming", style = type.labelMedium, color = colors.info) }
                listOf(250L, 0L, -125L).forEach { value ->
                    Text(signedText(value), style = MtsTheme.typography.score, color = signedColor(value))
                }
            }
        }
    }
}

@Composable
private fun CatalogTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
}

@Composable
private fun Swatch(name: String, background: Color, content: Color, border: Color = Color.Transparent) {
    Box(
        modifier = Modifier
            .size(width = 112.dp, height = 56.dp)
            .background(background, MaterialTheme.shapes.small)
            .border(2.dp, border, MaterialTheme.shapes.small)
            .padding(MtsSpacing.xs),
        contentAlignment = Alignment.Center,
    ) {
        Text(name, color = content, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
    }
}

@Composable
private fun SpacingBar(name: String, value: Dp) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MtsSpacing.sm)) {
        Text("$name ${value.value.toInt()}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(64.dp))
        Box(Modifier.size(width = value, height = MtsSpacing.md).background(MaterialTheme.colorScheme.primary))
    }
}

@Composable
private fun ShapeSample(name: String, shape: Shape) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(MtsSpacing.xs)) {
        Box(Modifier.size(56.dp).background(MaterialTheme.colorScheme.primaryContainer, shape))
        Text(name, style = MaterialTheme.typography.labelSmall)
    }
}

@Preview(widthDp = 900, heightDp = 1400)
@Composable
private fun MtsCatalogLightPreview() {
    MtsTheme(useDarkTheme = false) { MtsCatalog() }
}

@Preview(widthDp = 900, heightDp = 1400)
@Composable
private fun MtsCatalogDarkPreview() {
    MtsTheme(useDarkTheme = true) { MtsCatalog() }
}

@Preview(widthDp = 380, heightDp = 1600)
@Composable
private fun MtsCatalogCompactPreview() {
    MtsTheme(useDarkTheme = false) { MtsCatalog() }
}
