package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.domain.model.TournamentAgendaItem
import com.etologic.mahjongtournamentsuite.domain.model.TournamentRoundSchedule
import com.etologic.mahjongtournamentsuite.presentation.util.UiText
import mahjongtournamentsuite.adminapp.generated.resources.Res
import mahjongtournamentsuite.adminapp.generated.resources.activity
import mahjongtournamentsuite.adminapp.generated.resources.add_activity
import mahjongtournamentsuite.adminapp.generated.resources.date
import mahjongtournamentsuite.adminapp.generated.resources.dd_mm_yyyy
import mahjongtournamentsuite.adminapp.generated.resources.hh_mm
import mahjongtournamentsuite.adminapp.generated.resources.player_agenda
import mahjongtournamentsuite.adminapp.generated.resources.registration_lunch_break_awards
import mahjongtournamentsuite.adminapp.generated.resources.remove
import mahjongtournamentsuite.adminapp.generated.resources.round_n
import mahjongtournamentsuite.adminapp.generated.resources.schedule_agenda_invalid_date
import mahjongtournamentsuite.adminapp.generated.resources.schedule_agenda_invalid_time
import mahjongtournamentsuite.adminapp.generated.resources.schedule_choose_activity_date
import mahjongtournamentsuite.adminapp.generated.resources.schedule_choose_activity_time
import mahjongtournamentsuite.adminapp.generated.resources.schedule_choose_round_date
import mahjongtournamentsuite.adminapp.generated.resources.schedule_choose_round_time
import mahjongtournamentsuite.adminapp.generated.resources.schedule_enter_rounds
import mahjongtournamentsuite.adminapp.generated.resources.schedule_format_note
import mahjongtournamentsuite.adminapp.generated.resources.schedule_no_activities
import mahjongtournamentsuite.adminapp.generated.resources.schedule_round_invalid_date
import mahjongtournamentsuite.adminapp.generated.resources.schedule_round_invalid_time
import mahjongtournamentsuite.adminapp.generated.resources.schedule_round_outside_range
import mahjongtournamentsuite.adminapp.generated.resources.start
import mahjongtournamentsuite.adminapp.generated.resources.tournament_schedule
import org.jetbrains.compose.resources.stringResource

data class RoundScheduleEditorRow(
    val roundId: Int,
    val date: String = "",
    val startTime: String = "",
)

data class AgendaItemEditorRow(
    val title: String = "",
    val date: String = "",
    val startTime: String = "",
)

private enum class SchedulePickerField {
    ROUND_DATE,
    ROUND_START_TIME,
    AGENDA_DATE,
    AGENDA_START_TIME,
}

private data class SchedulePickerRequest(
    val field: SchedulePickerField,
    val rowIndex: Int,
    val returnFocusRequester: FocusRequester,
)

@Composable
fun TournamentScheduleEditor(
    roundRows: List<RoundScheduleEditorRow>,
    onRoundRowsChange: (List<RoundScheduleEditorRow>) -> Unit,
    agendaRows: List<AgendaItemEditorRow>,
    onAgendaRowsChange: (List<AgendaItemEditorRow>) -> Unit,
    enabled: Boolean,
    minimumDisplayDate: String? = null,
    maximumDisplayDate: String? = null,
    modifier: Modifier = Modifier,
) {
    var pickerRequest by remember { mutableStateOf<SchedulePickerRequest?>(null) }
    var pendingFocusRestore by remember { mutableStateOf<FocusRequester?>(null) }

    fun closePicker(request: SchedulePickerRequest) {
        pendingFocusRestore = request.returnFocusRequester
        pickerRequest = null
    }

    LaunchedEffect(pickerRequest, pendingFocusRestore) {
        if (pickerRequest == null) {
            pendingFocusRestore?.requestFocus()
            pendingFocusRestore = null
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .appFocusGroup(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(Res.string.tournament_schedule), style = MaterialTheme.typography.titleSmall)
        Text(
            stringResource(Res.string.schedule_format_note),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )

        if (roundRows.isEmpty()) {
            Text(
                stringResource(Res.string.schedule_enter_rounds),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            roundRows.forEachIndexed { index, row ->
                if (index > 0) HorizontalDivider()
                val datePickerFocusRequester = remember(row.roundId) { FocusRequester() }
                val timePickerFocusRequester = remember(row.roundId) { FocusRequester() }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(Res.string.round_n, row.roundId),
                        modifier = Modifier.width(72.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedTextField(
                        value = row.date,
                        onValueChange = { value ->
                            onRoundRowsChange(
                                roundRows.replaceAt(index, row.copy(date = value.toDateInput())),
                            )
                        },
                        modifier = Modifier.weight(1f),
                        label = { Text(stringResource(Res.string.date)) },
                        placeholder = { Text(stringResource(Res.string.dd_mm_yyyy)) },
                        trailingIcon = {
                            FocusedIconButton(
                                enabled = enabled,
                                focusRequester = datePickerFocusRequester,
                                onClick = {
                                    pickerRequest = SchedulePickerRequest(
                                        field = SchedulePickerField.ROUND_DATE,
                                        rowIndex = index,
                                        returnFocusRequester = datePickerFocusRequester,
                                    )
                                },
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DateRange,
                                    contentDescription = stringResource(Res.string.schedule_choose_round_date, row.roundId),
                                )
                            }
                        },
                        singleLine = true,
                        enabled = enabled,
                        isError = row.date.isNotBlank() && row.date.toIsoTournamentDateOrNull() == null,
                    )
                    OutlinedTextField(
                        value = row.startTime,
                        onValueChange = { value ->
                            onRoundRowsChange(
                                roundRows.replaceAt(index, row.copy(startTime = value.toTimeInput())),
                            )
                        },
                        modifier = Modifier.width(128.dp),
                        label = { Text(stringResource(Res.string.start)) },
                        placeholder = { Text(stringResource(Res.string.hh_mm)) },
                        trailingIcon = {
                            FocusedIconButton(
                                enabled = enabled,
                                focusRequester = timePickerFocusRequester,
                                onClick = {
                                    pickerRequest = SchedulePickerRequest(
                                        field = SchedulePickerField.ROUND_START_TIME,
                                        rowIndex = index,
                                        returnFocusRequester = timePickerFocusRequester,
                                    )
                                },
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccessTime,
                                    contentDescription = stringResource(Res.string.schedule_choose_round_time, row.roundId),
                                )
                            }
                        },
                        singleLine = true,
                        enabled = enabled,
                        isError = row.startTime.isNotBlank() && row.startTime.toTournamentTimeOrNull() == null,
                    )
                }
            }
        }

        Text(stringResource(Res.string.player_agenda), style = MaterialTheme.typography.titleSmall)
        if (agendaRows.isEmpty()) {
            Text(
                stringResource(Res.string.schedule_no_activities),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        agendaRows.forEachIndexed { index, row ->
            if (index > 0) HorizontalDivider()
            val datePickerFocusRequester = remember(index) { FocusRequester() }
            val startTimePickerFocusRequester = remember(index) { FocusRequester() }
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = row.title,
                        onValueChange = { value ->
                            onAgendaRowsChange(agendaRows.replaceAt(index, row.copy(title = value)))
                        },
                        modifier = Modifier.weight(1f),
                        label = { Text(stringResource(Res.string.activity)) },
                        placeholder = { Text(stringResource(Res.string.registration_lunch_break_awards)) },
                        singleLine = true,
                        enabled = enabled,
                    )
                    FocusedTextButton(
                        enabled = enabled,
                        onClick = {
                            onAgendaRowsChange(
                                agendaRows.filterIndexed { itemIndex, _ -> itemIndex != index },
                            )
                        },
                    ) {
                        Text(stringResource(Res.string.remove))
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    OutlinedTextField(
                        value = row.date,
                        onValueChange = { value ->
                            onAgendaRowsChange(
                                agendaRows.replaceAt(index, row.copy(date = value.toDateInput())),
                            )
                        },
                        modifier = Modifier.weight(1f),
                        label = { Text(stringResource(Res.string.date)) },
                        placeholder = { Text(stringResource(Res.string.dd_mm_yyyy)) },
                        trailingIcon = {
                            FocusedIconButton(
                                enabled = enabled,
                                focusRequester = datePickerFocusRequester,
                                onClick = {
                                    pickerRequest = SchedulePickerRequest(
                                        field = SchedulePickerField.AGENDA_DATE,
                                        rowIndex = index,
                                        returnFocusRequester = datePickerFocusRequester,
                                    )
                                },
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DateRange,
                                    contentDescription = stringResource(Res.string.schedule_choose_activity_date, index + 1),
                                )
                            }
                        },
                        singleLine = true,
                        enabled = enabled,
                        isError = row.title.isNotBlank() && row.date.isNotBlank() &&
                            row.date.toIsoTournamentDateOrNull() == null,
                    )
                    OutlinedTextField(
                        value = row.startTime,
                        onValueChange = { value ->
                            onAgendaRowsChange(
                                agendaRows.replaceAt(index, row.copy(startTime = value.toTimeInput())),
                            )
                        },
                        modifier = Modifier.width(128.dp),
                        label = { Text(stringResource(Res.string.start)) },
                        placeholder = { Text(stringResource(Res.string.hh_mm)) },
                        trailingIcon = {
                            FocusedIconButton(
                                enabled = enabled,
                                focusRequester = startTimePickerFocusRequester,
                                onClick = {
                                    pickerRequest = SchedulePickerRequest(
                                        field = SchedulePickerField.AGENDA_START_TIME,
                                        rowIndex = index,
                                        returnFocusRequester = startTimePickerFocusRequester,
                                    )
                                },
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccessTime,
                                    contentDescription = stringResource(Res.string.schedule_choose_activity_time, index + 1),
                                )
                            }
                        },
                        singleLine = true,
                        enabled = enabled,
                        isError = row.title.isNotBlank() && row.startTime.isNotBlank() &&
                            row.startTime.toTournamentTimeOrNull() == null,
                    )
                }
            }
        }
        FocusedButton(
            enabled = enabled,
            onClick = { onAgendaRowsChange(agendaRows + AgendaItemEditorRow()) },
        ) {
            Text(stringResource(Res.string.add_activity))
        }
    }

    pickerRequest?.let { request ->
        when (request.field) {
            SchedulePickerField.ROUND_DATE,
            SchedulePickerField.AGENDA_DATE,
            -> TournamentDatePickerDialog(
                selectedDisplayDate = when (request.field) {
                    SchedulePickerField.ROUND_DATE -> roundRows.getOrNull(request.rowIndex)?.date.orEmpty()
                    else -> agendaRows.getOrNull(request.rowIndex)?.date.orEmpty()
                },
                // Round dates stay inside the tournament. Agenda items may use any day.
                minimumDisplayDate = minimumDisplayDate.takeIf { request.field == SchedulePickerField.ROUND_DATE },
                maximumDisplayDate = maximumDisplayDate.takeIf { request.field == SchedulePickerField.ROUND_DATE },
                onDismiss = { closePicker(request) },
                onDateSelected = { selectedDate ->
                    when (request.field) {
                        SchedulePickerField.ROUND_DATE -> roundRows.getOrNull(request.rowIndex)?.let { row ->
                            onRoundRowsChange(
                                roundRows.replaceAt(request.rowIndex, row.copy(date = selectedDate)),
                            )
                        }
                        SchedulePickerField.AGENDA_DATE -> agendaRows.getOrNull(request.rowIndex)?.let { row ->
                            onAgendaRowsChange(
                                agendaRows.replaceAt(request.rowIndex, row.copy(date = selectedDate)),
                            )
                        }
                    }
                    closePicker(request)
                },
            )

            SchedulePickerField.ROUND_START_TIME,
            SchedulePickerField.AGENDA_START_TIME,
            -> TournamentTimePickerDialog(
                selectedTime = when (request.field) {
                    SchedulePickerField.ROUND_START_TIME ->
                        roundRows.getOrNull(request.rowIndex)?.startTime.orEmpty()
                    else -> agendaRows.getOrNull(request.rowIndex)?.startTime.orEmpty()
                },
                onDismiss = { closePicker(request) },
                onTimeSelected = { selectedTime ->
                    when (request.field) {
                        SchedulePickerField.ROUND_START_TIME ->
                            roundRows.getOrNull(request.rowIndex)?.let { row ->
                                onRoundRowsChange(
                                    roundRows.replaceAt(
                                        request.rowIndex,
                                        row.copy(startTime = selectedTime),
                                    ),
                                )
                            }
                        SchedulePickerField.AGENDA_START_TIME ->
                            agendaRows.getOrNull(request.rowIndex)?.let { row ->
                                onAgendaRowsChange(
                                    agendaRows.replaceAt(
                                        request.rowIndex,
                                        row.copy(startTime = selectedTime),
                                    ),
                                )
                            }
                    }
                    closePicker(request)
                },
            )
        }
    }
}

fun synchronizeRoundScheduleRows(
    rows: List<RoundScheduleEditorRow>,
    roundCount: Int,
): List<RoundScheduleEditorRow> {
    if (roundCount <= 0) return emptyList()
    val rowsByRound = rows.associateBy(RoundScheduleEditorRow::roundId)
    return (1..roundCount).map { roundId -> rowsByRound[roundId] ?: RoundScheduleEditorRow(roundId) }
}

fun TournamentRoundSchedule.toEditorRow(): RoundScheduleEditorRow = RoundScheduleEditorRow(
    roundId = roundId,
    date = date.toDisplayTournamentDate(),
    startTime = startTime.orEmpty(),
)

fun TournamentAgendaItem.toEditorRow(): AgendaItemEditorRow = AgendaItemEditorRow(
    title = title,
    date = date.toDisplayTournamentDate(),
    startTime = startTime.orEmpty(),
)

fun List<RoundScheduleEditorRow>.toTournamentRoundSchedules(): List<TournamentRoundSchedule> = map { row ->
    TournamentRoundSchedule(
        roundId = row.roundId,
        date = row.date.toIsoTournamentDateOrNull(),
        startTime = row.startTime.toTournamentTimeOrNull(),
    )
}

fun List<AgendaItemEditorRow>.toTournamentAgendaItems(): List<TournamentAgendaItem> =
    filter { it.title.isNotBlank() }.map { row ->
        TournamentAgendaItem(
            title = row.title.trim(),
            date = row.date.toIsoTournamentDateOrNull(),
            startTime = row.startTime.toTournamentTimeOrNull(),
        )
    }

fun tournamentScheduleEditorError(
    roundRows: List<RoundScheduleEditorRow>,
    agendaRows: List<AgendaItemEditorRow>,
    eventStartDate: String,
    eventEndDate: String,
): UiText? {
    roundRows.forEach { row ->
        val date = row.date.trim()
        val isoDate = date.toIsoTournamentDateOrNull()
        if (date.isNotEmpty() && isoDate == null) return UiText.of(Res.string.schedule_round_invalid_date, row.roundId)
        if (isoDate != null && isoDate !in eventStartDate..eventEndDate) {
            return UiText.of(Res.string.schedule_round_outside_range, row.roundId)
        }
        if (row.startTime.isNotBlank() && row.startTime.toTournamentTimeOrNull() == null) {
            return UiText.of(Res.string.schedule_round_invalid_time, row.roundId)
        }
    }

    agendaRows.forEachIndexed { index, row ->
        if (row.title.isBlank()) return@forEachIndexed
        val date = row.date.trim()
        val isoDate = date.toIsoTournamentDateOrNull()
        if (date.isNotEmpty() && isoDate == null) return UiText.of(Res.string.schedule_agenda_invalid_date, index + 1)
        val startTime = row.startTime.toTournamentTimeOrNull()
        if (row.startTime.isNotBlank() && startTime == null) return UiText.of(Res.string.schedule_agenda_invalid_time, index + 1)
    }
    return null
}

private fun String.toTournamentTimeOrNull(): String? {
    val value = trim()
    if (!Regex("^(?:[01]\\d|2[0-3]):[0-5]\\d$").matches(value)) return null
    return value
}

private fun <T> List<T>.replaceAt(index: Int, value: T): List<T> =
    toMutableList().also { it[index] = value }
