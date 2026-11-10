package com.etologic.mahjongtournamentsuite.player

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.runtime.LaunchedEffect
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
import com.etologic.mahjongtournamentsuite.presentation.util.UiText
import com.etologic.mahjongtournamentsuite.presentation.util.toUiText
import mahjongtournamentsuite.playerapp.generated.resources.Res
import mahjongtournamentsuite.playerapp.generated.resources.menu_profile
import mahjongtournamentsuite.playerapp.generated.resources.menu_sign_in
import org.jetbrains.compose.resources.stringResource
import androidx.navigation.NavHostController
import com.etologic.mahjongtournamentsuite.presentation.components.SignOutConfirmDialog
import org.koin.compose.koinInject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

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
    private var error by mutableStateOf<UiText?>(null)
    private var confirmingSignOut by mutableStateOf(false)
    /** Null until the session is known. Without an account the button opens the sign-in. */
    private var signedIn by mutableStateOf<Boolean?>(null)

    private fun openProfile() {
        scope.launch {
            when (val result = session.loadProfile()) {
                is AppResult.Success -> profile = result.value
                is AppResult.Failure -> error = result.error.toUiText()
            }
        }
    }

    /**
     * The Profile button left of the title. The Exit button is inside the profile dialog.
     * Without an account it is the Sign in button: everything can be read without one.
     */
    @Composable
    fun Buttons() {
        LaunchedEffect(Unit) { signedIn = session.hasSession() }
        when (signedIn) {
            null -> Unit
            true -> AppTopBarButton(text = stringResource(Res.string.menu_profile), icon = Icons.Default.Person, onClick = ::openProfile)
            false -> AppTopBarButton(
                text = stringResource(Res.string.menu_sign_in),
                icon = Icons.AutoMirrored.Filled.Login,
                onClick = { navController.navigate(SignInRoute()) },
            )
        }
    }

    @Composable
    fun Dialogs() {
        if (confirmingSignOut) {
            SignOutConfirmDialog(
                onConfirm = {
                    confirmingSignOut = false
                    scope.launch {
                        session.signOut()
                        // The same screens stay open, now without the own player.
                        navController.reopen(navController.openRoutes())
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
