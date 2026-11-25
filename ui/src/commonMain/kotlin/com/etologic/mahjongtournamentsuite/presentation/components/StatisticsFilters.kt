package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.domain.model.Player
import com.etologic.mahjongtournamentsuite.domain.model.StatisticsGroupFilter
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.displayName
import com.etologic.mahjongtournamentsuite.domain.usecase.PersonStatisticsData
import com.etologic.mahjongtournamentsuite.presentation.telemetry.TrackedDialog
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import mahjongtournamentsuite.ui.generated.resources.Res
import mahjongtournamentsuite.ui.generated.resources.no_country
import mahjongtournamentsuite.ui.generated.resources.stats_all_tournaments
import mahjongtournamentsuite.ui.generated.resources.stats_chip_from_year
import mahjongtournamentsuite.ui.generated.resources.stats_chip_players_from
import mahjongtournamentsuite.ui.generated.resources.stats_chip_remove
import mahjongtournamentsuite.ui.generated.resources.stats_chip_rival
import mahjongtournamentsuite.ui.generated.resources.stats_chip_table
import mahjongtournamentsuite.ui.generated.resources.stats_chip_to_year
import mahjongtournamentsuite.ui.generated.resources.stats_chip_tournaments_in
import mahjongtournamentsuite.ui.generated.resources.stats_chip_years
import mahjongtournamentsuite.ui.generated.resources.stats_filter_any_country
import mahjongtournamentsuite.ui.generated.resources.stats_filter_any_player
import mahjongtournamentsuite.ui.generated.resources.stats_filter_any_rival
import mahjongtournamentsuite.ui.generated.resources.stats_filter_any_year
import mahjongtournamentsuite.ui.generated.resources.stats_filter_apply
import mahjongtournamentsuite.ui.generated.resources.stats_filter_clear
import mahjongtournamentsuite.ui.generated.resources.stats_filter_everything
import mahjongtournamentsuite.ui.generated.resources.stats_filter_from_year
import mahjongtournamentsuite.ui.generated.resources.stats_filter_player
import mahjongtournamentsuite.ui.generated.resources.stats_filter_player_country
import mahjongtournamentsuite.ui.generated.resources.stats_filter_rival
import mahjongtournamentsuite.ui.generated.resources.stats_filter_to_year
import mahjongtournamentsuite.ui.generated.resources.stats_filter_tournament
import mahjongtournamentsuite.ui.generated.resources.stats_filter_tournament_country
import mahjongtournamentsuite.ui.generated.resources.stats_filters
import mahjongtournamentsuite.ui.generated.resources.stats_filters_title
import org.jetbrains.compose.resources.stringResource

/**
 * The filters of the statistics screen, the same in both apps. They combine. A null field keeps everything.
 *
 * - [emaId]: one person, over the tournaments that the other filters keep.
 * - [rivalEmaId]: with [emaId], the person compared with this other player, who may have never played with the person.
 * - [together]: with [rivalEmaId], only the tables where the two played together. Without it, all the tables of each one.
 * - [slotId]: one seat of [tournamentId] without an EMA number, such as a guest.
 * - [tournamentId]: one tournament.
 * - [tournamentCountry]: the two-letter code of the country where the tournaments were played.
 * - [playerCountry]: the EMA code of the country of the players. Empty keeps the players without a country.
 *   It does not apply with a person.
 * - [fromYear] and [toYear]: the years of the tournaments, by their first day.
 * - [roundId] and [tableId]: one table of [tournamentId]. A table of the tournament opens with them.
 */
data class StatisticsFilters(
    val emaId: String? = null,
    val rivalEmaId: String? = null,
    val together: Boolean = false,
    val slotId: Int? = null,
    val tournamentId: String? = null,
    val tournamentCountry: String? = null,
    val playerCountry: String? = null,
    val fromYear: Int? = null,
    val toYear: Int? = null,
    val roundId: Int? = null,
    val tableId: Int? = null,
) {
    /** What the screen shows for these filters. */
    val view: StatisticsView
        get() = when {
            emaId != null -> StatisticsView.PERSON
            tournamentId != null && roundId != null && tableId != null -> StatisticsView.TABLE
            slotId != null && tournamentId != null -> StatisticsView.SLOT
            tournamentId != null && playerCountry == null -> StatisticsView.TOURNAMENT
            else -> StatisticsView.GROUP
        }

    /** True when [tournament] passes the filters of the tournaments. */
    fun keeps(tournament: Tournament): Boolean {
        val year = tournament.eventStartDate?.take(4)?.toIntOrNull()
        return (tournamentId == null || tournament.id == tournamentId) &&
            (tournamentCountry == null || tournament.hostCountry.equals(tournamentCountry, ignoreCase = true)) &&
            (fromYear == null || (year != null && year >= fromYear)) &&
            (toYear == null || (year != null && year <= toYear))
    }

    fun groupFilter(): StatisticsGroupFilter = StatisticsGroupFilter(
        tournamentIds = tournamentId?.let { setOf(it) },
        tournamentCountry = tournamentCountry,
        playerCountry = playerCountry,
        fromYear = fromYear,
        toYear = toYear,
    )

    /** One person, with the filters of the tournaments kept. */
    fun withPerson(emaId: String): StatisticsFilters = copy(
        emaId = emaId,
        rivalEmaId = rivalEmaId?.takeIf { it != emaId && emaId == this.emaId },
        together = together && rivalEmaId != null && rivalEmaId != emaId && emaId == this.emaId,
        slotId = null,
        playerCountry = null,
        roundId = null,
        tableId = null,
    )

    /** A text that [decode] reads back, to keep the filters across a page refresh. */
    fun encode(): String = listOfNotNull(
        emaId?.let { "player=$it" },
        rivalEmaId?.let { "rival=$it" },
        "together=1".takeIf { together && rivalEmaId != null },
        slotId?.let { "slot=$it" },
        tournamentId?.let { "tournament=$it" },
        tournamentCountry?.let { "tournamentCountry=$it" },
        playerCountry?.let { "playerCountry=$it" },
        fromYear?.let { "from=$it" },
        toYear?.let { "to=$it" },
        roundId?.let { "round=$it" },
        tableId?.let { "table=$it" },
    ).joinToString("&")

    companion object {
        fun decode(text: String?): StatisticsFilters? {
            if (text == null) return null
            val values = text.split("&").filter { "=" in it }.associate { it.substringBefore("=") to it.substringAfter("=") }
            return StatisticsFilters(
                emaId = values["player"]?.takeIf { it.isNotBlank() },
                rivalEmaId = values["rival"]?.takeIf { it.isNotBlank() && values["player"]?.isNotBlank() == true },
                together = values["together"] == "1",
                slotId = values["slot"]?.toIntOrNull(),
                tournamentId = values["tournament"]?.takeIf { it.isNotBlank() },
                tournamentCountry = values["tournamentCountry"]?.takeIf { it.isNotBlank() },
                playerCountry = values["playerCountry"],
                fromYear = values["from"]?.toIntOrNull(),
                toYear = values["to"]?.toIntOrNull(),
                roundId = values["round"]?.toIntOrNull(),
                tableId = values["table"]?.toIntOrNull(),
            )
        }
    }
}

/** What the statistics screen shows. */
enum class StatisticsView {
    /** One person over the kept tournaments: the cards of the person and the list of the tournaments. */
    PERSON,

    /** One table of one tournament. */
    TABLE,

    /** One seat of one tournament: the place, and the place after each round. */
    SLOT,

    /** One whole tournament, with the chips of the rounds. */
    TOURNAMENT,

    /** A group of tournaments and players. */
    GROUP,
}

/** The key of "any" in the selects of the filters dialog. */
private const val ANY = ""

/** The key of the players without a country in the select of the country of the players. */
private const val NO_COUNTRY_KEY = "-"

/**
 * The row of the active filters of the statistics, each one a chip that removes it, and the button that opens the
 * filters dialog. [players] and [tournaments] give the names and the choices. [slotName] is the name of the seat
 * of [StatisticsFilters.slotId]. [opponents] are the EMA numbers of the players who shared a table with the person of
 * [StatisticsFilters.emaId]: they come first in the list of the players to compare with. See [opponentsOf].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StatisticsFilterBar(
    filters: StatisticsFilters,
    onChange: (StatisticsFilters) -> Unit,
    players: List<Player>,
    tournaments: List<Tournament>,
    modifier: Modifier = Modifier,
    slotName: String? = null,
    maxWidth: Dp = 720.dp,
    opponents: Set<String> = emptySet(),
) {
    var editing by remember { mutableStateOf(false) }
    val chips = filterChips(filters, players, tournaments, slotName)
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        FlowRow(
            Modifier.widthIn(max = maxWidth).fillMaxWidth().padding(horizontal = MtsTheme.spacing.lg, vertical = MtsTheme.spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(MtsTheme.spacing.sm, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(MtsTheme.spacing.xs),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            if (chips.isEmpty()) {
                Text(
                    stringResource(Res.string.stats_filter_everything),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            chips.forEach { chip -> FilterChip(chip) { onChange(chip.remove(filters)) } }
            DebouncedOutlinedButton(onClick = { editing = true }) {
                Icon(Icons.Default.FilterList, contentDescription = null, Modifier.size(18.dp))
                Spacer(Modifier.width(MtsTheme.spacing.sm))
                Text(stringResource(Res.string.stats_filters), maxLines = 1)
            }
        }
    }
    if (editing) {
        StatisticsFiltersDialog(
            filters = filters,
            players = players,
            tournaments = tournaments,
            opponents = opponents,
            onApply = {
                editing = false
                onChange(it)
            },
            onDismiss = { editing = false },
        )
    }
}

private class Chip(
    val label: String,
    val icon: @Composable () -> Unit,
    val remove: (StatisticsFilters) -> StatisticsFilters,
)

@Composable
private fun filterChips(filters: StatisticsFilters, players: List<Player>, tournaments: List<Tournament>, slotName: String?): List<Chip> {
    val trophy: @Composable () -> Unit = { Icon(Icons.Default.EmojiEvents, contentDescription = null, Modifier.size(18.dp)) }
    val calendar: @Composable () -> Unit = { Icon(Icons.Default.CalendarMonth, contentDescription = null, Modifier.size(18.dp)) }
    return buildList {
        filters.emaId?.let { ema ->
            val player = players.firstOrNull { it.emaId == ema }
            add(Chip(player?.displayName ?: "EMA $ema", { CountryFlag(player?.country.orEmpty(), width = 20.dp) }) { it.copy(emaId = null, rivalEmaId = null) })
        }
        filters.rivalEmaId?.takeIf { filters.emaId != null }?.let { ema ->
            val rival = players.firstOrNull { it.emaId == ema }
            val name = rival?.displayName ?: "EMA $ema"
            add(Chip(stringResource(Res.string.stats_chip_rival, name), { CountryFlag(rival?.country.orEmpty(), width = 20.dp) }) { it.copy(rivalEmaId = null, together = false) })
        }
        if (filters.emaId == null && filters.slotId != null) {
            add(Chip(slotName ?: "#${filters.slotId}", { CountryFlag("", width = 20.dp) }) { it.copy(slotId = null) })
        }
        filters.tournamentId?.let { id ->
            val name = tournaments.firstOrNull { it.id == id }?.name ?: id
            add(Chip(name, trophy) { it.copy(tournamentId = null, slotId = null, roundId = null, tableId = null) })
        }
        if (filters.emaId == null && filters.roundId != null && filters.tableId != null) {
            val table: @Composable () -> Unit = { Icon(Icons.Default.GridView, contentDescription = null, Modifier.size(18.dp)) }
            add(Chip(stringResource(Res.string.stats_chip_table, filters.tableId, filters.roundId), table) { it.copy(roundId = null, tableId = null) })
        }
        filters.tournamentCountry?.let { code ->
            add(Chip(stringResource(Res.string.stats_chip_tournaments_in, countryDisplayName(code)), { CountryFlag(code, width = 20.dp) }) { it.copy(tournamentCountry = null) })
        }
        if (filters.emaId == null) {
            filters.playerCountry?.let { code ->
                val name = if (code.isEmpty()) stringResource(Res.string.no_country) else countryDisplayName(code)
                add(Chip(stringResource(Res.string.stats_chip_players_from, name), { CountryFlag(code, width = 20.dp) }) { it.copy(playerCountry = null) })
            }
        }
        val from = filters.fromYear
        val to = filters.toYear
        when {
            from != null && to != null -> add(
                Chip(if (from == to) from.toString() else stringResource(Res.string.stats_chip_years, from, to), calendar) { it.copy(fromYear = null, toYear = null) },
            )
            from != null -> add(Chip(stringResource(Res.string.stats_chip_from_year, from), calendar) { it.copy(fromYear = null) })
            to != null -> add(Chip(stringResource(Res.string.stats_chip_to_year, to), calendar) { it.copy(toYear = null) })
        }
    }
}

@Composable
private fun FilterChip(chip: Chip, onRemove: () -> Unit) {
    val description = stringResource(Res.string.stats_chip_remove, chip.label)
    InputChip(
        selected = true,
        onClick = onRemove,
        label = { Text(chip.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        modifier = Modifier.widthIn(max = 320.dp).semantics { contentDescription = description },
        leadingIcon = chip.icon,
        trailingIcon = { Icon(Icons.Default.Close, contentDescription = null, Modifier.size(InputChipDefaults.IconSize)) },
    )
}

/** The dialog that chooses the filters. Nothing changes until Apply. */
@Composable
internal fun StatisticsFiltersDialog(
    filters: StatisticsFilters,
    players: List<Player>,
    tournaments: List<Tournament>,
    onApply: (StatisticsFilters) -> Unit,
    onDismiss: () -> Unit,
    /** The opponents of the person of [filters]. See [StatisticsFilterBar]. */
    opponents: Set<String> = emptySet(),
) {
    var draft by remember { mutableStateOf(filters) }
    val anyPlayer = stringResource(Res.string.stats_filter_any_player)
    val anyCountry = stringResource(Res.string.stats_filter_any_country)
    val anyYear = stringResource(Res.string.stats_filter_any_year)
    val allTournaments = stringResource(Res.string.stats_all_tournaments)
    val noCountry = stringResource(Res.string.no_country)
    val anyRival = stringResource(Res.string.stats_filter_any_rival)
    // There are thousands of players. Their options are built once, not at each change of the dialog.
    val sortedOptions = remember(players) {
        players.sortedBy { it.displayName.lowercase() }.map { player ->
            player to SelectOption(
                player.emaId,
                "${player.displayName} (${player.emaId})",
                searchText = player.emaId,
                leadingIcon = { CountryFlag(player.country, width = 20.dp) },
            )
        }
    }
    // A chosen country of the players narrows the list of the players. The chosen ones always stay in their lists.
    val countryOfPlayers = draft.playerCountry
    val playerOptions = remember(sortedOptions, countryOfPlayers, draft.emaId, anyPlayer) {
        listOf(SelectOption(ANY, anyPlayer)) +
            sortedOptions.filter { (player, _) -> countryOfPlayers == null || player.country.equals(countryOfPlayers, ignoreCase = true) || player.emaId == draft.emaId }.map { it.second }
    }
    // The person can be compared with any other player with an EMA number. The players who shared a table with the
    // person come first. They are known only for the person on the screen.
    val rivalOptions = remember(sortedOptions, opponents, draft.emaId, filters.emaId, anyRival) {
        val known = if (draft.emaId == filters.emaId) opponents else emptySet()
        listOf(SelectOption(ANY, anyRival)) +
            sortedOptions.filter { (player, _) -> player.emaId.isNotBlank() && player.emaId != draft.emaId }
                .sortedBy { (player, _) -> player.emaId !in known }
                .map { it.second }
    }
    val showRival = draft.emaId != null && rivalOptions.size > 1
    // The country and the years of the tournaments narrow the list of the tournaments.
    val tournamentOptions = listOf(SelectOption(ANY, allTournaments)) +
        tournaments.filter { it.id == draft.tournamentId || draft.copy(tournamentId = null).keeps(it) }
            .sortedByDescending { it.eventStartDate.orEmpty() }
            .map { SelectOption(it.id, it.name, leadingIcon = { CountryFlag(it.hostCountry, width = 20.dp) }) }
    val tournamentCountries = tournaments.map { it.hostCountry.uppercase() }.filter { it.isNotBlank() }.distinct()
    val tournamentCountryOptions = listOf(SelectOption(ANY, anyCountry)) +
        tournamentCountries.map { code -> SelectOption(code, countryDisplayName(code), searchText = code, leadingIcon = { CountryFlag(code, width = 20.dp) }) }
            .sortedBy { it.label.lowercase() }
    val playerCountries = remember(players) { players.map { it.country.uppercase() }.filter { it.isNotBlank() }.distinct() }
    val playerCountryOptions = listOf(SelectOption(ANY, anyCountry), SelectOption(NO_COUNTRY_KEY, noCountry, leadingIcon = { CountryFlag("", width = 20.dp) })) +
        playerCountries.map { code -> SelectOption(code, countryDisplayName(code), searchText = code, leadingIcon = { CountryFlag(code, width = 20.dp) }) }
            .sortedBy { it.label.lowercase() }
    val years = tournaments.mapNotNull { it.eventStartDate?.take(4)?.toIntOrNull() }.distinct().sortedDescending()
    val yearOptions = listOf(SelectOption<Int?>(null, anyYear)) + years.map { SelectOption<Int?>(it, it.toString()) }

    TrackedDialog("StatisticsFilters") {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { MtsDialogTitle(stringResource(Res.string.stats_filters_title)) },
            text = {
                Column(
                    Modifier.fillMaxWidth().verticalScrollWithIndicators(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(MtsTheme.spacing.md),
                ) {
                    SelectField(
                        stringResource(Res.string.stats_filter_player),
                        playerOptions,
                        draft.emaId ?: ANY,
                        { ema -> draft = if (ema == ANY) draft.copy(emaId = null, rivalEmaId = null, together = false) else draft.withPerson(ema) },
                        Modifier.fillMaxWidth(),
                        // The menu composes all its items. The search finds the players that it does not show.
                        maxShown = SELECT_MAX_SHOWN,
                    )
                    if (showRival) {
                        SelectField(
                            stringResource(Res.string.stats_filter_rival),
                            rivalOptions,
                            draft.rivalEmaId ?: ANY,
                            { ema -> draft = draft.copy(rivalEmaId = ema.takeIf { it != ANY }, together = false) },
                            Modifier.fillMaxWidth(),
                            maxShown = SELECT_MAX_SHOWN,
                        )
                    }
                    SelectField(
                        stringResource(Res.string.stats_filter_tournament),
                        tournamentOptions,
                        draft.tournamentId ?: ANY,
                        { id -> draft = draft.copy(tournamentId = id.takeIf { it != ANY }, slotId = null, roundId = null, tableId = null) },
                        Modifier.fillMaxWidth(),
                    )
                    SelectField(
                        stringResource(Res.string.stats_filter_tournament_country),
                        tournamentCountryOptions,
                        draft.tournamentCountry ?: ANY,
                        { code -> draft = draft.copy(tournamentCountry = code.takeIf { it != ANY }) },
                        Modifier.fillMaxWidth(),
                    )
                    SelectField(
                        stringResource(Res.string.stats_filter_player_country),
                        playerCountryOptions,
                        when (val code = draft.playerCountry) {
                            null -> ANY
                            "" -> NO_COUNTRY_KEY
                            else -> code
                        },
                        { code ->
                            draft = draft.copy(
                                playerCountry = when (code) {
                                    ANY -> null
                                    NO_COUNTRY_KEY -> ""
                                    else -> code
                                },
                            )
                        },
                        Modifier.fillMaxWidth(),
                        // With a person the country of the players does not apply.
                        enabled = draft.emaId == null,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(MtsTheme.spacing.sm)) {
                        SelectField(
                            stringResource(Res.string.stats_filter_from_year),
                            yearOptions,
                            draft.fromYear,
                            { year -> draft = draft.copy(fromYear = year, toYear = draft.toYear?.takeIf { year == null || it >= year }) },
                            Modifier.weight(1f),
                        )
                        SelectField(
                            stringResource(Res.string.stats_filter_to_year),
                            yearOptions,
                            draft.toYear,
                            { year -> draft = draft.copy(toYear = year, fromYear = draft.fromYear?.takeIf { year == null || it <= year }) },
                            Modifier.weight(1f),
                        )
                    }
                }
            },
            confirmButton = {
                MtsDialogButtons {
                    DebouncedButton(onClick = { onApply(draft) }) { Text(stringResource(Res.string.stats_filter_apply)) }
                    DebouncedTextButton(onClick = { draft = StatisticsFilters() }) { Text(stringResource(Res.string.stats_filter_clear)) }
                }
            },
        )
    }
}

/** The EMA numbers of the players who shared a table with the person of [data]. Guests without EMA are left out. */
fun opponentsOf(data: PersonStatisticsData?): Set<String> =
    data?.overall?.results.orEmpty().flatMap { it.rivals }.map { it.opponent }.filter { '#' !in it && it != data?.emaId }.toSet()
