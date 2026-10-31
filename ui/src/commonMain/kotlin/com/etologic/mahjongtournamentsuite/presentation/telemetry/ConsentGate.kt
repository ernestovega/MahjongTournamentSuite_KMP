package com.etologic.mahjongtournamentsuite.presentation.telemetry

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedButton
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedOutlinedButton
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import com.etologic.mahjongtournamentsuite.presentation.theme.rememberThemeController
import mahjongtournamentsuite.ui.generated.resources.Res
import mahjongtournamentsuite.ui.generated.resources.consent_accept
import mahjongtournamentsuite.ui.generated.resources.consent_collects
import mahjongtournamentsuite.ui.generated.resources.consent_decline
import mahjongtournamentsuite.ui.generated.resources.consent_declined_note
import mahjongtournamentsuite.ui.generated.resources.consent_intro
import mahjongtournamentsuite.ui.generated.resources.consent_not_collected
import mahjongtournamentsuite.ui.generated.resources.consent_required
import mahjongtournamentsuite.ui.generated.resources.consent_title
import org.jetbrains.compose.resources.stringResource

/**
 * Withdraws the consent of the user. The app then shows the consent screen again and stays closed until the user
 * accepts. It is null when this app does not ask for consent.
 */
val LocalWithdrawConsent = staticCompositionLocalOf<(() -> Unit)?> { null }

/**
 * Shows [content] only after the user accepted the anonymous analytics. Without the answer the user cannot enter.
 * A platform that sends no data does not ask, so [content] opens at once.
 */
@Composable
fun ConsentGate(content: @Composable () -> Unit) {
    val telemetry = LocalTelemetry.current
    if (!telemetry.requiresConsent) {
        content()
        return
    }
    var accepted by remember(telemetry) { mutableStateOf(telemetry.hasConsent()) }
    if (accepted) {
        CompositionLocalProvider(
            LocalWithdrawConsent provides {
                telemetry.setConsent(false)
                accepted = false
            },
            content = content,
        )
    } else {
        val themeController = rememberThemeController()
        MtsTheme(useDarkTheme = themeController.isDarkTheme) {
            ConsentScreen(onAccept = {
                telemetry.setConsent(true)
                accepted = true
            })
        }
    }
}

@Composable
private fun ConsentScreen(onAccept: () -> Unit) {
    var declined by remember { mutableStateOf(false) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                Modifier
                    .widthIn(max = 560.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(Res.string.consent_title), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(Res.string.consent_intro), style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(Res.string.consent_collects), style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(Res.string.consent_not_collected), style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(Res.string.consent_required), style = MaterialTheme.typography.bodyLarge)
                if (declined) {
                    Text(
                        stringResource(Res.string.consent_declined_note),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(Modifier.height(4.dp))
                FocusedButton(onClick = onAccept, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(Res.string.consent_accept))
                }
                FocusedOutlinedButton(onClick = { declined = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(Res.string.consent_decline))
                }
            }
        }
    }
}
