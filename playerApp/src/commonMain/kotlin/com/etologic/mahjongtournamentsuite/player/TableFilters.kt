package com.etologic.mahjongtournamentsuite.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.presentation.components.SelectField
import com.etologic.mahjongtournamentsuite.presentation.util.normalizeSearchText
import mahjongtournamentsuite.playerapp.generated.resources.Res
import mahjongtournamentsuite.playerapp.generated.resources.filter_all_rounds
import mahjongtournamentsuite.playerapp.generated.resources.filter_all_statuses
import mahjongtournamentsuite.playerapp.generated.resources.filter_clear
import mahjongtournamentsuite.playerapp.generated.resources.filter_round
import mahjongtournamentsuite.playerapp.generated.resources.filter_status
import mahjongtournamentsuite.playerapp.generated.resources.filter_table_search
import mahjongtournamentsuite.playerapp.generated.resources.round_n
import mahjongtournamentsuite.playerapp.generated.resources.status_finished
import mahjongtournamentsuite.playerapp.generated.resources.table_status_not_started
import mahjongtournamentsuite.playerapp.generated.resources.table_status_playing
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import com.etologic.mahjongtournamentsuite.presentation.components.DebouncedIconButton as IconButton

/** The state of a table, as the color of its card shows it. */
internal enum class TableStatus(val label: StringResource) {
    NOT_STARTED(Res.string.table_status_not_started),
    PLAYING(Res.string.table_status_playing),
    FINISHED(Res.string.status_finished),
}

internal val PlayerTable.status: TableStatus
    get() = when {
        isComplete -> TableStatus.FINISHED
        doneHands > 0 -> TableStatus.PLAYING
        else -> TableStatus.NOT_STARTED
    }

/**
 * The filters of a table list. They combine: a table shows when it passes all of them.
 * The search takes a table number or parts of the names of its players. A null value lets every table through.
 */
internal data class TableFilter(
    val query: String = "",
    val round: Int? = null,
    val status: TableStatus? = null,
) {
    val isActive: Boolean get() = this != TableFilter()

    /** A short text for the saved state of the screen. */
    fun encode(): String = listOf(query.replace('|', ' '), round?.toString().orEmpty(), status?.name.orEmpty()).joinToString("|")

    companion object {
        fun decode(text: String?): TableFilter {
            val parts = text?.split('|') ?: return TableFilter()
            if (parts.size != 3) return TableFilter()
            return TableFilter(
                query = parts[0],
                round = parts[1].toIntOrNull(),
                status = TableStatus.entries.firstOrNull { it.name == parts[2] },
            )
        }
    }
}

/**
 * True when the table passes every filter. A number in the search is a table number. Every other word must be in the
 * name of one of the players of the table, without case or accents. [names] gives the names of the players of a table.
 */
internal fun TableFilter.accepts(table: PlayerTable, names: List<String>): Boolean {
    if (round != null && table.roundId != round) return false
    if (status != null && table.status != status) return false
    val words = query.split(' ', '\t').map(::normalizeSearchText).filter { it.isNotEmpty() }
    if (words.isEmpty()) return true
    val text = names.joinToString(" ") { normalizeSearchText(it) }
    return words.all { word -> word.toIntOrNull()?.let { it == table.tableId } ?: (word in text) }
}

/** The tables of [tables] that pass the filter. */
internal fun TableFilter.apply(view: PlayerTournamentView, tables: List<PlayerTable>): List<PlayerTable> {
    if (!isActive) return tables
    return tables.filter { table -> accepts(table, table.playerSlots().map(view::nameOf)) }
}

/** The search field and the round and status dropdowns above a long table list. */
@Composable
internal fun TableFilterBar(
    filter: TableFilter,
    onChange: (TableFilter) -> Unit,
    rounds: List<Int>,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = filter.query,
            onValueChange = { onChange(filter.copy(query = it)) },
            modifier = Modifier.fillMaxWidth().testTag(TABLE_SEARCH_TAG),
            singleLine = true,
            placeholder = { Text(stringResource(Res.string.filter_table_search), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = if (!filter.isActive) {
                null
            } else {
                {
                    // The button clears all the filters, so it also shows when only a dropdown is set.
                    IconButton(onClick = { onChange(TableFilter()) }) {
                        Icon(Icons.Default.Clear, contentDescription = stringResource(Res.string.filter_clear))
                    }
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Search),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            val allRounds = stringResource(Res.string.filter_all_rounds)
            val roundOptions = rounds.map { it to stringResource(Res.string.round_n, it) }
            SelectField(
                label = stringResource(Res.string.filter_round),
                options = listOf(0 to allRounds) + roundOptions,
                selected = filter.round ?: 0,
                onSelect = { onChange(filter.copy(round = it.takeIf { round -> round > 0 })) },
                modifier = Modifier.weight(1f),
                compact = false,
            )
            val allStatuses = stringResource(Res.string.filter_all_statuses)
            val statusOptions = TableStatus.entries.map { it.name to stringResource(it.label) }
            SelectField(
                label = stringResource(Res.string.filter_status),
                options = listOf("" to allStatuses) + statusOptions,
                selected = filter.status?.name.orEmpty(),
                onSelect = { name -> onChange(filter.copy(status = TableStatus.entries.firstOrNull { it.name == name })) },
                modifier = Modifier.weight(1f),
                compact = false,
            )
        }
    }
}

/** The test tag of the search field of the table list. */
internal const val TABLE_SEARCH_TAG = "table-search"
