package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.autofill.contentType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.presentation.SignInRoute
import com.etologic.mahjongtournamentsuite.presentation.TournamentsRoute
import com.etologic.mahjongtournamentsuite.presentation.components.AppErrorDialog
import com.etologic.mahjongtournamentsuite.presentation.components.AppScaffold
import com.etologic.mahjongtournamentsuite.presentation.components.AppTopBarLeadingActions
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedButton as Button
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedIconButton as IconButton
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedTextButton as TextButton
import com.etologic.mahjongtournamentsuite.presentation.components.ScreenColumn
import com.etologic.mahjongtournamentsuite.presentation.presenter.AuthPresenter
import com.etologic.mahjongtournamentsuite.presentation.platform.loadBrowserCredentials
import com.etologic.mahjongtournamentsuite.presentation.platform.saveBrowserCredentials
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import com.etologic.mahjongtournamentsuite.presentation.util.toUiMessage
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.jetbrains.compose.resources.painterResource
import mahjongtournamentsuite.composeapp.generated.resources.Res
import mahjongtournamentsuite.composeapp.generated.resources.ic_visibility
import mahjongtournamentsuite.composeapp.generated.resources.ic_visibility_off

@Composable
fun SignInScreen(
    navController: NavHostController,
) {
    val authPresenter = koinInject<AuthPresenter>()
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    val emailRequester = remember { FocusRequester() }
    val passwordRequester = remember { FocusRequester() }
    val loginButtonRequester = remember { FocusRequester() }

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isPasswordVisible by rememberSaveable { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var infoMessage by remember { mutableStateOf<String?>(null) }

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

    AppScaffold(
        title = "Sign in",
        isLoading = isLoading,
        autoFocusFirst = false,
        leadingActions = { AppTopBarLeadingActions(showThemeToggle = true) },
    ) {
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
                        errorMessage = result.error.toUiMessage()
                    }
                }
                isLoading = false
            }
        }

        fun requestPasswordReset() {
            errorMessage = null
            infoMessage = null
            if (email.trim().isEmpty()) {
                errorMessage = "Enter your email address first."
                emailRequester.requestFocus()
                return
            }

            isLoading = true
            coroutineScope.launch {
                when (val result = authPresenter.requestPasswordReset(email.trim())) {
                    is AppResult.Success -> {
                        infoMessage = "If an account exists, we sent password reset instructions to your email."
                    }
                    is AppResult.Failure -> errorMessage = result.error.toUiMessage()
                }
                isLoading = false
            }
        }

        ScreenColumn(
            maxWidth = 520.dp,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 32.dp),
        ) {
            Text(
                text = "Use your email to sign in. Accounts are invitation-only.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .contentType(ContentType.EmailAddress)
                            .focusRequester(emailRequester)
                            .onPreviewKeyEvent { e ->
                                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                when (e.key) {
                                    Key.Tab -> {
                                        if (e.isShiftPressed) {
                                            focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Previous)
                                        } else {
                                            passwordRequester.requestFocus()
                                        }
                                        true
                                    }
                                    Key.Enter, Key.NumPadEnter -> {
                                        passwordRequester.requestFocus()
                                        true
                                    }
                                    else -> false
                                }
                            },
                        singleLine = true,
                        enabled = !isLoading,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next,
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { passwordRequester.requestFocus() },
                        ),
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .contentType(ContentType.Password)
                            .focusRequester(passwordRequester)
                            .onPreviewKeyEvent { e ->
                                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                when (e.key) {
                                    Key.Tab -> {
                                        if (e.isShiftPressed) {
                                            emailRequester.requestFocus()
                                        } else {
                                            loginButtonRequester.requestFocus()
                                        }
                                        true
                                    }
                                    Key.Enter, Key.NumPadEnter -> {
                                        submit()
                                        true
                                    }
                                    else -> false
                                }
                            },
                        singleLine = true,
                        visualTransformation = if (isPasswordVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            val (icon, contentDescription) = if (isPasswordVisible) {
                                Res.drawable.ic_visibility_off to "Hide password"
                            } else {
                                Res.drawable.ic_visibility to "Show password"
                            }

                            IconButton(
                                buttonModifier = Modifier.focusProperties { canFocus = false },
                                enabled = !isLoading,
                                onClick = { isPasswordVisible = !isPasswordVisible },
                            ) {
                                Icon(
                                    painter = painterResource(icon),
                                    contentDescription = contentDescription,
                                )
                            }
                        },
                        enabled = !isLoading,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                focusManager.clearFocus()
                                submit()
                            },
                        ),
                    )

                    errorMessage?.let { message ->
                        AppErrorDialog(
                            message = message,
                            onDismiss = { errorMessage = null },
                        )
                    }

                    infoMessage?.let { message ->
                        Text(
                            text = message,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                    TextButton(
                        enabled = !isLoading,
                        onClick = { requestPasswordReset() },
                    ) {
                        Text("Forgot password?")
                    }

                    Button(
                        enabled = !isLoading,
                        onClick = { submit() },
                        modifier = Modifier.fillMaxWidth(),
                        buttonModifier = Modifier.fillMaxWidth(),
                        focusRequester = loginButtonRequester,
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text("Sign in")
                        }
                    }
                }
            }
        }
    }
}

@Preview(device = Devices.DESKTOP)
@Composable
private fun SignInScreenPreview() {
    MtsTheme(useDarkTheme = false) {
        AppScaffold(
            title = "Sign in",
            leadingActions = { AppTopBarLeadingActions(showThemeToggle = true) },
        ) {
            ScreenColumn(
                maxWidth = 520.dp,
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 32.dp),
            ) {
                Text(
                    text = "Use your email to sign in. Accounts are invitation-only.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        OutlinedTextField(
                            value = "demo@example.com",
                            onValueChange = {},
                            label = { Text("Email") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = "password",
                            onValueChange = {},
                            label = { Text("Password") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        Button(
                            enabled = true,
                            onClick = {},
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Sign in")
                        }
                    }
                }
            }
        }
    }
}
