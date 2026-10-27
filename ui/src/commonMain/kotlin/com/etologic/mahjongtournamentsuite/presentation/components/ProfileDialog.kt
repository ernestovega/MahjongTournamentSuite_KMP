package com.etologic.mahjongtournamentsuite.presentation.components

import com.etologic.mahjongtournamentsuite.presentation.language.LanguageSelector
import com.etologic.mahjongtournamentsuite.presentation.theme.ThemeSelector
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.UserProfile
import com.etologic.mahjongtournamentsuite.presentation.util.UiText
import com.etologic.mahjongtournamentsuite.presentation.util.asString
import com.etologic.mahjongtournamentsuite.presentation.util.toUiText
import kotlinx.coroutines.launch
import mahjongtournamentsuite.ui.generated.resources.Res
import mahjongtournamentsuite.ui.generated.resources.action_close
import mahjongtournamentsuite.ui.generated.resources.action_logout
import mahjongtournamentsuite.ui.generated.resources.action_save
import mahjongtournamentsuite.ui.generated.resources.field_email
import mahjongtournamentsuite.ui.generated.resources.profile_alias
import mahjongtournamentsuite.ui.generated.resources.profile_ema_number
import mahjongtournamentsuite.ui.generated.resources.profile_ema_number_info
import mahjongtournamentsuite.ui.generated.resources.profile_title
import org.jetbrains.compose.resources.stringResource

/** Longest alias that the server accepts. */
const val MAX_ALIAS_LENGTH = 60

/**
 * The profile of the signed-in user. The user can change the alias and the photo.
 * The email and the EMA number are shown only. Admins set them.
 * [onSaveAlias] saves the new alias. After a save, [onSaved] gets the new profile and the dialog closes.
 * [onSavePhoto] and [onRemovePhoto] save the own photo. After a save, [onSaved] gets the new profile and the dialog stays open.
 * When [onSignOut] is set, the dialog shows a red Logout button at the left of the button row that calls it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileDialog(
    profile: UserProfile,
    onSaveAlias: suspend (String) -> AppResult<UserProfile>,
    onSavePhoto: suspend (contentType: String, bytes: ByteArray) -> AppResult<UserProfile>,
    onRemovePhoto: suspend () -> AppResult<UserProfile>,
    onSaved: (UserProfile) -> Unit,
    onDismiss: () -> Unit,
    onSignOut: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    var alias by remember(profile.uid) { mutableStateOf(profile.alias) }
    var photoProfile by remember(profile.uid) { mutableStateOf(profile) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<UiText?>(null) }
    val aliasFocusRequester = remember { FocusRequester() }
    val trimmedAlias = alias.trim()
    val canSave = !saving && trimmedAlias.isNotEmpty() && trimmedAlias != profile.alias.trim()

    LaunchedEffect(Unit) { aliasFocusRequester.requestFocus() }

    // The function reads the state when it runs. A callback that captured older values could save a stale alias.
    fun save() {
        val newAlias = alias.trim()
        if (saving || newAlias.isEmpty() || newAlias == profile.alias.trim()) return
        saving = true
        error = null
        scope.launch {
            when (val result = onSaveAlias(newAlias)) {
                is AppResult.Success -> {
                    onSaved(result.value)
                    onDismiss()
                }

                is AppResult.Failure -> error = result.error.toUiText()
            }
            saving = false
        }
    }

    suspend fun savePhoto(call: suspend () -> AppResult<UserProfile>): UiText? = when (val result = call()) {
        is AppResult.Success -> {
            photoProfile = result.value
            onSaved(result.value)
            null
        }

        is AppResult.Failure -> result.error.toUiText()
    }

    AlertDialog(
        modifier = Modifier.appFocusGroup(),
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(stringResource(Res.string.profile_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PhotoEditor(
                    photoUrl = photoProfile.photoUrl,
                    hasOwnPhoto = photoProfile.hasOwnPhoto,
                    name = profile.alias.ifBlank { profile.email },
                    onUpload = { contentType, bytes -> savePhoto { onSavePhoto(contentType, bytes) } },
                    onRemove = { savePhoto { onRemovePhoto() } },
                )
                OutlinedTextField(
                    value = alias,
                    onValueChange = {
                        alias = it.take(MAX_ALIAS_LENGTH)
                        error = null
                    },
                    label = { Text(stringResource(Res.string.profile_alias)) },
                    supportingText = error?.let { message -> { Text(message.asString()) } },
                    isError = error != null,
                    singleLine = true,
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth().focusRequester(aliasFocusRequester),
                )
                OutlinedTextField(
                    value = profile.email,
                    onValueChange = {},
                    label = { Text(stringResource(Res.string.field_email)) },
                    readOnly = true,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                profile.emaId?.let { emaId ->
                    OutlinedTextField(
                        value = emaId,
                        onValueChange = {},
                        label = { Text(stringResource(Res.string.profile_ema_number)) },
                        trailingIcon = {
                            TooltipBox(
                                positionProvider = rememberAppTooltipPositionProvider(),
                                tooltip = { AppPlainTooltip { Text(stringResource(Res.string.profile_ema_number_info)) } },
                                state = rememberTooltipState(),
                            ) {
                                Icon(
                                    Icons.Outlined.Info,
                                    contentDescription = stringResource(Res.string.profile_ema_number_info),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        readOnly = true,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                LanguageSelector(Modifier.fillMaxWidth())
                ThemeSelector()
            }
        },
        // Logout is at the left of the button row. Close and Save are at the right.
        confirmButton = {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (onSignOut != null) {
                    FocusedTextButton(
                        enabled = !saving,
                        onClick = onSignOut,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text(stringResource(Res.string.action_logout)) }
                }
                Spacer(Modifier.weight(1f))
                FocusedTextButton(enabled = !saving, onClick = onDismiss) { Text(stringResource(Res.string.action_close)) }
                Spacer(Modifier.width(8.dp))
                FocusedButton(enabled = canSave, onClick = { save() }) { Text(stringResource(Res.string.action_save)) }
            }
        },
    )
}
