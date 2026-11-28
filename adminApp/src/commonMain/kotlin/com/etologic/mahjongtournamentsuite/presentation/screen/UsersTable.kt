package com.etologic.mahjongtournamentsuite.presentation.screen

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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.etologic.mahjongtournamentsuite.domain.model.GlobalUserRole
import com.etologic.mahjongtournamentsuite.domain.model.ManagedUser
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.presentation.components.ButtonText
import com.etologic.mahjongtournamentsuite.presentation.components.CountryFlag
import com.etologic.mahjongtournamentsuite.presentation.components.DataTableDivider
import com.etologic.mahjongtournamentsuite.presentation.components.DataTableHeaderRow
import com.etologic.mahjongtournamentsuite.presentation.components.DataTableHeaderText
import com.etologic.mahjongtournamentsuite.presentation.components.DataTableRow
import com.etologic.mahjongtournamentsuite.presentation.components.FilterGroup
import com.etologic.mahjongtournamentsuite.presentation.components.LazyColumnWithScrollbar
import com.etologic.mahjongtournamentsuite.presentation.components.LocalCompactLayout
import com.etologic.mahjongtournamentsuite.presentation.components.MtsScrollbar
import com.etologic.mahjongtournamentsuite.presentation.components.SelectField
import com.etologic.mahjongtournamentsuite.presentation.components.SelectOption
import com.etologic.mahjongtournamentsuite.presentation.components.SelectShownCheckbox
import com.etologic.mahjongtournamentsuite.presentation.components.ShowTabsAtTop
import com.etologic.mahjongtournamentsuite.presentation.components.appFocusGroup
import com.etologic.mahjongtournamentsuite.presentation.components.label
import com.etologic.mahjongtournamentsuite.presentation.components.rememberPersistentLazyListState
import com.etologic.mahjongtournamentsuite.presentation.components.screenFullBleed
import com.etologic.mahjongtournamentsuite.presentation.components.screenListPadding
import mahjongtournamentsuite.adminapp.generated.resources.Res
import mahjongtournamentsuite.adminapp.generated.resources.alias
import mahjongtournamentsuite.adminapp.generated.resources.all_tournaments
import mahjongtournamentsuite.adminapp.generated.resources.country
import mahjongtournamentsuite.adminapp.generated.resources.country_n
import mahjongtournamentsuite.adminapp.generated.resources.ema_id
import mahjongtournamentsuite.adminapp.generated.resources.email
import mahjongtournamentsuite.adminapp.generated.resources.email_hidden
import mahjongtournamentsuite.adminapp.generated.resources.filter_ema_photo
import mahjongtournamentsuite.adminapp.generated.resources.filter_own_photo
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
import com.etologic.mahjongtournamentsuite.presentation.components.MtsFilterChip as FilterChip

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
    // The space after the last account, so the floating buttons do not cover it.
    bottomPadding: Dp,
    selectedCount: Int = 0,
    onSelectAllShown: () -> Unit = {},
    onClearSelection: () -> Unit = {},
) {
    val compact = LocalCompactLayout.current
    val listState = rememberPersistentLazyListState("users")
    // Back at the top, the filters always show, also with a mouse wheel or a trackpad.
    ShowTabsAtTop(listState)
    LazyColumnWithScrollbar(
        state = listState,
        // The list reaches the edges and the bottom of the window, so its shadows take the whole width; the margins go
        // inside.
        scrollbar = MtsScrollbar.Overlay,
        modifier = Modifier.fillMaxWidth().screenFullBleed().appFocusGroup(),
        contentPadding = screenListPadding(bottom = bottomPadding),
    ) {
        if (!compact) {
            item {
                DataTableHeaderRow {
                    if (selectable) {
                        SelectShownCheckbox(
                            shownCount = users.size,
                            selectedCount = selectedCount,
                            enabled = enabled,
                            onSelectAllShown = onSelectAllShown,
                            onClear = onClearSelection,
                            modifier = Modifier.width(UserCheckboxColumnWidth),
                        )
                    }
                    DataTableHeaderText(stringResource(Res.string.photo), modifier = Modifier.width(PlayerPhotoColumnWidth))
                    DataTableHeaderText(stringResource(Res.string.country), modifier = Modifier.width(UserCountryColumnWidth))
                    DataTableHeaderText(stringResource(Res.string.alias), modifier = Modifier.weight(0.25f))
                    DataTableHeaderText(stringResource(Res.string.email), modifier = Modifier.weight(0.3f))
                    DataTableHeaderText(stringResource(Res.string.role), modifier = Modifier.weight(0.2f))
                    DataTableHeaderText(stringResource(Res.string.ema_id), modifier = Modifier.weight(0.12f))
                    DataTableHeaderText(stringResource(Res.string.status), modifier = Modifier.weight(0.13f))
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

/** The kinds of photo that the users list filters by. */
internal enum class UserPhotoFilter {
    /** The EMA player of the account has a photo. */
    EMA,

    /** The account has its own photo. */
    OWN,
}

/**
 * True when the account has one of the [filters] photos, or when there is no filter. [emaIdsWithPhoto] are the EMA
 * numbers of the EMA players that have a photo.
 */
internal fun ManagedUser.matchesPhotoFilters(filters: Set<UserPhotoFilter>, emaIdsWithPhoto: Set<String>): Boolean =
    filters.isEmpty() ||
        (UserPhotoFilter.OWN in filters && hasOwnPhoto && !photoUrl.isNullOrBlank()) ||
        (
            UserPhotoFilter.EMA in filters &&
                (emaId?.let { it in emaIdsWithPhoto } == true || (!hasOwnPhoto && !photoUrl.isNullOrBlank()))
            )

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
    selectedPhotoFilters: Set<UserPhotoFilter> = emptySet(),
    onPhotoFilterToggle: (UserPhotoFilter) -> Unit = {},
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
        val roleGroup: @Composable (Modifier) -> Unit = { modifier ->
            FilterGroup(stringResource(Res.string.role), modifier) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RoleFilterOrder.forEach { role ->
                        FilterChip(
                            selected = role in selectedRoles,
                            onClick = { onRoleToggle(role) },
                            label = {
                                // On a phone a two-word role takes two lines, so the four roles fit in one row.
                                val label = role.label()
                                Text(if (compact) label.replaceFirst(" ", "\n") else label, textAlign = TextAlign.Center)
                            },
                        )
                    }
                }
            }
        }
        // The photo filters let an admin review quickly the photos that the list shows.
        val photoGroup: @Composable (Modifier) -> Unit = { modifier ->
            FilterGroup(stringResource(Res.string.photo), modifier) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    UserPhotoFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = filter in selectedPhotoFilters,
                            onClick = { onPhotoFilterToggle(filter) },
                            label = {
                                Text(
                                    stringResource(
                                        when (filter) {
                                            UserPhotoFilter.EMA -> Res.string.filter_ema_photo
                                            UserPhotoFilter.OWN -> Res.string.filter_own_photo
                                        },
                                    ),
                                )
                            },
                        )
                    }
                }
            }
        }
        // A phone puts each group on its own line. A wider screen puts them side by side, under the two fields.
        if (compact) {
            roleGroup(Modifier.fillMaxWidth())
            photoGroup(Modifier.fillMaxWidth())
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                roleGroup(Modifier.weight(1f))
                photoGroup(Modifier.weight(1f))
            }
        }
    }
}
