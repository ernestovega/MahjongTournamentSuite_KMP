package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.etologic.mahjongtournamentsuite.presentation.util.matchesSearch
import kotlinx.datetime.TimeZone

/** Most zone names that the menu shows at one time. The text of the field narrows the list. */
private const val MAX_TIME_ZONE_OPTIONS = 40

private val FallbackTimeZoneIds = listOf(
    "UTC",
    "Europe/Berlin",
    "Europe/Lisbon",
    "Europe/London",
    "Europe/Madrid",
    "Europe/Paris",
    "Europe/Rome",
)

/** The IANA zone names that a tournament can use, in alphabetical order. */
internal val SelectableTimeZoneIds: List<String> by lazy {
    runCatching { TimeZone.availableZoneIds }
        .getOrDefault(emptySet())
        .filter { id -> id == "UTC" || (id.contains('/') && !id.startsWith("Etc/") && !id.startsWith("SystemV/")) }
        .sorted()
        .ifEmpty { fallbackTimeZoneIds() }
}

/** The short list for a platform that cannot list the zones. It includes the zone of the device. */
private fun fallbackTimeZoneIds(): List<String> {
    val deviceZone = runCatching { TimeZone.currentSystemDefault().id }.getOrNull()
    return (FallbackTimeZoneIds + listOfNotNull(deviceZone)).distinct().sorted()
}

/** The zone names that match the text of the field. A blank text matches all names. */
internal fun matchingTimeZoneIds(query: String, zoneIds: List<String> = SelectableTimeZoneIds): List<String> =
    zoneIds.filter { it.matchesSearch(query) }

/** A text field with a menu of time zone names. The user can pick a name or type one. */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun TimeZoneDropdown(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: String = "Time zone",
    required: Boolean = false,
    isError: Boolean = false,
    supportingText: String? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    // The menu shows all names until the user taps the text or types. Then it shows the names that match.
    var filtering by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf(TextFieldValue(value)) }
    var selectAllPending by remember { mutableStateOf(false) }
    val textInteractionSource = remember { MutableInteractionSource() }
    LaunchedEffect(value) {
        if (input.text.trim() != value) input = TextFieldValue(value)
    }
    val options = remember(value, filtering) {
        matchingTimeZoneIds(if (filtering) value else "").take(MAX_TIME_ZONE_OPTIONS)
    }

    OnFieldTextClick(textInteractionSource) {
        if (!enabled) return@OnFieldTextClick
        input = TextFieldValue(input.text, TextRange(0, input.text.length))
        selectAllPending = true
        filtering = true
        expanded = true
    }

    // The box ignores its own toggle. The arrow and the text decide when the menu opens.
    ExposedDropdownMenuBox(
        expanded = expanded && enabled,
        onExpandedChange = {},
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = input,
            onValueChange = {
                if (it.text == input.text) {
                    // A tap sets the cursor. Keep the selection of the whole text after the first tap.
                    if (selectAllPending) selectAllPending = false else input = it
                } else {
                    selectAllPending = false
                    input = it
                    filtering = true
                    expanded = true
                    onValueChange(it.text.trim())
                }
            },
            label = { if (required) RequiredLabel(label) else Text(label) },
            trailingIcon = {
                DropdownArrow(expanded && enabled, enabled) {
                    filtering = false
                    expanded = !expanded
                }
            },
            interactionSource = textInteractionSource,
            supportingText = supportingText?.let { message -> { Text(message) } },
            isError = isError,
            enabled = enabled,
            singleLine = true,
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, enabled = enabled)
                .fillMaxWidth()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.Enter, Key.NumPadEnter -> {
                            if (expanded && options.isNotEmpty()) {
                                onValueChange(options.first())
                                expanded = false
                                true
                            } else {
                                false
                            }
                        }

                        Key.Escape -> if (expanded) {
                            expanded = false
                            true
                        } else {
                            false
                        }

                        else -> false
                    }
                },
        )
        ExposedDropdownMenu(
            expanded = expanded && enabled,
            onDismissRequest = { expanded = false },
        ) {
            if (options.isEmpty()) {
                DropdownMenuItem(text = { Text("No matching time zones") }, onClick = {}, enabled = false)
            }
            options.forEach { zoneId ->
                DropdownMenuItem(
                    text = { Text(zoneId) },
                    onClick = {
                        onValueChange(zoneId)
                        expanded = false
                    },
                )
            }
        }
    }
}
