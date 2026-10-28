package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.etologic.mahjongtournamentsuite.domain.model.PlayerAccountActionResult
import com.etologic.mahjongtournamentsuite.domain.model.PlayerAccountFailure
import com.etologic.mahjongtournamentsuite.domain.model.PlayerAccountOutcome
import com.etologic.mahjongtournamentsuite.presentation.components.appFocusGroup
import com.etologic.mahjongtournamentsuite.presentation.util.UiText
import com.etologic.mahjongtournamentsuite.presentation.util.asString
import mahjongtournamentsuite.adminapp.generated.resources.Res
import mahjongtournamentsuite.adminapp.generated.resources.dialog_done_title
import mahjongtournamentsuite.adminapp.generated.resources.failure_email_failed
import mahjongtournamentsuite.adminapp.generated.resources.failure_email_in_use
import mahjongtournamentsuite.adminapp.generated.resources.failure_no_ema_id
import mahjongtournamentsuite.adminapp.generated.resources.failure_no_email
import mahjongtournamentsuite.adminapp.generated.resources.failure_player_not_found
import mahjongtournamentsuite.adminapp.generated.resources.failure_unexpected
import mahjongtournamentsuite.adminapp.generated.resources.ok
import mahjongtournamentsuite.adminapp.generated.resources.outcome_invited
import mahjongtournamentsuite.adminapp.generated.resources.outcome_reset_sent
import mahjongtournamentsuite.adminapp.generated.resources.something_went_wrong
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** The result of one account action, shown in its own dialog. */
data class AccountOutcome(val message: UiText, val isError: Boolean)

internal fun PlayerAccountFailure.textRes(): StringResource = when (this) {
    PlayerAccountFailure.PLAYER_NOT_FOUND -> Res.string.failure_player_not_found
    PlayerAccountFailure.NO_EMA_ID -> Res.string.failure_no_ema_id
    PlayerAccountFailure.NO_EMAIL -> Res.string.failure_no_email
    PlayerAccountFailure.EMAIL_IN_USE -> Res.string.failure_email_in_use
    PlayerAccountFailure.EMAIL_FAILED -> Res.string.failure_email_failed
    PlayerAccountFailure.UNEXPECTED -> Res.string.failure_unexpected
}

/** The plain sentence for the result of one account action on one slot. */
internal fun PlayerAccountActionResult.toOutcome(): AccountOutcome = when (outcome) {
    PlayerAccountOutcome.INVITED -> AccountOutcome(UiText.of(Res.string.outcome_invited), isError = false)
    PlayerAccountOutcome.RESET_SENT -> AccountOutcome(UiText.of(Res.string.outcome_reset_sent), isError = false)
    PlayerAccountOutcome.FAILED -> AccountOutcome(
        UiText.of((reason ?: PlayerAccountFailure.UNEXPECTED).textRes()),
        isError = true,
    )
}

@Composable
fun AccountOutcomeDialog(outcome: AccountOutcome, onDismiss: () -> Unit) {
    val color = if (outcome.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    AlertDialog(
        modifier = Modifier.appFocusGroup(),
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                if (outcome.isError) Icons.Filled.Warning else Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = color,
            )
        },
        title = { Text(stringResource(if (outcome.isError) Res.string.something_went_wrong else Res.string.dialog_done_title), color = color) },
        text = { Text(outcome.message.asString(), style = MaterialTheme.typography.bodyLarge) },
        confirmButton = { Button(onClick = onDismiss) { Text(stringResource(Res.string.ok)) } },
    )
}
