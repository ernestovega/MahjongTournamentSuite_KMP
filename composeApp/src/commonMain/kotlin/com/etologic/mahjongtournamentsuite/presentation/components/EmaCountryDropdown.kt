package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private data class EmaCountryOption(
    val code: String,
    val name: String,
    val flagCode: String,
)

private val EmaHostCountries = listOf(
    EmaCountryOption("AUT", "Austria", "AT"),
    EmaCountryOption("BEL", "Belgium", "BE"),
    EmaCountryOption("BLR", "Belarus", "BY"),
    EmaCountryOption("CZE", "Czechia", "CZ"),
    EmaCountryOption("DEN", "Denmark", "DK"),
    EmaCountryOption("ESP", "Spain", "ES"),
    EmaCountryOption("FIN", "Finland", "FI"),
    EmaCountryOption("FRA", "France", "FR"),
    EmaCountryOption("GBR", "United Kingdom", "GB"),
    EmaCountryOption("GER", "Germany", "DE"),
    EmaCountryOption("HUN", "Hungary", "HU"),
    EmaCountryOption("IRL", "Ireland", "IE"),
    EmaCountryOption("ITA", "Italy", "IT"),
    EmaCountryOption("LAT", "Latvia", "LV"),
    EmaCountryOption("NED", "Netherlands", "NL"),
    EmaCountryOption("NOR", "Norway", "NO"),
    EmaCountryOption("POL", "Poland", "PL"),
    EmaCountryOption("POR", "Portugal", "PT"),
    EmaCountryOption("ROU", "Romania", "RO"),
    EmaCountryOption("RUS", "Russia", "RU"),
    EmaCountryOption("SUI", "Switzerland", "CH"),
    EmaCountryOption("SVK", "Slovakia", "SK"),
    EmaCountryOption("SWE", "Sweden", "SE"),
    EmaCountryOption("UKR", "Ukraine", "UA"),
)

fun emaCountryFlagCode(code: String): String? =
    EmaHostCountries.firstOrNull { it.code == code.trim().uppercase() }?.flagCode

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun EmaCountryDropdown(
    selectedCode: String,
    onCountrySelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false,
    errorMessage: String? = null,
    label: String = "Host country",
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = EmaHostCountries.firstOrNull { it.code == selectedCode.trim().uppercase() }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = selected?.name ?: selectedCode,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            leadingIcon = selected?.let { option ->
                { CountryFlag(code = option.flagCode, contentDescription = option.name) }
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            enabled = enabled,
            isError = isError,
            supportingText = errorMessage?.let { message -> { Text(message) } },
            singleLine = true,
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            EmaHostCountries.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CountryFlag(code = option.flagCode, contentDescription = option.name)
                            Text(option.name)
                        }
                    },
                    onClick = {
                        expanded = false
                        onCountrySelected(option.code)
                    },
                )
            }
        }
    }
}
