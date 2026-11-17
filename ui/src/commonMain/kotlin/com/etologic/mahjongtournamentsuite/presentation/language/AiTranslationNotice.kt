package com.etologic.mahjongtournamentsuite.presentation.language

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedButton
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedTextButton
import com.etologic.mahjongtournamentsuite.presentation.components.MtsDialogButtons
import com.etologic.mahjongtournamentsuite.presentation.components.MtsDialogTitle
import com.etologic.mahjongtournamentsuite.presentation.components.appFocusGroup
import com.etologic.mahjongtournamentsuite.presentation.components.focusLoop
import com.etologic.mahjongtournamentsuite.presentation.telemetry.TrackedDialog
import mahjongtournamentsuite.ui.generated.resources.Res
import mahjongtournamentsuite.ui.generated.resources.action_ok
import mahjongtournamentsuite.ui.generated.resources.ai_translation_contact
import mahjongtournamentsuite.ui.generated.resources.ai_translation_message
import mahjongtournamentsuite.ui.generated.resources.ai_translation_title
import org.jetbrains.compose.resources.stringResource

/**
 * A notice, one time, when the app shows a language other than English: those translations were made with AI and
 * nobody has checked them yet, so the user can report a mistake. [onContact] opens the support form.
 */
@Composable
fun AiTranslationNotice(onContact: () -> Unit) {
    val controller = LocalLanguageController.current
    if (!controller.showAiNotice) return
    val okFocus = remember { FocusRequester() }
    val contactFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { okFocus.requestFocus() } }

    TrackedDialog("AiTranslationNotice") {
        AlertDialog(
            modifier = Modifier.appFocusGroup(),
            onDismissRequest = controller.onAiNoticeSeen,
            title = { MtsDialogTitle(stringResource(Res.string.ai_translation_title)) },
            text = { Text(stringResource(Res.string.ai_translation_message), style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                MtsDialogButtons {
                    FocusedButton(
                        onClick = controller.onAiNoticeSeen,
                        focusRequester = okFocus,
                        buttonModifier = Modifier.focusLoop(contactFocus, contactFocus),
                    ) { Text(stringResource(Res.string.action_ok)) }

                    FocusedTextButton(
                        onClick = {
                            controller.onAiNoticeSeen()
                            onContact()
                        },
                        focusRequester = contactFocus,
                        buttonModifier = Modifier.focusLoop(okFocus, okFocus),
                    ) { Text(stringResource(Res.string.ai_translation_contact)) }
                }
            },
        )
    }
}
