package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.AssistChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

private val FocusHaloPadding = 3.dp
private val ButtonPressThreshold = 500.milliseconds

/** Controls buttons in the current app screen. The back button is outside this scope. */
val LocalAppButtonsEnabled = staticCompositionLocalOf { true }

@Composable
private fun rememberThresholdOnClick(onClick: () -> Unit): () -> Unit {
    val currentOnClick by rememberUpdatedState(onClick)
    return remember {
        var lastPress: TimeMark? = null
        {
            val previousPress = lastPress
            if (previousPress == null || previousPress.elapsedNow() >= ButtonPressThreshold) {
                lastPress = TimeSource.Monotonic.markNow()
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
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
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
    colors: ButtonColors = ButtonDefaults.textButtonColors(),
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

@Composable
fun FocusedExtendedFloatingActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = FloatingActionButtonDefaults.containerColor,
    contentColor: Color = contentColorFor(containerColor),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val buttonEnabled = enabled && LocalAppButtonsEnabled.current
    val thresholdOnClick = rememberThresholdOnClick(onClick)
    val defaultInteractionSource = remember { MutableInteractionSource() }
    val resolvedInteractionSource = interactionSource ?: defaultInteractionSource
    FocusHighlightContainer(
        modifier = Modifier,
        interactionSource = resolvedInteractionSource,
    ) {
        ExtendedFloatingActionButton(
            onClick = if (buttonEnabled) thresholdOnClick else emptyFunction,
            modifier = modifier
                .focusProperties { canFocus = buttonEnabled }
                .alpha(if (buttonEnabled) 1f else 0.38f),
            containerColor = containerColor,
            contentColor = contentColor,
            interactionSource = resolvedInteractionSource,
            content = content,
        )
    }
}

private val emptyFunction: () -> Unit = {}

@Composable
fun FocusHighlightContainer(
    modifier: Modifier,
    interactionSource: MutableInteractionSource,
    showFocusHighlight: Boolean = true,
    content: @Composable () -> Unit,
) {
    val focused by interactionSource.collectIsFocusedAsState()
    val hovered by interactionSource.collectIsHoveredAsState()
    Box(
        modifier = modifier
            .padding(FocusHaloPadding)
            .clip(MaterialTheme.shapes.small)
            .background(
                if ((showFocusHighlight && focused) || hovered) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                } else {
                    androidx.compose.ui.graphics.Color.Transparent
                },
            ),
        propagateMinConstraints = true,
    ) {
        content()
    }
}
