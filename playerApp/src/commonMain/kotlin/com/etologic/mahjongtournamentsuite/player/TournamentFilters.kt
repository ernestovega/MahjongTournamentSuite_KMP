package com.etologic.mahjongtournamentsuite.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import com.etologic.mahjongtournamentsuite.presentation.components.DebouncedIconButton as IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import com.etologic.mahjongtournamentsuite.presentation.components.DebouncedTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.presentation.components.SelectField
import mahjongtournamentsuite.playerapp.generated.resources.Res
import mahjongtournamentsuite.playerapp.generated.resources.filter_all_countries
import mahjongtournamentsuite.playerapp.generated.resources.filter_all_years
import mahjongtournamentsuite.playerapp.generated.resources.filter_clear
import mahjongtournamentsuite.playerapp.generated.resources.filter_country
import mahjongtournamentsuite.playerapp.generated.resources.filter_search
import mahjongtournamentsuite.playerapp.generated.resources.filter_status
import mahjongtournamentsuite.playerapp.generated.resources.filter_all_statuses
import mahjongtournamentsuite.playerapp.generated.resources.filter_year
import mahjongtournamentsuite.playerapp.generated.resources.scope_my_tournaments
import org.jetbrains.compose.resources.stringResource

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

/** The search field, the status, country and year dropdowns and the "My tournaments" chip above the tournament list. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TournamentFilterBar(
    filter: TournamentFilter,
    onChange: (TournamentFilter) -> Unit,
    tournaments: List<Tournament>,
    signedIn: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = filter.query,
            onValueChange = { onChange(filter.copy(query = it)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text(stringResource(Res.string.filter_search)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = if (filter.query.isEmpty()) null else {
                {
                    IconButton(onClick = { onChange(filter.copy(query = "")) }) {
                        Icon(Icons.Default.Clear, contentDescription = stringResource(Res.string.filter_clear))
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        )
        // The three dropdowns share one row, also on a phone.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            val allStatuses = stringResource(Res.string.filter_all_statuses)
            val statusOptions = listOf(TournamentStatus.IN_PROGRESS, TournamentStatus.UPCOMING, TournamentStatus.FINISHED)
                .map { it.name to stringResource(it.label) }
            SelectField(
                label = stringResource(Res.string.filter_status),
                options = listOf("" to allStatuses) + statusOptions,
                selected = filter.status?.name.orEmpty(),
                onSelect = { name -> onChange(filter.copy(status = TournamentStatus.entries.firstOrNull { it.name == name })) },
                modifier = Modifier.weight(1f),
                compact = true,
            )
            val allCountries = stringResource(Res.string.filter_all_countries)
            val countries = tournaments.map { it.hostCountry.uppercase() }.filter { it.isNotBlank() }.distinct().sorted()
            SelectField(
                label = stringResource(Res.string.filter_country),
                options = listOf("" to allCountries) + countries.map { it to it },
                selected = filter.country.orEmpty(),
                onSelect = { onChange(filter.copy(country = it.ifBlank { null })) },
                modifier = Modifier.weight(1f),
                compact = true,
            )
            val allYears = stringResource(Res.string.filter_all_years)
            val years = tournaments.mapNotNull { it.year() }.distinct().sortedDescending()
            SelectField(
                label = stringResource(Res.string.filter_year),
                options = listOf(0 to allYears) + years.map { it to it.toString() },
                selected = filter.year ?: 0,
                onSelect = { onChange(filter.copy(year = it.takeIf { year -> year > 0 })) },
                modifier = Modifier.weight(1f),
                compact = true,
            )
        }
        if (signedIn || filter.isActive) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                if (signedIn) {
                    Chip(stringResource(Res.string.scope_my_tournaments), filter.mineOnly) { onChange(filter.copy(mineOnly = !filter.mineOnly)) }
                }
                if (filter.isActive) {
                    TextButton(onClick = { onChange(TournamentFilter()) }) { Text(stringResource(Res.string.filter_clear)) }
                }
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
