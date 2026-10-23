package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import mahjongtournamentsuite.ui.generated.resources.Res
import mahjongtournamentsuite.ui.generated.resources.action_cancel
import mahjongtournamentsuite.ui.generated.resources.action_ok
import mahjongtournamentsuite.ui.generated.resources.date_field_clear
import mahjongtournamentsuite.ui.generated.resources.date_field_pick
import org.jetbrains.compose.resources.stringResource

/**
 * A read-only field for a date in the form YYYY-MM-DD. The calendar button opens a date picker.
 * An empty [value] means no date. The clear button sets it empty.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = {},
        readOnly = true,
        singleLine = true,
        label = { Text(label) },
        trailingIcon = {
            Row {
                if (value.isNotEmpty()) {
                    IconButton(onClick = { onChange("") }) { Icon(Icons.Default.Clear, contentDescription = stringResource(Res.string.date_field_clear, label)) }
                }
                IconButton(onClick = { open = true }) { Icon(Icons.Default.CalendarMonth, contentDescription = stringResource(Res.string.date_field_pick, label)) }
            }
        },
        modifier = modifier,
    )
    if (open) {
        val state = rememberDatePickerState(initialSelectedDateMillis = isoDateToEpochMillis(value))
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(
                    enabled = state.selectedDateMillis != null,
                    onClick = {
                        state.selectedDateMillis?.let { onChange(epochMillisToIsoDate(it)) }
                        open = false
                    },
                ) { Text(stringResource(Res.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(Res.string.action_cancel)) } },
        ) { DatePicker(state = state) }
    }
}

private const val MILLIS_PER_DAY = 86_400_000L

/** The UTC start of the day in milliseconds, or null when [value] is not a date. */
internal fun isoDateToEpochMillis(value: String): Long? {
    val parts = value.split("-").mapNotNull { it.toIntOrNull() }
    if (parts.size != 3) return null
    val (year, month, day) = parts
    if (month !in 1..12 || day !in 1..31) return null
    val y = if (month <= 2) year - 1 else year
    val era = y.floorDiv(400)
    val yearOfEra = y - era * 400
    val dayOfYear = (153 * (month + (if (month > 2) -3 else 9)) + 2) / 5 + day - 1
    val dayOfEra = yearOfEra * 365 + yearOfEra / 4 - yearOfEra / 100 + dayOfYear
    return (era * 146_097L + dayOfEra - 719_468L) * MILLIS_PER_DAY
}

internal fun epochMillisToIsoDate(millis: Long): String {
    val z = millis.floorDiv(MILLIS_PER_DAY) + 719_468L
    val era = z.floorDiv(146_097L)
    val dayOfEra = z - era * 146_097L
    val yearOfEra = (dayOfEra - dayOfEra / 1460 + dayOfEra / 36_524 - dayOfEra / 146_096) / 365
    val dayOfYear = dayOfEra - (365 * yearOfEra + yearOfEra / 4 - yearOfEra / 100)
    val monthPart = (5 * dayOfYear + 2) / 153
    val day = dayOfYear - (153 * monthPart + 2) / 5 + 1
    val month = monthPart + if (monthPart < 10) 3 else -9
    val year = yearOfEra + era * 400 + if (month <= 2) 1 else 0
    return "${year.toString().padStart(4, '0')}-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"
}
