package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.etologic.mahjongtournamentsuite.presentation.platform.SelectedImage
import com.etologic.mahjongtournamentsuite.presentation.platform.rememberImagePicker
import kotlinx.coroutines.launch

private const val MAX_PHOTO_BYTES = 5 * 1024 * 1024
private val SUPPORTED_PHOTO_TYPES = setOf("image/jpeg", "image/png", "image/webp", "image/gif")

/**
 * The photo of an account with the buttons to change it. The user picks an image and crops it.
 * [onUpload] and [onRemove] save the change. They return an error text, or null when the save worked.
 * The caller shows the new photo through [photoUrl] after a save.
 * Without an own photo, [photoUrl] can be the photo of the EMA player. Then the remove button is hidden.
 */
@Composable
fun PhotoEditor(
    photoUrl: String?,
    hasOwnPhoto: Boolean,
    name: String,
    onUpload: suspend (contentType: String, bytes: ByteArray) -> String?,
    onRemove: suspend () -> String?,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var imageToCrop by remember { mutableStateOf<SelectedImage?>(null) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val picker = rememberImagePicker(
        onImageSelected = { image ->
            when {
                image.contentType !in SUPPORTED_PHOTO_TYPES -> error = "Choose a JPEG, PNG, WebP, or GIF image."
                image.bytes.size > MAX_PHOTO_BYTES -> error = "Choose a photo that is 5 MB or smaller."
                else -> {
                    error = null
                    imageToCrop = image
                }
            }
        },
        onError = { error = it },
    )

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ProfilePhoto(photoUrl = photoUrl, name = name)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FocusedTextButton(enabled = !saving, onClick = { picker.launch() }) {
                Text(if (hasOwnPhoto) "Change photo" else "Add photo")
            }
            if (hasOwnPhoto) {
                FocusedTextButton(
                    enabled = !saving,
                    onClick = {
                        saving = true
                        error = null
                        scope.launch {
                            error = onRemove()
                            saving = false
                        }
                    },
                ) { Text("Remove photo") }
            }
            if (saving) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        }
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        }
    }

    imageToCrop?.let { image ->
        ImageCropDialog(
            image = image,
            title = "Crop photo",
            hint = "Drag the image or use the controls. The circle shows the area that the apps show.",
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
fun ProfilePhoto(photoUrl: String?, name: String) {
    val size = 120.dp
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
                contentDescription = "No photo",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(size * 0.62f),
            )
        } else {
            AsyncImage(
                model = photoUrl,
                contentDescription = "Photo of $name",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size),
            )
        }
    }
}
