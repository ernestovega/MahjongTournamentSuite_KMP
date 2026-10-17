package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.domain.model.TournamentVenue

/** Fields for the place of the tournament. Players see them in the player app. */
@Composable
fun TournamentVenueFields(
    venue: TournamentVenue,
    onVenueChange: (TournamentVenue) -> Unit,
    enabled: Boolean = true,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = venue.name,
            onValueChange = { onVenueChange(venue.copy(name = it.take(VENUE_NAME_MAX_LENGTH))) },
            label = { Text("Venue name") },
            singleLine = true,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = venue.address,
            onValueChange = { onVenueChange(venue.copy(address = it.take(VENUE_ADDRESS_MAX_LENGTH))) },
            label = { Text("Address") },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = venue.mapUrl,
            onValueChange = { onVenueChange(venue.copy(mapUrl = it.take(VENUE_MAP_URL_MAX_LENGTH))) },
            label = { Text("Google Maps link") },
            placeholder = { Text("https://maps.app.goo.gl/...") },
            singleLine = true,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = venue.contactEmail,
            onValueChange = { onVenueChange(venue.copy(contactEmail = it.take(CONTACT_EMAIL_MAX_LENGTH))) },
            label = { Text("Contact email") },
            placeholder = { Text("organizers@example.com") },
            singleLine = true,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private const val VENUE_NAME_MAX_LENGTH = 120
private const val VENUE_ADDRESS_MAX_LENGTH = 250
private const val VENUE_MAP_URL_MAX_LENGTH = 500
private const val CONTACT_EMAIL_MAX_LENGTH = 254
