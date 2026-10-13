package com.etologic.mahjongtournamentsuite.player

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Box
import com.etologic.mahjongtournamentsuite.presentation.theme.LocalThemeController

/** The three-dots menu of the top bar. It changes the theme and, when [onSignOut] is set, signs out. */
@Composable
fun PlayerOverflowMenu(onSignOut: (() -> Unit)? = null) {
    var open by remember { mutableStateOf(false) }
    val theme = LocalThemeController.current
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = "Menu", tint = Color.White)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Theme: ${theme.preference.name}") },
                onClick = {
                    theme.onTogglePreference()
                    open = false
                },
            )
            if (onSignOut != null) {
                DropdownMenuItem(
                    text = { Text("Sign out") },
                    onClick = {
                        open = false
                        onSignOut()
                    },
                )
            }
        }
    }
}
