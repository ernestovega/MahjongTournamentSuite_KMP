package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.domain.model.UserProfile

/**
 * The button of the own profile in the top bar: the round photo and, beside it, the name.
 * With [secondLine], such as the role, the name takes one line and [secondLine] goes under it. Without it, the name can
 * take two lines. In a three-dots menu, or in a bar that shows only icons, it is a plain top bar button with the photo.
 */
@Composable
fun ProfileTopBarButton(
    profile: UserProfile,
    name: String,
    clickLabel: String,
    onClick: () -> Unit,
    secondLine: String? = null,
) {
    if (LocalTopBarMenu.current != null || !LocalTopBarLabels.current) {
        AppTopBarButton(
            text = name,
            icon = Icons.Default.Person,
            onClick = onClick,
            iconContent = { ProfilePhoto(photoUrl = profile.photoUrl, name = name, size = 28.dp) },
        )
        return
    }
    Row(
        modifier = Modifier
            .padding(end = 8.dp)
            .clip(MaterialTheme.shapes.small)
            .clickable(enabled = LocalAppButtonsEnabled.current, role = Role.Button, onClickLabel = clickLabel, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProfilePhoto(photoUrl = profile.photoUrl, name = name, size = 36.dp)
        Column(
            modifier = Modifier.widthIn(max = ProfileNameMaxWidth),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodySmall,
                maxLines = if (secondLine == null) 2 else 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            secondLine?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** A long alias wraps or ends in "…" instead of pushing the title away. */
private val ProfileNameMaxWidth = 140.dp
