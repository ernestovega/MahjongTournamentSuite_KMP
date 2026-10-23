package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.navigation.NavHostController
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.presentation.SignInRoute
import com.etologic.mahjongtournamentsuite.presentation.TournamentsRoute
import com.etologic.mahjongtournamentsuite.presentation.components.SignInForm
import com.etologic.mahjongtournamentsuite.presentation.platform.loadBrowserCredentials
import com.etologic.mahjongtournamentsuite.presentation.platform.saveBrowserCredentials
import com.etologic.mahjongtournamentsuite.presentation.presenter.AuthPresenter
import com.etologic.mahjongtournamentsuite.presentation.presenter.VersionPresenter
import com.etologic.mahjongtournamentsuite.presentation.util.UiText
import com.etologic.mahjongtournamentsuite.presentation.util.toUiText
import kotlinx.coroutines.launch
import mahjongtournamentsuite.adminapp.generated.resources.Res
import mahjongtournamentsuite.adminapp.generated.resources.sign_in_enter_email
import mahjongtournamentsuite.adminapp.generated.resources.sign_in_reset_sent
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
fun SignInScreen(
    navController: NavHostController,
) {
    val authPresenter = koinInject<AuthPresenter>()
    val versionPresenter = koinInject<VersionPresenter>()
    val coroutineScope = rememberCoroutineScope()
    val emailRequester = remember { FocusRequester() }

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<UiText?>(null) }
    var infoMessage by remember { mutableStateOf<UiText?>(null) }
    var backendVersion by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(versionPresenter) {
        backendVersion = versionPresenter.loadBackendVersion()
    }

    LaunchedEffect(authPresenter) {
        // Chrome mediates this request through its password manager UI.
        loadBrowserCredentials { savedEmail, savedPassword ->
            email = savedEmail
            password = savedPassword
        }
        if (authPresenter.hasActiveSession()) {
            navController.navigate(TournamentsRoute) {
                popUpTo(SignInRoute) { inclusive = true }
            }
        } else {
            emailRequester.requestFocus()
        }
    }

    fun submit() {
        errorMessage = null
        infoMessage = null
        isLoading = true

        coroutineScope.launch {
            when (val result = authPresenter.signIn(email = email.trim(), password = password)) {
                is AppResult.Success -> {
                    saveBrowserCredentials(email.trim(), password)
                    navController.navigate(TournamentsRoute) {
                        popUpTo(SignInRoute) { inclusive = true }
                    }
                }
                is AppResult.Failure -> {
                    errorMessage = result.error.toUiText()
                }
            }
            isLoading = false
        }
    }

    fun requestPasswordReset() {
        errorMessage = null
        infoMessage = null
        if (email.trim().isEmpty()) {
            errorMessage = UiText.of(Res.string.sign_in_enter_email)
            emailRequester.requestFocus()
            return
        }

        isLoading = true
        coroutineScope.launch {
            when (val result = authPresenter.requestPasswordReset(email.trim())) {
                is AppResult.Success -> {
                    infoMessage = UiText.of(Res.string.sign_in_reset_sent)
                }
                is AppResult.Failure -> errorMessage = result.error.toUiText()
            }
            isLoading = false
        }
    }

    SignInForm(
        email = email,
        onEmailChange = { email = it },
        password = password,
        onPasswordChange = { password = it },
        isLoading = isLoading,
        errorMessage = errorMessage,
        infoMessage = infoMessage,
        backendVersion = backendVersion,
        emailRequester = emailRequester,
        onSubmit = ::submit,
        onForgotPassword = ::requestPasswordReset,
        onErrorDismiss = { errorMessage = null },
    )
}
