package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.autofill.contentType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.SupportMessage
import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryEvent
import com.etologic.mahjongtournamentsuite.domain.telemetry.track
import com.etologic.mahjongtournamentsuite.presentation.telemetry.LocalTelemetry
import com.etologic.mahjongtournamentsuite.presentation.util.UiText
import com.etologic.mahjongtournamentsuite.presentation.util.toUiText
import kotlinx.coroutines.launch
import mahjongtournamentsuite.ui.generated.resources.Res
import mahjongtournamentsuite.ui.generated.resources.field_email
import mahjongtournamentsuite.ui.generated.resources.support_email_missing
import mahjongtournamentsuite.ui.generated.resources.support_field_message
import mahjongtournamentsuite.ui.generated.resources.support_field_name
import mahjongtournamentsuite.ui.generated.resources.support_intro
import mahjongtournamentsuite.ui.generated.resources.support_message_missing
import mahjongtournamentsuite.ui.generated.resources.support_send
import mahjongtournamentsuite.ui.generated.resources.support_sent
import mahjongtournamentsuite.ui.generated.resources.support_title
import org.jetbrains.compose.resources.stringResource

/**
 * The "Need help?" screen of the sign-in. It sends a message to the support of the apps through the server,
 * which keeps the support address. [app] tells support which app sent it, for example "player".
 */
@Composable
fun SupportForm(
    app: String,
    initialEmail: String,
    onSend: suspend (SupportMessage) -> AppResult<Unit>,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val appVersion = LocalAppVersion.current
    val telemetry = LocalTelemetry.current
    val nameRequester = remember { FocusRequester() }
    val emailRequester = remember { FocusRequester() }
    val messageRequester = remember { FocusRequester() }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf(initialEmail.trim()) }
    var message by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var sent by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<UiText?>(null) }

    LaunchedEffect(Unit) {
        if (email.isEmpty()) emailRequester.requestFocusOnOpen() else messageRequester.requestFocusOnOpen()
    }

    fun submit() {
        if (busy) return
        when {
            email.isBlank() -> {
                errorMessage = UiText.of(Res.string.support_email_missing)
                emailRequester.requestFocus()
                return
            }
            message.isBlank() -> {
                errorMessage = UiText.of(Res.string.support_message_missing)
                messageRequester.requestFocus()
                return
            }
        }
        busy = true
        scope.launch {
            val result = onSend(SupportMessage(name = name, email = email, message = message, app = app, appVersion = appVersion))
                .track(telemetry, TelemetryEvent.SUPPORT_SENT)
            when (result) {
                is AppResult.Success -> sent = true
                is AppResult.Failure -> errorMessage = result.error.toUiText()
            }
            busy = false
        }
    }

    AppScaffold(
        title = stringResource(Res.string.support_title),
        isLoading = busy,
        autoFocusFirst = false,
        onBack = onBack,
    ) {
        ScreenColumn(
            maxWidth = 520.dp,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 32.dp),
            scrollable = true,
        ) {
            Text(
                text = stringResource(Res.string.support_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(100) },
                        label = { Text(stringResource(Res.string.support_field_name)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .contentType(ContentType.PersonFullName)
                            .focusRequester(nameRequester),
                        singleLine = true,
                        enabled = !busy,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                    )
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it.take(254) },
                        label = { Text(stringResource(Res.string.field_email)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .contentType(ContentType.EmailAddress)
                            .focusRequester(emailRequester),
                        singleLine = true,
                        enabled = !busy,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    )
                    OutlinedTextField(
                        value = message,
                        onValueChange = { message = it.take(MAX_MESSAGE_LENGTH) },
                        label = { Text(stringResource(Res.string.support_field_message)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 160.dp)
                            .focusRequester(messageRequester),
                        minLines = 6,
                        enabled = !busy,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        supportingText = { Text("${message.length} / $MAX_MESSAGE_LENGTH") },
                    )
                    FocusedButton(
                        enabled = !busy,
                        onClick = ::submit,
                        modifier = Modifier.fillMaxWidth(),
                        buttonModifier = Modifier.fillMaxWidth(),
                    ) {
                        if (busy) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Text(stringResource(Res.string.support_send))
                        }
                    }
                }
            }
        }
    }

    errorMessage?.let { AppErrorDialog(message = it, onDismiss = { errorMessage = null }) }
    if (sent) AppSuccessDialog(message = UiText.of(Res.string.support_sent), onDismiss = onBack)
}

private const val MAX_MESSAGE_LENGTH = 5000
