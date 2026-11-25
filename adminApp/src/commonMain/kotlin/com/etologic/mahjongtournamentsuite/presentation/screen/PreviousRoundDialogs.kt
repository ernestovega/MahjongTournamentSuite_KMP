package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.etologic.mahjongtournamentsuite.presentation.components.AppErrorDialog
import com.etologic.mahjongtournamentsuite.presentation.components.ButtonText
import com.etologic.mahjongtournamentsuite.presentation.components.MtsButtons
import com.etologic.mahjongtournamentsuite.presentation.components.MtsDialogButtons
import com.etologic.mahjongtournamentsuite.presentation.components.MtsDialogText
import com.etologic.mahjongtournamentsuite.presentation.components.MtsDialogTitle
import com.etologic.mahjongtournamentsuite.presentation.components.appFocusGroup
import com.etologic.mahjongtournamentsuite.presentation.telemetry.TrackedDialog
import com.etologic.mahjongtournamentsuite.presentation.util.toUiText
import kotlinx.coroutines.launch
import mahjongtournamentsuite.adminapp.generated.resources.Res
import mahjongtournamentsuite.adminapp.generated.resources.cancel
import mahjongtournamentsuite.adminapp.generated.resources.round_close_previous_confirm
import mahjongtournamentsuite.adminapp.generated.resources.round_close_previous_message
import mahjongtournamentsuite.adminapp.generated.resources.round_close_previous_never_started
import mahjongtournamentsuite.adminapp.generated.resources.round_close_previous_title
import org.jetbrains.compose.resources.stringResource
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedButton as Button

/**
 * The answers of the server to a Play whose round before is not closed (Ernesto, 2026-11-25), on the Timer and on Round
 * control. Open tables: "Close them and start" completes them and starts the round. Still in play: an error that says
 * to stop it first.
 */
@Composable
internal fun PreviousRoundDialogs(state: RoundTimerState, controller: RoundTimerController) {
    val scope = rememberCoroutineScope()
    state.closePrevious?.let { open ->
        TrackedDialog("ConfirmClosePreviousRound") {
            AlertDialog(
                modifier = Modifier.appFocusGroup(),
                onDismissRequest = { controller.dismissClosePrevious() },
                title = { MtsDialogTitle(stringResource(Res.string.round_close_previous_title, state.roundId)) },
                text = {
                    MtsDialogText(
                        stringResource(
                            if (open.started) Res.string.round_close_previous_message else Res.string.round_close_previous_never_started,
                            open.roundId,
                            open.tables,
                        ),
                    )
                },
                confirmButton = {
                    MtsDialogButtons {
                        // It closes tables of the players: a destructive action, in red.
                        Button(onClick = { scope.launch { controller.startClosingPrevious() } }, colors = MtsButtons.destructiveColors()) {
                            ButtonText(stringResource(Res.string.round_close_previous_confirm))
                        }
                        Button(onClick = { controller.dismissClosePrevious() }) { ButtonText(stringResource(Res.string.cancel)) }
                    }
                },
            )
        }
    }
    state.refusal?.let { error -> AppErrorDialog(error.toUiText(), onDismiss = { controller.dismissRefusal() }) }
}
