package com.etologic.mahjongtournamentsuite.presentation.screen

import com.etologic.mahjongtournamentsuite.presentation.components.rememberMenuScrollState
import com.etologic.mahjongtournamentsuite.presentation.util.rememberTemplate
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.etologic.mahjongtournamentsuite.presentation.components.FocusHighlightContainer
import com.etologic.mahjongtournamentsuite.presentation.components.InfoTooltipIcon
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import mahjongtournamentsuite.adminapp.generated.resources.Res
import mahjongtournamentsuite.adminapp.generated.resources.player_n
import mahjongtournamentsuite.adminapp.generated.resources.show_info_for
import org.jetbrains.compose.resources.stringResource

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun LabeledSwitch(
    label: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    FocusHighlightContainer(
        modifier = Modifier,
        interactionSource = interactionSource,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Switch(
                checked = checked,
                enabled = enabled,
                onCheckedChange = onCheckedChange,
                interactionSource = interactionSource,
            )
            InfoTooltipIcon(
                description = description,
                contentDescription = stringResource(Res.string.show_info_for, label),
            )
        }
    }
}

@Composable
internal fun HandToggleControl(
    width: Dp,
    label: String?,
    checked: Boolean,
    enabled: Boolean,
    isChanged: Boolean = false,
    onCheckedChange: ((Boolean) -> Unit)?,
    focusRequester: FocusRequester? = null,
    onNavigateLeft: (() -> Boolean)? = null,
    onNavigateRight: (() -> Boolean)? = null,
    onNavigateUp: (() -> Boolean)? = null,
    onNavigateDown: (() -> Boolean)? = null,
) {
    val shape = MaterialTheme.shapes.extraSmall
    var focused by remember { mutableStateOf(false) }
    val focusModifier = Modifier
        .clip(shape)
        .background(
            if (focused && enabled) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
            } else {
                Color.Transparent
            },
        )
    val checkboxColors = when {
        !enabled -> CheckboxDefaults.colors(
            checkedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
            uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
            checkmarkColor = MaterialTheme.colorScheme.surface,
            disabledCheckedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
            disabledUncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
            disabledIndeterminateColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
        )
        isChanged -> CheckboxDefaults.colors(
            checkedColor = MaterialTheme.colorScheme.tertiary,
            uncheckedColor = MaterialTheme.colorScheme.tertiary,
            checkmarkColor = MaterialTheme.colorScheme.onTertiary,
        )
        else -> CheckboxDefaults.colors()
    }

    Column(
        modifier = Modifier
            .width(width)
            .then(focusModifier)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = when {
                    !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                    isChanged -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Checkbox(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
            colors = checkboxColors,
            modifier = (focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
                .onFocusChanged { focused = it.isFocused }
                .onPreviewKeyEvent { event ->
                    if (!enabled || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionLeft -> onNavigateLeft?.invoke() ?: false
                        Key.DirectionRight -> onNavigateRight?.invoke() ?: false
                        Key.DirectionUp -> onNavigateUp?.invoke() ?: false
                        Key.DirectionDown -> onNavigateDown?.invoke() ?: false
                        else -> false
                    }
                },
        )
    }
}

internal fun SeatTextValues?.orZero(): SeatTextValues = this ?: SeatTextValues.ZERO

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun PlayerDropdown(
    modifier: Modifier = Modifier,
    label: String?,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    playerIds: List<Int>,
    value: String,
    enabled: Boolean,
    onChange: (String) -> Unit,
    onDismiss: () -> Unit = {},
    onFocusLost: () -> Unit = {},
    playerNamesById: Map<Int, String> = emptyMap(),
    showPlayerId: Boolean = false,
    optionSuffixById: Map<Int, String> = emptyMap(),
    excludedPlayerIds: Set<Int> = emptySet(),
    emptyOptionLabel: String? = null,
    isChanged: Boolean = false,
    focusRequester: FocusRequester? = null,
    onNavigateLeft: (() -> Boolean)? = null,
    onNavigateRight: (() -> Boolean)? = null,
    onNavigateUp: (() -> Boolean)? = null,
    onNavigateDown: (() -> Boolean)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    var lastDismissMark by remember { mutableStateOf<TimeMark?>(null) }
    val anchorFocusRequester = remember { FocusRequester() }
    data class DropdownOption(
        val value: String,
        val label: String,
        val onSelect: () -> Unit,
    )

    val trimmed = value.trim()
    val selectedId = trimmed.toIntOrNull()
    val playerN = rememberTemplate(Res.string.player_n)
    fun playerLabel(id: Int): String {
        val name = playerNamesById[id] ?: playerN(id)
        return if (showPlayerId) "$id - $name" else name
    }
    val displayValue = when {
        trimmed.isBlank() || selectedId == null && trimmed == "-" -> emptyOptionLabel.orEmpty()
        selectedId == null -> trimmed
        else -> playerLabel(selectedId)
    }
    val options = buildList {
        emptyOptionLabel?.let { emptyLabel ->
            add(
                DropdownOption(
                    value = emptyLabel,
                    label = emptyLabel,
                    onSelect = {
                        expanded = false
                        onChange(emptyLabel)
                        onDismiss()
                    },
                ),
            )
        }
        playerIds.forEach { id ->
            if (id in excludedPlayerIds) return@forEach
            val suffix = optionSuffixById[id]
            val base = playerLabel(id)
            add(
                DropdownOption(
                    value = id.toString(),
                    label = if (suffix.isNullOrBlank()) base else "$base ($suffix)",
                    onSelect = {
                        expanded = false
                        onChange(id.toString())
                        onDismiss()
                    },
                ),
            )
        }
    }
    val selectedOptionIndex = options.indexOfFirst { it.value == trimmed }.takeIf { it >= 0 } ?: 0
    var activeOptionIndex by remember { mutableStateOf(selectedOptionIndex) }

    fun openMenu() {
        activeOptionIndex = selectedOptionIndex
        expanded = true
        anchorFocusRequester.requestFocus()
    }

    fun moveActive(delta: Int) {
        if (options.isEmpty()) return
        val nextIndex = (activeOptionIndex + delta).coerceIn(0, options.lastIndex)
        activeOptionIndex = nextIndex
    }

    fun moveAway(key: Key): Boolean {
        if (expanded) expanded = false
        return when (key) {
            Key.DirectionLeft -> onNavigateLeft?.invoke() ?: false
            Key.DirectionRight -> onNavigateRight?.invoke() ?: false
            Key.DirectionUp -> onNavigateUp?.invoke() ?: false
            Key.DirectionDown -> onNavigateDown?.invoke() ?: false
            else -> false
        }
    }

    LaunchedEffect(expanded, selectedOptionIndex, options.size) {
        if (!expanded) {
            activeOptionIndex = selectedOptionIndex
        } else {
            activeOptionIndex = activeOptionIndex.coerceIn(0, options.lastIndex.coerceAtLeast(0))
        }
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        // The menu remains inspectable when the field is disabled. Its options stay disabled below.
        onExpandedChange = { shouldExpand ->
            // The popup closes on the press and the anchor toggles on the release.
            // Ignore that release so a click on the open anchor does not reopen the menu.
            if (shouldExpand && (lastDismissMark?.elapsedNow() ?: 1.seconds) < 400.milliseconds) {
                return@ExposedDropdownMenuBox
            }
            expanded = shouldExpand
        },
        modifier = modifier,
    ) {
        CompactOutlinedTextField(
            modifier = Modifier
                .menuAnchor(
                    type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                    enabled = true,
                )
                .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
                .focusRequester(anchorFocusRequester)
                .onFocusChanged { if (!it.isFocused) onFocusLost() }
                .onPreviewKeyEvent { e: KeyEvent ->
                    if (!enabled || e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (e.key) {
                        Key.DirectionDown -> if (expanded) {
                            moveActive(1)
                            true
                        } else {
                            openMenu()
                            true
                        }

                        Key.DirectionUp -> if (expanded) {
                            moveActive(-1)
                            true
                        } else {
                            openMenu()
                            true
                        }

                        Key.DirectionLeft, Key.DirectionRight -> moveAway(e.key)

                        Key.Enter, Key.NumPadEnter, Key.Spacebar -> {
                            if (expanded) {
                                options.getOrNull(activeOptionIndex)?.onSelect()
                            } else {
                                openMenu()
                            }
                            true
                        }
                        Key.Escape -> {
                            if (expanded) {
                                expanded = false
                                true
                            } else {
                                false
                            }
                        }
                        else -> false
                    }
                }
                .fillMaxWidth(),
            value = displayValue,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = label?.let { { Text(it) } },
            textStyle = textStyle,
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
        colors = if (enabled && isChanged) {
            androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = MaterialTheme.colorScheme.tertiary,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedLabelColor = MaterialTheme.colorScheme.tertiary,
            )
        } else {
            ExposedDropdownMenuDefaults.outlinedTextFieldColors()
        },
        )
        // A non-focusable popup lets the click reach another dropdown below it.
        // A focusable popup would swallow that click and need a second one.
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                lastDismissMark = TimeSource.Monotonic.markNow()
                expanded = false
                onDismiss()
            },
            modifier = Modifier.exposedDropdownSize(),
            scrollState = rememberMenuScrollState(expanded, options.indexOfFirst { it.value == trimmed }),
            properties = PopupProperties(focusable = false, dismissOnClickOutside = true),
        ) {
            Column(
                modifier = Modifier
                    .onPreviewKeyEvent { e ->
                        if (!enabled || !expanded || e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (e.key) {
                            Key.DirectionDown -> {
                                if (activeOptionIndex < options.lastIndex) {
                                    moveActive(1)
                                    true
                                } else {
                                    moveAway(Key.DirectionDown)
                                }
                            }

                            Key.DirectionUp -> {
                                if (activeOptionIndex > 0) {
                                    moveActive(-1)
                                    true
                                } else {
                                    moveAway(Key.DirectionUp)
                                }
                            }

                            Key.DirectionLeft, Key.DirectionRight -> moveAway(e.key)
                            Key.Enter, Key.NumPadEnter, Key.Spacebar -> {
                                options.getOrNull(activeOptionIndex)?.onSelect()
                                true
                            }

                            Key.Escape -> {
                                expanded = false
                                true
                            }

                            else -> false
                        }
                    },
            ) {
                options.forEachIndexed { index, option ->
                    DropdownMenuItem(
                        modifier = Modifier.background(
                            if (index == activeOptionIndex) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                Color.Transparent
                            },
                        ),
                        text = { Text(option.label) },
                        enabled = enabled,
                        onClick = {
                            activeOptionIndex = index
                            option.onSelect()
                            anchorFocusRequester.requestFocus()
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                    )
                }
            }
        }
    }
}
