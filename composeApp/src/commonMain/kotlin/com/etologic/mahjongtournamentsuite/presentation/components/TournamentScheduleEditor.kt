package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.DateRange
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

data class RoundScheduleEditorRow(
    val roundId: Int,
    val date: String = "",
    val startTime: String = "",
)

data class AgendaItemEditorRow(
    val title: String = "",
    val date: String = "",
    val startTime: String = "",
    val endTime: String = "",
)

private enum class SchedulePickerField {
    ROUND_DATE,
    ROUND_START_TIME,
    AGENDA_DATE,
    AGENDA_START_TIME,
    AGENDA_END_TIME,
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
        Text("Tournament schedule", style = MaterialTheme.typography.titleSmall)
        Text(
            "Dates use DD/MM/YYYY. Times use the tournament's local time in HH:mm format.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )

        if (roundRows.isEmpty()) {
            Text(
                "Enter a valid number of rounds to configure their start times.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            roundRows.forEachIndexed { index, row ->
                val datePickerFocusRequester = remember(row.roundId) { FocusRequester() }
                val timePickerFocusRequester = remember(row.roundId) { FocusRequester() }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        "Round ${row.roundId}",
                        modifier = Modifier.width(72.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedTextField(
                        value = row.date,
                        onValueChange = { value ->
                            onRoundRowsChange(
                                roundRows.replaceAt(index, row.copy(date = value.take(10))),
                            )
                        },
                        modifier = Modifier.weight(1f),
                        label = { Text("Date") },
                        placeholder = { Text("DD/MM/YYYY") },
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
                                    contentDescription = "Choose round ${row.roundId} date",
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
                                roundRows.replaceAt(index, row.copy(startTime = value.take(5))),
                            )
                        },
                        modifier = Modifier.width(128.dp),
                        label = { Text("Start") },
                        placeholder = { Text("HH:mm") },
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
                                    contentDescription = "Choose round ${row.roundId} start time",
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

        Text("Player agenda", style = MaterialTheme.typography.titleSmall)
        if (agendaRows.isEmpty()) {
            Text(
                "No activities have been added.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        agendaRows.forEachIndexed { index, row ->
            val datePickerFocusRequester = remember(index) { FocusRequester() }
            val startTimePickerFocusRequester = remember(index) { FocusRequester() }
            val endTimePickerFocusRequester = remember(index) { FocusRequester() }
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    OutlinedTextField(
                        value = row.title,
                        onValueChange = { value ->
                            onAgendaRowsChange(agendaRows.replaceAt(index, row.copy(title = value)))
                        },
                        modifier = Modifier.weight(1f),
                        label = { Text("Activity") },
                        placeholder = { Text("Registration, lunch, break, awards…") },
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
                        Text("Remove")
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
                                agendaRows.replaceAt(index, row.copy(date = value.take(10))),
                            )
                        },
                        modifier = Modifier.weight(1f),
                        label = { Text("Date") },
                        placeholder = { Text("DD/MM/YYYY") },
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
                                    contentDescription = "Choose activity ${index + 1} date",
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
                                agendaRows.replaceAt(index, row.copy(startTime = value.take(5))),
                            )
                        },
                        modifier = Modifier.width(128.dp),
                        label = { Text("Start") },
                        placeholder = { Text("HH:mm") },
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
                                    contentDescription = "Choose activity ${index + 1} start time",
                                )
                            }
                        },
                        singleLine = true,
                        enabled = enabled,
                        isError = row.title.isNotBlank() && row.startTime.isNotBlank() &&
                            row.startTime.toTournamentTimeOrNull() == null,
                    )
                    OutlinedTextField(
                        value = row.endTime,
                        onValueChange = { value ->
                            onAgendaRowsChange(
                                agendaRows.replaceAt(index, row.copy(endTime = value.take(5))),
                            )
                        },
                        modifier = Modifier.width(128.dp),
                        label = { Text("End") },
                        placeholder = { Text("HH:mm") },
                        trailingIcon = {
                            FocusedIconButton(
                                enabled = enabled,
                                focusRequester = endTimePickerFocusRequester,
                                onClick = {
                                    pickerRequest = SchedulePickerRequest(
                                        field = SchedulePickerField.AGENDA_END_TIME,
                                        rowIndex = index,
                                        returnFocusRequester = endTimePickerFocusRequester,
                                    )
                                },
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccessTime,
                                    contentDescription = "Choose activity ${index + 1} end time",
                                )
                            }
                        },
                        singleLine = true,
                        enabled = enabled,
                        isError = row.title.isNotBlank() && row.endTime.isNotBlank() &&
                            row.endTime.toTournamentTimeOrNull() == null,
                    )
                }
            }
        }
        FocusedButton(
            enabled = enabled,
            onClick = { onAgendaRowsChange(agendaRows + AgendaItemEditorRow()) },
        ) {
            Text("Add activity")
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
                minimumDisplayDate = minimumDisplayDate,
                maximumDisplayDate = maximumDisplayDate,
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
            SchedulePickerField.AGENDA_END_TIME,
            -> TournamentTimePickerDialog(
                selectedTime = when (request.field) {
                    SchedulePickerField.ROUND_START_TIME ->
                        roundRows.getOrNull(request.rowIndex)?.startTime.orEmpty()
                    SchedulePickerField.AGENDA_START_TIME ->
                        agendaRows.getOrNull(request.rowIndex)?.startTime.orEmpty()
                    else -> agendaRows.getOrNull(request.rowIndex)?.endTime.orEmpty()
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
                        SchedulePickerField.AGENDA_END_TIME ->
                            agendaRows.getOrNull(request.rowIndex)?.let { row ->
                                onAgendaRowsChange(
                                    agendaRows.replaceAt(
                                        request.rowIndex,
                                        row.copy(endTime = selectedTime),
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
    endTime = endTime.orEmpty(),
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
            endTime = row.endTime.toTournamentTimeOrNull(),
        )
    }

fun tournamentScheduleEditorError(
    roundRows: List<RoundScheduleEditorRow>,
    agendaRows: List<AgendaItemEditorRow>,
    eventStartDate: String,
    eventEndDate: String,
): String? {
    roundRows.forEach { row ->
        val date = row.date.trim()
        val isoDate = date.toIsoTournamentDateOrNull()
        if (date.isNotEmpty() && isoDate == null) return "Round ${row.roundId} has an invalid date."
        if (isoDate != null && isoDate !in eventStartDate..eventEndDate) {
            return "Round ${row.roundId} must be inside the tournament date range."
        }
        if (row.startTime.isNotBlank() && row.startTime.toTournamentTimeOrNull() == null) {
            return "Round ${row.roundId} has an invalid start time."
        }
    }

    agendaRows.forEachIndexed { index, row ->
        if (row.title.isBlank()) return@forEachIndexed
        val date = row.date.trim()
        val isoDate = date.toIsoTournamentDateOrNull()
        if (date.isNotEmpty() && isoDate == null) return "Agenda item ${index + 1} has an invalid date."
        if (isoDate != null && isoDate !in eventStartDate..eventEndDate) {
            return "Agenda item ${index + 1} must be inside the tournament date range."
        }
        val startTime = row.startTime.toTournamentTimeOrNull()
        val endTime = row.endTime.toTournamentTimeOrNull()
        if (row.startTime.isNotBlank() && startTime == null) return "Agenda item ${index + 1} has an invalid start time."
        if (row.endTime.isNotBlank() && endTime == null) return "Agenda item ${index + 1} has an invalid end time."
        if (startTime != null && endTime != null && endTime < startTime) {
            return "Agenda item ${index + 1} must not end before it starts."
        }
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
