package com.etologic.mahjongtournamentsuite.player

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.automirrored.filled.Logout
import com.etologic.mahjongtournamentsuite.presentation.components.AppTopBarButton
import com.etologic.mahjongtournamentsuite.presentation.components.StatisticsIcon
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.etologic.mahjongtournamentsuite.presentation.theme.LocalThemeController
import com.etologic.mahjongtournamentsuite.presentation.theme.contentDescription
import com.etologic.mahjongtournamentsuite.presentation.theme.icon

/** The top bar button that switches the theme. It shows the icon of the current preference. */
@Composable
fun ThemeAction() {
    val theme = LocalThemeController.current
    AppTopBarButton(
        text = theme.preference.contentDescription,
        icon = theme.preference.icon,
        onClick = theme.onTogglePreference,
    )
}

/** The top bar button that signs the player out. */
@Composable
fun SignOutAction(onClick: () -> Unit) {
    AppTopBarButton(text = "Exit", icon = Icons.AutoMirrored.Filled.Logout, onClick = onClick)
}

/** The top bar button that opens the statistics of the screen. */
@Composable
fun StatisticsAction(onClick: () -> Unit) {
    AppTopBarButton(text = "Statistics", icon = StatisticsIcon, onClick = onClick)
}

/**
 * The state of the profile dialog. The screen owns it, so the dialog stays open
 * when the top bar button is inside a three-dots menu that closes.
 */
class ProfileDialogState(private val session: PlayerSessionPresenter, private val scope: CoroutineScope) {
    var profile by mutableStateOf<UserProfile?>(null)
    var error by mutableStateOf<String?>(null)

    fun open() {
        scope.launch {
            when (val result = session.loadProfile()) {
                is AppResult.Success -> profile = result.value
                is AppResult.Failure -> error = result.error.toUiMessage()
            }
        }
    }

    @Composable
    fun Dialogs() {
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
}

@Composable
fun rememberProfileDialogState(session: PlayerSessionPresenter): ProfileDialogState {
    val scope = rememberCoroutineScope()
    return remember(session, scope) { ProfileDialogState(session, scope) }
}

/** The top bar button that opens the profile of the player. In the profile the player can change the alias. */
@Composable
fun ProfileAction(state: ProfileDialogState) {
    AppTopBarButton(text = "Profile", icon = Icons.Default.Person, onClick = state::open)
}
