package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.etologic.mahjongtournamentsuite.presentation.platform.SelectedImage
import com.etologic.mahjongtournamentsuite.presentation.platform.rememberImagePicker
import com.etologic.mahjongtournamentsuite.presentation.telemetry.TrackedDialog
import com.etologic.mahjongtournamentsuite.presentation.util.UiText
import com.etologic.mahjongtournamentsuite.presentation.util.asString
import kotlinx.coroutines.launch
import mahjongtournamentsuite.ui.generated.resources.Res
import mahjongtournamentsuite.ui.generated.resources.action_cancel
import mahjongtournamentsuite.ui.generated.resources.photo_crop_hint
import mahjongtournamentsuite.ui.generated.resources.photo_crop_title
import mahjongtournamentsuite.ui.generated.resources.photo_custom_info
import mahjongtournamentsuite.ui.generated.resources.photo_ema_info
import mahjongtournamentsuite.ui.generated.resources.photo_edit
import mahjongtournamentsuite.ui.generated.resources.photo_error_size
import mahjongtournamentsuite.ui.generated.resources.photo_error_type
import mahjongtournamentsuite.ui.generated.resources.photo_none
import mahjongtournamentsuite.ui.generated.resources.photo_of
import mahjongtournamentsuite.ui.generated.resources.photo_public_notice_accept
import mahjongtournamentsuite.ui.generated.resources.photo_public_notice_title
import mahjongtournamentsuite.ui.generated.resources.photo_remove
import org.jetbrains.compose.resources.stringResource

private const val MAX_PHOTO_BYTES = 5 * 1024 * 1024
private val SUPPORTED_PHOTO_TYPES = setOf("image/jpeg", "image/png", "image/webp", "image/gif")

/**
 * The photo of an account with the buttons to change it. The user picks an image and crops it.
 * [onUpload] and [onRemove] save the change. They return an error text, or null when the save worked.
 * The caller shows the new photo through [photoUrl] after a save.
 * Without an own photo, [photoUrl] can be the photo of the EMA player. Then the remove button is hidden.
 * An info icon says if the photo is the photo of the EMA player or the photo that the user uploaded.
 * The remove button restores the photo of the EMA player.
 * [photoSize] is the diameter of the photo. With [editBadge] the buttons give way to a small pencil on the photo, for a
 * narrow column: it opens the image picker, or a menu with Edit and Remove when the user has an own photo.
 * With [uploadNotice], the person reads it and accepts it before choosing a new photo: the own profile says there
 * that the photo will be public (Ernesto, 2026-11-27). An admin who changes the photo of someone else sees no notice.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoEditor(
    photoUrl: String?,
    hasOwnPhoto: Boolean,
    name: String,
    onUpload: suspend (contentType: String, bytes: ByteArray) -> UiText?,
    onRemove: suspend () -> UiText?,
    modifier: Modifier = Modifier,
    photoSize: Dp = 120.dp,
    editBadge: Boolean = false,
    uploadNotice: String? = null,
) {
    val scope = rememberCoroutineScope()
    var imageToCrop by remember { mutableStateOf<SelectedImage?>(null) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<UiText?>(null) }
    var showingNotice by remember { mutableStateOf(false) }
    val picker = rememberImagePicker(
        onImageSelected = { image ->
            when {
                image.contentType !in SUPPORTED_PHOTO_TYPES -> error = UiText.of(Res.string.photo_error_type)
                image.bytes.size > MAX_PHOTO_BYTES -> error = UiText.of(Res.string.photo_error_size)
                else -> {
                    error = null
                    imageToCrop = image
                }
            }
        },
        onError = { error = it },
    )

    // A new photo: first the notice, when there is one, and then the image picker.
    fun choosePhoto() {
        if (uploadNotice != null) showingNotice = true else picker.launch()
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val usesEmaPhoto = !hasOwnPhoto && !photoUrl.isNullOrBlank()
        // The info says where the photo comes from: the EMA player, or the photo that the user uploaded.
        val info = when {
            hasOwnPhoto -> Res.string.photo_custom_info
            usesEmaPhoto -> Res.string.photo_ema_info
            else -> null
        }
        var menuOpen by remember { mutableStateOf(false) }
        fun remove() {
            saving = true
            error = null
            scope.launch {
                error = onRemove()
                saving = false
            }
        }
        Box {
            ProfilePhoto(photoUrl = photoUrl, name = name, size = photoSize)
            if (info != null) {
                AppTooltipBox(stringResource(info), Modifier.align(if (editBadge) Alignment.BottomStart else Alignment.BottomEnd)) {
                    Icon(
                        Icons.Outlined.Info,
                        contentDescription = stringResource(info),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            if (editBadge) {
                Box(Modifier.align(Alignment.BottomEnd).offset(x = 4.dp, y = 4.dp)) {
                    FocusedIconButton(
                        enabled = !saving,
                        onClick = { if (hasOwnPhoto) menuOpen = true else choosePhoto() },
                        buttonModifier = Modifier.size(32.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(),
                    ) {
                        if (saving) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.Edit, contentDescription = stringResource(Res.string.photo_edit), Modifier.size(18.dp))
                        }
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.photo_edit)) },
                            onClick = {
                                menuOpen = false
                                choosePhoto()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.photo_remove)) },
                            onClick = {
                                menuOpen = false
                                remove()
                            },
                        )
                    }
                }
            }
        }
        if (!editBadge) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FocusedTextButton(enabled = !saving, onClick = { choosePhoto() }) {
                    ButtonText(stringResource(Res.string.photo_edit))
                }
                if (hasOwnPhoto) {
                    FocusedTextButton(enabled = !saving, onClick = { remove() }) { ButtonText(stringResource(Res.string.photo_remove)) }
                }
                if (saving) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            }
        }
        error?.let {
            Text(it.asString(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        }
    }

    if (showingNotice && uploadNotice != null) {
        TrackedDialog("PhotoPublicNotice") {
            AlertDialog(
                modifier = Modifier.appFocusGroup(),
                onDismissRequest = { showingNotice = false },
                title = { MtsDialogTitle(stringResource(Res.string.photo_public_notice_title)) },
                text = { MtsDialogText(uploadNotice) },
                confirmButton = {
                    MtsDialogButtons {
                        FocusedButton(onClick = {
                            showingNotice = false
                            picker.launch()
                        }) { ButtonText(stringResource(Res.string.photo_public_notice_accept)) }
                        FocusedTextButton(onClick = { showingNotice = false }) { ButtonText(stringResource(Res.string.action_cancel)) }
                    }
                },
            )
        }
    }

    imageToCrop?.let { image ->
        ImageCropDialog(
            image = image,
            title = stringResource(Res.string.photo_crop_title),
            hint = stringResource(Res.string.photo_crop_hint),
            onDismiss = { imageToCrop = null },
            onCropped = { cropped ->
                imageToCrop = null
                saving = true
                error = null
                scope.launch {
                    error = onUpload(cropped.contentType, cropped.bytes)
                    saving = false
                }
            },
            onError = {
                imageToCrop = null
                error = it
            },
        )
    }
}

/** Round photo of an account. Without a photo, it shows the same placeholder icon as the EMA players. */
@Composable
fun ProfilePhoto(photoUrl: String?, name: String, size: Dp = 120.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (photoUrl.isNullOrBlank()) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = stringResource(Res.string.photo_none),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(size * 0.62f),
            )
        } else {
            AsyncImage(
                model = photoUrl,
                contentDescription = stringResource(Res.string.photo_of, name),
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size),
            )
        }
    }
}
