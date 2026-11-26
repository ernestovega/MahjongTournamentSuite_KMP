package com.etologic.mahjongtournamentsuite.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Gavel
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.etologic.mahjongtournamentsuite.presentation.components.AppTopBarButton
import com.etologic.mahjongtournamentsuite.presentation.components.LocalTopBarMenu
import com.etologic.mahjongtournamentsuite.presentation.components.TopBarMenuScope
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import mahjongtournamentsuite.playerapp.generated.resources.Res
import mahjongtournamentsuite.playerapp.generated.resources.combinations_title
import mahjongtournamentsuite.playerapp.generated.resources.menu_green_book
import mahjongtournamentsuite.playerapp.generated.resources.menu_penalties_overview
import mahjongtournamentsuite.playerapp.generated.resources.menu_rules
import org.jetbrains.compose.resources.stringResource

/**
 * The Rules button of the top bar: it opens a menu with the Green Book, the Combinations and the Penalties overview.
 *
 * On a phone the button is already inside the three-dots menu, and a menu inside a menu is awkward to use.
 * There the three options show directly in that menu, under a "Rules" heading.
 * [dividerAbove] and [dividerBelow] separate that group from the other options of the menu.
 */
@Composable
fun RulesTopBarButton(
    navController: NavHostController,
    dividerAbove: Boolean = false,
    dividerBelow: Boolean = false,
) {
    // Out of the menus, so it lives on while a menu closes.
    val openGreenBook = rememberOpenGreenBook()
    val options: @Composable () -> Unit = {
        AppTopBarButton(
            text = stringResource(Res.string.menu_green_book),
            icon = Icons.Default.Book,
            onClick = openGreenBook,
        )
        AppTopBarButton(
            text = stringResource(Res.string.combinations_title),
            icon = Icons.AutoMirrored.Filled.MenuBook,
            onClick = { navController.navigate(CombinationsRoute) },
        )
        AppTopBarButton(
            text = stringResource(Res.string.menu_penalties_overview),
            icon = Icons.Default.Gavel,
            onClick = { navController.navigate(PenaltiesRoute) },
        )
    }
    val rules = stringResource(Res.string.menu_rules)

    if (LocalTopBarMenu.current != null) {
        if (dividerAbove) HorizontalDivider()
        Text(
            text = rules,
            style = MaterialTheme.typography.labelLarge,
            color = MtsTheme.colors.link,
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .semantics { heading() },
        )
        options()
        if (dividerBelow) HorizontalDivider()
        return
    }

    var open by remember { mutableStateOf(false) }
    Box {
        AppTopBarButton(
            text = rules,
            icon = Icons.AutoMirrored.Filled.LibraryBooks,
            onClick = { open = true },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            // The options draw as menu items and close this menu when they are chosen.
            CompositionLocalProvider(LocalTopBarMenu provides TopBarMenuScope { open = false }) { options() }
        }
    }
}
