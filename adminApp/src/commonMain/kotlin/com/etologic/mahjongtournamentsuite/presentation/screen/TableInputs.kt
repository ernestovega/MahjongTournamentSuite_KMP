package com.etologic.mahjongtournamentsuite.presentation.screen

import com.etologic.mahjongtournamentsuite.presentation.components.SelectField
import com.etologic.mahjongtournamentsuite.presentation.components.SelectOption
import com.etologic.mahjongtournamentsuite.presentation.components.rememberSelectMenuState
import com.etologic.mahjongtournamentsuite.presentation.util.rememberTemplate
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.etologic.mahjongtournamentsuite.presentation.components.FocusHighlightContainer
import com.etologic.mahjongtournamentsuite.presentation.components.InfoTooltipIcon
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

/**
 * A player menu in a table cell. When the menu is closed, the arrow keys move the focus to the next cell.
 * The Enter key and the space key open the menu.
 */
@Composable
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
    val menu = rememberSelectMenuState()
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
    // The menu of a disabled field stays readable. Its options are disabled.
    val options = buildList {
        emptyOptionLabel?.let { emptyLabel -> add(SelectOption(emptyLabel, emptyLabel, enabled = enabled)) }
        playerIds.forEach { id ->
            if (id in excludedPlayerIds) return@forEach
            val suffix = optionSuffixById[id]
            val base = playerLabel(id)
            add(SelectOption(id.toString(), if (suffix.isNullOrBlank()) base else "$base ($suffix)", enabled = enabled))
        }
    }
    val colors = if (enabled && isChanged) {
        OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = MaterialTheme.colorScheme.tertiary,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedLabelColor = MaterialTheme.colorScheme.tertiary,
        )
    } else {
        OutlinedTextFieldDefaults.colors()
    }
    SelectField(
        label = label,
        options = options,
        selected = trimmed,
        onSelect = onChange,
        modifier = modifier,
        enabled = enabled,
        openWhenDisabled = true,
        valueText = displayValue,
        textStyle = textStyle,
        colors = colors,
        onClosed = onDismiss,
        state = menu,
        fieldModifier = (focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
            .onFocusChanged { if (!it.isFocused) onFocusLost() }
            .onPreviewKeyEvent { event: KeyEvent ->
                if (!enabled || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.Enter, Key.NumPadEnter, Key.Spacebar -> {
                        menu.open()
                        true
                    }
                    Key.DirectionUp -> onNavigateUp?.invoke() ?: false
                    Key.DirectionDown -> onNavigateDown?.invoke() ?: false
                    Key.DirectionLeft -> onNavigateLeft?.invoke() ?: false
                    Key.DirectionRight -> onNavigateRight?.invoke() ?: false
                    else -> false
                }
            },
    )
}
