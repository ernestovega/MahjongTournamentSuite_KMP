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
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
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

private val FocusHaloPadding = 3.dp

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
    content: @Composable RowScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    FocusHighlightContainer(
        modifier = modifier,
        interactionSource = interactionSource,
    ) {
        Button(
            onClick = onClick,
            modifier = buttonModifier.then(
                if (focusRequester == null) Modifier else Modifier.focusRequester(focusRequester),
            ),
            enabled = enabled,
            shape = shape,
            colors = colors,
            elevation = elevation,
            border = border,
            contentPadding = contentPadding,
            interactionSource = interactionSource,
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
    content: @Composable RowScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    FocusHighlightContainer(
        modifier = modifier,
        interactionSource = interactionSource,
    ) {
        OutlinedButton(
            onClick = onClick,
            modifier = buttonModifier.then(
                if (focusRequester == null) Modifier else Modifier.focusRequester(focusRequester),
            ),
            enabled = enabled,
            shape = shape,
            colors = colors,
            elevation = elevation,
            border = border,
            contentPadding = contentPadding,
            interactionSource = interactionSource,
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
    content: @Composable RowScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    FocusHighlightContainer(
        modifier = modifier,
        interactionSource = interactionSource,
    ) {
        TextButton(
            onClick = onClick,
            modifier = buttonModifier.then(
                if (focusRequester == null) Modifier else Modifier.focusRequester(focusRequester),
            ),
            enabled = enabled,
            shape = shape,
            colors = colors,
            elevation = elevation,
            border = border,
            contentPadding = contentPadding,
            interactionSource = interactionSource,
            content = content,
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
    val interactionSource = remember { MutableInteractionSource() }
    FocusHighlightContainer(
        modifier = modifier,
        interactionSource = interactionSource,
        showFocusHighlight = showFocusHighlight,
    ) {
        IconButton(
            onClick = onClick,
            modifier = buttonModifier.then(
                if (focusRequester == null) Modifier else Modifier.focusRequester(focusRequester),
            ),
            enabled = enabled,
            colors = colors,
            interactionSource = interactionSource,
            content = content,
        )
    }
}

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
