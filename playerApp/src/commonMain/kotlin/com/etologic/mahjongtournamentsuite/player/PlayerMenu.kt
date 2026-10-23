package com.etologic.mahjongtournamentsuite.player

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import com.etologic.mahjongtournamentsuite.presentation.components.AppTopBarButton
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
import androidx.navigation.NavHostController
import com.etologic.mahjongtournamentsuite.presentation.components.SignOutConfirmDialog
import org.koin.compose.koinInject
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

/**
 * The Exit and Profile buttons and their dialogs. The screen owns the state, so the dialogs stay open
 * when the top bar buttons are inside a three-dots menu that closes.
 */
class AccountActions(
    private val session: PlayerSessionPresenter,
    private val navController: NavHostController,
    private val scope: CoroutineScope,
) {
    private var profile by mutableStateOf<UserProfile?>(null)
    private var error by mutableStateOf<String?>(null)
    private var confirmingSignOut by mutableStateOf(false)

    private fun openProfile() {
        scope.launch {
            when (val result = session.loadProfile()) {
                is AppResult.Success -> profile = result.value
                is AppResult.Failure -> error = result.error.toUiMessage()
            }
        }
    }

    /** The Profile button left of the title. The Exit button is inside the profile dialog. */
    @Composable
    fun Buttons() {
        AppTopBarButton(text = "Profile", icon = Icons.Default.Person, onClick = ::openProfile)
    }

    @Composable
    fun Dialogs() {
        if (confirmingSignOut) {
            SignOutConfirmDialog(
                onConfirm = {
                    confirmingSignOut = false
                    scope.launch {
                        session.signOut()
                        navController.navigate(SignInRoute()) { popUpTo(0) }
                    }
                },
                onDismiss = { confirmingSignOut = false },
            )
        }
        profile?.let { current ->
            ProfileDialog(
                profile = current,
                onSaveAlias = session::updateAlias,
                onSavePhoto = session::updatePhoto,
                onRemovePhoto = session::removePhoto,
                onSaved = { profile = it },
                onDismiss = { profile = null },
                onSignOut = {
                    profile = null
                    confirmingSignOut = true
                },
            )
        }
        error?.let { AppErrorDialog(message = it, onDismiss = { error = null }) }
    }
}

@Composable
fun rememberAccountActions(navController: NavHostController): AccountActions {
    val session = koinInject<PlayerSessionPresenter>()
    val scope = rememberCoroutineScope()
    return remember(session, navController, scope) { AccountActions(session, navController, scope) }
}
