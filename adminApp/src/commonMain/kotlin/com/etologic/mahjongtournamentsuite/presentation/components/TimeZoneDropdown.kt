package com.etologic.mahjongtournamentsuite.presentation.components

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
import androidx.compose.ui.Modifier
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.offsetAt
import mahjongtournamentsuite.adminapp.generated.resources.Res
import mahjongtournamentsuite.adminapp.generated.resources.time_zone
import org.jetbrains.compose.resources.stringResource

private val FallbackTimeZoneIds = listOf(
    "UTC",
    "Europe/Berlin",
    "Europe/Lisbon",
    "Europe/London",
    "Europe/Madrid",
    "Europe/Paris",
    "Europe/Rome",
)

/** The UTC offset of a zone now, in seconds. A zone that the platform cannot read counts as 0. */
private fun offsetSeconds(zoneId: String): Int = runCatching {
    TimeZone.of(zoneId).offsetAt(Clock.System.now()).totalSeconds
}.getOrDefault(0)

/** The text of a menu item, for example "UTC +1 Europe/Madrid" or "UTC +5:30 Asia/Kolkata". */
internal fun timeZoneLabel(zoneId: String): String {
    val seconds = offsetSeconds(zoneId)
    val sign = if (seconds < 0) "-" else "+"
    val minutes = kotlin.math.abs(seconds) / 60
    val minutePart = if (minutes % 60 == 0) "" else ":" + (minutes % 60).toString().padStart(2, '0')
    return "UTC $sign${minutes / 60}$minutePart $zoneId"
}

/** The IANA zone names that a tournament can use, from the lowest UTC offset to the highest, then by name. */
internal val SelectableTimeZoneIds: List<String> by lazy {
    runCatching { TimeZone.availableZoneIds }
        .getOrDefault(emptySet())
        .filter { id -> id == "UTC" || (id.contains('/') && !id.startsWith("Etc/") && !id.startsWith("SystemV/")) }
        .sortedWith(compareBy({ offsetSeconds(it) }, { it }))
        .ifEmpty { fallbackTimeZoneIds() }
}

/** The zone name of the device when the list has it. Otherwise "UTC", because a browser can report "SYSTEM". */
internal fun deviceTimeZoneId(): String {
    val id = runCatching { TimeZone.currentSystemDefault().id }.getOrNull()
    return id?.takeIf { it in SelectableTimeZoneIds } ?: "UTC"
}

/** The short list for a platform that cannot list the zones. It includes the zone of the device. */
private fun fallbackTimeZoneIds(): List<String> {
    val deviceZone = runCatching { TimeZone.currentSystemDefault().id }.getOrNull()
    return (FallbackTimeZoneIds + listOfNotNull(deviceZone)).distinct().sortedWith(compareBy({ offsetSeconds(it) }, { it }))
}

/** A field with a menu of time zone names. The user can only pick a name from the menu. */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun TimeZoneDropdown(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: String = stringResource(Res.string.time_zone),
    required: Boolean = false,
    isError: Boolean = false,
    supportingText: String? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    // A zone name that is not in the list (for example from an old tournament) stays in the menu, so the user can see it.
    val options = remember(value) { if (value.isBlank() || value in SelectableTimeZoneIds) SelectableTimeZoneIds else listOf(value) + SelectableTimeZoneIds }

    // Without a selected zone, the menu opens at "UTC". If the list has no "UTC", it opens at the first zone with UTC +0.
    val menuScroll = rememberMenuScrollState(
        expanded = expanded,
        selectedIndex = options.indexOf(value).takeIf { it >= 0 },
        defaultIndex = options.indexOf("UTC").takeIf { it >= 0 } ?: options.indexOfFirst { offsetSeconds(it) == 0 }.coerceAtLeast(0),
    )

    ExposedDropdownMenuBox(
        expanded = expanded && enabled,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = if (value.isBlank()) value else timeZoneLabel(value),
            onValueChange = {},
            readOnly = true,
            label = { if (required) RequiredLabel(label) else Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded && enabled) },
            supportingText = supportingText?.let { message -> { Text(message) } },
            isError = isError,
            enabled = enabled,
            singleLine = true,
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = enabled)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded && enabled,
            onDismissRequest = { expanded = false },
            scrollState = menuScroll,
        ) {
            options.forEach { zoneId ->
                DropdownMenuItem(
                    text = { Text(timeZoneLabel(zoneId)) },
                    onClick = {
                        onValueChange(zoneId)
                        expanded = false
                    },
                )
            }
        }
    }
}
