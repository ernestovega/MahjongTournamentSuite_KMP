package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.Country
import com.etologic.mahjongtournamentsuite.domain.model.TournamentVenue
import kotlinx.coroutines.delay

/** The fields that a Google Maps link can fill. A field that the link filled is locked. */
private enum class PlaceField { NAME, ADDRESS, COUNTRY, CITY }

private sealed interface LinkStatus {
    data object Idle : LinkStatus
    data object Reading : LinkStatus
    data class Read(val message: String) : LinkStatus
    data class Failed(val message: String) : LinkStatus
}

/** Time after the last key press before the form reads the link. */
private const val LINK_READ_DELAY_MS = 400L

/**
 * The place of the tournament: a Google Maps link, the venue name and address, the country and the city,
 * and the contact email. Players see them in the player app.
 *
 * With a link, the form reads the venue name, the address, the country and the city from Google Maps.
 * A field that the link fills is locked. A field that the link does not hold stays open for typing.
 * Without a link, the user types every field. [resolveLink] turns a short link into a full link.
 */
@Composable
fun TournamentPlaceFields(
    venue: TournamentVenue,
    onVenueChange: (TournamentVenue) -> Unit,
    hostCountry: String,
    onHostCountryChange: (String) -> Unit,
    hostCity: String,
    onHostCityChange: (String) -> Unit,
    countries: List<Country>,
    resolveLink: suspend (String) -> AppResult<String>,
    enabled: Boolean = true,
    countryError: String? = null,
    cityError: String? = null,
    countryFocusRequester: FocusRequester? = null,
    cityFocusRequester: FocusRequester? = null,
    venueError: String? = null,
    contactEmailError: String? = null,
    mapUrlFocusRequester: FocusRequester? = null,
    contactEmailFocusRequester: FocusRequester? = null,
) {
    // The form reads a link only after the user changes it. A saved link is not read again when the form opens.
    var handledLink by remember { mutableStateOf(venue.mapUrl.trim()) }
    var locked by remember { mutableStateOf<Set<PlaceField>>(emptySet()) }
    var status by remember { mutableStateOf<LinkStatus>(LinkStatus.Idle) }
    val currentVenue by rememberUpdatedState(venue)
    val currentCountries by rememberUpdatedState(countries)
    val currentOnVenueChange by rememberUpdatedState(onVenueChange)
    val currentOnCountryChange by rememberUpdatedState(onHostCountryChange)
    val currentOnCityChange by rememberUpdatedState(onHostCityChange)
    val currentResolveLink by rememberUpdatedState(resolveLink)
    val currentLocked by rememberUpdatedState(locked)

    LaunchedEffect(venue.mapUrl) {
        val link = venue.mapUrl.trim()
        if (link == handledLink) return@LaunchedEffect
        delay(LINK_READ_DELAY_MS)
        handledLink = link

        // Values from an older link go away before the new link fills the fields.
        var name = currentVenue.name.takeUnless { PlaceField.NAME in currentLocked }.orEmpty()
        var address = currentVenue.address.takeUnless { PlaceField.ADDRESS in currentLocked }.orEmpty()
        if (PlaceField.COUNTRY in currentLocked) currentOnCountryChange("")
        if (PlaceField.CITY in currentLocked) currentOnCityChange("")
        locked = emptySet()
        currentOnVenueChange(currentVenue.copy(name = name, address = address))

        if (link.isEmpty()) {
            status = LinkStatus.Idle
            return@LaunchedEffect
        }
        if (!isGoogleMapsLink(link)) {
            status = LinkStatus.Failed("Use an https link from Google Maps. Or clear the link and type the place.")
            return@LaunchedEffect
        }
        status = LinkStatus.Reading
        val fullLink = if (isShortMapLink(link)) {
            when (val result = currentResolveLink(link)) {
                is AppResult.Success -> result.value
                is AppResult.Failure -> {
                    status = LinkStatus.Failed("The link could not be opened. Check it or type the place.")
                    return@LaunchedEffect
                }
            }
        } else {
            link
        }
        val place = parseMapLink(fullLink, currentCountries)
        if (place == null) {
            status = LinkStatus.Failed("The link has no place data. Use the link of a place, or type the place.")
            return@LaunchedEffect
        }

        val found = mutableSetOf<PlaceField>()
        place.name?.let { name = it.take(VENUE_NAME_MAX_LENGTH); found += PlaceField.NAME }
        place.address?.let { address = it.take(VENUE_ADDRESS_MAX_LENGTH); found += PlaceField.ADDRESS }
        place.countryCode?.let { currentOnCountryChange(it); found += PlaceField.COUNTRY }
        place.city?.let { currentOnCityChange(it); found += PlaceField.CITY }
        currentOnVenueChange(currentVenue.copy(name = name, address = address))
        locked = found
        val missing = PlaceField.entries.filter { it !in found }
        status = LinkStatus.Read(
            if (missing.isEmpty()) {
                "The venue name, the address, the country and the city come from the link."
            } else {
                "The link has no " + missing.joinToString(", ") { it.label() } + ". Type " +
                    (if (missing.size == 1) "it." else "them.")
            },
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = venue.mapUrl,
            onValueChange = { onVenueChange(venue.copy(mapUrl = it.take(VENUE_MAP_URL_MAX_LENGTH))) },
            label = { RequiredLabel("Google Maps link") },
            placeholder = { Text("https://maps.app.goo.gl/...") },
            supportingText = {
                Text(
                    when (val current = status) {
                        LinkStatus.Idle -> venueError
                            ?: "Add a link, or type the venue name and the address. The form reads the place from the link."
                        LinkStatus.Reading -> "Reading the link…"
                        is LinkStatus.Read -> current.message
                        is LinkStatus.Failed -> current.message
                    },
                )
            },
            isError = status is LinkStatus.Failed || venueError != null,
            singleLine = true,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().optionalFocusRequester(mapUrlFocusRequester),
        )
        OutlinedTextField(
            value = venue.name,
            onValueChange = { onVenueChange(venue.copy(name = it.take(VENUE_NAME_MAX_LENGTH))) },
            label = { Text("Venue name") },
            isError = venueError != null,
            singleLine = true,
            enabled = enabled && PlaceField.NAME !in locked,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = venue.address,
            onValueChange = { onVenueChange(venue.copy(address = it.take(VENUE_ADDRESS_MAX_LENGTH))) },
            label = { Text("Address") },
            isError = venueError != null,
            enabled = enabled && PlaceField.ADDRESS !in locked,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            EmaCountryDropdown(
                selectedCode = hostCountry,
                onCountrySelected = onHostCountryChange,
                countries = countries,
                enabled = enabled && PlaceField.COUNTRY !in locked,
                modifier = Modifier.weight(1f),
                focusRequester = countryFocusRequester,
                isError = countryError != null,
                errorMessage = countryError,
                label = "Country",
                required = true,
            )
            OutlinedTextField(
                value = hostCity,
                onValueChange = onHostCityChange,
                label = { RequiredLabel("City") },
                singleLine = true,
                isError = cityError != null,
                supportingText = cityError?.let { message -> { Text(message) } },
                enabled = enabled && PlaceField.CITY !in locked,
                modifier = Modifier.weight(2f).let { base ->
                    if (cityFocusRequester != null) base.focusRequester(cityFocusRequester) else base
                },
            )
        }
        OutlinedTextField(
            value = venue.contactEmail,
            onValueChange = { onVenueChange(venue.copy(contactEmail = it.take(CONTACT_EMAIL_MAX_LENGTH))) },
            label = { RequiredLabel("Contact email") },
            placeholder = { Text("organizers@example.com") },
            isError = contactEmailError != null,
            supportingText = contactEmailError?.let { message -> { Text(message) } },
            singleLine = true,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().optionalFocusRequester(contactEmailFocusRequester),
        )
    }
}

private fun Modifier.optionalFocusRequester(focusRequester: FocusRequester?): Modifier =
    if (focusRequester != null) focusRequester(focusRequester) else this

/** A venue needs a Google Maps link, or a name and an address that the user types. Returns null when it is valid. */
fun venueRequiredError(venue: TournamentVenue): String? =
    if (venue.mapUrl.isBlank() && (venue.name.isBlank() || venue.address.isBlank())) {
        "Add a Google Maps link, or type the venue name and the address."
    } else {
        null
    }

/** The contact email is required and must look like an email address. Returns null when it is valid. */
fun contactEmailRequiredError(email: String): String? = when {
    email.isBlank() -> "Contact email is required."
    !Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(email.trim()) -> "Use a valid email address."
    else -> null
}

private fun PlaceField.label(): String = when (this) {
    PlaceField.NAME -> "venue name"
    PlaceField.ADDRESS -> "address"
    PlaceField.COUNTRY -> "country"
    PlaceField.CITY -> "city"
}

private const val VENUE_NAME_MAX_LENGTH = 120
private const val VENUE_ADDRESS_MAX_LENGTH = 250
private const val VENUE_MAP_URL_MAX_LENGTH = 500
private const val CONTACT_EMAIL_MAX_LENGTH = 254
