package com.etologic.mahjongtournamentsuite.presentation.components

import com.etologic.mahjongtournamentsuite.presentation.util.UiText
import com.etologic.mahjongtournamentsuite.presentation.util.asString
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.autofill.contentType
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import mahjongtournamentsuite.ui.generated.resources.Res
import mahjongtournamentsuite.ui.generated.resources.field_email
import mahjongtournamentsuite.ui.generated.resources.field_password
import mahjongtournamentsuite.ui.generated.resources.ic_visibility
import mahjongtournamentsuite.ui.generated.resources.ic_visibility_off
import mahjongtournamentsuite.ui.generated.resources.password_hide
import mahjongtournamentsuite.ui.generated.resources.password_show
import mahjongtournamentsuite.ui.generated.resources.sign_in_button
import mahjongtournamentsuite.ui.generated.resources.sign_in_description
import mahjongtournamentsuite.ui.generated.resources.sign_in_forgot_password
import mahjongtournamentsuite.ui.generated.resources.sign_in_title
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * The sign-in screen of the editor app and of the player app.
 * Both apps use it, so the two screens look the same. Only the logo changes ([LocalAppLogo]).
 */
@Composable
fun SignInForm(
    email: String,
    onEmailChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    isLoading: Boolean,
    errorMessage: UiText?,
    infoMessage: UiText?,
    emailRequester: FocusRequester,
    onSubmit: () -> Unit,
    onForgotPassword: () -> Unit,
    onErrorDismiss: () -> Unit,
    description: String = stringResource(Res.string.sign_in_description),
) {
    val focusManager = LocalFocusManager.current
    val passwordRequester = remember { FocusRequester() }
    val loginButtonRequester = remember { FocusRequester() }
    var isPasswordVisible by rememberSaveable { mutableStateOf(false) }

    AppScaffold(
        title = stringResource(Res.string.sign_in_title),
        isLoading = isLoading,
        autoFocusFirst = false,
        showBackgroundLogo = false,
        leadingActions = { AppTopBarLeadingActions(showThemeToggle = true) },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            ScreenColumn(
                maxWidth = 520.dp,
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 32.dp),
            ) {
                AppLogo(size = 144.dp)

                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        OutlinedTextField(
                            value = email,
                            onValueChange = onEmailChange,
                            label = { Text(stringResource(Res.string.field_email)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .contentType(ContentType.EmailAddress)
                                .focusRequester(emailRequester)
                                .onPreviewKeyEvent { e ->
                                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                    when (e.key) {
                                        Key.Tab -> {
                                            if (e.isShiftPressed) {
                                                focusManager.moveFocus(FocusDirection.Previous)
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
                            onValueChange = onPasswordChange,
                            label = { Text(stringResource(Res.string.field_password)) },
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
                                            onSubmit()
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
                                    Res.drawable.ic_visibility_off to stringResource(Res.string.password_hide)
                                } else {
                                    Res.drawable.ic_visibility to stringResource(Res.string.password_show)
                                }

                                FocusedIconButton(
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
                                    onSubmit()
                                },
                            ),
                        )

                        errorMessage?.let { message ->
                            AppErrorDialog(message = message, onDismiss = onErrorDismiss)
                        }

                        infoMessage?.let { message ->
                            Text(
                                text = message.asString(),
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }

                        FocusedTextButton(
                            enabled = !isLoading,
                            onClick = onForgotPassword,
                        ) {
                            Text(stringResource(Res.string.sign_in_forgot_password))
                        }

                        FocusedButton(
                            enabled = !isLoading,
                            onClick = onSubmit,
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
                                Text(stringResource(Res.string.sign_in_button))
                            }
                        }
                    }
                }
            }

            AppVersionLabel(
                modifier = Modifier.align(Alignment.BottomStart),
            )
        }
    }
}
