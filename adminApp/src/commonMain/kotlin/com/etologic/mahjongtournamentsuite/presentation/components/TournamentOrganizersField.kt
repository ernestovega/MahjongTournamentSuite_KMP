package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.domain.model.Organization
import mahjongtournamentsuite.adminapp.generated.resources.Res
import mahjongtournamentsuite.adminapp.generated.resources.remove_organization_n
import mahjongtournamentsuite.adminapp.generated.resources.tournament_add_co_organizer
import mahjongtournamentsuite.adminapp.generated.resources.tournament_co_organizers
import mahjongtournamentsuite.adminapp.generated.resources.tournament_main_organizer
import mahjongtournamentsuite.adminapp.generated.resources.tournament_organizers_branding
import mahjongtournamentsuite.adminapp.generated.resources.tournament_organizers_locked
import mahjongtournamentsuite.adminapp.generated.resources.tournament_organizers_none
import org.jetbrains.compose.resources.stringResource
import com.etologic.mahjongtournamentsuite.presentation.components.MtsInputChip as InputChip

/**
 * The organizers of a tournament: the main one first, then the co-organizers. The tournament shows the logo and the
 * color of the main organizer. With [editable] false the list only shows, with the reason.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TournamentOrganizersField(
    organizations: List<Organization>,
    selected: List<String>,
    onChange: (List<String>) -> Unit,
    enabled: Boolean,
    editable: Boolean = true,
    /** The organizations that the main organizer can be, for a club admin its own ones. Null: every organization. */
    mainChoices: Set<String>? = null,
    error: String? = null,
) {
    val byId = remember(organizations) { organizations.associateBy { it.id } }
    val main = selected.firstOrNull()
    val coOrganizers = selected.drop(1)
    fun option(organization: Organization) = SelectOption(
        key = organization.id,
        label = organization.name,
        searchText = organization.country,
        leadingIcon = { OrganizationLogo(organization, size = 24.dp) },
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (organizations.isEmpty()) {
            Text(
                stringResource(Res.string.tournament_organizers_none),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            return@Column
        }
        if (editable) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OrganizationLogo(byId[main], size = 56.dp)
                SelectField(
                    label = stringResource(Res.string.tournament_main_organizer),
                    options = organizations.filter { mainChoices == null || it.id in mainChoices || it.id == main }.map(::option),
                    selected = main,
                    onSelect = { id -> onChange(listOf(id) + coOrganizers.filter { it != id }) },
                    enabled = enabled,
                    required = true,
                    isError = error != null,
                    supportingText = error,
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            Text(stringResource(Res.string.tournament_main_organizer), style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OrganizationLogo(byId[main], size = 56.dp)
                Text(byId[main]?.name ?: main.orEmpty(), style = MaterialTheme.typography.bodyLarge)
            }
        }
        if (editable || coOrganizers.isNotEmpty()) {
            Text(stringResource(Res.string.tournament_co_organizers), style = MaterialTheme.typography.titleSmall)
        }
        if (coOrganizers.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                coOrganizers.forEach { id ->
                    val name = byId[id]?.name ?: id
                    InputChip(
                        selected = false,
                        onClick = { if (editable && enabled) onChange(selected - id) },
                        enabled = enabled,
                        label = { Text(name) },
                        leadingIcon = { OrganizationLogo(byId[id], size = 20.dp) },
                        trailingIcon = if (editable) {
                            { Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.remove_organization_n, name)) }
                        } else {
                            null
                        },
                    )
                }
            }
        }
        val addable = organizations.filter { it.id !in selected }
        if (editable && main != null && addable.isNotEmpty()) {
            SelectField(
                label = stringResource(Res.string.tournament_add_co_organizer),
                options = addable.map(::option),
                selected = null,
                onSelect = { id -> onChange(selected + id) },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Text(
            stringResource(if (editable) Res.string.tournament_organizers_branding else Res.string.tournament_organizers_locked),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
