package com.etologic.mahjongtournamentsuite.player

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.Icon
import com.etologic.mahjongtournamentsuite.presentation.components.StatisticsIcon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.UserProfile
import com.etologic.mahjongtournamentsuite.presentation.components.AppErrorDialog
import com.etologic.mahjongtournamentsuite.presentation.components.ProfileDialog
import com.etologic.mahjongtournamentsuite.presentation.util.toUiMessage
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color
import com.etologic.mahjongtournamentsuite.presentation.theme.LocalThemeController
import com.etologic.mahjongtournamentsuite.presentation.theme.contentDescription
import com.etologic.mahjongtournamentsuite.presentation.theme.icon

/** The top bar button that switches the theme. It shows the icon of the current preference. */
@Composable
fun ThemeAction() {
    val theme = LocalThemeController.current
    IconButton(onClick = theme.onTogglePreference) {
        Icon(
            theme.preference.icon,
            contentDescription = theme.preference.contentDescription,
            tint = Color.White,
        )
    }
}

/** The top bar button that signs the player out. It sits on the left of the top bar. */
@Composable
fun SignOutAction(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Sign out", tint = Color.White)
    }
}

/** The top bar button that opens the statistics of the screen. */
@Composable
fun StatisticsAction(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(StatisticsIcon, contentDescription = "Statistics", tint = Color.White)
    }
}

/** The top bar button that opens the profile of the player. In the profile the player can change the alias. */
@Composable
fun ProfileAction(session: PlayerSessionPresenter) {
    val scope = rememberCoroutineScope()
    var profile by remember { mutableStateOf<UserProfile?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    IconButton(
        onClick = {
            scope.launch {
                when (val result = session.loadProfile()) {
                    is AppResult.Success -> profile = result.value
                    is AppResult.Failure -> error = result.error.toUiMessage()
                }
            }
        },
    ) {
        Icon(Icons.Default.Person, contentDescription = "Profile", tint = Color.White)
    }
    profile?.let { current ->
        ProfileDialog(
            profile = current,
            onSaveAlias = session::updateAlias,
            onSavePhoto = session::updatePhoto,
            onRemovePhoto = session::removePhoto,
            onSaved = { profile = it },
            onDismiss = { profile = null },
        )
    }
    error?.let { AppErrorDialog(message = it, onDismiss = { error = null }) }
}
