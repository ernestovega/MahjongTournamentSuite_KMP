package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsInputModality
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.isSpecified
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

private val FocusHaloPadding = 3.dp

/** The circle that an icon button draws inside its 48 dp touch area. */
internal val IconButtonVisibleSize = 40.dp
private val ButtonPressThreshold = 500.milliseconds

/** Controls buttons in the current app screen. The back button is outside this scope. */
val LocalAppButtonsEnabled = staticCompositionLocalOf { true }

/** The clock of the press threshold. Tests give a clock that they move by hand, so a slow machine cannot change the result. */
val LocalPressTimeSource = staticCompositionLocalOf<TimeSource> { TimeSource.Monotonic }

@Composable
internal fun rememberThresholdOnClick(onClick: () -> Unit): () -> Unit {
    val currentOnClick by rememberUpdatedState(onClick)
    val timeSource = LocalPressTimeSource.current
    return remember(timeSource) {
        var lastPress: TimeMark? = null
        {
            val previousPress = lastPress
            if (previousPress == null || previousPress.elapsedNow() >= ButtonPressThreshold) {
                lastPress = timeSource.markNow()
                currentOnClick()
            }
        }
    }
}

/** Preserves child focus and handles arrow keys that the focused control does not use. */
@Composable
fun Modifier.appFocusGroup(): Modifier {
    val focusManager = LocalFocusManager.current
    return this
        .focusRestorer()
        .focusGroup()
        .onKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
            val direction = when (event.key) {
                Key.DirectionLeft -> FocusDirection.Left
                Key.DirectionRight -> FocusDirection.Right
                Key.DirectionUp -> FocusDirection.Up
                Key.DirectionDown -> FocusDirection.Down
                else -> return@onKeyEvent false
            }
            focusManager.moveFocus(direction)
        }
}

/** Moves focus within a closed control group. Enter remains available for activation. */
fun Modifier.focusLoop(
    previous: FocusRequester,
    next: FocusRequester,
): Modifier = onPreviewKeyEvent { event ->
    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
    when (event.key) {
        Key.Tab -> {
            if (event.isShiftPressed) previous.requestFocus() else next.requestFocus()
            true
        }
        Key.DirectionLeft, Key.DirectionUp -> {
            previous.requestFocus()
            true
        }
        Key.DirectionRight, Key.DirectionDown -> {
            next.requestFocus()
            true
        }
        else -> false
    }
}

/** Moves focus horizontally only when the caret is at the matching text boundary. */
fun Modifier.textFieldFocusLoop(
    previous: FocusRequester,
    next: FocusRequester,
    value: () -> TextFieldValue,
): Modifier = onPreviewKeyEvent { event ->
    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
    when (event.key) {
        Key.Tab -> {
            if (event.isShiftPressed) previous.requestFocus() else next.requestFocus()
            true
        }
        Key.DirectionLeft -> {
            val selection = value().selection
            if (selection.collapsed && selection.start == 0) {
                previous.requestFocus()
                true
            } else {
                false
            }
        }
        Key.DirectionRight -> {
            val currentValue = value()
            val selection = currentValue.selection
            if (selection.collapsed && selection.end == currentValue.text.length) {
                next.requestFocus()
                true
            } else {
                false
            }
        }
        Key.DirectionUp -> {
            previous.requestFocus()
            true
        }
        Key.DirectionDown -> {
            next.requestFocus()
            true
        }
        else -> false
    }
}

/** Activates a custom clickable control with Enter or Numpad Enter. */
fun Modifier.activateOnEnter(
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier = onPreviewKeyEvent { event ->
    if (!enabled || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
    when (event.key) {
        Key.Enter, Key.NumPadEnter -> {
            onClick()
            true
        }
        else -> false
    }
}

@Composable
fun FocusedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    buttonModifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.shape,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    elevation: ButtonElevation? = ButtonDefaults.buttonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    focusRequester: FocusRequester? = null,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val buttonEnabled = enabled && LocalAppButtonsEnabled.current
    val thresholdOnClick = rememberThresholdOnClick(onClick)
    val defaultInteractionSource = remember { MutableInteractionSource() }
    val resolvedInteractionSource = interactionSource ?: defaultInteractionSource
    FocusHighlightContainer(
        modifier = modifier,
        interactionSource = resolvedInteractionSource,
        shape = shape,
        visibleHeight = ButtonDefaults.MinHeight,
    ) {
        Button(
            onClick = thresholdOnClick,
            modifier = buttonModifier.then(
                if (focusRequester == null) Modifier else Modifier.focusRequester(focusRequester),
            ),
            enabled = buttonEnabled,
            shape = shape,
            colors = colors,
            elevation = elevation,
            border = border,
            contentPadding = contentPadding,
            interactionSource = resolvedInteractionSource,
            content = content,
        )
    }
}

@Composable
fun FocusedOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    buttonModifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.outlinedShape,
    colors: ButtonColors = MtsButtons.outlinedColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = ButtonDefaults.outlinedButtonBorder(enabled),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    focusRequester: FocusRequester? = null,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val buttonEnabled = enabled && LocalAppButtonsEnabled.current
    val thresholdOnClick = rememberThresholdOnClick(onClick)
    val defaultInteractionSource = remember { MutableInteractionSource() }
    val resolvedInteractionSource = interactionSource ?: defaultInteractionSource
    FocusHighlightContainer(
        modifier = modifier,
        interactionSource = resolvedInteractionSource,
        shape = shape,
        visibleHeight = ButtonDefaults.MinHeight,
    ) {
        OutlinedButton(
            onClick = thresholdOnClick,
            modifier = buttonModifier.then(
                if (focusRequester == null) Modifier else Modifier.focusRequester(focusRequester),
            ),
            enabled = buttonEnabled,
            shape = shape,
            colors = colors,
            elevation = elevation,
            border = if (buttonEnabled) border else ButtonDefaults.outlinedButtonBorder(false),
            contentPadding = contentPadding,
            interactionSource = resolvedInteractionSource,
            content = content,
        )
    }
}

@Composable
fun FocusedTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    buttonModifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.textShape,
    colors: ButtonColors = MtsButtons.textColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
    focusRequester: FocusRequester? = null,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val buttonEnabled = enabled && LocalAppButtonsEnabled.current
    val thresholdOnClick = rememberThresholdOnClick(onClick)
    val defaultInteractionSource = remember { MutableInteractionSource() }
    val resolvedInteractionSource = interactionSource ?: defaultInteractionSource
    FocusHighlightContainer(
        modifier = modifier,
        interactionSource = resolvedInteractionSource,
        shape = shape,
        visibleHeight = ButtonDefaults.MinHeight,
    ) {
        TextButton(
            onClick = thresholdOnClick,
            modifier = buttonModifier.then(
                if (focusRequester == null) Modifier else Modifier.focusRequester(focusRequester),
            ),
            enabled = buttonEnabled,
            shape = shape,
            colors = colors,
            elevation = elevation,
            border = border,
            contentPadding = contentPadding,
            interactionSource = resolvedInteractionSource,
            content = content,
        )
    }
}

@Composable
fun FocusedAssistChip(
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    chipModifier: Modifier = Modifier,
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null,
) {
    val buttonEnabled = enabled && LocalAppButtonsEnabled.current
    val thresholdOnClick = rememberThresholdOnClick(onClick)
    val interactionSource = remember { MutableInteractionSource() }
    FocusHighlightContainer(
        modifier = modifier,
        interactionSource = interactionSource,
        shape = AssistChipDefaults.shape,
        visibleHeight = AssistChipDefaults.Height,
    ) {
        AssistChip(
            onClick = thresholdOnClick,
            label = label,
            modifier = chipModifier.then(
                if (focusRequester == null) Modifier else Modifier.focusRequester(focusRequester),
            ),
            enabled = buttonEnabled,
            interactionSource = interactionSource,
        )
    }
}

@Composable
fun FocusedIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    buttonModifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
    focusRequester: FocusRequester? = null,
    showFocusHighlight: Boolean = true,
    content: @Composable () -> Unit,
) {
    val buttonEnabled = enabled && LocalAppButtonsEnabled.current
    val thresholdOnClick = rememberThresholdOnClick(onClick)
    val interactionSource = remember { MutableInteractionSource() }
    FocusHighlightContainer(
        modifier = modifier,
        interactionSource = interactionSource,
        showFocusHighlight = showFocusHighlight,
        shape = CircleShape,
        visibleHeight = IconButtonVisibleSize,
    ) {
        IconButton(
            onClick = thresholdOnClick,
            modifier = buttonModifier.then(
                if (focusRequester == null) Modifier else Modifier.focusRequester(focusRequester),
            ),
            enabled = buttonEnabled,
            colors = colors,
            interactionSource = interactionSource,
            content = content,
        )
    }
}

private val emptyFunction: () -> Unit = {}

private val FocusRingWidth = 2.dp

/** True while the person uses the keyboard. Only then the focus shows (see [MtsInputModality]). */
@Composable
fun keyboardInUse(): Boolean {
    InputModalityTracker()
    return MtsInputModality.keyboard
}

/**
 * Draws a keyboard focus ring in [shape] around the control while it has the focus and the keyboard is in use. It is
 * a line in the accent color (pink), not a fill, so it does not mix with the green of the controls.
 */
@Composable
fun Modifier.focusRing(focused: Boolean, shape: Shape): Modifier =
    if (focused && keyboardInUse()) border(FocusRingWidth, MaterialTheme.colorScheme.tertiary, shape) else this

/**
 * The pink focus ring of a control, right on the edge that the control draws. It shows only while the control has the
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
    val showRing = enabled && focused && keyboardInUse()
    val ringColor = MaterialTheme.colorScheme.tertiary
    val touchSize = LocalMinimumInteractiveComponentSize.current
    return drawWithContent {
        drawContent()
        if (!showRing) return@drawWithContent
        val stroke = FocusRingWidth.toPx()
        val drawnHeight = visibleHeight?.toPx()?.coerceAtMost(size.height) ?: size.height
        val narrow = touchSize.isSpecified && size.width <= touchSize.toPx() + 0.5f
        val drawnWidth = if (visibleHeight != null && narrow) minOf(size.width, drawnHeight) else size.width
        // The line goes just outside the drawn edge, touching it. Without room around the control (a Debounced button
        // is as big as what it draws) it goes just inside the edge, so no parent clips it.
        val ringSize = Size(
            if (drawnWidth + 2 * stroke <= size.width) drawnWidth + stroke else size.width - stroke,
            if (drawnHeight + 2 * stroke <= size.height) drawnHeight + stroke else size.height - stroke,
        )
        val outline = shape.createOutline(ringSize, layoutDirection, this)
        translate((size.width - ringSize.width) / 2f, (size.height - ringSize.height) / 2f) {
            drawOutline(outline, ringColor, style = Stroke(stroke))
        }
    }
}

/**
 * A press of the mouse moves the focus to the control under it, so Tab and the arrows go on from there. A finger does
 * not: on a phone the focus would leave a text field and close its keyboard.
 */
@Composable
fun Modifier.focusOnMousePress(): Modifier {
    val requester = remember { FocusRequester() }
    return focusRequester(requester).pointerInput(requester) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (down.type == PointerType.Mouse) runCatching { requester.requestFocus() }
        }
    }
}

/**
 * The room around a control for its keyboard focus. A control lights up in one place only, and only one mark shows
 * at a time: the hover while the mouse is in use, the focus while the keyboard is (Ernesto, 27-nov).
 *
 * - With [lightsUpContent] false (the default) the control inside draws its own hover layer, as every Material
 *   button, chip, card or ripple does. The container draws the pink [keyboardFocusRing] on the edge of the control.
 * - With [lightsUpContent] true the control inside has no indication of its own (`indication = null`). The container
 *   fills its area in [shape] on hover, and on focus while the keyboard is in use. That fill is the only mark.
 *
 * A press of the mouse on the control moves the focus to it ([focusOnMousePress]).
 */
@Composable
fun FocusHighlightContainer(
    modifier: Modifier,
    interactionSource: MutableInteractionSource,
    showFocusHighlight: Boolean = true,
    shape: Shape = MaterialTheme.shapes.small,
    lightsUpContent: Boolean = false,
    visibleHeight: Dp? = null,
    content: @Composable () -> Unit,
) {
    val boxModifier = if (lightsUpContent) {
        val focused by interactionSource.collectIsFocusedAsState()
        val hovered by interactionSource.collectIsHoveredAsState()
        val lit = (showFocusHighlight && focused && keyboardInUse()) || hovered
        modifier
            .padding(FocusHaloPadding)
            .clip(shape)
            .background(if (lit) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.Transparent)
    } else {
        modifier
            .padding(FocusHaloPadding)
            .keyboardFocusRing(interactionSource, shape, visibleHeight, enabled = showFocusHighlight)
    }
    Box(modifier = boxModifier.focusOnMousePress(), propagateMinConstraints = true) {
        content()
    }
}
