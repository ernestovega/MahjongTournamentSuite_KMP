package com.etologic.mahjongtournamentsuite.presentation.screen

import com.etologic.mahjongtournamentsuite.presentation.components.ButtonText
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.etologic.mahjongtournamentsuite.domain.model.AdminStatus
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.CustomRole
import com.etologic.mahjongtournamentsuite.domain.model.GlobalUserRole
import com.etologic.mahjongtournamentsuite.domain.model.LevelPermissions
import com.etologic.mahjongtournamentsuite.domain.model.ManagedUser
import com.etologic.mahjongtournamentsuite.domain.model.Permission
import com.etologic.mahjongtournamentsuite.domain.model.RolesCatalog
import com.etologic.mahjongtournamentsuite.presentation.components.AppErrorDialog
import com.etologic.mahjongtournamentsuite.presentation.components.AppScaffold
import com.etologic.mahjongtournamentsuite.presentation.components.AppSuccessDialog
import com.etologic.mahjongtournamentsuite.presentation.components.DebouncedExtendedFloatingActionButton as ExtendedFloatingActionButton
import com.etologic.mahjongtournamentsuite.presentation.components.FocusHighlightContainer
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedButton
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedOutlinedButton
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedTextButton
import com.etologic.mahjongtournamentsuite.presentation.components.MtsButtons
import com.etologic.mahjongtournamentsuite.presentation.components.MtsCheckboxRow
import com.etologic.mahjongtournamentsuite.presentation.components.MtsDialogButtons
import com.etologic.mahjongtournamentsuite.presentation.components.MtsDialogText
import com.etologic.mahjongtournamentsuite.presentation.components.MtsDialogTitle
import com.etologic.mahjongtournamentsuite.presentation.components.ScreenColumn
import com.etologic.mahjongtournamentsuite.presentation.components.ScrollableColumnWithScrollbar
import com.etologic.mahjongtournamentsuite.presentation.components.SectionCard
import com.etologic.mahjongtournamentsuite.presentation.components.SelectField
import com.etologic.mahjongtournamentsuite.presentation.components.SelectOption
import com.etologic.mahjongtournamentsuite.presentation.components.activateOnEnter
import com.etologic.mahjongtournamentsuite.presentation.components.appFocusGroup
import com.etologic.mahjongtournamentsuite.presentation.components.label
import com.etologic.mahjongtournamentsuite.presentation.presenter.UsersPresenter
import com.etologic.mahjongtournamentsuite.presentation.telemetry.TrackedDialog
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import com.etologic.mahjongtournamentsuite.presentation.util.UiText
import com.etologic.mahjongtournamentsuite.presentation.util.toUiText
import kotlinx.coroutines.launch
import mahjongtournamentsuite.adminapp.generated.resources.Res
import mahjongtournamentsuite.adminapp.generated.resources.account_permissions
import mahjongtournamentsuite.adminapp.generated.resources.account_permissions_info
import mahjongtournamentsuite.adminapp.generated.resources.account_permissions_only_super_admin
import mahjongtournamentsuite.adminapp.generated.resources.account_permissions_outside
import mahjongtournamentsuite.adminapp.generated.resources.account_permissions_reset
import mahjongtournamentsuite.adminapp.generated.resources.account_permissions_role
import mahjongtournamentsuite.adminapp.generated.resources.account_permissions_save_as_role
import mahjongtournamentsuite.adminapp.generated.resources.account_permissions_saved
import mahjongtournamentsuite.adminapp.generated.resources.cancel
import mahjongtournamentsuite.adminapp.generated.resources.close
import mahjongtournamentsuite.adminapp.generated.resources.perm_accounts_changeEmaId
import mahjongtournamentsuite.adminapp.generated.resources.perm_accounts_changeEmail
import mahjongtournamentsuite.adminapp.generated.resources.perm_accounts_createPlayer
import mahjongtournamentsuite.adminapp.generated.resources.perm_accounts_delete
import mahjongtournamentsuite.adminapp.generated.resources.perm_accounts_disable
import mahjongtournamentsuite.adminapp.generated.resources.perm_accounts_manageStaff
import mahjongtournamentsuite.adminapp.generated.resources.perm_accounts_manageSuperAdmins
import mahjongtournamentsuite.adminapp.generated.resources.perm_accounts_passwordReset
import mahjongtournamentsuite.adminapp.generated.resources.perm_accounts_photo
import mahjongtournamentsuite.adminapp.generated.resources.perm_accounts_requestRemoval
import mahjongtournamentsuite.adminapp.generated.resources.perm_accounts_reviewRequests
import mahjongtournamentsuite.adminapp.generated.resources.perm_accounts_view
import mahjongtournamentsuite.adminapp.generated.resources.perm_changed_marker
import mahjongtournamentsuite.adminapp.generated.resources.perm_dev_activity
import mahjongtournamentsuite.adminapp.generated.resources.perm_dev_errors
import mahjongtournamentsuite.adminapp.generated.resources.perm_ema_create
import mahjongtournamentsuite.adminapp.generated.resources.perm_ema_delete
import mahjongtournamentsuite.adminapp.generated.resources.perm_ema_edit
import mahjongtournamentsuite.adminapp.generated.resources.perm_group_accounts
import mahjongtournamentsuite.adminapp.generated.resources.perm_group_dev
import mahjongtournamentsuite.adminapp.generated.resources.perm_group_ema
import mahjongtournamentsuite.adminapp.generated.resources.perm_group_organizations
import mahjongtournamentsuite.adminapp.generated.resources.perm_group_tools
import mahjongtournamentsuite.adminapp.generated.resources.perm_group_tournament
import mahjongtournamentsuite.adminapp.generated.resources.perm_group_tournaments
import mahjongtournamentsuite.adminapp.generated.resources.perm_organizations_create
import mahjongtournamentsuite.adminapp.generated.resources.perm_organizations_delete
import mahjongtournamentsuite.adminapp.generated.resources.perm_organizations_edit
import mahjongtournamentsuite.adminapp.generated.resources.perm_permissions_manage
import mahjongtournamentsuite.adminapp.generated.resources.perm_required_hint
import mahjongtournamentsuite.adminapp.generated.resources.perm_tools_idCards
import mahjongtournamentsuite.adminapp.generated.resources.perm_tools_venues
import mahjongtournamentsuite.adminapp.generated.resources.perm_tournament_activity
import mahjongtournamentsuite.adminapp.generated.resources.perm_tournament_configure
import mahjongtournamentsuite.adminapp.generated.resources.perm_tournament_delete
import mahjongtournamentsuite.adminapp.generated.resources.perm_tournament_edit
import mahjongtournamentsuite.adminapp.generated.resources.perm_tournament_editCompleted
import mahjongtournamentsuite.adminapp.generated.resources.perm_tournament_members
import mahjongtournamentsuite.adminapp.generated.resources.perm_tournament_playerEmails
import mahjongtournamentsuite.adminapp.generated.resources.perm_tournament_resetTable
import mahjongtournamentsuite.adminapp.generated.resources.perm_tournament_roundControl
import mahjongtournamentsuite.adminapp.generated.resources.perm_tournament_view
import mahjongtournamentsuite.adminapp.generated.resources.perm_tournaments_changeOrganizers
import mahjongtournamentsuite.adminapp.generated.resources.perm_tournaments_create
import mahjongtournamentsuite.adminapp.generated.resources.perm_tournaments_deleteFinished
import mahjongtournamentsuite.adminapp.generated.resources.role_accounts_count
import mahjongtournamentsuite.adminapp.generated.resources.role_base
import mahjongtournamentsuite.adminapp.generated.resources.role_base_info
import mahjongtournamentsuite.adminapp.generated.resources.role_copy_name
import mahjongtournamentsuite.adminapp.generated.resources.role_delete
import mahjongtournamentsuite.adminapp.generated.resources.role_delete_text_unused
import mahjongtournamentsuite.adminapp.generated.resources.role_delete_text_used
import mahjongtournamentsuite.adminapp.generated.resources.role_delete_title
import mahjongtournamentsuite.adminapp.generated.resources.role_duplicate
import mahjongtournamentsuite.adminapp.generated.resources.role_edit
import mahjongtournamentsuite.adminapp.generated.resources.role_name
import mahjongtournamentsuite.adminapp.generated.resources.role_name_taken
import mahjongtournamentsuite.adminapp.generated.resources.role_new
import mahjongtournamentsuite.adminapp.generated.resources.role_permissions_count
import mahjongtournamentsuite.adminapp.generated.resources.role_standard_option
import mahjongtournamentsuite.adminapp.generated.resources.roles_custom
import mahjongtournamentsuite.adminapp.generated.resources.roles_custom_empty
import mahjongtournamentsuite.adminapp.generated.resources.roles_standard
import mahjongtournamentsuite.adminapp.generated.resources.roles_standard_info
import mahjongtournamentsuite.adminapp.generated.resources.roles_title
import mahjongtournamentsuite.adminapp.generated.resources.save
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/** The permissions by area, in the order that every list of permissions shows them. */
internal val PERMISSION_GROUPS: List<Pair<StringResource, List<Permission>>> = listOf(
    Res.string.perm_group_tournament to Permission.entries.filter { it.inTournament },
    Res.string.perm_group_tournaments to Permission.entries.filter { it.key.startsWith("tournaments.") },
    Res.string.perm_group_organizations to Permission.entries.filter { it.key.startsWith("organizations.") },
    Res.string.perm_group_accounts to Permission.entries.filter { it.key.startsWith("accounts.") },
    Res.string.perm_group_ema to Permission.entries.filter { it.key.startsWith("ema.") },
    Res.string.perm_group_tools to Permission.entries.filter { it.key.startsWith("tools.") },
    Res.string.perm_group_dev to Permission.entries.filter { it.key.startsWith("dev.") || it.key.startsWith("permissions.") },
)

/** The name of the permission in the language of the app. */
@Composable
internal fun Permission.label(): String = stringResource(
    when (this) {
        Permission.TOURNAMENT_VIEW -> Res.string.perm_tournament_view
        Permission.TOURNAMENT_EDIT -> Res.string.perm_tournament_edit
        Permission.TOURNAMENT_ROUND_CONTROL -> Res.string.perm_tournament_roundControl
        Permission.TOURNAMENT_EDIT_COMPLETED -> Res.string.perm_tournament_editCompleted
        Permission.TOURNAMENT_CONFIGURE -> Res.string.perm_tournament_configure
        Permission.TOURNAMENT_DELETE -> Res.string.perm_tournament_delete
        Permission.TOURNAMENT_RESET_TABLE -> Res.string.perm_tournament_resetTable
        Permission.TOURNAMENT_MEMBERS -> Res.string.perm_tournament_members
        Permission.TOURNAMENT_ACTIVITY -> Res.string.perm_tournament_activity
        Permission.TOURNAMENT_PLAYER_EMAILS -> Res.string.perm_tournament_playerEmails
        Permission.TOURNAMENTS_CREATE -> Res.string.perm_tournaments_create
        Permission.TOURNAMENTS_CHANGE_ORGANIZERS -> Res.string.perm_tournaments_changeOrganizers
        Permission.TOURNAMENTS_DELETE_FINISHED -> Res.string.perm_tournaments_deleteFinished
        Permission.ORGANIZATIONS_CREATE -> Res.string.perm_organizations_create
        Permission.ORGANIZATIONS_EDIT -> Res.string.perm_organizations_edit
        Permission.ORGANIZATIONS_DELETE -> Res.string.perm_organizations_delete
        Permission.ACCOUNTS_VIEW -> Res.string.perm_accounts_view
        Permission.ACCOUNTS_CREATE_PLAYER -> Res.string.perm_accounts_createPlayer
        Permission.ACCOUNTS_MANAGE_STAFF -> Res.string.perm_accounts_manageStaff
        Permission.ACCOUNTS_MANAGE_SUPER_ADMINS -> Res.string.perm_accounts_manageSuperAdmins
        Permission.ACCOUNTS_CHANGE_EMAIL -> Res.string.perm_accounts_changeEmail
        Permission.ACCOUNTS_CHANGE_EMA_ID -> Res.string.perm_accounts_changeEmaId
        Permission.ACCOUNTS_PASSWORD_RESET -> Res.string.perm_accounts_passwordReset
        Permission.ACCOUNTS_PHOTO -> Res.string.perm_accounts_photo
        Permission.ACCOUNTS_DISABLE -> Res.string.perm_accounts_disable
        Permission.ACCOUNTS_DELETE -> Res.string.perm_accounts_delete
        Permission.ACCOUNTS_REQUEST_REMOVAL -> Res.string.perm_accounts_requestRemoval
        Permission.ACCOUNTS_REVIEW_REQUESTS -> Res.string.perm_accounts_reviewRequests
        Permission.EMA_CREATE -> Res.string.perm_ema_create
        Permission.EMA_EDIT -> Res.string.perm_ema_edit
        Permission.EMA_DELETE -> Res.string.perm_ema_delete
        Permission.TOOLS_VENUES -> Res.string.perm_tools_venues
        Permission.TOOLS_ID_CARDS -> Res.string.perm_tools_idCards
        Permission.DEV_ACTIVITY -> Res.string.perm_dev_activity
        Permission.DEV_ERRORS -> Res.string.perm_dev_errors
        Permission.PERMISSIONS_MANAGE -> Res.string.perm_permissions_manage
    },
)

/**
 * The permissions of a role or a person by area, with a box each. Only [shown] appear. [required] stay ticked and
 * cannot change. [reference] marks the boxes that differ from the role of a person.
 */
@Composable
internal fun PermissionChecklist(
    shown: Set<Permission>,
    checked: Set<Permission>,
    required: Set<Permission>,
    enabled: Boolean,
    onToggle: (Permission) -> Unit,
    reference: Set<Permission>? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        PERMISSION_GROUPS.forEach { (title, permissions) ->
            val visible = permissions.filter { it in shown }
            if (visible.isEmpty()) return@forEach
            Text(
                stringResource(title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 8.dp),
            )
            visible.forEach { permission ->
                val locked = permission in required
                PermissionRow(
                    label = permission.label(),
                    hint = when {
                        locked -> stringResource(Res.string.perm_required_hint)
                        reference != null && (permission in checked) != (permission in reference) -> stringResource(Res.string.perm_changed_marker)
                        else -> null
                    },
                    checked = permission in checked || locked,
                    enabled = enabled && !locked,
                    onToggle = { onToggle(permission) },
                )
            }
        }
    }
}

@Composable
private fun PermissionRow(label: String, hint: String?, checked: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    FocusHighlightContainer(modifier = Modifier.fillMaxWidth(), interactionSource = interactionSource) {
        MtsCheckboxRow(
            checked = checked,
            onCheckedChange = { onToggle() },
            modifier = Modifier.fillMaxWidth().activateOnEnter(enabled, onToggle),
            enabled = enabled,
            gap = 8.dp,
            interactionSource = interactionSource,
            indication = null,
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
            controlPadding = 0.dp,
        ) {
            Column(Modifier.weight(1f)) {
                Text(label)
                if (hint != null) {
                    Text(hint, style = MaterialTheme.typography.bodySmall, color = MtsTheme.colors.link)
                }
            }
        }
    }
}

/** A role being written: a new one ([id] null), a custom one, or a standard one that only shows ([readOnly]). */
internal data class RoleDraft(
    val id: String?,
    val name: String,
    val base: GlobalUserRole,
    val permissions: Set<Permission>,
    val readOnly: Boolean = false,
    val accounts: Int = 0,
)

/** The standard roles and the custom ones. Only super admins open it, from the accounts screen. */
@Composable
fun RolesScreen(navController: NavHostController) {
    val presenter = koinInject<UsersPresenter>()
    val scope = rememberCoroutineScope()
    var catalog by remember { mutableStateOf<RolesCatalog?>(null) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<UiText?>(null) }
    var draft by remember { mutableStateOf<RoleDraft?>(null) }
    var deleting by remember { mutableStateOf<CustomRole?>(null) }

    fun refresh() {
        scope.launch {
            loading = true
            when (val result = presenter.loadRoles()) {
                is AppResult.Success -> catalog = result.value
                is AppResult.Failure -> error = result.error.toUiText()
            }
            loading = false
        }
    }
    LaunchedEffect(presenter) { refresh() }

    fun save(target: RoleDraft, name: String, base: GlobalUserRole, permissions: Set<Permission>) {
        scope.launch {
            saving = true
            val result = if (target.id == null) presenter.createRole(name, base, permissions) else presenter.updateRole(target.id, name, permissions)
            when (result) {
                is AppResult.Success -> {
                    draft = null
                    when (val roles = presenter.loadRoles()) {
                        is AppResult.Success -> catalog = roles.value
                        is AppResult.Failure -> error = roles.error.toUiText()
                    }
                }
                is AppResult.Failure -> error = result.error.toUiText()
            }
            saving = false
        }
    }

    fun delete(role: CustomRole) {
        scope.launch {
            saving = true
            when (val result = presenter.deleteRole(role.id)) {
                is AppResult.Success -> {
                    deleting = null
                    draft = null
                    catalog = catalog?.let { it.copy(custom = it.custom.filterNot { item -> item.id == role.id }) }
                }
                is AppResult.Failure -> error = result.error.toUiText()
            }
            saving = false
        }
    }

    AppScaffold(
        title = stringResource(Res.string.roles_title),
        isLoading = loading || saving,
        contentLoading = loading && catalog == null,
        onBack = { navController.popBackStack() },
        // The main action of the screen, like New tournament in the tournament list.
        floatingActionButton = if (catalog != null) {
            {
                ExtendedFloatingActionButton(onClick = {
                    draft = RoleDraft(id = null, name = "", base = GlobalUserRole.EDITOR, permissions = catalog?.standard?.get(GlobalUserRole.EDITOR).orEmpty())
                }) {
                    androidx.compose.material3.Icon(Icons.Default.Add, contentDescription = null)
                    ButtonText(stringResource(Res.string.role_new), Modifier.padding(start = 12.dp))
                }
            }
        } else {
            null
        },
    ) {
        ScreenColumn(
            maxWidth = 900.dp,
            scrollable = true,
            // Room under the last card so the button covers no role.
            contentPadding = PaddingValues(start = MtsTheme.spacing.xl, top = MtsTheme.spacing.xl, end = MtsTheme.spacing.xl, bottom = FabClearance),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            error?.let { AppErrorDialog(message = it, onDismiss = { error = null }) }
            val roles = catalog
            SectionCard(title = stringResource(Res.string.roles_standard), subtitle = stringResource(Res.string.roles_standard_info)) {
                listOf(GlobalUserRole.EDITOR, GlobalUserRole.CLUB_ADMIN, GlobalUserRole.SUPER_ADMIN).forEach { role ->
                    val permissions = roles?.standard?.get(role) ?: Permission.defaultsOf(role)
                    RoleRow(
                        name = role.label(),
                        details = pluralStringResource(Res.plurals.role_permissions_count, permissions.size, permissions.size),
                        enabled = !saving,
                        onClick = { draft = RoleDraft(id = null, name = "", base = role, permissions = permissions, readOnly = true) },
                    )
                }
            }
            SectionCard(title = stringResource(Res.string.roles_custom)) {
                when {
                    // The logo with a progress covers the screen while it loads.
                    roles == null -> Unit
                    roles.custom.isEmpty() -> Text(stringResource(Res.string.roles_custom_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else -> roles.custom.forEachIndexed { index, role ->
                        if (index > 0) HorizontalDivider()
                        RoleRow(
                            name = role.name,
                            details = role.base.label() + " · " +
                                pluralStringResource(Res.plurals.role_permissions_count, role.permissions.size, role.permissions.size) + " · " +
                                pluralStringResource(Res.plurals.role_accounts_count, role.accounts, role.accounts),
                            enabled = !saving,
                            onClick = { draft = RoleDraft(role.id, role.name, role.base, role.permissions, accounts = role.accounts) },
                        )
                    }
                }
            }
        }
    }

    val roles = catalog
    draft?.let { current ->
        if (roles != null) {
            val copyName = stringResource(Res.string.role_copy_name, if (current.readOnly) current.base.label() else current.name)
            RoleEditorDialog(
                draft = current,
                catalog = roles,
                saving = saving,
                onDismiss = { draft = null },
                onSave = { name, base, permissions -> save(current, name, base, permissions) },
                onDuplicate = {
                    // A copy of a standard role builds on it; the super admin role has no smaller copy, so it starts as a club admin.
                    val base = if (current.base == GlobalUserRole.SUPER_ADMIN) GlobalUserRole.CLUB_ADMIN else current.base
                    draft = RoleDraft(id = null, name = copyName, base = base, permissions = current.permissions.intersect(roles.grantable[base].orEmpty()))
                },
                onDelete = { roles.custom.firstOrNull { it.id == current.id }?.let { deleting = it } },
            )
        }
    }
    deleting?.let { role ->
        DeleteRoleDialog(role = role, saving = saving, onDismiss = { deleting = null }, onConfirm = { delete(role) })
    }
}

@Composable
private fun RoleRow(name: String, details: String, enabled: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    FocusHighlightContainer(modifier = Modifier.fillMaxWidth(), interactionSource = interactionSource) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .activateOnEnter(enabled, onClick)
                .clickable(enabled = enabled, interactionSource = interactionSource, indication = null, onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            androidx.compose.material3.Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Writes a new or a custom role, or shows a standard one with a button to duplicate it. */
@Composable
internal fun RoleEditorDialog(
    draft: RoleDraft,
    catalog: RolesCatalog,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, GlobalUserRole, Set<Permission>) -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    var name by remember(draft) { mutableStateOf(draft.name) }
    var base by remember(draft) { mutableStateOf(draft.base) }
    val checked = remember(draft) { mutableStateMapOf<Permission, Boolean>().apply { draft.permissions.forEach { put(it, true) } } }
    val nameFocus = remember { FocusRequester() }
    val trimmed = name.trim()
    val taken = catalog.custom.any { it.id != draft.id && it.name.equals(trimmed, ignoreCase = true) }
    val grantable = if (draft.readOnly) Permission.entries.toSet() else catalog.grantable[base].orEmpty()
    val selected = checked.filterValues { it }.keys.intersect(grantable)
    LaunchedEffect(draft) { if (!draft.readOnly) nameFocus.requestFocus() }

    TrackedDialog(if (draft.readOnly) "StandardRole" else "EditRole") {
        AlertDialog(
            onDismissRequest = { if (!saving) onDismiss() },
            modifier = Modifier.appFocusGroup(),
            title = {
                MtsDialogTitle(
                    when {
                        draft.readOnly -> draft.base.label()
                        draft.id == null -> stringResource(Res.string.role_new)
                        else -> stringResource(Res.string.role_edit)
                    },
                    subtitle = if (draft.readOnly) stringResource(Res.string.roles_standard_info) else null,
                )
            },
            text = {
                ScrollableColumnWithScrollbar(state = rememberScrollState(), modifier = Modifier.heightIn(max = 560.dp), inDialog = true) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!draft.readOnly) {
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it.take(40) },
                                label = { Text(stringResource(Res.string.role_name)) },
                                isError = taken,
                                supportingText = if (taken) ({ Text(stringResource(Res.string.role_name_taken)) }) else null,
                                singleLine = true,
                                enabled = !saving,
                                modifier = Modifier.fillMaxWidth().focusRequester(nameFocus),
                            )
                            SelectField(
                                label = stringResource(Res.string.role_base),
                                options = RolesCatalog.CUSTOMIZABLE.map { SelectOption(it, it.label()) },
                                selected = base,
                                onSelect = { next ->
                                    base = next
                                    // The boxes start from the standard role of the new base.
                                    checked.clear()
                                    catalog.standard[next].orEmpty().forEach { checked[it] = true }
                                },
                                enabled = draft.id == null && !saving,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(stringResource(Res.string.role_base_info), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        PermissionChecklist(
                            shown = if (draft.readOnly) draft.permissions else grantable,
                            checked = if (draft.readOnly) draft.permissions else selected,
                            required = if (draft.readOnly) emptySet() else catalog.required,
                            enabled = !draft.readOnly && !saving,
                            onToggle = { permission -> checked[permission] = checked[permission] != true },
                        )
                    }
                }
            },
            confirmButton = {
                MtsDialogButtons {
                    if (draft.readOnly) {
                        FocusedButton(onClick = onDuplicate, enabled = !saving) { ButtonText(stringResource(Res.string.role_duplicate)) }
                    } else {
                        FocusedButton(
                            onClick = { onSave(trimmed, base, selected + catalog.required) },
                            enabled = !saving && trimmed.isNotEmpty() && !taken,
                        ) { ButtonText(stringResource(Res.string.save)) }
                        if (draft.id != null) {
                            FocusedOutlinedButton(onClick = onDuplicate, enabled = !saving) { ButtonText(stringResource(Res.string.role_duplicate)) }
                            FocusedOutlinedButton(
                                onClick = onDelete,
                                enabled = !saving,
                                colors = MtsButtons.destructiveOutlinedColors(),
                                border = MtsButtons.destructiveBorder(!saving),
                            ) { ButtonText(stringResource(Res.string.role_delete)) }
                        }
                    }
                    FocusedTextButton(onClick = onDismiss, enabled = !saving) {
                        ButtonText(stringResource(if (draft.readOnly) Res.string.close else Res.string.cancel))
                    }
                }
            },
        )
    }
}

@Composable
internal fun DeleteRoleDialog(role: CustomRole, saving: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    TrackedDialog("DeleteRole") {
        AlertDialog(
            onDismissRequest = { if (!saving) onDismiss() },
            modifier = Modifier.appFocusGroup(),
            title = { MtsDialogTitle(stringResource(Res.string.role_delete_title)) },
            text = {
                MtsDialogText(
                    if (role.accounts == 0) {
                        stringResource(Res.string.role_delete_text_unused, role.name)
                    } else {
                        pluralStringResource(Res.plurals.role_delete_text_used, role.accounts, role.accounts, role.name, role.base.label())
                    },
                )
            },
            confirmButton = {
                MtsDialogButtons {
                    FocusedButton(onClick = onConfirm, enabled = !saving, colors = MtsButtons.destructiveColors()) {
                        ButtonText(stringResource(Res.string.role_delete))
                    }
                    FocusedTextButton(onClick = onDismiss, enabled = !saving) { ButtonText(stringResource(Res.string.cancel)) }
                }
            },
        )
    }
}

/**
 * The role of an editor or a club admin at each level that it holds, and for a super admin the single permissions
 * of this person. A club admin chooses only among the roles that do not give more than it has.
 */
@Composable
internal fun AccountPermissionsDialog(
    user: ManagedUser,
    catalog: RolesCatalog,
    access: AdminStatus,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (Map<GlobalUserRole, LevelPermissions>) -> Unit,
    onSaveAsNewRole: (GlobalUserRole, Set<Permission>) -> Unit,
) {
    val levels = RolesCatalog.CUSTOMIZABLE.filter { it in user.roles }
    val managesPermissions = access.has(Permission.PERMISSIONS_MANAGE)
    val choosesRole = access.isSuperAdmin || !user.hasGrantsOutside
    val roleIds = remember(user) { mutableStateMapOf<GlobalUserRole, String?>().apply { levels.forEach { put(it, user.customRoleIds[it]) } } }
    val permissions = remember(user) {
        mutableStateMapOf<GlobalUserRole, Set<Permission>>().apply {
            levels.forEach { level -> put(level, user.rolePermissions[level] ?: catalog.permissionsOf(level, user.customRoleIds[level])) }
        }
    }

    TrackedDialog("AccountPermissions") {
        AlertDialog(
            onDismissRequest = { if (!saving) onDismiss() },
            modifier = Modifier.appFocusGroup(),
            title = { MtsDialogTitle(stringResource(Res.string.account_permissions), subtitle = user.alias.ifBlank { user.email }) },
            text = {
                ScrollableColumnWithScrollbar(state = rememberScrollState(), modifier = Modifier.heightIn(max = 560.dp), inDialog = true) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            stringResource(if (managesPermissions) Res.string.account_permissions_info else Res.string.account_permissions_only_super_admin),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (!choosesRole) {
                            Text(stringResource(Res.string.account_permissions_outside), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                        levels.forEach { level ->
                            val roleId = roleIds[level]
                            val rolePermissions = catalog.permissionsOf(level, roleId)
                            val current = permissions[level].orEmpty()
                            // Never a role above the own one, unless the viewer is a super admin. The current role always shows.
                            val choices = catalog.customFor(level).filter { role ->
                                access.isSuperAdmin || role.id == user.customRoleIds[level] || access.permissions.containsAll(role.permissions)
                            }
                            HorizontalDivider(Modifier.padding(vertical = 4.dp))
                            SelectField(
                                label = stringResource(Res.string.account_permissions_role, level.label()),
                                options = listOf(SelectOption<String?>(null, stringResource(Res.string.role_standard_option, level.label()))) +
                                    choices.map { SelectOption<String?>(it.id, it.name) },
                                selected = roleId,
                                onSelect = { next ->
                                    roleIds[level] = next
                                    permissions[level] = catalog.permissionsOf(level, next)
                                },
                                enabled = choosesRole && !saving,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            PermissionChecklist(
                                shown = if (managesPermissions) catalog.grantable[level].orEmpty() else current,
                                checked = current,
                                required = catalog.required,
                                enabled = managesPermissions && !saving,
                                onToggle = { permission -> permissions[level] = if (permission in current) current - permission else current + permission },
                                reference = rolePermissions,
                            )
                            if (managesPermissions && current != rolePermissions) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FocusedTextButton(onClick = { permissions[level] = rolePermissions }, enabled = !saving) {
                                        ButtonText(stringResource(Res.string.account_permissions_reset))
                                    }
                                    FocusedTextButton(onClick = { onSaveAsNewRole(level, current) }, enabled = !saving) {
                                        ButtonText(stringResource(Res.string.account_permissions_save_as_role))
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                MtsDialogButtons {
                    FocusedButton(
                        onClick = {
                            onSave(levels.associateWith { level -> LevelPermissions(roleIds[level], if (managesPermissions) permissions[level] else null) })
                        },
                        enabled = !saving && choosesRole,
                    ) { ButtonText(stringResource(Res.string.save)) }
                    FocusedTextButton(onClick = onDismiss, enabled = !saving) { ButtonText(stringResource(Res.string.cancel)) }
                }
            },
        )
    }
}

/** Asks the name of a new role made from the permissions of one person. */
@Composable
internal fun NewRoleNameDialog(
    catalog: RolesCatalog,
    initialName: String,
    saving: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    val focus = remember { FocusRequester() }
    val trimmed = name.trim()
    val taken = catalog.custom.any { it.name.equals(trimmed, ignoreCase = true) }
    LaunchedEffect(Unit) { focus.requestFocus() }
    TrackedDialog("NewRoleName") {
        AlertDialog(
            onDismissRequest = { if (!saving) onDismiss() },
            modifier = Modifier.appFocusGroup(),
            title = { MtsDialogTitle(stringResource(Res.string.account_permissions_save_as_role)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    label = { Text(stringResource(Res.string.role_name)) },
                    isError = taken,
                    supportingText = if (taken) ({ Text(stringResource(Res.string.role_name_taken)) }) else null,
                    singleLine = true,
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            },
            confirmButton = {
                MtsDialogButtons {
                    FocusedButton(onClick = { onConfirm(trimmed) }, enabled = !saving && trimmed.isNotEmpty() && !taken) { ButtonText(stringResource(Res.string.save)) }
                    FocusedTextButton(onClick = onDismiss, enabled = !saving) { ButtonText(stringResource(Res.string.cancel)) }
                }
            },
        )
    }
}

/** The dialogs of the permissions of one account, with their saves. The accounts screen shows them. */
@Composable
internal fun AccountPermissionsFlow(
    user: ManagedUser,
    access: AdminStatus,
    presenter: UsersPresenter,
    onDismiss: () -> Unit,
    onSaved: (ManagedUser) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var catalog by remember { mutableStateOf<RolesCatalog?>(null) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<UiText?>(null) }
    var newRole by remember { mutableStateOf<Pair<GlobalUserRole, Set<Permission>>?>(null) }
    LaunchedEffect(user.uid) {
        when (val result = presenter.loadRoles()) {
            is AppResult.Success -> catalog = result.value
            is AppResult.Failure -> error = result.error.toUiText()
        }
    }

    fun save(levels: Map<GlobalUserRole, LevelPermissions>) {
        scope.launch {
            saving = true
            when (val result = presenter.setUserPermissions(user.uid, levels)) {
                is AppResult.Success -> onSaved(result.value)
                is AppResult.Failure -> error = result.error.toUiText()
            }
            saving = false
        }
    }

    error?.let {
        AppErrorDialog(message = it, onDismiss = {
            error = null
            if (catalog == null) onDismiss()
        })
    }
    val roles = catalog ?: return
    val pending = newRole
    if (pending != null) {
        NewRoleNameDialog(
            catalog = roles,
            initialName = user.alias.ifBlank { user.email.substringBefore('@') },
            saving = saving,
            onDismiss = { newRole = null },
            onConfirm = { name ->
                scope.launch {
                    saving = true
                    when (val created = presenter.createRole(name, pending.first, pending.second)) {
                        is AppResult.Success -> {
                            newRole = null
                            saving = false
                            val levels = RolesCatalog.CUSTOMIZABLE.filter { it in user.roles }.associateWith { level ->
                                if (level == pending.first) LevelPermissions(created.value.id, created.value.permissions) else LevelPermissions(user.customRoleIds[level], null)
                            }
                            save(levels)
                        }
                        is AppResult.Failure -> {
                            error = created.error.toUiText()
                            saving = false
                        }
                    }
                }
            },
        )
        return
    }
    AccountPermissionsDialog(
        user = user,
        catalog = roles,
        access = access,
        saving = saving,
        onDismiss = onDismiss,
        onSave = ::save,
        onSaveAsNewRole = { level, permissions -> newRole = level to permissions },
    )
}

/** Shows that the permissions of an account were saved. */
@Composable
internal fun PermissionsSavedNotice(user: ManagedUser, onDismiss: () -> Unit) {
    AppSuccessDialog(UiText.of(Res.string.account_permissions_saved, user.alias.ifBlank { user.email }), onDismiss)
}
