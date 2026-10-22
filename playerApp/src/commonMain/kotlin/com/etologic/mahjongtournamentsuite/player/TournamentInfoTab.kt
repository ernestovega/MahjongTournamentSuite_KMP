package com.etologic.mahjongtournamentsuite.player

import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.ui.text.font.FontWeight
import com.etologic.mahjongtournamentsuite.presentation.components.CountryFlag
import com.etologic.mahjongtournamentsuite.presentation.util.formatLocalizedDate
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.TournamentAgendaItem
import com.etologic.mahjongtournamentsuite.presentation.util.formatLocalizedDateRange
import com.etologic.mahjongtournamentsuite.presentation.util.formatLocalizedTime

/** One day of the agenda. A null [date] holds the items that have no date yet. */
data class AgendaDay(val date: String?, val items: List<TournamentAgendaItem>)

/** Groups the agenda by day. The days are in date order and the items are in time order. Items without a date come last. */
fun agendaByDay(items: List<TournamentAgendaItem>): List<AgendaDay> =
    items.groupBy { it.date }
        .map { (date, dayItems) -> AgendaDay(date, dayItems.sortedBy { it.startTime ?: "99:99" }) }
        .sortedWith(compareBy<AgendaDay> { it.date == null }.thenBy { it.date.orEmpty() })

/** The venue, the contact and the schedule of the tournament. The tab scrolls when its content is taller than the screen. */
@Composable
fun TournamentInfoTab(tournament: Tournament) {
    val venue = tournament.venue
    val hasPlace = tournament.hostCity.isNotBlank() || tournament.hostCountry.isNotBlank()
    if (venue.isEmpty && !hasPlace && !hasSchedule(tournament)) {
        Text(
            "The organizers did not add information yet.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 24.dp),
        )
        return
    }
    Column(
        // The padding is inside the scroll, so the content ends at the tabs.
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // An email alone gives no venue card. It has its own card.
        if (hasPlace || venue.name.isNotBlank() || venue.address.isNotBlank()) VenueCard(tournament)
        if (venue.contactEmail.isNotBlank()) ContactCard(venue.contactEmail)
        if (hasSchedule(tournament)) {
            InfoCard("Schedule") { TournamentSchedule(tournament, Modifier.fillMaxWidth()) }
        }
    }
}

@Composable
private fun VenueCard(tournament: Tournament) {
    val venue = tournament.venue
    val uriHandler = LocalUriHandler.current
    val mapLink = venueMapLink(tournament)
    InfoCard("Venue") {
        if (tournament.hostCity.isNotBlank() || tournament.hostCountry.isNotBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (tournament.hostCountry.isNotBlank()) {
                    CountryFlag(code = tournament.hostCountry, contentDescription = tournament.hostCountry)
                }
                if (tournament.hostCity.isNotBlank()) {
                    Text(tournament.hostCity, style = MaterialTheme.typography.titleSmall)
                }
            }
        }
        if (venue.name.isNotBlank() || venue.address.isNotBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (venue.name.isNotBlank()) Text(venue.name, style = MaterialTheme.typography.titleSmall)
                    if (venue.address.isNotBlank()) Text(venue.address)
                }
                if (mapLink != null) {
                    IconButton(onClick = { uriHandler.openUri(mapLink) }) {
                        Icon(
                            Icons.Default.Directions,
                            contentDescription = "Open directions in Google Maps",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

/** The contact email of the organizers. A tap opens the mail app. */
@Composable
private fun ContactCard(email: String) {
    val uriHandler = LocalUriHandler.current
    InfoCard("Contact") {
        TextButton(onClick = { uriHandler.openUri("mailto:$email") }) {
            Icon(Icons.Default.Email, contentDescription = null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(email)
        }
    }
}

@Composable
private fun InfoCard(title: String, content: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

/** One line of the schedule: a round start or an agenda event. */
private data class ScheduleEntry(val date: String?, val time: String?, val title: String)

/** The rounds and the agenda events in one list, sorted by date and then by start time. Entries without a date come last. */
private fun scheduleByDay(tournament: Tournament): List<Pair<String?, List<ScheduleEntry>>> {
    val rounds = tournament.roundSchedules
        .filter { it.date != null || it.startTime != null }
        .map { ScheduleEntry(it.date, it.startTime, "Round ${it.roundId}") }
    val events = tournament.agendaItems.map { ScheduleEntry(it.date, it.startTime, it.title) }
    return (rounds + events)
        .sortedWith(compareBy<ScheduleEntry> { it.date == null }.thenBy { it.date.orEmpty() }.thenBy { it.time ?: "99:99" })
        .groupBy { it.date }
        .toList()
}

/** True when the schedule has at least one entry. */
fun hasSchedule(tournament: Tournament): Boolean =
    tournament.roundSchedules.any { it.date != null || it.startTime != null } || tournament.agendaItems.isNotEmpty()

/** Percent-encodes a text for a query value of a link. */
private fun encodeQueryValue(text: String): String = buildString {
    text.encodeToByteArray().forEach { byte ->
        val c = byte.toInt() and 0xFF
        val safe = c.toChar().let { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it in "-_.~" }
        if (safe) append(c.toChar()) else append('%').append(c.toString(16).uppercase().padStart(2, '0'))
    }
}

/** The link that opens Google Maps with the venue. It uses the link of the organizers or, without it, a directions link. */
fun venueMapLink(tournament: Tournament): String? {
    val venue = tournament.venue
    if (venue.mapUrl.isNotBlank()) return venue.mapUrl
    val place = listOf(venue.name, venue.address, tournament.hostCity).filter { it.isNotBlank() }.joinToString(", ")
    if (venue.name.isBlank() && venue.address.isBlank()) return null
    return "https://www.google.com/maps/dir/?api=1&destination=${encodeQueryValue(place)}"
}

/** The venue name and address in one text, for a short view. */
fun venueSummary(tournament: Tournament): String =
    listOf(tournament.venue.name, tournament.venue.address).filter { it.isNotBlank() }.joinToString(" - ")

/**
 * The schedule of a tournament: the rounds and the agenda events in chronological order.
 * Each day has a tinted title bar over the full width. Each row has the name on the left
 * and the time on the right, so the row uses the whole width. Days are separated by a large space.
 */
@Composable
fun TournamentSchedule(tournament: Tournament, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        scheduleByDay(tournament).forEach { (date, entries) ->
            Column(Modifier.fillMaxWidth()) {
                Text(
                    date?.let { formatLocalizedDate(it, withWeekday = true) } ?: "No date yet",
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.primary.copy(alpha = 0.14f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                entries.forEachIndexed { index, entry ->
                    if (index > 0) HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.6f))
                    val start = entry.time?.let(::formatLocalizedTime)
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        val textStyle = MaterialTheme.typography.bodyMedium
                        Text(
                            entry.title,
                            Modifier.weight(1f),
                            style = textStyle,
                        )
                        Text(
                            start ?: "-",
                            style = textStyle,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}
