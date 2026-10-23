package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
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
import com.etologic.mahjongtournamentsuite.presentation.util.toUiMessage
import kotlinx.coroutines.launch

/** Longest alias that the server accepts. */
const val MAX_ALIAS_LENGTH = 60

/**
 * The profile of the signed-in user. The user can change the alias and the photo.
 * The email and the EMA number are shown only. Admins set them.
 * [onSaveAlias] saves the new alias. After a save, [onSaved] gets the new profile and the dialog closes.
 * [onSavePhoto] and [onRemovePhoto] save the own photo. After a save, [onSaved] gets the new profile and the dialog stays open.
 */
@Composable
fun ProfileDialog(
    profile: UserProfile,
    onSaveAlias: suspend (String) -> AppResult<UserProfile>,
    onSavePhoto: suspend (contentType: String, bytes: ByteArray) -> AppResult<UserProfile>,
    onRemovePhoto: suspend () -> AppResult<UserProfile>,
    onSaved: (UserProfile) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var alias by remember(profile.uid) { mutableStateOf(profile.alias) }
    var photoProfile by remember(profile.uid) { mutableStateOf(profile) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
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

                is AppResult.Failure -> error = result.error.toUiMessage()
            }
            saving = false
        }
    }

    suspend fun savePhoto(call: suspend () -> AppResult<UserProfile>): String? = when (val result = call()) {
        is AppResult.Success -> {
            photoProfile = result.value
            onSaved(result.value)
            null
        }

        is AppResult.Failure -> result.error.toUiMessage()
    }

    AlertDialog(
        modifier = Modifier.appFocusGroup(),
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Profile") },
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
                    label = { Text("Alias") },
                    supportingText = { Text(error ?: "The name that the apps show for your account.") },
                    isError = error != null,
                    singleLine = true,
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth().focusRequester(aliasFocusRequester),
                )
                OutlinedTextField(
                    value = profile.email,
                    onValueChange = {},
                    label = { Text("Email") },
                    readOnly = true,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                profile.emaId?.let { emaId ->
                    OutlinedTextField(
                        value = emaId,
                        onValueChange = {},
                        label = { Text("EMA number") },
                        readOnly = true,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            FocusedButton(enabled = canSave, onClick = { save() }) { Text("Save") }
        },
        dismissButton = {
            FocusedTextButton(enabled = !saving, onClick = onDismiss) { Text("Close") }
        },
    )
}
