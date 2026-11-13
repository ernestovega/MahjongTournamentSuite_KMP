package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.runtime.Composable
import com.etologic.mahjongtournamentsuite.presentation.telemetry.TrackedTab

/** An [MtsTab] that counts a view in the analytics while it is selected. Both apps use it in every tab row. */
@Composable
fun CompactTab(
    selected: Boolean,
    onClick: () -> Unit,
    title: String,
    /** A fixed name for the analytics. It does not change with the language. */
    trackingName: String,
    icon: (@Composable () -> Unit)? = null,
) {
    if (selected) TrackedTab(trackingName)
    MtsTab(selected = selected, onClick = onClick, title = title, icon = icon)
}
