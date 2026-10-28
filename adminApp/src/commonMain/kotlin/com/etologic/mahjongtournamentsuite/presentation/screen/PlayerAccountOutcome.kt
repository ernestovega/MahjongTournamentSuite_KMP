package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import com.etologic.mahjongtournamentsuite.presentation.components.DebouncedButton as Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.etologic.mahjongtournamentsuite.domain.model.PlayerAccountActionResult
import com.etologic.mahjongtournamentsuite.domain.model.PlayerAccountFailure
import com.etologic.mahjongtournamentsuite.domain.model.PlayerAccountOutcome
import com.etologic.mahjongtournamentsuite.domain.model.PlayerEmailFailure
import com.etologic.mahjongtournamentsuite.domain.model.PlayerEmailResult
import com.etologic.mahjongtournamentsuite.presentation.components.appFocusGroup
import com.etologic.mahjongtournamentsuite.presentation.util.UiText
import com.etologic.mahjongtournamentsuite.presentation.util.asString
import mahjongtournamentsuite.adminapp.generated.resources.Res
import mahjongtournamentsuite.adminapp.generated.resources.account_has
import mahjongtournamentsuite.adminapp.generated.resources.account_none
import mahjongtournamentsuite.adminapp.generated.resources.dialog_done_title
import mahjongtournamentsuite.adminapp.generated.resources.failure_email_failed
import mahjongtournamentsuite.adminapp.generated.resources.failure_email_in_use
import mahjongtournamentsuite.adminapp.generated.resources.failure_invalid_email
import mahjongtournamentsuite.adminapp.generated.resources.failure_no_ema_id
import mahjongtournamentsuite.adminapp.generated.resources.failure_no_email
import mahjongtournamentsuite.adminapp.generated.resources.failure_player_not_found
import mahjongtournamentsuite.adminapp.generated.resources.failure_unexpected
import mahjongtournamentsuite.adminapp.generated.resources.ok
import mahjongtournamentsuite.adminapp.generated.resources.outcome_invited
import mahjongtournamentsuite.adminapp.generated.resources.outcome_reminded
import mahjongtournamentsuite.adminapp.generated.resources.something_went_wrong
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** A check for a player with an account and a cross for a player without one. The description names the state for screen readers. */
@Composable
internal fun AccountStateIcon(hasAccount: Boolean, modifier: Modifier = Modifier) {
    Icon(
        imageVector = if (hasAccount) Icons.Outlined.Check else Icons.Outlined.Close,
        contentDescription = stringResource(if (hasAccount) Res.string.account_has else Res.string.account_none),
        tint = if (hasAccount) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        modifier = modifier,
    )
}

/** The result of one account action, shown in its own dialog. */
data class AccountOutcome(val message: UiText, val isError: Boolean)

internal fun PlayerAccountFailure.textRes(): StringResource = when (this) {
    PlayerAccountFailure.PLAYER_NOT_FOUND -> Res.string.failure_player_not_found
    PlayerAccountFailure.NO_EMA_ID -> Res.string.failure_no_ema_id
    PlayerAccountFailure.NO_EMAIL -> Res.string.failure_no_email
    PlayerAccountFailure.EMAIL_IN_USE -> Res.string.failure_email_in_use
    PlayerAccountFailure.INVALID_EMAIL -> Res.string.failure_invalid_email
    PlayerAccountFailure.EMAIL_FAILED -> Res.string.failure_email_failed
    PlayerAccountFailure.UNEXPECTED -> Res.string.failure_unexpected
}

/** A pending email that the server refused, as a failed account action, so the dialogs show it like any other failure. */
internal fun PlayerEmailResult.toFailedAction(): PlayerAccountActionResult = PlayerAccountActionResult(
    playerId = playerId,
    outcome = PlayerAccountOutcome.FAILED,
    reason = when (reason) {
        PlayerEmailFailure.PLAYER_NOT_FOUND -> PlayerAccountFailure.PLAYER_NOT_FOUND
        PlayerEmailFailure.NO_EMA_ID -> PlayerAccountFailure.NO_EMA_ID
        PlayerEmailFailure.EMAIL_IN_USE -> PlayerAccountFailure.EMAIL_IN_USE
        PlayerEmailFailure.INVALID_EMAIL -> PlayerAccountFailure.INVALID_EMAIL
        PlayerEmailFailure.HAS_ACCOUNT, null -> PlayerAccountFailure.UNEXPECTED
    },
)

/** The plain sentence for the result of one account action on one slot. */
internal fun PlayerAccountActionResult.toOutcome(): AccountOutcome = when (outcome) {
    PlayerAccountOutcome.INVITED -> AccountOutcome(UiText.of(Res.string.outcome_invited), isError = false)
    PlayerAccountOutcome.REMINDED -> AccountOutcome(UiText.of(Res.string.outcome_reminded), isError = false)
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
