package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.UserProfile
import com.etologic.mahjongtournamentsuite.presentation.language.LanguageSelector
import com.etologic.mahjongtournamentsuite.presentation.telemetry.ConsentDialog
import com.etologic.mahjongtournamentsuite.presentation.telemetry.LocalAnalyticsConsent
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
import mahjongtournamentsuite.ui.generated.resources.consent_title
import mahjongtournamentsuite.ui.generated.resources.field_email
import mahjongtournamentsuite.ui.generated.resources.profile_alias
import mahjongtournamentsuite.ui.generated.resources.profile_alias_info
import mahjongtournamentsuite.ui.generated.resources.profile_delete_account
import mahjongtournamentsuite.ui.generated.resources.profile_delete_account_confirm
import mahjongtournamentsuite.ui.generated.resources.profile_delete_account_message
import mahjongtournamentsuite.ui.generated.resources.profile_delete_account_title
import mahjongtournamentsuite.ui.generated.resources.profile_ema_number
import mahjongtournamentsuite.ui.generated.resources.profile_ema_number_info
import mahjongtournamentsuite.ui.generated.resources.profile_public_photo
import mahjongtournamentsuite.ui.generated.resources.profile_public_photo_info
import mahjongtournamentsuite.ui.generated.resources.profile_title
import mahjongtournamentsuite.ui.generated.resources.photo_public_notice_hide
import mahjongtournamentsuite.ui.generated.resources.photo_public_notice_text
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Longest alias that the server accepts. */
const val MAX_ALIAS_LENGTH = 60

/**
 * The profile of the signed-in user. The user can change the alias and the photo.
 * The email and the EMA number are shown only. Admins set them.
 * [onSaveAlias] saves the new alias. After a save, [onSaved] gets the new profile and the dialog closes.
 * The alias is private and the photo is public: an info icon on each says so. [onSetPublicPhoto] hides the photo.
 * [onSavePhoto] and [onRemovePhoto] save the own photo. After a save, [onSaved] gets the new profile and the dialog stays open.
 * The title is centered. The app version from [LocalAppVersion] sits faint in the top left corner.
 * The account actions (logout, delete the account, the anonymous analytics) are red buttons under the form.
 * "Anonymous analytics" shows again the screen of the first start, when the app asks for them ([LocalAnalyticsConsent]).
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
    /**
     * Shows or hides the photo of the person in public places. Without it, or for an account without an EMA number
     * (its photo is never public), the dialog has no switch. After a save, [onSaved] gets the new profile.
     */
    onSetPublicPhoto: (suspend (Boolean) -> AppResult<UserProfile>)? = null,
) {
    val scope = rememberCoroutineScope()
    var alias by remember(profile.uid) { mutableStateOf(profile.alias) }
    var photoProfile by remember(profile.uid) { mutableStateOf(profile) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<UiText?>(null) }
    var savingVisibility by remember { mutableStateOf(false) }
    var visibilityError by remember { mutableStateOf<UiText?>(null) }
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

    fun setPublicPhoto(visible: Boolean) {
        val call = onSetPublicPhoto ?: return
        if (savingVisibility) return
        savingVisibility = true
        visibilityError = null
        scope.launch {
            savePhoto { call(visible) }?.let { visibilityError = it }
            savingVisibility = false
        }
    }

    var askingAnalytics by remember { mutableStateOf(false) }
    LocalAnalyticsConsent.current?.let { consent ->
        if (askingAnalytics) {
            ConsentDialog(
                onAnswer = {
                    consent.onChange(it)
                    askingAnalytics = false
                },
                onDismiss = { askingAnalytics = false },
            )
        }
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
            // The title is centered, like in the other dialogs. The version of the running app sits faint in the top left corner.
            title = {
                Box(Modifier.fillMaxWidth()) {
                    MtsDialogTitle(stringResource(Res.string.profile_title))
                    AppVersionLabel(Modifier.align(Alignment.TopStart).offset(x = (-16).dp, y = (-16).dp))
                    // A cross in the top right corner closes the dialog. It takes the place of a Close button, which made
                    // the dialog taller on a phone (Ernesto, 2026-11-26). It does not count in the height of the title, so
                    // the theme selector stays close to it.
                    Box(Modifier.matchParentSize()) {
                        FocusedIconButton(
                            onClick = onDismiss,
                            enabled = !saving,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .wrapContentHeight(align = Alignment.Top, unbounded = true)
                                .offset(x = 12.dp, y = (-12).dp),
                        ) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.action_close))
                        }
                    }
                }
            },
            text = {
                // It scrolls when the window is not tall enough, so no field is cut.
                Column(
                    modifier = Modifier.fillMaxWidth().dialogVerticalScroll(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // The photo is small. The EMA number, a short field, sits beside it.
                    // The preferences come first, under the title: the theme and the language. A line with room above and
                    // below keeps them apart from the profile.
                    ThemeSelector(Modifier.padding(bottom = MtsTheme.spacing.md))
                    LanguageSelector(Modifier.fillMaxWidth())
                    HorizontalDivider(Modifier.padding(vertical = MtsTheme.spacing.lg))
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
                            // The person accepts that the photo is public before choosing it. With the switch below, the
                            // notice also says how to hide it.
                            uploadNotice = stringResource(Res.string.photo_public_notice_text) +
                                if (onSetPublicPhoto != null && profile.emaId != null) "\n\n" + stringResource(Res.string.photo_public_notice_hide) else "",
                        )
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            profile.emaId?.let { emaId ->
                                OutlinedTextField(
                                    value = emaId,
                                    onValueChange = {},
                                    label = { Text(stringResource(Res.string.profile_ema_number)) },
                                    trailingIcon = { InfoIcon(stringResource(Res.string.profile_ema_number_info)) },
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
                                // The alias is private. The tooltip says who sees it and what it is for.
                                trailingIcon = { InfoIcon(stringResource(Res.string.profile_alias_info)) },
                                supportingText = error?.let { message -> { Text(message.asString()) } },
                                isError = error != null,
                                singleLine = true,
                                enabled = !saving,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    // Only the photo of an EMA player shows in public places, so only an account with an EMA number
                    // chooses. The tooltip says where the photo shows and which one.
                    if (onSetPublicPhoto != null && profile.emaId != null) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            MtsSwitchRow(
                                checked = photoProfile.publicPhoto,
                                onCheckedChange = { setPublicPhoto(it) },
                                enabled = !saving && !savingVisibility,
                                switchFirst = true,
                                gap = 12.dp,
                                modifier = Modifier.weight(1f, fill = false),
                            ) {
                                Text(stringResource(Res.string.profile_public_photo), Modifier.weight(1f, fill = false).padding(end = 12.dp))
                            }
                            InfoIcon(stringResource(Res.string.profile_public_photo_info))
                        }
                        visibilityError?.let {
                            Text(it.asString(), Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
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
                    // Save belongs to the alias, so it comes right after the fields. The cross of the title closes the dialog.
                    // It has as much room above as below, where the line of the account actions has its own padding
                    // (Ernesto, 2026-11-26).
                    MtsDialogButtons(Modifier.padding(top = MtsTheme.spacing.sm), stacked = false) {
                        FocusedButton(enabled = canSave, onClick = { save() }) { ButtonText(stringResource(Res.string.action_save)) }
                    }
                    // The actions on the account, apart from the form: all of them are secondary destructive buttons.
                    // The anonymous analytics are one of them, not a switch in the form (Ernesto, 2026-11-27): the button opens
                    // the same screen as the first start, where the user accepts or declines again.
                    val analyticsConsent = LocalAnalyticsConsent.current
                    if (onSignOut != null || onDeleteAccount != null || analyticsConsent != null) {
                        // The same room above and below the line, more than between the fields.
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        AccountActions {
                            // The special cases, rarely used, first and as red text without a border. Logout last, red and filled.
                            if (onDeleteAccount != null) {
                                DestructiveButton(Res.string.profile_delete_account, !saving, filled = false) { confirmingDelete = true }
                            }
                            if (analyticsConsent != null) {
                                DestructiveButton(
                                    Res.string.consent_title,
                                    !saving,
                                    filled = false,
                                    modifier = Modifier.testTag(PROFILE_ANALYTICS_TAG),
                                ) { askingAnalytics = true }
                            }
                            onSignOut?.let {
                                // A text button looks shorter than its height, so the filled one needs more room to look evenly spaced.
                                Spacer(Modifier.height(8.dp))
                                DestructiveButton(Res.string.action_logout, !saving, filled = true, onClick = it)
                            }
                        }
                    }
                }
            },
            // Save sits under the fields it belongs to, inside the content.
            confirmButton = {},
        )
    }
}

/** An info icon that shows [text] in a tooltip. The text is also what a screen reader says. */
@Composable
private fun InfoIcon(text: String) {
    AppTooltipBox(text) {
        Icon(Icons.Outlined.Info, contentDescription = text, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The account buttons, one under the other: as wide as the dialog on a phone, all as wide as the longest on a bigger window. */
@Composable
private fun AccountActions(content: @Composable () -> Unit) {
    val compact = MtsTheme.windowWidth == MtsWindowWidth.Compact
    Column(
        if (compact) Modifier.fillMaxWidth() else Modifier.width(IntrinsicSize.Max),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) { content() }
}

/** A red button of the account: filled for the usual action (logout), only text for the special ones. */
@Composable
private fun DestructiveButton(
    text: StringResource,
    enabled: Boolean,
    filled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    if (filled) {
        FocusedButton(
            enabled = enabled,
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            buttonModifier = Modifier.fillMaxWidth(),
            colors = MtsButtons.destructiveColors(),
        ) { ButtonText(stringResource(text), textAlign = TextAlign.Center) }
    } else {
        // A low text button: these rare actions take little height.
        FocusedTextButton(
            enabled = enabled,
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            buttonModifier = modifier.fillMaxWidth().heightIn(min = 32.dp),
            contentPadding = PaddingValues(horizontal = 12.dp),
            colors = MtsButtons.destructiveTextColors(),
        ) { ButtonText(stringResource(text), textAlign = TextAlign.Center) }
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
                    ) { ButtonText(stringResource(Res.string.profile_delete_account_confirm)) }

                    FocusedTextButton(
                        onClick = onDismiss,
                        enabled = !deleting,
                        focusRequester = cancelFocus,
                        buttonModifier = Modifier.focusLoop(deleteFocus, deleteFocus),
                    ) { ButtonText(stringResource(Res.string.action_cancel)) }
                }
            },
        )
    }
}

/** The switch of the anonymous analytics in the profile. */
const val PROFILE_ANALYTICS_TAG = "profile-analytics"
