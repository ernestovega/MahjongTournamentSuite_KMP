package com.etologic.mahjongtournamentsuite.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import mahjongtournamentsuite.playerapp.generated.resources.filter_individual
import mahjongtournamentsuite.playerapp.generated.resources.filter_search
import mahjongtournamentsuite.playerapp.generated.resources.filter_teams
import mahjongtournamentsuite.playerapp.generated.resources.filter_year
import mahjongtournamentsuite.playerapp.generated.resources.scope_my_tournaments
import org.jetbrains.compose.resources.stringResource

/** Individual or team tournaments. */
internal enum class TournamentKind { INDIVIDUAL, TEAMS }

/**
 * The filters of the tournament list. They combine: a tournament shows when it passes all of them.
 * An empty set or a null value lets every tournament through.
 */
internal data class TournamentFilter(
    val query: String = "",
    val statuses: Set<TournamentStatus> = emptySet(),
    val kind: TournamentKind? = null,
    val country: String? = null,
    val year: Int? = null,
    val mineOnly: Boolean = false,
) {
    val isActive: Boolean get() = this != TournamentFilter()

    /** A short text for the saved state of the screen. */
    fun encode(): String = listOf(
        query.replace('|', ' '),
        statuses.joinToString(",") { it.name },
        kind?.name.orEmpty(),
        country.orEmpty(),
        year?.toString().orEmpty(),
        mineOnly.toString(),
    ).joinToString("|")

    companion object {
        fun decode(text: String?): TournamentFilter {
            val parts = text?.split('|') ?: return TournamentFilter()
            if (parts.size != 6) return TournamentFilter()
            return TournamentFilter(
                query = parts[0],
                statuses = parts[1].split(',').mapNotNull { name -> TournamentStatus.entries.firstOrNull { it.name == name } }.toSet(),
                kind = TournamentKind.entries.firstOrNull { it.name == parts[2] },
                country = parts[3].ifBlank { null },
                year = parts[4].toIntOrNull(),
                mineOnly = parts[5] == "true",
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
        (statuses.isEmpty() || status in statuses) &&
        (kind == null || (kind == TournamentKind.TEAMS) == tournament.isTeams) &&
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

/** The search field and the filter chips above the tournament list. */
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
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            if (signedIn) {
                Chip(stringResource(Res.string.scope_my_tournaments), filter.mineOnly) { onChange(filter.copy(mineOnly = !filter.mineOnly)) }
            }
            listOf(TournamentStatus.IN_PROGRESS, TournamentStatus.UPCOMING, TournamentStatus.FINISHED).forEach { status ->
                Chip(stringResource(status.label), status in filter.statuses) {
                    onChange(filter.copy(statuses = if (status in filter.statuses) filter.statuses - status else filter.statuses + status))
                }
            }
            Chip(stringResource(Res.string.filter_individual), filter.kind == TournamentKind.INDIVIDUAL) {
                onChange(filter.copy(kind = if (filter.kind == TournamentKind.INDIVIDUAL) null else TournamentKind.INDIVIDUAL))
            }
            Chip(stringResource(Res.string.filter_teams), filter.kind == TournamentKind.TEAMS) {
                onChange(filter.copy(kind = if (filter.kind == TournamentKind.TEAMS) null else TournamentKind.TEAMS))
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            val allCountries = stringResource(Res.string.filter_all_countries)
            val countries = tournaments.map { it.hostCountry.uppercase() }.filter { it.isNotBlank() }.distinct().sorted()
            SelectField(
                label = stringResource(Res.string.filter_country),
                options = listOf("" to allCountries) + countries.map { it to it },
                selected = filter.country.orEmpty(),
                onSelect = { onChange(filter.copy(country = it.ifBlank { null })) },
                modifier = Modifier.widthIn(min = 150.dp, max = 200.dp),
                compact = true,
            )
            val allYears = stringResource(Res.string.filter_all_years)
            val years = tournaments.mapNotNull { it.year() }.distinct().sortedDescending()
            SelectField(
                label = stringResource(Res.string.filter_year),
                options = listOf(0 to allYears) + years.map { it to it.toString() },
                selected = filter.year ?: 0,
                onSelect = { onChange(filter.copy(year = it.takeIf { year -> year > 0 })) },
                modifier = Modifier.widthIn(min = 130.dp, max = 170.dp),
                compact = true,
            )
            if (filter.isActive) {
                TextButton(onClick = { onChange(TournamentFilter()) }) { Text(stringResource(Res.string.filter_clear)) }
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
