package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.domain.model.AdminStatus
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.GlobalUserRole
import com.etologic.mahjongtournamentsuite.domain.model.ManagedUser
import com.etologic.mahjongtournamentsuite.domain.model.Organization
import com.etologic.mahjongtournamentsuite.domain.model.Permission
import com.etologic.mahjongtournamentsuite.domain.model.Player
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.TournamentAssignment
import com.etologic.mahjongtournamentsuite.domain.model.displayName
import com.etologic.mahjongtournamentsuite.presentation.components.FocusHighlightContainer
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedButton
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedOutlinedButton
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedTextButton
import com.etologic.mahjongtournamentsuite.presentation.components.LazyColumnWithScrollbar
import com.etologic.mahjongtournamentsuite.presentation.components.MtsButtons
import com.etologic.mahjongtournamentsuite.presentation.components.MtsCheckboxRow
import com.etologic.mahjongtournamentsuite.presentation.components.MtsDialogButtons
import com.etologic.mahjongtournamentsuite.presentation.components.MtsDialogText
import com.etologic.mahjongtournamentsuite.presentation.components.MtsDialogTitle
import com.etologic.mahjongtournamentsuite.presentation.components.PhotoEditor
import com.etologic.mahjongtournamentsuite.presentation.components.SelectField
import com.etologic.mahjongtournamentsuite.presentation.components.SelectOption
import com.etologic.mahjongtournamentsuite.presentation.components.activateOnEnter
import com.etologic.mahjongtournamentsuite.presentation.components.appFocusGroup
import com.etologic.mahjongtournamentsuite.presentation.components.focusLoop
import com.etologic.mahjongtournamentsuite.presentation.components.label
import com.etologic.mahjongtournamentsuite.presentation.components.verticalScrollWithIndicators
import com.etologic.mahjongtournamentsuite.presentation.telemetry.TrackedDialog
import com.etologic.mahjongtournamentsuite.presentation.util.matchesSearch
import com.etologic.mahjongtournamentsuite.presentation.util.toUiText
import mahjongtournamentsuite.adminapp.generated.resources.Res
import mahjongtournamentsuite.adminapp.generated.resources.account_disabled_without_tournaments
import mahjongtournamentsuite.adminapp.generated.resources.account_grants_outside
import mahjongtournamentsuite.adminapp.generated.resources.account_permissions
import mahjongtournamentsuite.adminapp.generated.resources.accounts
import mahjongtournamentsuite.adminapp.generated.resources.add_account
import mahjongtournamentsuite.adminapp.generated.resources.alias
import mahjongtournamentsuite.adminapp.generated.resources.another_account_already_uses_this_email
import mahjongtournamentsuite.adminapp.generated.resources.assign_tournaments
import mahjongtournamentsuite.adminapp.generated.resources.cancel
import mahjongtournamentsuite.adminapp.generated.resources.change
import mahjongtournamentsuite.adminapp.generated.resources.clear_all
import mahjongtournamentsuite.adminapp.generated.resources.close
import mahjongtournamentsuite.adminapp.generated.resources.confirm_email
import mahjongtournamentsuite.adminapp.generated.resources.delete
import mahjongtournamentsuite.adminapp.generated.resources.delete_account
import mahjongtournamentsuite.adminapp.generated.resources.delete_account_action
import mahjongtournamentsuite.adminapp.generated.resources.delete_user_message
import mahjongtournamentsuite.adminapp.generated.resources.disable
import mahjongtournamentsuite.adminapp.generated.resources.disable_account
import mahjongtournamentsuite.adminapp.generated.resources.disable_account_action
import mahjongtournamentsuite.adminapp.generated.resources.disable_user_message
import mahjongtournamentsuite.adminapp.generated.resources.edit_account
import mahjongtournamentsuite.adminapp.generated.resources.editor_needs_tournament
import mahjongtournamentsuite.adminapp.generated.resources.ema_id
import mahjongtournamentsuite.adminapp.generated.resources.ema_needed_for_player
import mahjongtournamentsuite.adminapp.generated.resources.ema_owner_other
import mahjongtournamentsuite.adminapp.generated.resources.ema_player_n
import mahjongtournamentsuite.adminapp.generated.resources.email
import mahjongtournamentsuite.adminapp.generated.resources.email_hidden
import mahjongtournamentsuite.adminapp.generated.resources.email_hidden_info
import mahjongtournamentsuite.adminapp.generated.resources.enable_account
import mahjongtournamentsuite.adminapp.generated.resources.global_role
import mahjongtournamentsuite.adminapp.generated.resources.no_tournaments_are_available
import mahjongtournamentsuite.adminapp.generated.resources.no_tournaments_assigned
import mahjongtournamentsuite.adminapp.generated.resources.no_tournaments_match
import mahjongtournamentsuite.adminapp.generated.resources.no_tournaments_selected
import mahjongtournamentsuite.adminapp.generated.resources.optional_display_name
import mahjongtournamentsuite.adminapp.generated.resources.plus_more
import mahjongtournamentsuite.adminapp.generated.resources.remove_from_my_organizations
import mahjongtournamentsuite.adminapp.generated.resources.role
import mahjongtournamentsuite.adminapp.generated.resources.save
import mahjongtournamentsuite.adminapp.generated.resources.save_and_send_password_reset
import mahjongtournamentsuite.adminapp.generated.resources.search
import mahjongtournamentsuite.adminapp.generated.resources.search_ema_players
import mahjongtournamentsuite.adminapp.generated.resources.select_results
import mahjongtournamentsuite.adminapp.generated.resources.selected_count
import mahjongtournamentsuite.adminapp.generated.resources.selected_only
import mahjongtournamentsuite.adminapp.generated.resources.send_password_reset_email
import mahjongtournamentsuite.adminapp.generated.resources.the_email_addresses_do_not_match
import mahjongtournamentsuite.adminapp.generated.resources.tournament
import mahjongtournamentsuite.adminapp.generated.resources.tournament_assignments
import mahjongtournamentsuite.adminapp.generated.resources.tournament_assignments_other_organizations
import mahjongtournamentsuite.adminapp.generated.resources.tournaments_selected
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import com.etologic.mahjongtournamentsuite.presentation.components.DebouncedIconButton as IconButton
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedIconButton as IconButton

/** What the account dialog saves. */
internal data class AccountEdit(
    val email: String,
    val alias: String,
    val role: GlobalUserRole,
    val assignments: List<TournamentAssignment>,
    val emaId: String?,
    val adminOrganizations: Set<String>,
)

/**
 * The roles that the viewer may give in the account dialog. A super admin gives every role. A club admin gives
 * player, editor and club admin, but never demotes an account below what it has in organizations that are not its own.
 * An editor creates only player accounts.
 */
internal fun roleOptions(access: AdminStatus, original: ManagedUser?): List<GlobalUserRole> {
    if (access.isSuperAdmin) return listOf(GlobalUserRole.EDITOR, GlobalUserRole.CLUB_ADMIN, GlobalUserRole.SUPER_ADMIN, GlobalUserRole.PLAYER)
    if (!access.canManageStaff) return listOf(GlobalUserRole.PLAYER)
    val organizationsOutside = original?.adminOrganizations.orEmpty() - access.adminOrganizations
    return buildList {
        if (organizationsOutside.isEmpty()) add(GlobalUserRole.EDITOR)
        add(GlobalUserRole.CLUB_ADMIN)
        if (original?.hasGrantsOutside != true) add(GlobalUserRole.PLAYER)
    }
}

@Composable
internal fun ManageUserDialog(
    organizations: List<Organization>,
    original: ManagedUser?,
    tournaments: List<Tournament>,
    accounts: List<ManagedUser>,
    emaPlayers: List<Player>,
    isSelf: Boolean,
    access: AdminStatus,
    saving: Boolean,
    onDismiss: () -> Unit,
    onUploadPhoto: suspend (ManagedUser, String, ByteArray) -> AppResult<ManagedUser>,
    onRemovePhoto: suspend (ManagedUser) -> AppResult<ManagedUser>,
    onSave: (AccountEdit) -> Unit,
    onResetPassword: (String) -> Unit,
    onChangeDisabled: (ManagedUser) -> Unit,
    /** Deletes the account for good, after a confirmation. Only super admins have it. */
    onDelete: (ManagedUser) -> Unit,
    /** A club admin takes the account out of its organizations, after a dialog. */
    onRemoveFromMyOrganizations: (ManagedUser) -> Unit,
    /** The custom role and the single permissions of an editor or a club admin. */
    onEditPermissions: (ManagedUser) -> Unit = {},
) {
    val canEditPhoto = access.canEditUserPhotos && original != null
    val canDisable = access.canDisableUsers
    val canDelete = access.canDeleteUsers
    val emailHidden = original?.emailHidden == true
    // A new email plus a password reset takes over the account, so only a super admin changes it.
    val canChangeEmail = original == null || access.canChangeEmails
    // The EMA ID links the account to a player. It is set when the account is created; later only a super admin changes it.
    val canChangeEmaId = original == null || access.canChangeEmaIds
    val options = remember(access, original) { roleOptions(access, original) }
    // The tournaments where the viewer assigns editors: the ones of its organizations, or every one for a super admin.
    val assignableTournaments = tournaments.filter { access.can(Permission.TOURNAMENT_MEMBERS, it.permissions) }
    var email by remember(original) { mutableStateOf(original?.email.orEmpty()) }
    var alias by remember(original) { mutableStateOf(original?.alias.orEmpty()) }
    var confirmEmail by remember(original) { mutableStateOf(original?.email.orEmpty()) }
    var emaId by remember(original) { mutableStateOf(original?.emaId.orEmpty()) }
    var role by remember(original) { mutableStateOf(original?.role ?: options.first()) }
    var adminOrganizations by remember(original) { mutableStateOf(original?.adminOrganizations.orEmpty()) }
    val assignments = remember(original) {
        mutableStateMapOf<String, Boolean>().apply {
            tournaments.forEach { tournament ->
                put(tournament.id, original?.tournamentAssignments?.any { it.tournamentId == tournament.id } == true)
            }
        }
    }
    // A club admin manages the tournaments of its organizations without an assignment. It is an editor elsewhere only.
    val editorTournaments = when (role) {
        GlobalUserRole.EDITOR -> assignableTournaments
        GlobalUserRole.CLUB_ADMIN -> assignableTournaments.filter { tournament -> tournament.organizerIds.none { it in adminOrganizations } }
        else -> emptyList()
    }
    // The photo saves at once, apart from the other fields. This state shows the saved photo.
    var photoUser by remember(original) { mutableStateOf(original) }
    var showTournamentSelector by remember { mutableStateOf(false) }
    var restoreTournamentSelectorFocus by remember { mutableStateOf(false) }
    val emailFocus = remember { FocusRequester() }
    val tournamentSelectorFocus = remember { FocusRequester() }
    val saveFocus = remember { FocusRequester() }
    val cancelFocus = remember { FocusRequester() }
    val normalizedEmail = email.trim()
    val emailsMatch = original != null || normalizedEmail.equals(confirmEmail.trim(), ignoreCase = true)
    // One account per email and one account per EMA ID. The server checks again when it saves.
    val emailOwner = accounts.firstOrNull { it.uid != original?.uid && it.email.equals(normalizedEmail, ignoreCase = true) && normalizedEmail.isNotEmpty() }
    val emaOwner = emaId.trim().takeIf { it.isNotEmpty() }
        ?.let { id -> accounts.firstOrNull { it.uid != original?.uid && it.emaId == id } }
    val isPlayerRole = role == GlobalUserRole.PLAYER
    val selectedEditorTournaments = editorTournaments.filter { assignments[it.id] == true }
    // An editor needs a tournament. Tournaments that the viewer does not see count too, and they stay as they are.
    val editorWithoutTournament = role == GlobalUserRole.EDITOR && selectedEditorTournaments.isEmpty() && original?.hasGrantsOutside != true
    val clubAdminWithoutOrganization = role == GlobalUserRole.CLUB_ADMIN && adminOrganizations.isEmpty()
    val valid = (emailHidden || normalizedEmail.contains("@")) &&
        emailsMatch &&
        emailOwner == null &&
        emaOwner == null &&
        (!isPlayerRole || emaId.isNotBlank()) &&
        !editorWithoutTournament &&
        !clubAdminWithoutOrganization
    var showEmaPicker by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { emailFocus.requestFocus() }
    LaunchedEffect(showTournamentSelector, restoreTournamentSelectorFocus) {
        if (!showTournamentSelector && restoreTournamentSelectorFocus) {
            tournamentSelectorFocus.requestFocus()
            restoreTournamentSelectorFocus = false
        }
    }

    if (showTournamentSelector) {
        TournamentAssignmentsDialog(
            tournaments = editorTournaments,
            assignments = assignments,
            enabled = !saving,
            onAssignmentChange = { tournamentId, assigned -> assignments[tournamentId] = assigned },
            onDismiss = { showTournamentSelector = false },
        )
        return
    }

    TrackedDialog("AddAccount") {
        AlertDialog(
            onDismissRequest = { if (!saving) onDismiss() },
            modifier = Modifier.appFocusGroup(),
            title = { MtsDialogTitle(stringResource(if (original == null) Res.string.add_account else Res.string.edit_account)) },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 560.dp).verticalScrollWithIndicators(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Only super admins set the photo of another account. The others do not see this part.
                    photoUser?.takeIf { canEditPhoto }?.let { target ->
                        PhotoEditor(
                            photoUrl = target.photoUrl,
                            hasOwnPhoto = target.hasOwnPhoto,
                            name = target.alias.ifBlank { target.email },
                            onUpload = { contentType, bytes ->
                                when (val result = onUploadPhoto(target, contentType, bytes)) {
                                    is AppResult.Success -> {
                                        photoUser = result.value
                                        null
                                    }
                                    is AppResult.Failure -> result.error.toUiText()
                                }
                            },
                            onRemove = {
                                when (val result = onRemovePhoto(target)) {
                                    is AppResult.Success -> {
                                        photoUser = result.value
                                        null
                                    }
                                    is AppResult.Failure -> result.error.toUiText()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    OutlinedTextField(
                        value = if (emailHidden) stringResource(Res.string.email_hidden) else email,
                        onValueChange = { email = it },
                        label = { Text(stringResource(Res.string.email)) },
                        supportingText = {
                            when {
                                emailOwner != null -> Text(stringResource(Res.string.another_account_already_uses_this_email))
                                emailHidden -> Text(stringResource(Res.string.email_hidden_info))
                            }
                        },
                        isError = emailOwner != null,
                        singleLine = true,
                        enabled = !saving && canChangeEmail && !emailHidden,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth().focusRequester(emailFocus),
                    )
                    OutlinedTextField(
                        value = alias,
                        onValueChange = { alias = it },
                        label = { Text(stringResource(Res.string.alias)) },
                        supportingText = { Text(stringResource(Res.string.optional_display_name)) },
                        singleLine = true,
                        enabled = !saving,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    // A new account gets its EMA ID here. Later only a super admin changes it, so the others see it only.
                    if (canChangeEmaId || emaId.isNotBlank()) {
                        val emaPlayer = emaPlayers.firstOrNull { it.emaId == emaId.trim() }
                        OutlinedTextField(
                            value = emaId,
                            onValueChange = { emaId = it.filter(Char::isLetterOrDigit) },
                            label = { Text(stringResource(Res.string.ema_id)) },
                            supportingText = {
                                val message = when {
                                    emaOwner != null -> stringResource(Res.string.ema_owner_other, emaOwner.email)
                                    emaPlayer != null -> stringResource(Res.string.ema_player_n, emaPlayer.displayName)
                                    isPlayerRole && emaId.isBlank() -> stringResource(Res.string.ema_needed_for_player)
                                    else -> null
                                }
                                if (message != null) Text(message)
                            },
                            isError = emaOwner != null,
                            trailingIcon = if (canChangeEmaId) {
                                {
                                    IconButton(onClick = { showEmaPicker = true }, enabled = !saving) {
                                        Icon(Icons.Default.Search, contentDescription = stringResource(Res.string.search_ema_players))
                                    }
                                }
                            } else {
                                null
                            },
                            singleLine = true,
                            enabled = !saving && canChangeEmaId,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (original == null) {
                        OutlinedTextField(
                            value = confirmEmail,
                            onValueChange = { confirmEmail = it },
                            label = { Text(stringResource(Res.string.confirm_email)) },
                            supportingText = {
                                if (confirmEmail.isNotEmpty() && !emailsMatch) Text(stringResource(Res.string.the_email_addresses_do_not_match))
                            },
                            isError = confirmEmail.isNotEmpty() && !emailsMatch,
                            singleLine = true,
                            enabled = !saving,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    GlobalRoleDropdown(
                        role = role,
                        // The own role never changes here. A club admin changes the staff of its organizations only.
                        options = if (role in options) options else listOf(role) + options,
                        enabled = options.size > 1 && !isSelf && !saving && (original == null || original.canManage || access.isSuperAdmin),
                        onSelect = { role = it },
                    )
                    if (original?.disabledWithoutRole == true) {
                        Text(
                            stringResource(Res.string.account_disabled_without_tournaments),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (original?.hasGrantsOutside == true && !isSelf) {
                        Text(
                            stringResource(Res.string.account_grants_outside),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (role == GlobalUserRole.CLUB_ADMIN) {
                        AdminOrganizationsField(
                            organizations = organizations,
                            selected = adminOrganizations,
                            editable = if (access.isSuperAdmin) null else access.adminOrganizations,
                            enabled = !saving && !isSelf,
                            onChange = { adminOrganizations = it },
                        )
                    }
                    if (role == GlobalUserRole.EDITOR || (role == GlobalUserRole.CLUB_ADMIN && editorTournaments.isNotEmpty())) {
                        Text(
                            stringResource(
                                if (role == GlobalUserRole.CLUB_ADMIN) Res.string.tournament_assignments_other_organizations else Res.string.tournament_assignments,
                            ),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        if (editorTournaments.isEmpty()) {
                            Text(stringResource(Res.string.no_tournaments_are_available), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            val assignmentSummary = when (selectedEditorTournaments.size) {
                                0 -> stringResource(Res.string.no_tournaments_assigned)
                                1 -> selectedEditorTournaments.single().name
                                else -> pluralStringResource(Res.plurals.tournaments_selected, selectedEditorTournaments.size, selectedEditorTournaments.size)
                            }
                            FocusedOutlinedButton(
                                onClick = {
                                    restoreTournamentSelectorFocus = true
                                    showTournamentSelector = true
                                },
                                enabled = !saving && !isSelf,
                                modifier = Modifier.fillMaxWidth(),
                                buttonModifier = Modifier.fillMaxWidth(),
                                focusRequester = tournamentSelectorFocus,
                            ) {
                                Text(
                                    text = assignmentSummary,
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Start,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(stringResource(Res.string.change))
                            }
                            if (selectedEditorTournaments.size > 1) {
                                Text(
                                    text = selectedEditorTournaments.take(2).joinToString { it.name } +
                                        if (selectedEditorTournaments.size > 2) " · " + stringResource(Res.string.plus_more, selectedEditorTournaments.size - 2) else "",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        if (editorWithoutTournament) {
                            Text(
                                stringResource(Res.string.editor_needs_tournament),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                // Save on top. The account actions under it, outlined, the destructive ones in red. Cancel last.
                MtsDialogButtons {
                    FocusedButton(
                        onClick = {
                            val selectedAssignments = selectedEditorTournaments
                                .map { TournamentAssignment(it.id, it.name, it.organizerIds) }
                            onSave(
                                AccountEdit(
                                    // A hidden email goes as empty: the account keeps it.
                                    email = if (emailHidden) "" else normalizedEmail,
                                    alias = alias.trim(),
                                    role = role,
                                    assignments = selectedAssignments,
                                    emaId = emaId.trim().ifBlank { null },
                                    adminOrganizations = if (role == GlobalUserRole.CLUB_ADMIN) adminOrganizations else emptySet(),
                                ),
                            )
                        },
                        enabled = valid && !saving,
                        focusRequester = saveFocus,
                        buttonModifier = Modifier.focusLoop(cancelFocus, cancelFocus),
                    ) { Text(stringResource(if (original == null) Res.string.save_and_send_password_reset else Res.string.save)) }
                    if (original != null && !isSelf && access.canSendPasswordResets && !emailHidden) {
                        FocusedOutlinedButton(
                            onClick = { onResetPassword(original.email) },
                            enabled = !saving,
                        ) { Text(stringResource(Res.string.send_password_reset_email)) }
                    }
                    // The role of an editor or a club admin can be a custom one, with single permissions changed.
                    val customizable = original != null &&
                        GlobalUserRole.SUPER_ADMIN !in original.roles &&
                        (GlobalUserRole.EDITOR in original.roles || GlobalUserRole.CLUB_ADMIN in original.roles)
                    if (original != null && !isSelf && customizable && access.canManageStaff && (original.canManage || access.isSuperAdmin)) {
                        FocusedOutlinedButton(
                            onClick = { onEditPermissions(original) },
                            enabled = !saving,
                        ) { Text(stringResource(Res.string.account_permissions)) }
                    }
                    // A club admin takes the account out of its organizations. A super admin disables or deletes it.
                    if (original != null && !isSelf && access.canRequestRemoval && GlobalUserRole.SUPER_ADMIN !in original.roles) {
                        FocusedOutlinedButton(
                            onClick = { onRemoveFromMyOrganizations(original) },
                            enabled = !saving,
                            colors = MtsButtons.destructiveOutlinedColors(),
                            border = MtsButtons.destructiveBorder(!saving),
                        ) { Text(stringResource(Res.string.remove_from_my_organizations)) }
                    }
                    if (original != null && !isSelf && canDisable) {
                        // Enabling an account again is not destructive: only Disable is red.
                        FocusedOutlinedButton(
                            onClick = { onChangeDisabled(original) },
                            enabled = !saving,
                            colors = if (original.disabled) ButtonDefaults.outlinedButtonColors() else MtsButtons.destructiveOutlinedColors(),
                            border = if (original.disabled) ButtonDefaults.outlinedButtonBorder(!saving) else MtsButtons.destructiveBorder(!saving),
                        ) { Text(stringResource(if (original.disabled) Res.string.enable_account else Res.string.disable_account_action)) }
                    }
                    if (original != null && !isSelf && canDelete) {
                        FocusedOutlinedButton(
                            onClick = { onDelete(original) },
                            enabled = !saving,
                            colors = MtsButtons.destructiveOutlinedColors(),
                            border = MtsButtons.destructiveBorder(!saving),
                        ) { Text(stringResource(Res.string.delete_account_action)) }
                    }
                    FocusedTextButton(
                        onClick = onDismiss,
                        enabled = !saving,
                        focusRequester = cancelFocus,
                        buttonModifier = Modifier.focusLoop(saveFocus, saveFocus),
                    ) { Text(stringResource(Res.string.cancel)) }
                }
            },
        )
    }
    if (showEmaPicker) {
        EmaPlayerPickerDialog(
            players = emaPlayers,
            owners = accounts.filter { it.uid != original?.uid && it.emaId != null }.associate { it.emaId!! to it.email },
            selectedEmaId = emaId.trim().ifBlank { null },
            onSelect = {
                emaId = it.emaId
                showEmaPicker = false
            },
            onDismiss = { showEmaPicker = false },
        )
    }
}

@Composable
private fun GlobalRoleDropdown(
    role: GlobalUserRole,
    options: List<GlobalUserRole>,
    enabled: Boolean,
    onSelect: (GlobalUserRole) -> Unit,
) {
    SelectField(
        label = stringResource(Res.string.global_role),
        options = options.map { SelectOption(it, it.label()) },
        selected = role,
        onSelect = onSelect,
        enabled = enabled,
    )
}

@Composable
private fun TournamentAssignmentsDialog(
    tournaments: List<Tournament>,
    assignments: Map<String, Boolean>,
    enabled: Boolean,
    onAssignmentChange: (String, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var searchText by remember { mutableStateOf("") }
    var showSelectedOnly by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val searchFocus = remember { FocusRequester() }
    val closeFocus = remember { FocusRequester() }
    val filteredTournaments = tournaments.filter { tournament ->
        (!showSelectedOnly || assignments[tournament.id] == true) &&
            tournament.name.matchesSearch(searchText)
    }
    val selectedCount = tournaments.count { assignments[it.id] == true }

    LaunchedEffect(Unit) { searchFocus.requestFocus() }
    LaunchedEffect(searchText, showSelectedOnly) {
        if (filteredTournaments.isNotEmpty()) listState.scrollToItem(0)
    }

    TrackedDialog("AssignTournaments") {
        AlertDialog(
            onDismissRequest = onDismiss,
            modifier = Modifier.appFocusGroup(),
            title = { MtsDialogTitle(stringResource(Res.string.assign_tournaments)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = searchText,
                        onValueChange = { searchText = it },
                        label = { Text(stringResource(Res.string.search)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(searchFocus)
                            .focusProperties { previous = closeFocus },
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = showSelectedOnly,
                            onClick = { showSelectedOnly = !showSelectedOnly },
                            label = { Text(stringResource(Res.string.selected_only)) },
                        )
                        Text(
                            text = stringResource(Res.string.selected_count, selectedCount),
                            modifier = Modifier.weight(1f).padding(top = 8.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.End,
                        )
                    }
                    if (filteredTournaments.isEmpty()) {
                        Text(
                            text = if (showSelectedOnly && selectedCount == 0) {
                                stringResource(Res.string.no_tournaments_selected)
                            } else {
                                stringResource(Res.string.no_tournaments_match)
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.height(240.dp).padding(top = 16.dp),
                        )
                    } else {
                        LazyColumnWithScrollbar(
                            state = listState,
                            modifier = Modifier.fillMaxWidth().height(240.dp),
                        ) {
                            items(
                                count = filteredTournaments.size,
                                key = { filteredTournaments[it].id },
                            ) { index ->
                                val tournament = filteredTournaments[index]
                                TournamentSelectionRow(
                                    tournament = tournament,
                                    selected = assignments[tournament.id] == true,
                                    enabled = enabled,
                                    onToggle = {
                                        onAssignmentChange(tournament.id, assignments[tournament.id] != true)
                                    },
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                MtsDialogButtons {
                    FocusedButton(
                        onClick = onDismiss,
                        enabled = enabled,
                        focusRequester = closeFocus,
                        buttonModifier = Modifier.focusProperties { next = searchFocus },
                    ) { Text(stringResource(Res.string.close)) }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        FocusedTextButton(
                            onClick = {
                                filteredTournaments.forEach { onAssignmentChange(it.id, true) }
                            },
                            enabled = enabled && filteredTournaments.isNotEmpty(),
                        ) { Text(stringResource(Res.string.select_results)) }
                        FocusedTextButton(
                            onClick = {
                                tournaments.forEach { onAssignmentChange(it.id, false) }
                            },
                            enabled = enabled && selectedCount > 0,
                        ) { Text(stringResource(Res.string.clear_all)) }
                    }
                }
            },
        )
    }
}

@Composable
private fun TournamentSelectionRow(
    tournament: Tournament,
    selected: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    FocusHighlightContainer(
        modifier = Modifier.fillMaxWidth(),
        interactionSource = interactionSource,
    ) {
        MtsCheckboxRow(
            checked = selected,
            onCheckedChange = { onToggle() },
            modifier = Modifier.fillMaxWidth().activateOnEnter(enabled, onToggle),
            enabled = enabled,
            gap = 8.dp,
            interactionSource = interactionSource,
            indication = null,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            controlPadding = 0.dp,
        ) {
            Text(
                text = tournament.name,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun DeleteUserDialog(
    user: ManagedUser,
    saving: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val deleteFocus = remember { FocusRequester() }
    val cancelFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { cancelFocus.requestFocus() }
    TrackedDialog("DeleteAccount") {
        AlertDialog(
            onDismissRequest = { if (!saving) onDismiss() },
            modifier = Modifier.appFocusGroup(),
            title = { MtsDialogTitle(stringResource(Res.string.delete_account)) },
            text = { MtsDialogText(stringResource(Res.string.delete_user_message, user.email)) },
            confirmButton = {
                MtsDialogButtons {
                    FocusedButton(
                        onClick = onConfirm,
                        enabled = !saving,
                        focusRequester = deleteFocus,
                        buttonModifier = Modifier.focusLoop(cancelFocus, cancelFocus),
                        colors = MtsButtons.destructiveColors(),
                    ) { Text(stringResource(Res.string.delete)) }

                    FocusedTextButton(
                        onClick = onDismiss,
                        enabled = !saving,
                        focusRequester = cancelFocus,
                        buttonModifier = Modifier.focusLoop(deleteFocus, deleteFocus),
                    ) { Text(stringResource(Res.string.cancel)) }
                }
            },
        )
    }
}

@Composable
internal fun DisableUserDialog(
    user: ManagedUser,
    saving: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val disableFocus = remember { FocusRequester() }
    val cancelFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { cancelFocus.requestFocus() }
    TrackedDialog("DisableAccount") {
        AlertDialog(
            onDismissRequest = { if (!saving) onDismiss() },
            modifier = Modifier.appFocusGroup(),
            title = { MtsDialogTitle(stringResource(Res.string.disable_account)) },
            text = { MtsDialogText(stringResource(Res.string.disable_user_message, user.email)) },
            confirmButton = {
                MtsDialogButtons {
                    FocusedButton(
                        onClick = onConfirm,
                        enabled = !saving,
                        focusRequester = disableFocus,
                        buttonModifier = Modifier.focusLoop(cancelFocus, cancelFocus),
                    ) { Text(stringResource(Res.string.disable)) }

                    FocusedTextButton(
                        onClick = onDismiss,
                        enabled = !saving,
                        focusRequester = cancelFocus,
                        buttonModifier = Modifier.focusLoop(disableFocus, disableFocus),
                    ) { Text(stringResource(Res.string.cancel)) }
                }
            },
        )
    }
}
