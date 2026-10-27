package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.etologic.mahjongtournamentsuite.domain.model.Country
import mahjongtournamentsuite.adminapp.generated.resources.Res
import mahjongtournamentsuite.adminapp.generated.resources.host_country
import org.jetbrains.compose.resources.stringResource

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

/** A field with a menu of countries. The search field of the menu finds a country by its name or its code. */
@Composable
fun EmaCountryDropdown(
    selectedCode: String,
    onCountrySelected: (String) -> Unit,
    countries: List<Country> = emptyList(),
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    enabled: Boolean = true,
    isError: Boolean = false,
    errorMessage: String? = null,
    label: String = stringResource(Res.string.host_country),
    required: Boolean = false,
) {
    val countryNameOf = rememberCountryNames()
    val options = remember(countries, countryNameOf) {
        countries
            .mapNotNull { country ->
                val code = country.code.trim().uppercase()
                val name = country.name.trim()
                if (code.length == 2 && name.isNotEmpty()) {
                    EmaCountryOption(code = code, name = countryNameOf(code, name), flagCode = code)
                } else {
                    null
                }
            }
            .distinctBy(EmaCountryOption::code)
            .sortedBy(EmaCountryOption::name)
            .ifEmpty { EmaHostCountries.map { it.copy(name = countryNameOf(it.flagCode, it.name)) } }
    }
    val normalizedSelectedCode = selectedCode.trim().uppercase()
    val selected = options.firstOrNull { it.code == normalizedSelectedCode }
        ?: EmaHostCountries.firstOrNull { it.code == normalizedSelectedCode }?.let { it.copy(name = countryNameOf(it.flagCode, it.name)) }
    val selectOptions = remember(options) {
        options.map { option ->
            SelectOption(
                key = option.code,
                label = option.name,
                searchText = option.code,
                leadingIcon = { CountryFlag(code = option.flagCode, contentDescription = option.name) },
            )
        }
    }
    SelectField(
        label = label,
        options = selectOptions,
        selected = normalizedSelectedCode,
        onSelect = onCountrySelected,
        modifier = modifier,
        enabled = enabled,
        required = required,
        isError = isError,
        supportingText = errorMessage,
        valueText = selected?.name ?: selectedCode,
        leadingIcon = selected?.let { option -> { CountryFlag(code = option.flagCode, contentDescription = option.name) } },
        fieldModifier = focusRequester?.let(Modifier::focusRequester) ?: Modifier,
    )
}
