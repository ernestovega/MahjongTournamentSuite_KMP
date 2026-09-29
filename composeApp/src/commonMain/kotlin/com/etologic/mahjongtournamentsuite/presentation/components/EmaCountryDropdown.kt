package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.domain.model.Country

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
    code.trim().uppercase().takeIf { it.length == 2 }
        ?: EmaHostCountries.firstOrNull { it.code == code.trim().uppercase() }?.flagCode

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun EmaCountryDropdown(
    selectedCode: String,
    onCountrySelected: (String) -> Unit,
    countries: List<Country> = emptyList(),
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    enabled: Boolean = true,
    isError: Boolean = false,
    errorMessage: String? = null,
    label: String = "Host country",
) {
    var expanded by remember { mutableStateOf(false) }
    var hasFocus by remember { mutableStateOf(false) }
    var moveFocusToMenu by remember { mutableStateOf(false) }
    val anchorFocusRequester = remember { FocusRequester() }
    val menuFocusRequester = remember { FocusRequester() }
    val options = remember(countries) {
        countries
            .mapNotNull { country ->
                val code = country.code.trim().uppercase()
                val name = country.name.trim()
                if (code.length == 2 && name.isNotEmpty()) {
                    EmaCountryOption(code = code, name = name, flagCode = code)
                } else {
                    null
                }
            }
            .distinctBy(EmaCountryOption::code)
            .sortedBy(EmaCountryOption::name)
            .ifEmpty { EmaHostCountries }
    }
    val normalizedSelectedCode = selectedCode.trim().uppercase()
    val selected = options.firstOrNull { it.code == normalizedSelectedCode }
        ?: EmaHostCountries.firstOrNull { it.code == normalizedSelectedCode }
    val selectedName = selected?.name ?: selectedCode
    var input by remember { mutableStateOf(TextFieldValue(selectedName)) }
    val hasFullSelection = input.selection.start == 0 && input.selection.end == input.text.length
    val query = if (hasFocus && !hasFullSelection) input.text.trim() else ""
    val filteredOptions = remember(options, query) {
        if (query.isEmpty()) {
            options
        } else {
            options.filter { option ->
                option.name.contains(query, ignoreCase = true) ||
                    option.code.contains(query, ignoreCase = true)
            }
        }
    }
    val selectedOptionIndex = filteredOptions.indexOfFirst { it.code == normalizedSelectedCode }
        .takeIf { it >= 0 }
        ?: 0
    var activeOptionIndex by remember(filteredOptions) { mutableStateOf(selectedOptionIndex) }

    fun closeMenu() {
        expanded = false
        moveFocusToMenu = false
    }

    fun selectOption(index: Int) {
        val option = filteredOptions.getOrNull(index) ?: return
        input = TextFieldValue(option.name)
        closeMenu()
        onCountrySelected(option.code)
        anchorFocusRequester.requestFocus()
    }

    fun openMenu(moveFocus: Boolean) {
        if (!enabled || filteredOptions.isEmpty()) return
        activeOptionIndex = selectedOptionIndex.coerceIn(0, filteredOptions.lastIndex)
        expanded = true
        moveFocusToMenu = moveFocus
    }

    fun moveActiveOption(delta: Int) {
        if (filteredOptions.isEmpty()) return
        activeOptionIndex = (activeOptionIndex + delta).coerceIn(0, filteredOptions.lastIndex)
    }

    LaunchedEffect(selectedName) {
        if (!hasFocus) input = TextFieldValue(selectedName)
    }

    LaunchedEffect(expanded, moveFocusToMenu) {
        if (expanded && moveFocusToMenu) {
            menuFocusRequester.requestFocus()
            moveFocusToMenu = false
        }
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { shouldExpand ->
            if (enabled) {
                expanded = shouldExpand
                moveFocusToMenu = false
            }
        },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = input,
            onValueChange = {
                input = it
                expanded = enabled
            },
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
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
                .then(focusRequester?.let(Modifier::focusRequester) ?: Modifier)
                .focusRequester(anchorFocusRequester)
                .onFocusChanged { focusState ->
                    if (focusState.isFocused && !hasFocus) {
                        input = TextFieldValue(
                            text = selectedName,
                            selection = TextRange(0, selectedName.length),
                        )
                    } else if (!focusState.isFocused && hasFocus) {
                        input = TextFieldValue(selectedName)
                    }
                    hasFocus = focusState.isFocused
                }
                .onPreviewKeyEvent { event: KeyEvent ->
                    if (!enabled || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionDown -> {
                            if (expanded) moveActiveOption(1) else openMenu(moveFocus = true)
                            if (expanded) moveFocusToMenu = true
                            true
                        }

                        Key.DirectionUp -> {
                            if (expanded) moveActiveOption(-1) else openMenu(moveFocus = true)
                            if (expanded) moveFocusToMenu = true
                            true
                        }

                        Key.Enter, Key.NumPadEnter, Key.Spacebar -> {
                            if (expanded) selectOption(activeOptionIndex) else openMenu(moveFocus = true)
                            true
                        }

                        Key.Escape -> {
                            if (expanded) {
                                closeMenu()
                                true
                            } else {
                                false
                            }
                        }

                        else -> false
                    }
                }
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = ::closeMenu,
        ) {
            Column(
                modifier = Modifier
                    .focusRequester(menuFocusRequester)
                    .focusable()
                    .onPreviewKeyEvent { event ->
                        if (!enabled || !expanded || event.type != KeyEventType.KeyDown) {
                            return@onPreviewKeyEvent false
                        }
                        when (event.key) {
                            Key.DirectionDown -> {
                                moveActiveOption(1)
                                true
                            }

                            Key.DirectionUp -> {
                                moveActiveOption(-1)
                                true
                            }

                            Key.Enter, Key.NumPadEnter, Key.Spacebar -> {
                                selectOption(activeOptionIndex)
                                true
                            }

                            Key.Escape -> {
                                closeMenu()
                                anchorFocusRequester.requestFocus()
                                true
                            }

                            else -> false
                        }
                    },
            ) {
                filteredOptions.forEachIndexed { index, option ->
                    DropdownMenuItem(
                        modifier = Modifier.background(
                            if (index == activeOptionIndex) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                Color.Transparent
                            },
                        ),
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
                            activeOptionIndex = index
                            selectOption(index)
                        },
                    )
                }
                if (filteredOptions.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text("No matching countries") },
                        onClick = {},
                        enabled = false,
                    )
                }
            }
        }
    }
}
