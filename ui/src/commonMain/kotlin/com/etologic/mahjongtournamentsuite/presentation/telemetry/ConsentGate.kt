package com.etologic.mahjongtournamentsuite.presentation.telemetry

import com.etologic.mahjongtournamentsuite.presentation.components.ButtonText
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedButton
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedOutlinedButton
import com.etologic.mahjongtournamentsuite.presentation.components.MtsScrollbar
import com.etologic.mahjongtournamentsuite.presentation.components.verticalScrollWithIndicators
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import com.etologic.mahjongtournamentsuite.presentation.theme.rememberThemeController
import mahjongtournamentsuite.ui.generated.resources.Res
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.SentimentVerySatisfied
import androidx.compose.material3.Icon
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedTextButton
import org.jetbrains.compose.resources.StringResource
import mahjongtournamentsuite.ui.generated.resources.consent_accept
import mahjongtournamentsuite.ui.generated.resources.consent_change
import mahjongtournamentsuite.ui.generated.resources.consent_change_title
import mahjongtournamentsuite.ui.generated.resources.consent_decline
import mahjongtournamentsuite.ui.generated.resources.consent_message
import mahjongtournamentsuite.ui.generated.resources.consent_more_details
import mahjongtournamentsuite.ui.generated.resources.consent_title
import mahjongtournamentsuite.ui.generated.resources.consent_what
import mahjongtournamentsuite.ui.generated.resources.consent_what_title
import mahjongtournamentsuite.ui.generated.resources.consent_where
import mahjongtournamentsuite.ui.generated.resources.consent_where_title
import mahjongtournamentsuite.ui.generated.resources.consent_why
import mahjongtournamentsuite.ui.generated.resources.consent_why_title
import org.jetbrains.compose.resources.stringResource

/**
 * The answer of the user to the anonymous analytics, and how to change it. The profile has a button that shows the
 * consent screen again ([ConsentDialog]).
 * It is null when this app does not ask for consent.
 */
class AnalyticsConsent(val enabled: Boolean, val onChange: (Boolean) -> Unit)

val LocalAnalyticsConsent = compositionLocalOf<AnalyticsConsent?> { null }

/**
 * Asks once for the anonymous analytics before [content]. The answer is optional: after "Decline" the app opens the
 * same way and sends nothing. The user can change the answer in the profile at any time, without leaving the app.
 * A platform that sends no data does not ask, so [content] opens at once.
 */
@Composable
fun ConsentGate(content: @Composable () -> Unit) {
    val telemetry = LocalTelemetry.current
    if (!telemetry.requiresConsent) {
        content()
        return
    }
    var answered by remember(telemetry) { mutableStateOf(telemetry.hasAnswered()) }
    var accepted by remember(telemetry) { mutableStateOf(telemetry.hasConsent()) }
    val answer: (Boolean) -> Unit = { granted ->
        telemetry.setConsent(granted)
        accepted = granted
        answered = true
    }
    if (answered) {
        CompositionLocalProvider(LocalAnalyticsConsent provides AnalyticsConsent(accepted, answer), content = content)
    } else {
        val themeController = rememberThemeController()
        MtsTheme(useDarkTheme = themeController.isDarkTheme) {
            ConsentScreen(onAnswer = answer)
        }
    }
}

/**
 * One short question, with the details folded under "More details": why, what and where. Accept and Decline are
 * the same size, so saying no is as easy as saying yes.
 */
@Composable
internal fun ConsentScreen(onAnswer: (Boolean) -> Unit, initiallyShowDetails: Boolean = false) {
    var showDetails by rememberSaveable { mutableStateOf(initiallyShowDetails) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                Modifier
                    .widthIn(max = 480.dp)
                    .verticalScrollWithIndicators(rememberScrollState(), MtsScrollbar.Overlay)
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(Res.string.consent_title), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(Res.string.consent_message), style = MaterialTheme.typography.bodyLarge)
                FocusedTextButton(
                    onClick = { showDetails = !showDetails },
                    buttonModifier = Modifier.testTag(CONSENT_DETAILS_TAG),
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp),
                ) {
                    ButtonText(stringResource(Res.string.consent_more_details))
                    Icon(
                        if (showDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
                AnimatedVisibility(showDetails) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ConsentDetail(Res.string.consent_why_title, Res.string.consent_why)
                        ConsentDetail(Res.string.consent_what_title, Res.string.consent_what)
                        ConsentDetail(Res.string.consent_where_title, Res.string.consent_where)
                        ConsentDetail(Res.string.consent_change_title, Res.string.consent_change)
                    }
                }
                Spacer(Modifier.height(4.dp))
                // A happy face on Accept asks for the help with a smile (Ernesto, 2026-11-27).
                FocusedButton(onClick = { onAnswer(true) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.SentimentVerySatisfied, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    ButtonText(stringResource(Res.string.consent_accept))
                }
                FocusedOutlinedButton(onClick = { onAnswer(false) }, modifier = Modifier.fillMaxWidth()) {
                    ButtonText(stringResource(Res.string.consent_decline))
                }
            }
        }
    }
}

/**
 * The consent screen of the first start, over the whole window, opened again from the profile. [onAnswer] gets the
 * new answer. Back or Escape closes it and changes nothing.
 */
@Composable
fun ConsentDialog(onAnswer: (Boolean) -> Unit, onDismiss: () -> Unit) {
    TrackedDialog("Consent") {
        Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().testTag(CONSENT_DIALOG_TAG)) {
                ConsentScreen(onAnswer = onAnswer)
            }
        }
    }
}

@Composable
private fun ConsentDetail(title: StringResource, text: StringResource) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(stringResource(text), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The consent screen opened again from the profile. */
const val CONSENT_DIALOG_TAG = "consent-dialog"

/** The "More details" button of the consent screen. */
const val CONSENT_DETAILS_TAG = "consent-details"
