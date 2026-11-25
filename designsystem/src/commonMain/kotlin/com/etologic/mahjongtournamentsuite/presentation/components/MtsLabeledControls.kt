package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.Indication
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxColors
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme

/*
 * The checkboxes, switches and radio buttons of the suite, with their text. The whole line is one control: a tap on
 * the text (or anywhere in the line) changes the value, like a tap on the box. The box itself only draws
 * (onCheckedChange = null), so the line is one node for the keyboard and for TalkBack.
 *
 * The screens do not call Material's Checkbox, Switch or RadioButton directly: LabeledControlsTest fails when they do.
 *
 * A Material control without its own click has no touch target, so it would be smaller than a normal one. The padding
 * around it gives back the size of the touch target (48 dp, or what LocalMinimumInteractiveComponentSize says), so a
 * line looks the same as before.
 */

/** The padding that makes a control of [size] as tall as the touch target. */
@Composable
private fun touchTargetPadding(size: Dp): Dp = ((LocalMinimumInteractiveComponentSize.current - size) / 2).coerceAtLeast(0.dp)

/** The size of the box of a Checkbox and of the circle of a RadioButton, with their own padding. */
private val SmallControlSize = 24.dp

/** The height of a Switch. */
private val SwitchHeight = 32.dp

/**
 * A checkbox with its text. [content] is the text (and anything else that belongs to the line); a tap anywhere on the
 * line changes the value. [onCheckedChange] null makes the line read-only. [boxLast] puts the box after the text, at
 * the end of the line, where a thumb reaches it on a phone.
 */
@Composable
fun MtsCheckboxRow(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    gap: Dp = MtsTheme.spacing.none,
    colors: CheckboxColors = CheckboxDefaults.colors(),
    interactionSource: MutableInteractionSource? = null,
    indication: Indication? = LocalIndication.current,
    contentPadding: PaddingValues = PaddingValues(),
    checkboxModifier: Modifier = Modifier,
    controlPadding: Dp = touchTargetPadding(SmallControlSize),
    boxLast: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .then(
                if (onCheckedChange != null) {
                    Modifier.toggleable(
                        value = checked,
                        enabled = enabled,
                        role = Role.Checkbox,
                        interactionSource = interactionSource,
                        indication = indication,
                        onValueChange = onCheckedChange,
                    )
                } else {
                    Modifier
                },
            )
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(gap),
    ) {
        val box: @Composable () -> Unit = {
            Checkbox(
                checked = checked,
                onCheckedChange = null,
                enabled = enabled,
                colors = colors,
                modifier = checkboxModifier.padding(controlPadding),
            )
        }
        if (!boxLast) box()
        content()
        if (boxLast) box()
    }
}

/** A checkbox with a one-line text. A tap on the text changes the value too. */
@Composable
fun MtsCheckboxRow(
    text: String,
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
) {
    MtsCheckboxRow(checked = checked, onCheckedChange = onCheckedChange, modifier = modifier, enabled = enabled) {
        Text(text, style = style, modifier = Modifier.weight(1f, fill = false).padding(end = MtsTheme.spacing.sm))
    }
}

/**
 * A switch with its text. A tap anywhere on the line changes the value. [switchFirst] puts the switch before the text;
 * by default the text goes first and the switch at the end.
 */
@Composable
fun MtsSwitchRow(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    switchFirst: Boolean = false,
    gap: Dp = MtsTheme.spacing.sm,
    interactionSource: MutableInteractionSource? = null,
    indication: Indication? = LocalIndication.current,
    contentPadding: PaddingValues = PaddingValues(),
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .then(
                if (onCheckedChange != null) {
                    Modifier.toggleable(
                        value = checked,
                        enabled = enabled,
                        role = Role.Switch,
                        interactionSource = interactionSource,
                        indication = indication,
                        onValueChange = onCheckedChange,
                    )
                } else {
                    Modifier
                },
            )
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(gap),
    ) {
        val switchModifier = Modifier.padding(vertical = touchTargetPadding(SwitchHeight))
        if (switchFirst) Switch(checked = checked, onCheckedChange = null, enabled = enabled, modifier = switchModifier)
        content()
        if (!switchFirst) Switch(checked = checked, onCheckedChange = null, enabled = enabled, modifier = switchModifier)
    }
}

/** A radio button with its text. A tap anywhere on the line selects it. */
@Composable
fun MtsRadioRow(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    gap: Dp = MtsTheme.spacing.none,
    contentPadding: PaddingValues = PaddingValues(),
    controlPadding: Dp = touchTargetPadding(SmallControlSize),
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = { if (!selected) onClick() })
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(gap),
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled, modifier = Modifier.padding(controlPadding))
        content()
    }
}
