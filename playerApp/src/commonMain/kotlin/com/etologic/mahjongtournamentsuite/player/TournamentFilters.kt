package com.etologic.mahjongtournamentsuite.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FilterAltOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.presentation.components.PlayerFlag
import com.etologic.mahjongtournamentsuite.presentation.components.SelectField
import com.etologic.mahjongtournamentsuite.presentation.components.SelectOption
import com.etologic.mahjongtournamentsuite.presentation.components.rememberCountryNames
import mahjongtournamentsuite.playerapp.generated.resources.Res
import mahjongtournamentsuite.playerapp.generated.resources.filter_all_countries
import mahjongtournamentsuite.playerapp.generated.resources.filter_all_statuses
import mahjongtournamentsuite.playerapp.generated.resources.filter_all_years
import mahjongtournamentsuite.playerapp.generated.resources.filter_clear
import mahjongtournamentsuite.playerapp.generated.resources.filter_country
import mahjongtournamentsuite.playerapp.generated.resources.filter_search
import mahjongtournamentsuite.playerapp.generated.resources.filter_search_label
import mahjongtournamentsuite.playerapp.generated.resources.filter_status
import mahjongtournamentsuite.playerapp.generated.resources.filter_year
import mahjongtournamentsuite.playerapp.generated.resources.scope_my_tournaments
import org.jetbrains.compose.resources.stringResource
import com.etologic.mahjongtournamentsuite.presentation.components.DebouncedIconButton as IconButton
import com.etologic.mahjongtournamentsuite.presentation.components.DebouncedTextButton as TextButton

/**
 * The filters of the tournament list. They combine: a tournament shows when it passes all of them.
 * A null value lets every tournament through.
 */
internal data class TournamentFilter(
    val query: String = "",
    val status: TournamentStatus? = null,
    val country: String? = null,
    val year: Int? = null,
    val mineOnly: Boolean = false,
) {
    val isActive: Boolean get() = this != TournamentFilter()

    /** A short text for the saved state of the screen. */
    fun encode(): String = listOf(
        query.replace('|', ' '),
        status?.name.orEmpty(),
        country.orEmpty(),
        year?.toString().orEmpty(),
        mineOnly.toString(),
    ).joinToString("|")

    companion object {
        fun decode(text: String?): TournamentFilter {
            val parts = text?.split('|') ?: return TournamentFilter()
            if (parts.size != 5) return TournamentFilter()
            return TournamentFilter(
                query = parts[0],
                status = TournamentStatus.entries.firstOrNull { it.name == parts[1] },
                country = parts[2].ifBlank { null },
                year = parts[3].toIntOrNull(),
                mineOnly = parts[4] == "true",
            )
        }
    }
}

internal fun Tournament.year(): Int? = eventStartDate?.take(4)?.toIntOrNull()

/** True when the tournament passes every filter. [mine] holds the ids of the tournaments where the account plays. */
internal fun TournamentFilter.accepts(tournament: Tournament, status: TournamentStatus?, mine: Set<String>): Boolean {
    val words = query.trim().lowercase().split(' ').filter { it.isNotBlank() }
    val text = listOf(tournament.name, tournament.shortName, tournament.hostCity, tournament.venue.name)
        .joinToString(" ").lowercase()
    return words.all { it in text } &&
        (this.status == null || status == this.status) &&
        (country == null || tournament.hostCountry.equals(country, ignoreCase = true)) &&
        (year == null || tournament.year() == year) &&
        (!mineOnly || tournament.id in mine)
}

/**
 * The order of the list: the tournaments in play first, then the upcoming ones from the nearest, then the finished
 * ones from the newest. Tournaments without dates come last.
 */
internal fun List<Tournament>.sortedForList(statusOf: (Tournament) -> TournamentStatus?): List<Tournament> {
    fun group(tournament: Tournament) = when (statusOf(tournament)) {
        TournamentStatus.IN_PROGRESS -> 0
        TournamentStatus.UPCOMING -> 1
        TournamentStatus.FINISHED -> 2
        null -> 3
    }
    return sortedWith(
        compareBy<Tournament> { group(it) }
            .thenComparator { a, b ->
                val left = a.eventStartDate.orEmpty()
                val right = b.eventStartDate.orEmpty()
                // Upcoming: the nearest first. The others: the newest first.
                if (group(a) == 1) left.compareTo(right) else right.compareTo(left)
            }
            .thenBy { it.name.lowercase() },
    )
}

/** The search field with the "My tournaments" chip and the clear button, and the status, country and year dropdowns, above the tournament list. */
@Composable
internal fun TournamentFilterBar(
    filter: TournamentFilter,
    onChange: (TournamentFilter) -> Unit,
    tournaments: List<Tournament>,
    signedIn: Boolean,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        // On a phone the clear button is an icon, so the search field keeps room to type.
        val narrow = maxWidth < 560.dp
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // The search field shares its row with the "My tournaments" chip and the button that clears the filters.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = filter.query,
                    onValueChange = { onChange(filter.copy(query = it)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    // The title says what the field is. The hint, which is longer, shows while the person types.
                    label = { Text(stringResource(Res.string.filter_search_label)) },
                    placeholder = { Text(stringResource(Res.string.filter_search), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                )
                if (signedIn) {
                    Chip(stringResource(Res.string.scope_my_tournaments), filter.mineOnly) { onChange(filter.copy(mineOnly = !filter.mineOnly)) }
                }
                if (filter.isActive) {
                    if (narrow) {
                        IconButton(onClick = { onChange(TournamentFilter()) }) {
                            Icon(Icons.Default.FilterAltOff, contentDescription = stringResource(Res.string.filter_clear))
                        }
                    } else {
                        TextButton(onClick = { onChange(TournamentFilter()) }) { Text(stringResource(Res.string.filter_clear)) }
                    }
                }
            }
            // The three dropdowns share one row, also on a phone.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                val allStatuses = stringResource(Res.string.filter_all_statuses)
                // Each status has the color of its chip on the tournament cards, in the menu and in the field.
                val statusOptions = listOf(TournamentStatus.IN_PROGRESS, TournamentStatus.UPCOMING, TournamentStatus.FINISHED)
                    .map { SelectOption(it.name, stringResource(it.label), color = statusColor(it)) }
                val selectedStatusColor = filter.status?.let { statusColor(it) } ?: Color.Unspecified
                SelectField(
                    label = stringResource(Res.string.filter_status),
                    options = listOf(SelectOption("", allStatuses)) + statusOptions,
                    selected = filter.status?.name.orEmpty(),
                    onSelect = { name -> onChange(filter.copy(status = TournamentStatus.entries.firstOrNull { it.name == name })) },
                    modifier = Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = selectedStatusColor),
                )
                val allCountries = stringResource(Res.string.filter_all_countries)
                val countries = tournaments.map { it.hostCountry.uppercase() }.filter { it.isNotBlank() }.distinct().sorted()
                val countryName = rememberCountryNames()
                // Each country shows its flag. The search of the menu also finds a country by its name.
                val countryOptions = listOf(SelectOption("", allCountries)) + countries.map { code ->
                    SelectOption(code, code, searchText = countryName(code, code), leadingIcon = { PlayerFlag(code) })
                }
                SelectField(
                    label = stringResource(Res.string.filter_country),
                    options = countryOptions,
                    selected = filter.country.orEmpty(),
                    onSelect = { onChange(filter.copy(country = it.ifBlank { null })) },
                    modifier = Modifier.weight(1f),
                )
                val allYears = stringResource(Res.string.filter_all_years)
                val years = tournaments.mapNotNull { it.year() }.distinct().sortedDescending()
                SelectField(
                    label = stringResource(Res.string.filter_year),
                    options = listOf(0 to allYears) + years.map { it to it.toString() },
                    selected = filter.year ?: 0,
                    onSelect = { onChange(filter.copy(year = it.takeIf { year -> year > 0 })) },
                    modifier = Modifier.weight(1f),
                    compact = false,
                )
            }
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = if (selected) {
            { Icon(Icons.Default.Check, contentDescription = null, Modifier.size(FilterChipDefaults.IconSize)) }
        } else {
            null
        },
    )
}
