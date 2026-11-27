package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.triStateToggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import mahjongtournamentsuite.adminapp.generated.resources.Res
import mahjongtournamentsuite.adminapp.generated.resources.select_all_shown
import org.jetbrains.compose.resources.stringResource

/**
 * The box that selects every row that a list shows, in the header of the column of boxes. It is checked when all of
 * them are selected and half checked when some are. A tap selects them all, or clears them when all were selected.
 * [label] is the text beside the box on a phone, where the list has no header; a tap on it changes the box too.
 */
@Composable
internal fun SelectShownCheckbox(
    shownCount: Int,
    selectedCount: Int,
    enabled: Boolean,
    onSelectAllShown: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    val state = when {
        selectedCount == 0 -> ToggleableState.Off
        selectedCount >= shownCount -> ToggleableState.On
        else -> ToggleableState.Indeterminate
    }
    val description = stringResource(Res.string.select_all_shown, shownCount)
    Row(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .triStateToggleable(
                state = state,
                enabled = enabled && shownCount > 0,
                role = Role.Checkbox,
                onClick = { if (state == ToggleableState.On) onClear() else onSelectAllShown() },
            )
            .semantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TriStateCheckbox(state = state, onClick = null, enabled = enabled && shownCount > 0, modifier = Modifier.padding(12.dp))
        if (label != null) {
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 8.dp))
        }
    }
}
