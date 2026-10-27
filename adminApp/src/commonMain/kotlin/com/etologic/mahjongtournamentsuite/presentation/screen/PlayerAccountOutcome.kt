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
import com.etologic.mahjongtournamentsuite.domain.model.PlayerContactState
import com.etologic.mahjongtournamentsuite.presentation.components.appFocusGroup
import com.etologic.mahjongtournamentsuite.presentation.util.UiText
import com.etologic.mahjongtournamentsuite.presentation.util.asString
import com.etologic.mahjongtournamentsuite.presentation.util.localizedDateTimeOrNull
import mahjongtournamentsuite.adminapp.generated.resources.Res
import mahjongtournamentsuite.adminapp.generated.resources.contact_invited
import mahjongtournamentsuite.adminapp.generated.resources.contact_no_ema_id
import mahjongtournamentsuite.adminapp.generated.resources.contact_state_linked
import mahjongtournamentsuite.adminapp.generated.resources.contact_state_not_invited
import mahjongtournamentsuite.adminapp.generated.resources.contact_state_reminded
import mahjongtournamentsuite.adminapp.generated.resources.dialog_done_title
import mahjongtournamentsuite.adminapp.generated.resources.ok
import mahjongtournamentsuite.adminapp.generated.resources.outcome_failed
import mahjongtournamentsuite.adminapp.generated.resources.outcome_invited
import mahjongtournamentsuite.adminapp.generated.resources.outcome_notified
import mahjongtournamentsuite.adminapp.generated.resources.outcome_revoked
import mahjongtournamentsuite.adminapp.generated.resources.outcome_skipped
import mahjongtournamentsuite.adminapp.generated.resources.something_went_wrong
import org.jetbrains.compose.resources.stringResource

internal val EMAIL_PATTERN = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

/** The time of a contact as local date and time in the device language. */
internal fun contactTimeText(isoTime: String?): String? = localizedDateTimeOrNull(isoTime)

@Composable
internal fun PlayerContactState.label(): String = stringResource(
    when (this) {
        PlayerContactState.NO_EMA_ID -> Res.string.contact_no_ema_id
        PlayerContactState.NOT_INVITED -> Res.string.contact_state_not_invited
        PlayerContactState.LINKED -> Res.string.contact_state_linked
        PlayerContactState.INVITED -> Res.string.contact_invited
        PlayerContactState.CONTACTED -> Res.string.contact_state_reminded
    },
)

/** The result of one account action, shown in its own dialog after the account dialog closes. */
data class AccountOutcome(val message: UiText, val isError: Boolean)

/** The plain sentence for the result of one account action on one slot. */
internal fun PlayerAccountActionResult.toOutcome(): AccountOutcome {
    val detail = message?.let { " $it" }.orEmpty()
    return when (outcome.name) {
        "FAILED" -> AccountOutcome(UiText.of(Res.string.outcome_failed, detail), isError = true)
        "SKIPPED" -> AccountOutcome(UiText.of(Res.string.outcome_skipped, detail), isError = false)
        "INVITED" -> AccountOutcome(UiText.of(Res.string.outcome_invited, detail), isError = false)
        "NOTIFIED" -> AccountOutcome(UiText.of(Res.string.outcome_notified, detail), isError = false)
        "REVOKED" -> AccountOutcome(UiText.of(Res.string.outcome_revoked, detail), isError = false)
        else -> AccountOutcome(UiText.Plain(outcome.name.lowercase().replaceFirstChar(Char::uppercase) + "." + detail), isError = false)
    }
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
