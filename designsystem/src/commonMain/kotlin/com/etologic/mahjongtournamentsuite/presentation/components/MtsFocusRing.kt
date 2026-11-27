package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.SelectableChipElevation
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonColors
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.SingleChoiceSegmentedButtonRowScope
import androidx.compose.material3.Tab
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsInputModality

/*
 * The pink keyboard focus ring, and the chips, tabs and segmented buttons that draw it. Material's own focus layer is
 * off (MtsRippleConfiguration), so these replace the raw Material ones: with the mouse they show only their hover
 * layer, with the keyboard only the ring. `FocusableControlsConventionTest` (ui) fails when a source file imports a raw
 * FilterChip, InputChip, Tab or SegmentedButton. Import them with an alias, for example `MtsFilterChip as FilterChip`.
 */

private val FocusRingWidth = 2.dp

/**
 * The pink focus ring of a control, just inside the edge that the control draws. It shows only while the control has the
 * focus and the keyboard is in use: with the mouse, only the hover shows.
 *
 * [visibleHeight] is the height of what the control draws when that is less than its touch area (a Material button is
 * 40 dp high inside a 48 dp touch area). A control as narrow as the touch area (an icon button) is as wide as it is
 * high. Null means the control draws its whole area.
 */
@Composable
fun Modifier.keyboardFocusRing(
    interactionSource: InteractionSource,
    shape: Shape,
    visibleHeight: Dp? = null,
    enabled: Boolean = true,
): Modifier {
    val focused by interactionSource.collectIsFocusedAsState()
    val showRing = enabled && focused && MtsInputModality.keyboard
    val ringColor = MaterialTheme.colorScheme.tertiary
    val touchSize = LocalMinimumInteractiveComponentSize.current
    return drawWithContent {
        drawContent()
        if (!showRing) return@drawWithContent
        val stroke = FocusRingWidth.toPx()
        val drawnHeight = visibleHeight?.toPx()?.coerceAtMost(size.height) ?: size.height
        val narrow = touchSize.isSpecified && size.width <= touchSize.toPx() + 0.5f
        val drawnWidth = if (visibleHeight != null && narrow) minOf(size.width, drawnHeight) else size.width
        // The line goes just inside the drawn edge (inset), so no parent can clip it and it looks the same everywhere.
        val ringSize = Size(drawnWidth - stroke, drawnHeight - stroke)
        val outline = shape.createOutline(ringSize, layoutDirection, this)
        translate((size.width - ringSize.width) / 2f, (size.height - ringSize.height) / 2f) {
            drawOutline(outline, ringColor, style = Stroke(stroke))
        }
    }
}

@Composable
fun MtsFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    shape: Shape = FilterChipDefaults.shape,
    // Selected: the green of a filled button, so the choice stands out (Ernesto, 27-nov-2026).
    colors: SelectableChipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = MaterialTheme.colorScheme.primary,
        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
        selectedTrailingIconColor = MaterialTheme.colorScheme.onPrimary,
    ),
    elevation: SelectableChipElevation? = FilterChipDefaults.filterChipElevation(),
    border: BorderStroke? = FilterChipDefaults.filterChipBorder(enabled, selected),
    interactionSource: MutableInteractionSource? = null,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = label,
        modifier = modifier.keyboardFocusRing(source, shape, FilterChipDefaults.Height),
        enabled = enabled,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        interactionSource = source,
    )
}

@Composable
fun MtsInputChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null,
    avatar: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    shape: Shape = InputChipDefaults.shape,
    colors: SelectableChipColors = InputChipDefaults.inputChipColors(
        selectedContainerColor = MaterialTheme.colorScheme.primary,
        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
        selectedTrailingIconColor = MaterialTheme.colorScheme.onPrimary,
    ),
    border: BorderStroke? = InputChipDefaults.inputChipBorder(enabled, selected),
    interactionSource: MutableInteractionSource? = null,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    InputChip(
        selected = selected,
        onClick = onClick,
        label = label,
        modifier = modifier.keyboardFocusRing(source, shape, InputChipDefaults.Height),
        enabled = enabled,
        leadingIcon = leadingIcon,
        avatar = avatar,
        trailingIcon = trailingIcon,
        shape = shape,
        colors = colors,
        border = border,
        interactionSource = source,
    )
}

/** A Material tab with a text, and the pink focus ring inside its edges. */
@Composable
fun MtsTextTab(
    selected: Boolean,
    onClick: () -> Unit,
    text: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val source = remember { MutableInteractionSource() }
    Tab(
        selected = selected,
        onClick = onClick,
        text = text,
        modifier = modifier.keyboardFocusRing(source, MaterialTheme.shapes.small),
        enabled = enabled,
        interactionSource = source,
    )
}

@Composable
fun SingleChoiceSegmentedButtonRowScope.MtsSegmentedButton(
    selected: Boolean,
    onClick: () -> Unit,
    shape: Shape,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: SegmentedButtonColors = SegmentedButtonDefaults.colors(
        activeContainerColor = MaterialTheme.colorScheme.primary,
        activeContentColor = MaterialTheme.colorScheme.onPrimary,
    ),
    border: BorderStroke = SegmentedButtonDefaults.borderStroke(colors.activeBorderColor),
    contentPadding: PaddingValues = SegmentedButtonDefaults.ContentPadding,
    icon: @Composable () -> Unit = { SegmentedButtonDefaults.Icon(selected) },
    label: @Composable () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    SegmentedButton(
        selected = selected,
        onClick = onClick,
        shape = shape,
        modifier = modifier.keyboardFocusRing(source, shape, SegmentedButtonVisibleHeight),
        enabled = enabled,
        colors = colors,
        border = border,
        contentPadding = contentPadding,
        interactionSource = source,
        icon = icon,
        label = label,
    )
}

/** What a segmented button draws inside its 48 dp touch area. */
private val SegmentedButtonVisibleHeight = 40.dp
