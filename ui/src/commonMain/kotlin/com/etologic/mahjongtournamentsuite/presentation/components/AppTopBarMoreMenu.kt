package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsSpacing
import mahjongtournamentsuite.ui.generated.resources.Res
import mahjongtournamentsuite.ui.generated.resources.top_bar_more
import org.jetbrains.compose.resources.stringResource

/**
 * A "More" button with three dots at the end of the top bar. Its menu shows [content]: top bar buttons, which draw as
 * menu items there. It keeps the top bar light when a screen has many buttons.
 * On a phone the top bar is already a three-dots menu, so [content] goes into it below a divider, with no second menu.
 * A button that opens a dialog keeps the dialog state outside the button: the menu leaves the composition when it closes.
 */
@Composable
fun AppTopBarMoreMenu(content: @Composable () -> Unit) {
    if (LocalTopBarMenu.current != null) {
        HorizontalDivider()
        content()
        return
    }
    var open by remember { mutableStateOf(false) }
    Box {
        AppTopBarButton(
            text = stringResource(Res.string.top_bar_more),
            icon = Icons.Default.MoreVert,
            onClick = { open = true },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            CompositionLocalProvider(LocalTopBarMenu provides TopBarMenuScope { open = false }) { content() }
        }
    }
}

/**
 * A line of small grey text at the end of a More menu: information for support, such as the revision of the data.
 * It is not a button. It takes no click and closes nothing.
 */
@Composable
fun AppTopBarMenuNote(text: String) {
    HorizontalDivider()
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = MtsSpacing.lg, vertical = MtsSpacing.sm),
    )
}
