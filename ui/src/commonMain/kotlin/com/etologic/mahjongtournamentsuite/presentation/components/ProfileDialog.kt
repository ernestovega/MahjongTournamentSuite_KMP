package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.UserProfile
import com.etologic.mahjongtournamentsuite.presentation.language.CompactLanguageSelector
import com.etologic.mahjongtournamentsuite.presentation.telemetry.LocalWithdrawConsent
import com.etologic.mahjongtournamentsuite.presentation.telemetry.TrackedDialog
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsWindowWidth
import com.etologic.mahjongtournamentsuite.presentation.theme.ThemeSelector
import com.etologic.mahjongtournamentsuite.presentation.util.UiText
import com.etologic.mahjongtournamentsuite.presentation.util.asString
import com.etologic.mahjongtournamentsuite.presentation.util.toUiText
import kotlinx.coroutines.launch
import mahjongtournamentsuite.ui.generated.resources.Res
import mahjongtournamentsuite.ui.generated.resources.action_cancel
import mahjongtournamentsuite.ui.generated.resources.action_close
import mahjongtournamentsuite.ui.generated.resources.action_logout
import mahjongtournamentsuite.ui.generated.resources.action_save
import mahjongtournamentsuite.ui.generated.resources.consent_withdraw
import mahjongtournamentsuite.ui.generated.resources.field_email
import mahjongtournamentsuite.ui.generated.resources.profile_alias
import mahjongtournamentsuite.ui.generated.resources.profile_delete_account
import mahjongtournamentsuite.ui.generated.resources.profile_delete_account_confirm
import mahjongtournamentsuite.ui.generated.resources.profile_delete_account_message
import mahjongtournamentsuite.ui.generated.resources.profile_delete_account_title
import mahjongtournamentsuite.ui.generated.resources.profile_ema_number
import mahjongtournamentsuite.ui.generated.resources.profile_ema_number_info
import mahjongtournamentsuite.ui.generated.resources.profile_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Longest alias that the server accepts. */
const val MAX_ALIAS_LENGTH = 60

/**
 * The profile of the signed-in user. The user can change the alias and the photo.
 * The email and the EMA number are shown only. Admins set them.
 * [onSaveAlias] saves the new alias. After a save, [onSaved] gets the new profile and the dialog closes.
 * [onSavePhoto] and [onRemovePhoto] save the own photo. After a save, [onSaved] gets the new profile and the dialog stays open.
 * The title is centered. The app version from [LocalAppVersion] sits faint in the top left corner.
 * The account actions (logout, withdraw the analytics consent, delete the account) are red outlined buttons under the form.
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
    /** Deletes the own account, after a confirmation. Without it the dialog has no delete button. */
    onDeleteAccount: (suspend () -> AppResult<Unit>)? = null,
    /** Runs after the account was deleted. The session has ended. */
    onAccountDeleted: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var alias by remember(profile.uid) { mutableStateOf(profile.alias) }
    var photoProfile by remember(profile.uid) { mutableStateOf(profile) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<UiText?>(null) }
    val trimmedAlias = alias.trim()
    val canSave = !saving && trimmedAlias.isNotEmpty() && trimmedAlias != profile.alias.trim()

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

    var confirmingDelete by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<UiText?>(null) }
    if (confirmingDelete && onDeleteAccount != null) {
        DeleteOwnAccountDialog(
            deleting = saving,
            error = deleteError,
            onDismiss = {
                confirmingDelete = false
                deleteError = null
            },
            onConfirm = {
                saving = true
                deleteError = null
                scope.launch {
                    when (val result = onDeleteAccount()) {
                        is AppResult.Success -> {
                            saving = false
                            onDismiss()
                            onAccountDeleted()
                        }
                        is AppResult.Failure -> {
                            deleteError = result.error.toUiText()
                            saving = false
                        }
                    }
                }
            },
        )
        return
    }

    TrackedDialog("Profile") {
        AlertDialog(
            modifier = Modifier.appFocusGroup(),
            onDismissRequest = { if (!saving) onDismiss() },
            // The title is centered, like in the other dialogs. The version of the running app sits faint in the top left corner
            // and the language in the top right corner.
            title = {
                Box(Modifier.fillMaxWidth()) {
                    MtsDialogTitle(stringResource(Res.string.profile_title))
                    AppVersionLabel(Modifier.align(Alignment.TopStart).offset(x = (-16).dp, y = (-16).dp))
                    // The language takes little room: its flag in the top right corner, the names in the menu.
                    CompactLanguageSelector(Modifier.align(Alignment.CenterEnd).offset(x = 12.dp))
                }
            },
            text = {
                // It scrolls when the window is not tall enough, so no field is cut.
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // The photo is small. The EMA number, a short field, sits beside it.
                    // The theme comes first, under the title, apart from the profile by a line with room above and below.
                    ThemeSelector(Modifier.padding(top = 4.dp))
                    HorizontalDivider(Modifier.padding(vertical = 12.dp))
                    // The photo sits beside the EMA number and the alias, centered on them. On a phone it is small. On a bigger
                    // window there is room, so it is as tall as both fields.
                    val compact = MtsTheme.windowWidth == MtsWindowWidth.Compact
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PhotoEditor(
                            photoUrl = photoProfile.photoUrl,
                            hasOwnPhoto = photoProfile.hasOwnPhoto,
                            name = profile.alias.ifBlank { profile.email },
                            onUpload = { contentType, bytes -> savePhoto { onSavePhoto(contentType, bytes) } },
                            onRemove = { savePhoto { onRemovePhoto() } },
                            photoSize = if (compact) 80.dp else 128.dp,
                            editBadge = true,
                        )
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            profile.emaId?.let { emaId ->
                                OutlinedTextField(
                                    value = emaId,
                                    onValueChange = {},
                                    label = { Text(stringResource(Res.string.profile_ema_number)) },
                                    trailingIcon = {
                                        AppTooltipBox(stringResource(Res.string.profile_ema_number_info)) {
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
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    OutlinedTextField(
                        value = profile.email,
                        onValueChange = {},
                        label = { Text(stringResource(Res.string.field_email)) },
                        readOnly = true,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    // Save first, Close under it on a phone. They belong to the alias, so they come right after the fields.
                    MtsDialogButtons {
                        FocusedButton(enabled = canSave, onClick = { save() }) { Text(stringResource(Res.string.action_save)) }
                        FocusedTextButton(enabled = !saving, onClick = onDismiss) { Text(stringResource(Res.string.action_close)) }
                    }
                    // The actions on the account, apart from the form: all of them are secondary destructive buttons.
                    // The user can withdraw the consent at any time. The app then asks again and stays closed.
                    val withdrawConsent = LocalWithdrawConsent.current
                    if (onSignOut != null || withdrawConsent != null || onDeleteAccount != null) {
                        // The same room above and below the line, more than between the fields.
                        HorizontalDivider(Modifier.padding(vertical = 12.dp))
                        AccountActions {
                            // The special cases, rarely used, first and as red text without a border. Logout last, red and filled.
                            if (onDeleteAccount != null) {
                                DestructiveButton(Res.string.profile_delete_account, !saving, filled = false) { confirmingDelete = true }
                            }
                            withdrawConsent?.let { DestructiveButton(Res.string.consent_withdraw, !saving, filled = false, onClick = it) }
                            onSignOut?.let {
                                // A text button looks shorter than its height, so the filled one needs more room to look evenly spaced.
                                Spacer(Modifier.height(12.dp))
                                DestructiveButton(Res.string.action_logout, !saving, filled = true, onClick = it)
                            }
                        }
                    }
                }
            },
            // Save and Close sit under the fields they belong to, inside the content.
            confirmButton = {},
        )
    }
}

/** The account buttons, one under the other: as wide as the dialog on a phone, all as wide as the longest on a bigger window. */
@Composable
private fun AccountActions(content: @Composable () -> Unit) {
    val compact = MtsTheme.windowWidth == MtsWindowWidth.Compact
    Column(
        if (compact) Modifier.fillMaxWidth() else Modifier.width(IntrinsicSize.Max),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) { content() }
}

/** A red button of the account: filled for the usual action (logout), only text for the special ones. */
@Composable
private fun DestructiveButton(text: StringResource, enabled: Boolean, filled: Boolean, onClick: () -> Unit) {
    if (filled) {
        FocusedButton(
            enabled = enabled,
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            buttonModifier = Modifier.fillMaxWidth(),
            colors = MtsButtons.destructiveColors(),
        ) { Text(stringResource(text), textAlign = TextAlign.Center) }
    } else {
        FocusedTextButton(
            enabled = enabled,
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            buttonModifier = Modifier.fillMaxWidth(),
            colors = MtsButtons.destructiveTextColors(),
        ) { Text(stringResource(text), textAlign = TextAlign.Center) }
    }
}

/** Asks before the own account is deleted. Cancel has the focus, so Enter never deletes by mistake. */
@Composable
private fun DeleteOwnAccountDialog(
    deleting: Boolean,
    error: UiText?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val deleteFocus = remember { FocusRequester() }
    val cancelFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { cancelFocus.requestFocus() }
    TrackedDialog("DeleteOwnAccount") {
        AlertDialog(
            modifier = Modifier.appFocusGroup(),
            onDismissRequest = { if (!deleting) onDismiss() },
            title = { MtsDialogTitle(stringResource(Res.string.profile_delete_account_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MtsDialogText(stringResource(Res.string.profile_delete_account_message))
                    error?.let { Text(it.asString(), Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center) }
                }
            },
            confirmButton = {
                MtsDialogButtons {
                    FocusedButton(
                        onClick = onConfirm,
                        enabled = !deleting,
                        focusRequester = deleteFocus,
                        buttonModifier = Modifier.focusLoop(cancelFocus, cancelFocus),
                        colors = MtsButtons.destructiveColors(),
                    ) { Text(stringResource(Res.string.profile_delete_account_confirm)) }

                    FocusedTextButton(
                        onClick = onDismiss,
                        enabled = !deleting,
                        focusRequester = cancelFocus,
                        buttonModifier = Modifier.focusLoop(deleteFocus, deleteFocus),
                    ) { Text(stringResource(Res.string.action_cancel)) }
                }
            },
        )
    }
}
