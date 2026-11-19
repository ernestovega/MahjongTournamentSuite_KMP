package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.etologic.mahjongtournamentsuite.presentation.util.normalizeSearchText
import mahjongtournamentsuite.adminapp.generated.resources.Res
import mahjongtournamentsuite.adminapp.generated.resources.host_country
import mahjongtournamentsuite.adminapp.generated.resources.no_country
import org.jetbrains.compose.resources.stringResource

private data class EmaCountryOption(
    val code: String,
    val name: String,
    val flagCode: String,
    val searchText: String,
)

/** The two-letter code of the flag of a country, from its two-letter or three-letter code. */
fun emaCountryFlagCode(code: String): String? = normalizedFlagCountryCode(code)

/**
 * A field with a menu of every country, with its flag. The search field of the menu finds a country by its name or its
 * codes. A player has the three-letter code of the EMA (GER, NED, SUI...); a tournament has a two-letter code
 * ([twoLetterCodes]).
 *
 * With [allowNoCountry], the first option is "No country" (the globe of the guests), and it gives an empty code.
 * A null [selectedCode] means that nothing is chosen yet.
 */
@Composable
fun EmaCountryDropdown(
    selectedCode: String?,
    onCountrySelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    enabled: Boolean = true,
    isError: Boolean = false,
    errorMessage: String? = null,
    label: String = stringResource(Res.string.host_country),
    required: Boolean = false,
    /** True when the field gives two-letter codes, as the host country of a tournament. */
    twoLetterCodes: Boolean = false,
    allowNoCountry: Boolean = false,
    /** The two-letter codes of the countries that the list offers, or null for every country of the world. */
    allowedCountries: Set<String>? = null,
) {
    val countryNameOf = rememberCountryNames()
    val noCountryName = stringResource(Res.string.no_country)
    val options = remember(countryNameOf, twoLetterCodes, allowedCountries) {
        WorldCountries
            .filter { country -> allowedCountries == null || country.code in allowedCountries }
            .map { country ->
                EmaCountryOption(
                    code = if (twoLetterCodes) country.code else country.emaCode,
                    name = countryNameOf(country.code, country.code),
                    flagCode = country.code,
                    searchText = "${country.code} ${country.emaCode}",
                )
            }
            // Names with accents (Égypte, Österreich) sort next to the names without them.
            .sortedBy { normalizeSearchText(it.name) }
    }
    val normalizedSelectedCode = selectedCode?.trim()?.uppercase()
    val selectedIsGuest = allowNoCountry && normalizedSelectedCode != null && isGuestCountryCode(normalizedSelectedCode)
    // An old player can have another code of the same country (DEU for GER): the field shows that country.
    val selected = normalizedSelectedCode?.takeUnless { selectedIsGuest || it.isEmpty() }?.let { code ->
        options.firstOrNull { it.code == code }
            ?: normalizedFlagCountryCode(code)?.let { flag -> options.firstOrNull { it.flagCode == flag } }
    }
    val selectOptions = remember(options, allowNoCountry, noCountryName) {
        val countries = options.map { option ->
            SelectOption(
                key = option.code,
                label = option.name,
                searchText = option.searchText,
                leadingIcon = { CountryFlag(code = option.flagCode, contentDescription = option.name) },
            )
        }
        if (allowNoCountry) {
            listOf(
                SelectOption(
                    key = "",
                    label = noCountryName,
                    searchText = "EU EUR",
                    leadingIcon = { CountryFlag(code = "", contentDescription = noCountryName) },
                ),
            ) + countries
        } else {
            countries
        }
    }
    val guestIcon: @Composable () -> Unit = { CountryFlag(code = "", contentDescription = noCountryName) }
    val selectedIcon: @Composable () -> Unit = { CountryFlag(code = selected?.flagCode.orEmpty(), contentDescription = selected?.name) }
    SelectField(
        label = label,
        options = selectOptions,
        selected = if (selectedIsGuest) "" else selected?.code ?: normalizedSelectedCode,
        onSelect = onCountrySelected,
        modifier = modifier,
        enabled = enabled,
        required = required,
        isError = isError,
        supportingText = errorMessage,
        valueText = when {
            selectedIsGuest -> noCountryName
            selected != null -> selected.name
            else -> selectedCode.orEmpty()
        },
        leadingIcon = when {
            selectedIsGuest -> guestIcon
            selected != null -> selectedIcon
            else -> null
        },
        fieldModifier = focusRequester?.let(Modifier::focusRequester) ?: Modifier,
    )
}
