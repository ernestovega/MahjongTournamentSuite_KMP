package com.etologic.mahjongtournamentsuite.presentation.screen

import com.etologic.mahjongtournamentsuite.presentation.components.ButtonText
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.domain.model.AdminRegistrations
import com.etologic.mahjongtournamentsuite.domain.model.RegistrationPhase
import com.etologic.mahjongtournamentsuite.domain.model.RegistrationSettings
import com.etologic.mahjongtournamentsuite.domain.model.RegistrationStatus
import com.etologic.mahjongtournamentsuite.presentation.components.DateField
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedButton
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedTextButton
import com.etologic.mahjongtournamentsuite.presentation.components.LocalCompactLayout
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import com.etologic.mahjongtournamentsuite.presentation.util.formatLocalizedDate
import mahjongtournamentsuite.adminapp.generated.resources.Res
import mahjongtournamentsuite.adminapp.generated.resources.cancel
import mahjongtournamentsuite.adminapp.generated.resources.reg_add
import mahjongtournamentsuite.adminapp.generated.resources.reg_close_action
import mahjongtournamentsuite.adminapp.generated.resources.reg_closes_label
import mahjongtournamentsuite.adminapp.generated.resources.reg_closes_on_optional
import mahjongtournamentsuite.adminapp.generated.resources.reg_closes_until
import mahjongtournamentsuite.adminapp.generated.resources.reg_copy
import mahjongtournamentsuite.adminapp.generated.resources.reg_draw
import mahjongtournamentsuite.adminapp.generated.resources.reg_draw_needs_close
import mahjongtournamentsuite.adminapp.generated.resources.reg_edit
import mahjongtournamentsuite.adminapp.generated.resources.reg_link_label
import mahjongtournamentsuite.adminapp.generated.resources.reg_link_name
import mahjongtournamentsuite.adminapp.generated.resources.reg_no_close_date
import mahjongtournamentsuite.adminapp.generated.resources.reg_open_action
import mahjongtournamentsuite.adminapp.generated.resources.reg_places_label
import mahjongtournamentsuite.adminapp.generated.resources.reg_places_optional
import mahjongtournamentsuite.adminapp.generated.resources.reg_places_value
import mahjongtournamentsuite.adminapp.generated.resources.reg_places_value_no_limit
import mahjongtournamentsuite.adminapp.generated.resources.reg_save
import mahjongtournamentsuite.adminapp.generated.resources.reg_state_closed
import mahjongtournamentsuite.adminapp.generated.resources.reg_state_open
import mahjongtournamentsuite.adminapp.generated.resources.reg_status_closed_detail
import mahjongtournamentsuite.adminapp.generated.resources.reg_status_label
import mahjongtournamentsuite.adminapp.generated.resources.reg_waiting_and_pending
import org.jetbrains.compose.resources.stringResource
import com.etologic.mahjongtournamentsuite.presentation.components.DebouncedOutlinedButton as OutlinedButton

/**
 * The settings of the registrations as the rows of one card: status (with Close/Open and Make the draw), places, last
 * day and link. Each row says in words what its value means. Change turns the value of its row into its field.
 */
@Composable
internal fun RegistrationSettingsPanel(
    data: AdminRegistrations,
    editing: RegistrationSettingField?,
    busy: Boolean,
    canConfigure: Boolean,
    onEdit: (RegistrationSettingField) -> Unit,
    onCancel: () -> Unit,
    onSave: (RegistrationSettings) -> Unit,
    onCopyLink: () -> Unit,
    onDraw: () -> Unit,
) {
    val settings = data.settings
    val compact = LocalCompactLayout.current
    val pending = data.registrations.count { it.status == RegistrationStatus.PENDING }
    val changeButton: @Composable (RegistrationSettingField, String) -> Unit = { field, tag ->
        if (canConfigure && editing != field) {
            LabeledIconButton(
                text = stringResource(Res.string.reg_edit),
                icon = Icons.Default.Edit,
                enabled = !busy,
                onClick = { onEdit(field) },
                modifier = Modifier.testTag(tag),
            )
        }
    }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(horizontal = MtsTheme.layout.cardPadding, vertical = MtsTheme.spacing.xs)) {
            // Status: open or closed, what that means, and the two steps that follow: close, and make the draw.
            SettingRow(
                label = stringResource(Res.string.reg_status_label),
                actions = {
                    if (canConfigure) {
                        LabeledIconButton(
                            text = stringResource(if (settings.isOpen) Res.string.reg_close_action else Res.string.reg_open_action),
                            icon = if (settings.isOpen) Icons.Default.Lock else Icons.Default.LockOpen,
                            enabled = !busy,
                            onClick = { onSave(settings.copy(status = if (settings.isOpen) RegistrationPhase.CLOSED else RegistrationPhase.OPEN)) },
                            modifier = Modifier.testTag("registrations-toggle-status"),
                        )
                    }
                },
            ) {
                val color = if (settings.isOpen) MtsTheme.colors.link else MaterialTheme.colorScheme.onSurfaceVariant
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MtsTheme.spacing.sm)) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(color))
                    Text(
                        stringResource(if (settings.isOpen) Res.string.reg_state_open else Res.string.reg_state_closed),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = color,
                        modifier = Modifier.testTag("registrations-status-value"),
                    )
                }
                SettingDetail(stringResource(if (settings.isOpen) Res.string.reg_draw_needs_close else Res.string.reg_status_closed_detail))
                if (canConfigure && !settings.isOpen) {
                    OutlinedButton(onClick = onDraw, enabled = !busy, modifier = Modifier.padding(top = MtsTheme.spacing.xs)) {
                        Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                        ButtonText(stringResource(Res.string.reg_draw), modifier = Modifier.padding(start = MtsTheme.spacing.sm))
                    }
                }
            }
            HorizontalDivider()
            SettingRow(
                label = stringResource(Res.string.reg_places_label),
                actions = { changeButton(RegistrationSettingField.PLACES, "registrations-edit-places") },
            ) {
                if (editing == RegistrationSettingField.PLACES && canConfigure) {
                    var places by remember { mutableStateOf(settings.maxPlayers?.toString().orEmpty()) }
                    InlineEditor(onCancel = onCancel, onSave = { onSave(settings.copy(maxPlayers = places.toIntOrNull())) }) {
                        OutlinedTextField(
                            value = places,
                            onValueChange = { typed -> places = typed.filter(Char::isDigit).take(4) },
                            label = { Text(stringResource(Res.string.reg_places_optional)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("registrations-places-field"),
                        )
                    }
                } else {
                    val max = settings.maxPlayers
                    Text(
                        if (max == null) {
                            stringResource(Res.string.reg_places_value_no_limit, settings.confirmed)
                        } else {
                            stringResource(Res.string.reg_places_value, settings.confirmed, max)
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.testTag("registrations-places-value"),
                    )
                    SettingDetail(stringResource(Res.string.reg_waiting_and_pending, settings.waiting, pending), Modifier.testTag("registrations-waiting-value"))
                }
            }
            HorizontalDivider()
            SettingRow(
                label = stringResource(Res.string.reg_closes_label),
                actions = { changeButton(RegistrationSettingField.CLOSES_ON, "registrations-edit-closes") },
            ) {
                if (editing == RegistrationSettingField.CLOSES_ON && canConfigure) {
                    var closesOn by remember { mutableStateOf(settings.closesOn.orEmpty()) }
                    InlineEditor(onCancel = onCancel, onSave = { onSave(settings.copy(closesOn = closesOn.ifBlank { null })) }) {
                        DateField(
                            label = stringResource(Res.string.reg_closes_on_optional),
                            value = closesOn,
                            onChange = { closesOn = it },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                } else {
                    val closesOn = settings.closesOn
                    Text(
                        if (closesOn == null) stringResource(Res.string.reg_no_close_date) else stringResource(Res.string.reg_closes_until, formatLocalizedDate(closesOn)),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            if (data.publicLink.isNotBlank() || editing == RegistrationSettingField.LINK) {
                HorizontalDivider()
                SettingRow(
                    label = stringResource(Res.string.reg_link_label),
                    actions = {
                        if (editing != RegistrationSettingField.LINK) {
                            LabeledIconButton(
                                text = stringResource(Res.string.reg_copy),
                                icon = Icons.Default.ContentCopy,
                                onClick = onCopyLink,
                                modifier = Modifier.testTag("registrations-copy-link"),
                            )
                        }
                        changeButton(RegistrationSettingField.LINK, "registrations-edit-link")
                    },
                ) {
                    if (editing == RegistrationSettingField.LINK && canConfigure) {
                        var slug by remember { mutableStateOf(settings.slug) }
                        InlineEditor(onCancel = onCancel, saveEnabled = slug.isNotBlank(), onSave = { onSave(settings.copy(slug = slug)) }) {
                            OutlinedTextField(
                                value = slug,
                                onValueChange = { typed -> slug = typed.lowercase().filter { it in 'a'..'z' || it in '0'..'9' || it == '-' }.take(40) },
                                label = { Text(stringResource(Res.string.reg_link_name)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("registrations-slug-field"),
                            )
                        }
                    } else {
                        SelectionContainer {
                            Text(
                                data.publicLink,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MtsTheme.colors.link,
                                maxLines = if (compact) 2 else 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.testTag("registrations-link-value"),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * One row of the settings. A wide screen has the title in a column of its own, the value beside it and the buttons at
 * the end; a phone has the title and the buttons in one line and the value under them.
 */
@Composable
private fun SettingRow(label: String, actions: @Composable () -> Unit, value: @Composable () -> Unit) {
    val title: @Composable (Modifier) -> Unit = { modifier ->
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
    }
    if (LocalCompactLayout.current) {
        Column(Modifier.fillMaxWidth().padding(vertical = MtsTheme.spacing.xs)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
                title(Modifier.weight(1f))
                actions()
            }
            Column(Modifier.fillMaxWidth().padding(bottom = MtsTheme.spacing.xs)) { value() }
        }
    } else {
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(vertical = MtsTheme.spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            title(Modifier.width(200.dp))
            Column(Modifier.weight(1f)) { value() }
            Row(verticalAlignment = Alignment.CenterVertically) { actions() }
        }
    }
}

@Composable
private fun SettingDetail(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

/** The field of a setting with its own Cancel and Save. */
@Composable
private fun InlineEditor(onCancel: () -> Unit, onSave: () -> Unit, saveEnabled: Boolean = true, field: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = MtsTheme.spacing.xs), verticalArrangement = Arrangement.spacedBy(MtsTheme.spacing.sm)) {
        field()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MtsTheme.spacing.sm, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
            FocusedTextButton(onClick = onCancel) {
                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                ButtonText(stringResource(Res.string.cancel), modifier = Modifier.padding(start = MtsTheme.spacing.sm))
            }
            FocusedButton(onClick = onSave, enabled = saveEnabled, modifier = Modifier.testTag("registrations-save-setting")) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                ButtonText(stringResource(Res.string.reg_save), modifier = Modifier.padding(start = MtsTheme.spacing.sm))
            }
        }
    }
}

/** Add a person, alone: the list is empty, so there is no search beside it. */
@Composable
internal fun AddPersonButton(busy: Boolean, onAdd: () -> Unit, modifier: Modifier = Modifier) {
    FocusedButton(onClick = onAdd, enabled = !busy, modifier = modifier) {
        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        ButtonText(stringResource(Res.string.reg_add), modifier = Modifier.padding(start = MtsTheme.spacing.sm))
    }
}
