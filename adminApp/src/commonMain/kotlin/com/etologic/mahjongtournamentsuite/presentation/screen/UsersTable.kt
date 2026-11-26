package com.etologic.mahjongtournamentsuite.presentation.screen

import com.etologic.mahjongtournamentsuite.presentation.components.ButtonText
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.etologic.mahjongtournamentsuite.domain.model.GlobalUserRole
import com.etologic.mahjongtournamentsuite.domain.model.ManagedUser
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.presentation.components.CountryFlag
import com.etologic.mahjongtournamentsuite.presentation.components.DataTableDivider
import com.etologic.mahjongtournamentsuite.presentation.components.DataTableHeaderRow
import com.etologic.mahjongtournamentsuite.presentation.components.DataTableRow
import com.etologic.mahjongtournamentsuite.presentation.components.LazyColumnWithScrollbar
import com.etologic.mahjongtournamentsuite.presentation.components.LocalCompactLayout
import com.etologic.mahjongtournamentsuite.presentation.components.MtsScrollbar
import com.etologic.mahjongtournamentsuite.presentation.components.SelectField
import com.etologic.mahjongtournamentsuite.presentation.components.SelectOption
import com.etologic.mahjongtournamentsuite.presentation.components.appFocusGroup
import com.etologic.mahjongtournamentsuite.presentation.components.label
import com.etologic.mahjongtournamentsuite.presentation.components.rememberPersistentLazyListState
import mahjongtournamentsuite.adminapp.generated.resources.Res
import mahjongtournamentsuite.adminapp.generated.resources.alias
import mahjongtournamentsuite.adminapp.generated.resources.all_tournaments
import mahjongtournamentsuite.adminapp.generated.resources.country
import mahjongtournamentsuite.adminapp.generated.resources.country_n
import mahjongtournamentsuite.adminapp.generated.resources.ema_id
import mahjongtournamentsuite.adminapp.generated.resources.email
import mahjongtournamentsuite.adminapp.generated.resources.email_hidden
import mahjongtournamentsuite.adminapp.generated.resources.photo
import mahjongtournamentsuite.adminapp.generated.resources.photo_of
import mahjongtournamentsuite.adminapp.generated.resources.role
import mahjongtournamentsuite.adminapp.generated.resources.search
import mahjongtournamentsuite.adminapp.generated.resources.status
import mahjongtournamentsuite.adminapp.generated.resources.tournament
import mahjongtournamentsuite.adminapp.generated.resources.user_status_active
import mahjongtournamentsuite.adminapp.generated.resources.user_status_disabled
import org.jetbrains.compose.resources.stringResource
import com.etologic.mahjongtournamentsuite.presentation.components.DebouncedIconButton as IconButton
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedIconButton as IconButton

private val UserCountryColumnWidth = 64.dp
private val UserCheckboxColumnWidth = 48.dp

@Composable
internal fun UsersTable(
    users: List<ManagedUser>,
    countryByEmaId: Map<String, String>,
    enabled: Boolean,
    isSuperAdmin: Boolean,
    selfUid: String?,
    rowFocusRequesters: Map<String, FocusRequester>,
    selectable: Boolean,
    selectedUids: Set<String>,
    onToggleSelected: (String) -> Unit,
    onEdit: (ManagedUser) -> Unit,
) {
    val compact = LocalCompactLayout.current
    LazyColumnWithScrollbar(
        state = rememberPersistentLazyListState("users"),
        scrollbar = MtsScrollbar.Margin,
        modifier = Modifier.fillMaxWidth().appFocusGroup(),
    ) {
        if (!compact) {
            item {
                DataTableHeaderRow {
                    if (selectable) Spacer(Modifier.width(UserCheckboxColumnWidth))
                    Text(
                        stringResource(Res.string.photo),
                        modifier = Modifier.width(PlayerPhotoColumnWidth),
                        style = MaterialTheme.typography.labelLarge,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        stringResource(Res.string.country),
                        modifier = Modifier.width(UserCountryColumnWidth),
                        style = MaterialTheme.typography.labelLarge,
                        textAlign = TextAlign.Center,
                    )
                    Text(stringResource(Res.string.alias), modifier = Modifier.weight(0.25f), style = MaterialTheme.typography.labelLarge)
                    Text(stringResource(Res.string.email), modifier = Modifier.weight(0.3f), style = MaterialTheme.typography.labelLarge)
                    Text(stringResource(Res.string.role), modifier = Modifier.weight(0.2f), style = MaterialTheme.typography.labelLarge)
                    Text(stringResource(Res.string.ema_id), modifier = Modifier.weight(0.12f), style = MaterialTheme.typography.labelLarge)
                    Text(stringResource(Res.string.status), modifier = Modifier.weight(0.13f), style = MaterialTheme.typography.labelLarge)
                }
                DataTableDivider()
            }
        }
        itemsIndexed(users, key = { _, user -> user.uid }) { _, user ->
            // Everyone opens player accounts and the own account. A club admin opens the staff of its organizations,
            // and a super admin every account.
            val canEdit = enabled && (isSuperAdmin || user.canManage || user.role == GlobalUserRole.PLAYER || user.uid == selfUid)
            val emailText = if (user.emailHidden) stringResource(Res.string.email_hidden) else user.email
            DataTableRow(
                modifier = Modifier.focusRequester(rowFocusRequesters.getValue(user.uid)),
                onClick = { if (canEdit) onEdit(user) },
                clickFocusable = canEdit,
            ) {
                if (selectable) {
                    Checkbox(
                        checked = user.uid in selectedUids,
                        onCheckedChange = { onToggleSelected(user.uid) },
                        enabled = enabled,
                        modifier = Modifier.width(UserCheckboxColumnWidth).semantics { contentDescription = user.email },
                    )
                }
                val country = user.emaId?.let(countryByEmaId::get).orEmpty()
                val roleLabel = user.roles.sorted().map { it.label() }.joinToString(" + ").ifBlank { user.role.label() }
                val statusLabel = stringResource(if (user.disabled) Res.string.user_status_disabled else Res.string.user_status_active)
                // The own photo of the account, or the photo of its EMA player.
                Box(
                    modifier = Modifier.width(PlayerPhotoColumnWidth).height(PlayerPhotoSize),
                    contentAlignment = Alignment.Center,
                ) {
                    val photoUrl = user.photoUrl
                    if (photoUrl.isNullOrBlank()) {
                        PlayerPhotoPlaceholder(PlayerPhotoSize)
                    } else {
                        AsyncImage(
                            model = photoUrl,
                            contentDescription = stringResource(Res.string.photo_of, user.alias.ifBlank { user.email }),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(PlayerPhotoSize),
                        )
                    }
                }
                if (compact) {
                    // A phone shows the alias over the email, and the other columns as one small line.
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            user.alias.ifBlank { emailText },
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (user.alias.isNotBlank()) {
                            Text(
                                emailText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (country.isNotBlank()) CountryFlag(code = country, contentDescription = stringResource(Res.string.country_n, country))
                            Text(
                                listOfNotNull(roleLabel, user.emaId?.let { "EMA $it" }, statusLabel).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (user.disabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                } else {
                    Box(modifier = Modifier.width(UserCountryColumnWidth), contentAlignment = Alignment.Center) {
                        if (country.isBlank()) {
                            Text("—")
                        } else {
                            CountryFlag(code = country, contentDescription = stringResource(Res.string.country_n, country))
                        }
                    }
                    Text(user.alias.ifBlank { "—" }, modifier = Modifier.weight(0.25f), maxLines = 1)
                    Text(
                        emailText,
                        modifier = Modifier.weight(0.3f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (user.emailHidden) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
                    )
                    Text(
                        user.roles.sorted().map { it.label() }.joinToString(" + ").ifBlank { user.role.label() },
                        modifier = Modifier.weight(0.2f),
                        maxLines = 1,
                    )
                    Text(user.emaId ?: "—", modifier = Modifier.weight(0.12f), maxLines = 1)
                    Text(stringResource(if (user.disabled) Res.string.user_status_disabled else Res.string.user_status_active), modifier = Modifier.weight(0.13f))
                }
            }
            DataTableDivider()
        }
    }
}

/** The order of the role filter buttons. */
internal val RoleFilterOrder = listOf(GlobalUserRole.SUPER_ADMIN, GlobalUserRole.CLUB_ADMIN, GlobalUserRole.EDITOR, GlobalUserRole.PLAYER)

@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
internal fun UserFilters(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    selectedRoles: Set<GlobalUserRole>,
    onRoleToggle: (GlobalUserRole) -> Unit,
    tournaments: List<Tournament>,
    selectedTournamentId: String?,
    onTournamentSelected: (String?) -> Unit,
) {
    // A phone puts the search and the tournament on two lines, so both fields have room.
    val compact = LocalCompactLayout.current
    val fieldModifier = if (compact) Modifier.fillMaxWidth() else Modifier
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            maxItemsInEachRow = if (compact) 1 else 2,
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                label = { Text(stringResource(Res.string.search)) },
                singleLine = true,
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchChange("") }) { ButtonText("×") }
                    }
                },
                modifier = if (compact) fieldModifier else Modifier.weight(1f),
            )
            val allTournaments = stringResource(Res.string.all_tournaments)
            val options = remember(tournaments, allTournaments) {
                listOf(SelectOption<String?>(null, allTournaments)) + tournaments.map { SelectOption(it.id, it.name) }
            }
            SelectField(
                label = stringResource(Res.string.tournament),
                options = options,
                selected = selectedTournamentId,
                onSelect = onTournamentSelected,
                modifier = if (compact) fieldModifier else Modifier.weight(1f),
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RoleFilterOrder.forEach { role ->
                FilterChip(
                    selected = role in selectedRoles,
                    onClick = { onRoleToggle(role) },
                    label = { Text(role.label()) },
                )
            }
        }
    }
}
