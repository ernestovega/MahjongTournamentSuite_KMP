package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

/** Keeps digits and a hyphen at the start. Other characters are dropped. */
internal fun String.toSignedIntegerInput(): String =
    filterIndexed { index, char -> char.isDigit() || (index == 0 && char == '-') }

@Composable
internal fun CompactOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false,
    isChanged: Boolean = false,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    compactHeight: Dp? = null,
    colors: androidx.compose.material3.TextFieldColors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(),
    onNavigateLeft: (() -> Boolean)? = null,
    onNavigateRight: (() -> Boolean)? = null,
) {
    val resolvedColors = if (enabled && isChanged && !isError) {
        OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = MaterialTheme.colorScheme.tertiary,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedLabelColor = MaterialTheme.colorScheme.tertiary,
        )
    } else {
        colors
    }
    var fieldValue by remember(value) {
        mutableStateOf(TextFieldValue(text = value, selection = TextRange(value.length)))
    }
    LaunchedEffect(value) {
        if (fieldValue.text != value) {
            fieldValue = fieldValue.copy(text = value, selection = TextRange(value.length))
        }
    }
    val keyModifier = Modifier.onPreviewKeyEvent { event ->
        if (!enabled || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        val selection = fieldValue.selection
        when (event.key) {
            Key.DirectionLeft ->
                selection.collapsed && selection.start == 0 && (onNavigateLeft?.invoke() ?: false)
            Key.DirectionRight ->
                selection.collapsed && selection.end == fieldValue.text.length && (onNavigateRight?.invoke() ?: false)
            else -> false
        }
    }
    OutlinedTextField(
        value = fieldValue,
        onValueChange = { typed ->
            fieldValue = typed
            onValueChange(typed.text)
        },
        modifier = (if (compactHeight != null) modifier.height(compactHeight) else modifier).then(keyModifier),
        enabled = enabled,
        isError = isError,
        readOnly = readOnly,
        singleLine = singleLine,
        label = label,
        placeholder = placeholder,
        supportingText = supportingText,
        trailingIcon = trailingIcon,
        textStyle = textStyle,
        colors = resolvedColors,
    )
}

@Composable
internal fun KeyboardAwareOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    signedIntegerOnly: Boolean = false,
    currentSlot: HandFieldSlot,
    rowEnabled: Boolean,
    rowNavigation: HandRowKeyboardNavigator,
    previousNavigation: HandRowKeyboardNavigator? = null,
    nextNavigation: HandRowKeyboardNavigator? = null,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    enabled: Boolean = true,
    isError: Boolean = false,
    isChanged: Boolean = false,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    compactHeight: Dp? = null,
    colors: androidx.compose.material3.TextFieldColors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(),
) {
    val resolvedColors = if (enabled && isChanged && !isError) {
        OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = MaterialTheme.colorScheme.tertiary,
            focusedBorderColor = MaterialTheme.colorScheme.tertiary,
            unfocusedLabelColor = MaterialTheme.colorScheme.tertiary,
        )
    } else {
        colors
    }
    var fieldValue by remember(value) {
        mutableStateOf(TextFieldValue(text = value, selection = TextRange(value.length)))
    }

    LaunchedEffect(value) {
        if (fieldValue.text != value) {
            fieldValue = fieldValue.copy(text = value, selection = TextRange(value.length))
        }
    }

    OutlinedTextField(
        value = fieldValue,
        onValueChange = { typed ->
            val newValue = if (signedIntegerOnly) typed.copy(text = typed.text.toSignedIntegerInput()) else typed
            fieldValue = newValue
            onValueChange(newValue.text)
        },
        modifier = (if (compactHeight != null) modifier.height(compactHeight) else modifier)
            .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
            .onPreviewKeyEvent { event ->
                if (!enabled || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val selection = fieldValue.selection
                val textLength = fieldValue.text.length
                when (event.key) {
                    Key.DirectionLeft -> {
                        if (selection.collapsed && selection.start == 0) {
                            moveHandFieldFocus(
                                current = currentSlot,
                                direction = HandArrowDirection.Left,
                                rowEnabled = rowEnabled,
                                rowNavigation = rowNavigation,
                                previousNavigation = previousNavigation,
                                nextNavigation = nextNavigation,
                            )
                        } else {
                            false
                        }
                    }

                    Key.DirectionRight -> {
                        if (selection.collapsed && selection.end == textLength) {
                            moveHandFieldFocus(
                                current = currentSlot,
                                direction = HandArrowDirection.Right,
                                rowEnabled = rowEnabled,
                                rowNavigation = rowNavigation,
                                previousNavigation = previousNavigation,
                                nextNavigation = nextNavigation,
                            )
                        } else {
                            false
                        }
                    }

                    Key.DirectionUp -> moveHandFieldFocus(
                        current = currentSlot,
                        direction = HandArrowDirection.Up,
                        rowEnabled = rowEnabled,
                        rowNavigation = rowNavigation,
                        previousNavigation = previousNavigation,
                        nextNavigation = nextNavigation,
                    )

                    Key.DirectionDown -> moveHandFieldFocus(
                        current = currentSlot,
                        direction = HandArrowDirection.Down,
                        rowEnabled = rowEnabled,
                        rowNavigation = rowNavigation,
                        previousNavigation = previousNavigation,
                        nextNavigation = nextNavigation,
                    )

                    else -> false
                }
            },
        enabled = enabled,
        isError = isError,
        readOnly = readOnly,
        singleLine = singleLine,
        label = label,
        placeholder = placeholder,
        supportingText = supportingText,
        trailingIcon = trailingIcon,
        textStyle = textStyle,
        colors = resolvedColors,
    )
}

@Composable
internal fun SimpleHandCellField(
    value: String,
    onValueChange: (String) -> Unit,
    signedIntegerOnly: Boolean = false,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isChanged: Boolean = false,
    placeholder: String,
    focusRequester: FocusRequester? = null,
    onNavigateLeft: (() -> Boolean)? = null,
    onNavigateRight: (() -> Boolean)? = null,
    onNavigateUp: (() -> Boolean)? = null,
    onNavigateDown: (() -> Boolean)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val borderColor = when {
        focused -> MaterialTheme.colorScheme.primary
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        isChanged -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.outline
    }
    val shape = MaterialTheme.shapes.extraSmall
    val borderWidth = if (focused) 2.dp else 1.dp
    var textFieldValue by remember(value) {
        mutableStateOf(TextFieldValue(text = value, selection = TextRange(value.length)))
    }

    LaunchedEffect(value) {
        if (textFieldValue.text != value) {
            textFieldValue = textFieldValue.copy(text = value, selection = TextRange(value.length))
        }
    }

    Box(
        modifier = modifier
            .height(HandMiniFieldHeight)
            .border(borderWidth, borderColor, shape),
        contentAlignment = Alignment.Center,
    ) {
        BasicTextField(
            value = textFieldValue,
            onValueChange = { typed ->
                val newValue = if (signedIntegerOnly) typed.copy(text = typed.text.toSignedIntegerInput()) else typed
                textFieldValue = newValue
                onValueChange(newValue.text)
            },
            enabled = enabled,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                textAlign = TextAlign.Center,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .fillMaxSize()
                .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
                .onFocusChanged { focused = it.isFocused }
                .onPreviewKeyEvent { event ->
                    if (!enabled || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val selection = textFieldValue.selection
                    val textLength = textFieldValue.text.length
                    when (event.key) {
                        Key.DirectionLeft -> {
                            if (selection.collapsed && selection.start == 0) {
                                onNavigateLeft?.invoke() ?: false
                            } else {
                                false
                            }
                        }

                        Key.DirectionRight -> {
                            if (selection.collapsed && selection.end == textLength) {
                                onNavigateRight?.invoke() ?: false
                            } else {
                                false
                            }
                        }

                        Key.DirectionUp -> onNavigateUp?.invoke() ?: false
                        Key.DirectionDown -> onNavigateDown?.invoke() ?: false
                        else -> false
                    }
                },
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    if (value.isBlank() && !focused) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.58f),
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    innerTextField()
                }
            },
        )
    }
}
