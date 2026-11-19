package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.domain.model.AccountRequest
import com.etologic.mahjongtournamentsuite.domain.model.AccountRequestKind
import com.etologic.mahjongtournamentsuite.domain.model.ManagedUser
import com.etologic.mahjongtournamentsuite.presentation.components.CountryFlag
import com.etologic.mahjongtournamentsuite.presentation.components.EmaCountryDropdown
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedButton
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedOutlinedButton
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedTextButton
import com.etologic.mahjongtournamentsuite.presentation.components.MtsButtons
import com.etologic.mahjongtournamentsuite.presentation.components.MtsDialogButtons
import com.etologic.mahjongtournamentsuite.presentation.components.MtsDialogTitle
import com.etologic.mahjongtournamentsuite.presentation.components.SectionCard
import com.etologic.mahjongtournamentsuite.presentation.components.WorldCountries
import com.etologic.mahjongtournamentsuite.presentation.components.appFocusGroup
import com.etologic.mahjongtournamentsuite.presentation.components.focusLoop
import com.etologic.mahjongtournamentsuite.presentation.components.rememberCountryNames
import com.etologic.mahjongtournamentsuite.presentation.components.verticalScrollWithIndicators
import com.etologic.mahjongtournamentsuite.presentation.telemetry.TrackedDialog
import mahjongtournamentsuite.adminapp.generated.resources.Res
import mahjongtournamentsuite.adminapp.generated.resources.account_request_approve
import mahjongtournamentsuite.adminapp.generated.resources.account_request_delete
import mahjongtournamentsuite.adminapp.generated.resources.account_request_disable
import mahjongtournamentsuite.adminapp.generated.resources.account_request_reason
import mahjongtournamentsuite.adminapp.generated.resources.account_request_reject
import mahjongtournamentsuite.adminapp.generated.resources.account_requests
import mahjongtournamentsuite.adminapp.generated.resources.account_requests_count
import mahjongtournamentsuite.adminapp.generated.resources.admin_countries
import mahjongtournamentsuite.adminapp.generated.resources.admin_countries_add
import mahjongtournamentsuite.adminapp.generated.resources.admin_countries_needed
import mahjongtournamentsuite.adminapp.generated.resources.admin_countries_not_yours
import mahjongtournamentsuite.adminapp.generated.resources.cancel
import mahjongtournamentsuite.adminapp.generated.resources.remove_country_n
import mahjongtournamentsuite.adminapp.generated.resources.remove_from_countries_action
import mahjongtournamentsuite.adminapp.generated.resources.remove_from_countries_message
import mahjongtournamentsuite.adminapp.generated.resources.remove_from_countries_reason
import mahjongtournamentsuite.adminapp.generated.resources.remove_from_countries_request_delete
import mahjongtournamentsuite.adminapp.generated.resources.remove_from_countries_request_disable
import mahjongtournamentsuite.adminapp.generated.resources.remove_from_countries_request_none
import mahjongtournamentsuite.adminapp.generated.resources.remove_from_my_countries
import org.jetbrains.compose.resources.stringResource

/** The longest reason that the server keeps for a request. */
private const val MAX_REQUEST_REASON_LENGTH = 1000

/**
 * The countries of a country admin, as chips with a flag, and a field to add one. The viewer removes and adds only
 * the countries in [editable] (null: every country, for a super admin). The other countries show without the remove.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AdminCountriesField(
    selected: Set<String>,
    editable: Set<String>?,
    enabled: Boolean,
    onChange: (Set<String>) -> Unit,
) {
    val countryNameOf = rememberCountryNames()
    val allCodes = remember { WorldCountries.map { it.code }.toSet() }
    val canAdd = (editable ?: allCodes) - selected
    val notYours = editable?.let { selected - it }.orEmpty()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.admin_countries), style = MaterialTheme.typography.titleSmall)
        if (selected.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                selected.sortedBy { countryNameOf(it, it) }.forEach { code ->
                    val name = countryNameOf(code, code)
                    val removable = enabled && code !in notYours
                    InputChip(
                        selected = false,
                        onClick = { if (removable) onChange(selected - code) },
                        enabled = enabled,
                        label = { Text(name) },
                        leadingIcon = { CountryFlag(code = code, contentDescription = null) },
                        trailingIcon = if (code !in notYours) {
                            { Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.remove_country_n, name)) }
                        } else {
                            null
                        },
                    )
                }
            }
        }
        if (canAdd.isNotEmpty()) {
            EmaCountryDropdown(
                selectedCode = null,
                onCountrySelected = { code -> if (code.isNotBlank()) onChange(selected + code.uppercase()) },
                enabled = enabled,
                label = stringResource(Res.string.admin_countries_add),
                twoLetterCodes = true,
                allowedCountries = canAdd,
                isError = selected.isEmpty(),
                errorMessage = if (selected.isEmpty()) stringResource(Res.string.admin_countries_needed) else null,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (notYours.isNotEmpty()) {
            Text(
                stringResource(Res.string.admin_countries_not_yours),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * A country admin takes an account out of its countries. It may also ask the super admins to disable or delete the
 * whole account, with a reason that goes in their email.
 */
@Composable
internal fun RemoveFromCountriesDialog(
    user: ManagedUser,
    saving: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (AccountRequestKind?, String) -> Unit,
) {
    var kind by remember(user.uid) { mutableStateOf<AccountRequestKind?>(null) }
    var reason by remember(user.uid) { mutableStateOf("") }
    val confirmFocus = remember { FocusRequester() }
    val cancelFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { cancelFocus.requestFocus() }
    TrackedDialog("RemoveFromCountries") {
        AlertDialog(
            onDismissRequest = { if (!saving) onDismiss() },
            modifier = Modifier.appFocusGroup(),
            title = { MtsDialogTitle(stringResource(Res.string.remove_from_my_countries)) },
            text = {
                Column(
                    modifier = Modifier.verticalScrollWithIndicators(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(stringResource(Res.string.remove_from_countries_message, user.alias.ifBlank { user.email }))
                    Column(Modifier.selectableGroup()) {
                        listOf(
                            null to Res.string.remove_from_countries_request_none,
                            AccountRequestKind.DISABLE to Res.string.remove_from_countries_request_disable,
                            AccountRequestKind.DELETE to Res.string.remove_from_countries_request_delete,
                        ).forEach { (option, label) ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.small)
                                    .selectable(selected = kind == option, enabled = !saving, role = Role.RadioButton) { kind = option },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = kind == option, onClick = null, enabled = !saving, modifier = Modifier.padding(8.dp))
                                Text(stringResource(label), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    if (kind != null) {
                        OutlinedTextField(
                            value = reason,
                            onValueChange = { reason = it.take(MAX_REQUEST_REASON_LENGTH) },
                            label = { Text(stringResource(Res.string.remove_from_countries_reason)) },
                            enabled = !saving,
                            minLines = 2,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            },
            confirmButton = {
                MtsDialogButtons {
                    FocusedButton(
                        onClick = { onConfirm(kind, reason.trim()) },
                        enabled = !saving,
                        focusRequester = confirmFocus,
                        buttonModifier = Modifier.focusLoop(cancelFocus, cancelFocus),
                        colors = MtsButtons.destructiveColors(),
                    ) { Text(stringResource(Res.string.remove_from_countries_action)) }
                    FocusedTextButton(
                        onClick = onDismiss,
                        enabled = !saving,
                        focusRequester = cancelFocus,
                        buttonModifier = Modifier.focusLoop(confirmFocus, confirmFocus),
                    ) { Text(stringResource(Res.string.cancel)) }
                }
            },
        )
    }
}

/** The open requests of the country admins, for a super admin. Each one is approved or rejected here. */
@Composable
internal fun AccountRequestsCard(
    requests: List<AccountRequest>,
    enabled: Boolean,
    onApprove: (AccountRequest) -> Unit,
    onReject: (AccountRequest) -> Unit,
) {
    SectionCard(
        title = stringResource(Res.string.account_requests),
        subtitle = stringResource(Res.string.account_requests_count, requests.size),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            requests.forEach { request ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(
                            if (request.kind == AccountRequestKind.DELETE) Res.string.account_request_delete else Res.string.account_request_disable,
                            request.requestedByEmail,
                            request.targetEmail,
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    if (request.reason.isNotBlank()) {
                        Text(
                            stringResource(Res.string.account_request_reason, request.reason),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FocusedOutlinedButton(
                            onClick = { onApprove(request) },
                            enabled = enabled,
                            colors = MtsButtons.destructiveOutlinedColors(),
                            border = MtsButtons.destructiveBorder(enabled),
                        ) { Text(stringResource(Res.string.account_request_approve)) }
                        FocusedTextButton(
                            onClick = { onReject(request) },
                            enabled = enabled,
                        ) { Text(stringResource(Res.string.account_request_reject)) }
                    }
                }
            }
        }
    }
}
