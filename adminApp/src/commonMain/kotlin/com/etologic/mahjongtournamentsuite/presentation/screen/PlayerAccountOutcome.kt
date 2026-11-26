package com.etologic.mahjongtournamentsuite.presentation.screen

import com.etologic.mahjongtournamentsuite.presentation.components.ButtonText
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.domain.model.PlayerAccountActionResult
import com.etologic.mahjongtournamentsuite.domain.model.PlayerAccountFailure
import com.etologic.mahjongtournamentsuite.domain.model.PlayerAccountOutcome
import com.etologic.mahjongtournamentsuite.domain.model.PlayerEmailFailure
import com.etologic.mahjongtournamentsuite.domain.model.PlayerEmailResult
import com.etologic.mahjongtournamentsuite.presentation.components.MtsDialogButtons
import com.etologic.mahjongtournamentsuite.presentation.components.MtsDialogText
import com.etologic.mahjongtournamentsuite.presentation.components.MtsDialogTitle
import com.etologic.mahjongtournamentsuite.presentation.components.appFocusGroup
import com.etologic.mahjongtournamentsuite.presentation.telemetry.TrackedDialog
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
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
import mahjongtournamentsuite.adminapp.generated.resources.password_resets_partial_title
import mahjongtournamentsuite.adminapp.generated.resources.send_section_failed
import mahjongtournamentsuite.adminapp.generated.resources.send_section_invited
import mahjongtournamentsuite.adminapp.generated.resources.send_section_reminded
import mahjongtournamentsuite.adminapp.generated.resources.sending_text
import mahjongtournamentsuite.adminapp.generated.resources.sending_title
import mahjongtournamentsuite.adminapp.generated.resources.something_went_wrong
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import com.etologic.mahjongtournamentsuite.presentation.components.DebouncedButton as Button

/** A check for a player with an account and a cross for a player without one. The description names the state for screen readers. */
@Composable
internal fun AccountStateIcon(hasAccount: Boolean, modifier: Modifier = Modifier) {
    Icon(
        imageVector = if (hasAccount) Icons.Outlined.Check else Icons.Outlined.Close,
        contentDescription = stringResource(if (hasAccount) Res.string.account_has else Res.string.account_none),
        tint = if (hasAccount) MtsTheme.colors.link else MaterialTheme.colorScheme.error,
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

/** Tells the user that the emails are going out. It has no button: the app closes it when the send ends. */
@Composable
internal fun SendingDialog() {
    TrackedDialog("AccountActionsSending") {
        AlertDialog(
            modifier = Modifier.appFocusGroup(),
            onDismissRequest = {},
            title = { MtsDialogTitle(stringResource(Res.string.sending_title)) },
            // The text first and the progress under it, both centered.
            text = {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    MtsDialogText(stringResource(Res.string.sending_text))
                    CircularProgressIndicator()
                }
            },
            confirmButton = {},
        )
    }
}

/**
 * The end of a send for many players: how many accounts and reminders went out, and one line for each player that failed.
 * [nameOf] gives the text that names a player in the list.
 */
@Composable
internal fun SendSummaryDialog(
    results: List<PlayerAccountActionResult>,
    nameOf: (Int) -> String,
    onDismiss: () -> Unit,
) {
    val invited = results.filter { it.outcome == PlayerAccountOutcome.INVITED }
    val reminded = results.filter { it.outcome == PlayerAccountOutcome.REMINDED }
    val failed = results.filter { it.outcome == PlayerAccountOutcome.FAILED }
    TrackedDialog("AccountActionsDone") {
        AlertDialog(
            modifier = Modifier.appFocusGroup(),
            onDismissRequest = onDismiss,
            title = { MtsDialogTitle(stringResource(if (failed.isEmpty()) Res.string.dialog_done_title else Res.string.password_resets_partial_title)) },
            // One section for each outcome that happened: its title, its count and its players. A failed one shows its reason.
            text = {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (invited.isNotEmpty()) {
                        PlayerLinesSection(
                            Icons.Filled.PersonAdd,
                            stringResource(Res.string.send_section_invited),
                            invited.map { PlayerLine(nameOf(it.playerId), emptyList()) },
                            maxHeight = 120.dp,
                        )
                    }
                    if (reminded.isNotEmpty()) {
                        PlayerLinesSection(
                            Icons.Filled.NotificationsActive,
                            stringResource(Res.string.send_section_reminded),
                            reminded.map { PlayerLine(nameOf(it.playerId), emptyList()) },
                            maxHeight = 120.dp,
                        )
                    }
                    if (failed.isNotEmpty()) {
                        PlayerLinesSection(
                            Icons.Filled.ErrorOutline,
                            stringResource(Res.string.send_section_failed),
                            failed.map { result ->
                                PlayerLine(nameOf(result.playerId), listOf(stringResource((result.reason ?: PlayerAccountFailure.UNEXPECTED).textRes())))
                            },
                            color = MaterialTheme.colorScheme.error,
                            detailColor = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                MtsDialogButtons {
                    Button(onClick = onDismiss) { ButtonText(stringResource(Res.string.ok)) }
                }
            },
        )
    }
}

@Composable
fun AccountOutcomeDialog(outcome: AccountOutcome, onDismiss: () -> Unit) {
    TrackedDialog("AccountActionsError") {
        AlertDialog(
            modifier = Modifier.appFocusGroup(),
            onDismissRequest = onDismiss,
            title = { MtsDialogTitle(stringResource(if (outcome.isError) Res.string.something_went_wrong else Res.string.dialog_done_title)) },
            text = {
                Text(
                    outcome.message.asString(),
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
            },
            confirmButton = {
                MtsDialogButtons {
                    Button(onClick = onDismiss) { ButtonText(stringResource(Res.string.ok)) }
                }
            },
        )
    }
}
