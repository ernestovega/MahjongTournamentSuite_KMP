package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.domain.model.AccountRequest
import com.etologic.mahjongtournamentsuite.domain.model.AccountRequestKind
import com.etologic.mahjongtournamentsuite.domain.model.ManagedUser
import com.etologic.mahjongtournamentsuite.domain.model.Organization
import com.etologic.mahjongtournamentsuite.presentation.components.ButtonText
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedButton
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedOutlinedButton
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedTextButton
import com.etologic.mahjongtournamentsuite.presentation.components.MtsButtons
import com.etologic.mahjongtournamentsuite.presentation.components.MtsDialogButtons
import com.etologic.mahjongtournamentsuite.presentation.components.MtsDialogTitle
import com.etologic.mahjongtournamentsuite.presentation.components.MtsRadioRow
import com.etologic.mahjongtournamentsuite.presentation.components.OrganizationLogo
import com.etologic.mahjongtournamentsuite.presentation.components.SectionCard
import com.etologic.mahjongtournamentsuite.presentation.components.SelectField
import com.etologic.mahjongtournamentsuite.presentation.components.SelectOption
import com.etologic.mahjongtournamentsuite.presentation.components.appFocusGroup
import com.etologic.mahjongtournamentsuite.presentation.components.dialogVerticalScroll
import com.etologic.mahjongtournamentsuite.presentation.components.focusLoop
import com.etologic.mahjongtournamentsuite.presentation.telemetry.TrackedDialog
import mahjongtournamentsuite.adminapp.generated.resources.Res
import mahjongtournamentsuite.adminapp.generated.resources.account_request_approve
import mahjongtournamentsuite.adminapp.generated.resources.account_request_delete
import mahjongtournamentsuite.adminapp.generated.resources.account_request_disable
import mahjongtournamentsuite.adminapp.generated.resources.account_request_reason
import mahjongtournamentsuite.adminapp.generated.resources.account_request_reject
import mahjongtournamentsuite.adminapp.generated.resources.account_requests
import mahjongtournamentsuite.adminapp.generated.resources.account_requests_count
import mahjongtournamentsuite.adminapp.generated.resources.admin_organizations
import mahjongtournamentsuite.adminapp.generated.resources.admin_organizations_add
import mahjongtournamentsuite.adminapp.generated.resources.admin_organizations_needed
import mahjongtournamentsuite.adminapp.generated.resources.admin_organizations_not_yours
import mahjongtournamentsuite.adminapp.generated.resources.cancel
import mahjongtournamentsuite.adminapp.generated.resources.remove_from_my_organizations
import mahjongtournamentsuite.adminapp.generated.resources.remove_from_organizations_action
import mahjongtournamentsuite.adminapp.generated.resources.remove_from_organizations_message
import mahjongtournamentsuite.adminapp.generated.resources.remove_from_organizations_reason
import mahjongtournamentsuite.adminapp.generated.resources.remove_from_organizations_request_delete
import mahjongtournamentsuite.adminapp.generated.resources.remove_from_organizations_request_disable
import mahjongtournamentsuite.adminapp.generated.resources.remove_from_organizations_request_none
import mahjongtournamentsuite.adminapp.generated.resources.remove_organization_n
import org.jetbrains.compose.resources.stringResource
import com.etologic.mahjongtournamentsuite.presentation.components.MtsInputChip as InputChip

/** The longest reason that the server keeps for a request. */
private const val MAX_REQUEST_REASON_LENGTH = 1000

/**
 * The organizations of a club admin, as chips with the logo, and a field to add one. The viewer removes and adds only
 * the organizations in [editable] (null: every organization, for a super admin). The other ones show without the remove.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AdminOrganizationsField(
    organizations: List<Organization>,
    selected: Set<String>,
    editable: Set<String>?,
    enabled: Boolean,
    onChange: (Set<String>) -> Unit,
) {
    val byId = remember(organizations) { organizations.associateBy { it.id } }
    val allIds = remember(organizations) { organizations.map { it.id }.toSet() }
    val canAdd = (editable ?: allIds).intersect(allIds) - selected
    val notYours = editable?.let { selected - it }.orEmpty()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.admin_organizations), style = MaterialTheme.typography.titleSmall)
        if (selected.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                selected.sortedBy { byId[it]?.name?.lowercase() ?: it }.forEach { id ->
                    val organization = byId[id]
                    val name = organization?.name ?: id
                    val removable = enabled && id !in notYours
                    InputChip(
                        selected = false,
                        onClick = { if (removable) onChange(selected - id) },
                        enabled = enabled,
                        label = { Text(name) },
                        leadingIcon = { OrganizationLogo(organization, size = 20.dp) },
                        trailingIcon = if (id !in notYours) {
                            { Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.remove_organization_n, name)) }
                        } else {
                            null
                        },
                    )
                }
            }
        }
        if (canAdd.isNotEmpty()) {
            SelectField(
                label = stringResource(Res.string.admin_organizations_add),
                options = organizations.filter { it.id in canAdd }.map { organization ->
                    SelectOption(
                        key = organization.id,
                        label = organization.name,
                        searchText = organization.country,
                        leadingIcon = { OrganizationLogo(organization, size = 24.dp) },
                    )
                },
                selected = null,
                onSelect = { id -> onChange(selected + id) },
                enabled = enabled,
                isError = selected.isEmpty(),
                supportingText = if (selected.isEmpty()) stringResource(Res.string.admin_organizations_needed) else null,
                modifier = Modifier.fillMaxWidth(),
            )
        } else if (selected.isEmpty()) {
            Text(
                stringResource(Res.string.admin_organizations_needed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (notYours.isNotEmpty()) {
            Text(
                stringResource(Res.string.admin_organizations_not_yours),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * A club admin takes an account out of its organizations. It may also ask the super admins to disable or delete the
 * whole account, with a reason that goes in their email.
 */
@Composable
internal fun RemoveFromOrganizationsDialog(
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
    TrackedDialog("RemoveFromOrganizations") {
        AlertDialog(
            onDismissRequest = { if (!saving) onDismiss() },
            modifier = Modifier.appFocusGroup(),
            title = { MtsDialogTitle(stringResource(Res.string.remove_from_my_organizations)) },
            text = {
                Column(
                    modifier = Modifier.dialogVerticalScroll(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(stringResource(Res.string.remove_from_organizations_message, user.alias.ifBlank { user.email }))
                    Column(Modifier.selectableGroup()) {
                        listOf(
                            null to Res.string.remove_from_organizations_request_none,
                            AccountRequestKind.DISABLE to Res.string.remove_from_organizations_request_disable,
                            AccountRequestKind.DELETE to Res.string.remove_from_organizations_request_delete,
                        ).forEach { (option, label) ->
                            MtsRadioRow(
                                selected = kind == option,
                                onClick = { kind = option },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !saving,
                                controlPadding = 8.dp,
                            ) {
                                Text(stringResource(label), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    if (kind != null) {
                        OutlinedTextField(
                            value = reason,
                            onValueChange = { reason = it.take(MAX_REQUEST_REASON_LENGTH) },
                            label = { Text(stringResource(Res.string.remove_from_organizations_reason)) },
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
                    ) { ButtonText(stringResource(Res.string.remove_from_organizations_action)) }
                    FocusedTextButton(
                        onClick = onDismiss,
                        enabled = !saving,
                        focusRequester = cancelFocus,
                        buttonModifier = Modifier.focusLoop(confirmFocus, confirmFocus),
                    ) { ButtonText(stringResource(Res.string.cancel)) }
                }
            },
        )
    }
}

/** The open requests of the club admins, for a super admin. Each one is approved or rejected here. */
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
                        ) { ButtonText(stringResource(Res.string.account_request_approve)) }
                        FocusedTextButton(
                            onClick = { onReject(request) },
                            enabled = enabled,
                        ) { ButtonText(stringResource(Res.string.account_request_reject)) }
                    }
                }
            }
        }
    }
}
