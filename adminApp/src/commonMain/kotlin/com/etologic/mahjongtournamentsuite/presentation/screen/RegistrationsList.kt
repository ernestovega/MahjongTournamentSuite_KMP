package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.domain.model.AdminRegistration
import com.etologic.mahjongtournamentsuite.domain.model.RegistrationStatus
import com.etologic.mahjongtournamentsuite.domain.model.displayName
import com.etologic.mahjongtournamentsuite.presentation.components.CountryFlag
import com.etologic.mahjongtournamentsuite.presentation.components.DataTableDivider
import com.etologic.mahjongtournamentsuite.presentation.components.DataTableHeaderRow
import com.etologic.mahjongtournamentsuite.presentation.components.DataTableRow
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedIconButton
import com.etologic.mahjongtournamentsuite.presentation.components.LocalCompactLayout
import com.etologic.mahjongtournamentsuite.presentation.components.PlayerPhoto
import com.etologic.mahjongtournamentsuite.presentation.components.horizontalScrollWithIndicators
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import mahjongtournamentsuite.adminapp.generated.resources.Res
import mahjongtournamentsuite.adminapp.generated.resources.clear_search
import mahjongtournamentsuite.adminapp.generated.resources.reg_add
import mahjongtournamentsuite.adminapp.generated.resources.reg_col_food
import mahjongtournamentsuite.adminapp.generated.resources.reg_col_player
import mahjongtournamentsuite.adminapp.generated.resources.reg_col_team
import mahjongtournamentsuite.adminapp.generated.resources.reg_edit
import mahjongtournamentsuite.adminapp.generated.resources.reg_email
import mahjongtournamentsuite.adminapp.generated.resources.reg_filter_all
import mahjongtournamentsuite.adminapp.generated.resources.reg_filter_problems
import mahjongtournamentsuite.adminapp.generated.resources.reg_food
import mahjongtournamentsuite.adminapp.generated.resources.reg_food_of
import mahjongtournamentsuite.adminapp.generated.resources.reg_give_place
import mahjongtournamentsuite.adminapp.generated.resources.reg_more_actions
import mahjongtournamentsuite.adminapp.generated.resources.reg_move_to_waiting
import mahjongtournamentsuite.adminapp.generated.resources.reg_no_account
import mahjongtournamentsuite.adminapp.generated.resources.reg_paid
import mahjongtournamentsuite.adminapp.generated.resources.reg_paid_count
import mahjongtournamentsuite.adminapp.generated.resources.reg_remove
import mahjongtournamentsuite.adminapp.generated.resources.reg_shared_email
import mahjongtournamentsuite.adminapp.generated.resources.search
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The search with Add a person beside it, and one chip per list with the number of people that match the search. On a
 * phone the chips go in one line that scrolls sideways, and the paid count under them.
 */
@Composable
internal fun RegistrationFilters(
    query: String,
    onQueryChange: (String) -> Unit,
    matching: List<AdminRegistration>,
    selected: RegistrationStatus?,
    onSelect: (RegistrationStatus?) -> Unit,
    problemsOnly: Boolean,
    onProblemsOnly: (Boolean) -> Unit,
    paid: Int,
    confirmed: Int,
    busy: Boolean,
    onAdd: () -> Unit,
) {
    val compact = LocalCompactLayout.current
    Column(Modifier.padding(top = MtsTheme.spacing.lg, bottom = MtsTheme.spacing.xs), verticalArrangement = Arrangement.spacedBy(MtsTheme.spacing.sm)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MtsTheme.spacing.md)) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text(stringResource(Res.string.search)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                trailingIcon = if (query.isNotEmpty()) {
                    {
                        FocusedIconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.clear_search))
                        }
                    }
                } else {
                    null
                },
                modifier = Modifier.weight(1f).testTag("registrations-search"),
            )
            if (compact) {
                FocusedIconButton(onClick = onAdd, enabled = !busy) {
                    Icon(Icons.Default.PersonAdd, contentDescription = stringResource(Res.string.reg_add))
                }
            } else {
                AddPersonButton(busy = busy, onAdd = onAdd)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MtsTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = if (compact) Modifier.fillMaxWidth().horizontalScrollWithIndicators().padding(bottom = MtsTheme.spacing.sm) else Modifier.weight(1f).horizontalScrollWithIndicators(),
                horizontalArrangement = Arrangement.spacedBy(MtsTheme.spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = selected == null && !problemsOnly,
                    onClick = { onSelect(null) },
                    label = { Text(stringResource(Res.string.reg_filter_all, matching.size)) },
                )
                RegistrationSections.forEach { (status, title) ->
                    FilterChip(
                        selected = selected == status,
                        onClick = { onSelect(if (selected == status) null else status) },
                        label = { Text(stringResource(title, matching.count { it.status == status }), maxLines = 1) },
                        leadingIcon = { Box(Modifier.size(8.dp).clip(CircleShape).background(statusColor(status))) },
                    )
                }
                val withProblems = matching.count { it.problems.isNotEmpty() }
                if (withProblems > 0 || problemsOnly) {
                    FilterChip(
                        selected = problemsOnly,
                        onClick = { onProblemsOnly(!problemsOnly) },
                        label = { Text(stringResource(Res.string.reg_filter_problems, withProblems), maxLines = 1) },
                        leadingIcon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.testTag("registrations-filter-problems"),
                    )
                }
            }
            if (!compact) {
                Text(
                    stringResource(Res.string.reg_paid_count, paid, confirmed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (compact) {
            Text(
                stringResource(Res.string.reg_paid_count, paid, confirmed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun statusColor(status: RegistrationStatus): Color = when (status) {
    RegistrationStatus.CONFIRMED -> MaterialTheme.colorScheme.primary
    RegistrationStatus.WAITING -> MtsTheme.colors.warning
    RegistrationStatus.PENDING -> MaterialTheme.colorScheme.outline
}

@Composable
internal fun RegistrationsHeaderRow(isTeams: Boolean) {
    Column {
        DataTableHeaderRow {
            HeaderText("#", Modifier.width(PositionColumnWidth), TextAlign.Center)
            Spacer(Modifier.width(PhotoSize))
            HeaderText(stringResource(Res.string.reg_col_player), Modifier.weight(0.34f))
            HeaderText(stringResource(Res.string.reg_email), Modifier.weight(0.3f))
            if (isTeams) HeaderText(stringResource(Res.string.reg_col_team), Modifier.weight(0.2f))
            HeaderText(stringResource(Res.string.reg_col_food), Modifier.width(FoodColumnWidth), TextAlign.Center, maxLines = 1)
            HeaderText(stringResource(Res.string.reg_paid), Modifier.width(PaidColumnWidth), TextAlign.Center)
            Spacer(Modifier.width(ActionsColumnWidth))
        }
        DataTableDivider()
    }
}

@Composable
private fun HeaderText(text: String, modifier: Modifier, textAlign: TextAlign = TextAlign.Start, maxLines: Int = 2) {
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/** The title of one list: a colored mark, its name and how many people it has. */
@Composable
internal fun SectionTitle(status: RegistrationStatus, title: StringResource, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = MtsTheme.spacing.lg, bottom = MtsTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MtsTheme.spacing.sm),
    ) {
        Box(Modifier.width(4.dp).height(20.dp).clip(MtsTheme.shapes.pill).background(statusColor(status)))
        Text(stringResource(title, count), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

/**
 * One person. A wide screen shows a row of the table; a phone shows the name over the EMA number, the team and the
 * email, with Paid and the menu at the end.
 */
@Composable
internal fun RegistrationRow(
    registration: AdminRegistration,
    displayName: String,
    photoUrl: String?,
    teamName: String?,
    isTeams: Boolean,
    enabled: Boolean,
    onPaidChange: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onMove: (RegistrationStatus) -> Unit,
    onRemove: () -> Unit,
) {
    val compact = LocalCompactLayout.current
    // Without a team: a dash in the column of a wide screen, nothing on a phone.
    val team = if (isTeams) teamName ?: "—" else null
    DataTableRow(
        modifier = Modifier.testTag("registration-${registration.emaId}"),
        onClick = if (enabled) onEdit else null,
        contentPadding = PaddingValues(horizontal = if (compact) 0.dp else 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(if (compact) MtsTheme.spacing.sm else MtsTheme.spacing.md),
    ) {
        Text(
            if (registration.status == RegistrationStatus.PENDING) "—" else registration.position.toString(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(if (compact) 24.dp else PositionColumnWidth),
        )
        PlayerPhoto(photoUrl = photoUrl, name = displayName, size = PhotoSize)
        if (compact) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                NameLine(displayName, registration.country)
                Text(
                    listOfNotNull("EMA ${registration.shownEmaId}", teamName.takeIf { isTeams }).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (registration.email.isNotBlank()) {
                    Text(registration.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                RowNotes(registration, showFood = true)
                ProblemLines(registration.problems)
            }
            PaidBox(registration, enabled, onPaidChange, compact = true)
        } else {
            Column(Modifier.weight(0.34f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                NameLine(displayName, registration.country)
                Text("EMA ${registration.shownEmaId}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ProblemLines(registration.problems.filter { it in AdminRegistration.EMA_PROBLEMS })
            }
            Column(Modifier.weight(0.3f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(registration.email.ifBlank { "—" }, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                RowNotes(registration, showFood = false)
                ProblemLines(registration.problems.filter { it !in AdminRegistration.EMA_PROBLEMS })
            }
            if (team != null) {
                Text(
                    team,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (teamName == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(0.2f),
                )
            }
            Box(Modifier.width(FoodColumnWidth), contentAlignment = Alignment.Center) {
                if (registration.foodRestrictions.isNotBlank()) {
                    FoodIcon(registration.foodRestrictions)
                } else {
                    Text("—", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            PaidBox(registration, enabled, onPaidChange, compact = false)
        }
        RegistrationMenu(registration, displayName, enabled, onEdit, onMove, onRemove)
    }
}

@Composable
private fun NameLine(name: String, country: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MtsTheme.spacing.sm)) {
        CountryFlag(code = country, width = 20.dp)
        Text(name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** What the import found wrong in the row, for the organizer to fix it. */
@Composable
internal fun ProblemLines(problems: List<String>) {
    problems.forEach { problem ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MtsTheme.spacing.xs)) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
            Text(problemText(problem), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
        }
    }
}

/** No account, an email that another person shares and, on a phone, the food restrictions. */
@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun RowNotes(registration: AdminRegistration, showFood: Boolean) {
    val food = showFood && registration.foodRestrictions.isNotBlank()
    if (registration.hasAccount && !registration.sharedEmail && !food) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(MtsTheme.spacing.sm), itemVerticalAlignment = Alignment.CenterVertically) {
        if (!registration.hasAccount) {
            Text(stringResource(Res.string.reg_no_account), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (food) FoodIcon(registration.foodRestrictions, size = 18.dp)
        if (registration.sharedEmail) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MtsTheme.spacing.xs)) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                Text(stringResource(Res.string.reg_shared_email), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/** The food restrictions are private: an icon with a tooltip, so the list stays short. */
@Composable
private fun FoodIcon(text: String, size: Dp = 22.dp) {
    com.etologic.mahjongtournamentsuite.presentation.components.AppTooltipBox(stringResource(Res.string.reg_food_of, text)) {
        FocusedIconButton(onClick = { toggleTooltip() }, buttonModifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Restaurant, contentDescription = stringResource(Res.string.reg_food), tint = MtsTheme.colors.warning, modifier = Modifier.size(size))
        }
    }
}

@Composable
private fun PaidBox(registration: AdminRegistration, enabled: Boolean, onPaidChange: (Boolean) -> Unit, compact: Boolean) {
    // The box and its "Paid" under it are one control: a tap on the word checks it too.
    val paidToggle = if (registration.status != RegistrationStatus.PENDING) {
        Modifier
            .clip(MaterialTheme.shapes.small)
            .toggleable(value = registration.paid, enabled = enabled, role = Role.Checkbox, onValueChange = onPaidChange)
            .testTag("registration-paid-${registration.emaId}")
    } else {
        Modifier
    }
    Column(
        Modifier.width(if (compact) 48.dp else PaidColumnWidth).then(paidToggle),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (registration.status != RegistrationStatus.PENDING) {
            Checkbox(checked = registration.paid, onCheckedChange = null, enabled = enabled, modifier = Modifier.padding(MtsTheme.spacing.md))
            if (compact) Text(stringResource(Res.string.reg_paid), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RegistrationMenu(
    registration: AdminRegistration,
    displayName: String,
    enabled: Boolean,
    onEdit: () -> Unit,
    onMove: (RegistrationStatus) -> Unit,
    onRemove: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Box(Modifier.width(ActionsColumnWidth), contentAlignment = Alignment.Center) {
        FocusedIconButton(onClick = { menu = true }, enabled = enabled) {
            Icon(Icons.Default.MoreVert, contentDescription = stringResource(Res.string.reg_more_actions, displayName))
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.reg_edit)) },
                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                onClick = {
                    menu = false
                    onEdit()
                },
            )
            if (registration.status != RegistrationStatus.CONFIRMED) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.reg_give_place)) },
                    onClick = {
                        menu = false
                        onMove(RegistrationStatus.CONFIRMED)
                    },
                )
            }
            if (registration.status == RegistrationStatus.CONFIRMED) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.reg_move_to_waiting)) },
                    onClick = {
                        menu = false
                        onMove(RegistrationStatus.WAITING)
                    },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.reg_remove), color = MaterialTheme.colorScheme.error) },
                onClick = {
                    menu = false
                    onRemove()
                },
            )
        }
    }
}
