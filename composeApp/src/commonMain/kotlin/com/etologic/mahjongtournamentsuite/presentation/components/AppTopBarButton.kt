package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme

@Composable
fun AppTopBarButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
    enabled: Boolean = true,
    textColor: Color = Color.White,
    iconContent: (@Composable () -> Unit)? = null,
) {
    val buttonEnabled = enabled && LocalAppButtonsEnabled.current
    AppTextButton(
        onClick = onClick,
        enabled = enabled,
        focusRequester = focusRequester,
    ) {
        Column(
            modifier = Modifier.alpha(if (buttonEnabled) 1f else DisabledTopBarButtonAlpha),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (iconContent == null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = textColor,
                )
            } else {
                iconContent()
            }
            Text(text = text, color = textColor)
        }
    }
}

private const val DisabledTopBarButtonAlpha = 0.38f

@Preview(device = Devices.DESKTOP)
@Composable
private fun AppTopBarButtonPreview() {
    MtsTheme(useDarkTheme = false) {
        AppTopBarButton(
            text = "Example",
            icon = Icons.Default.Add,
            onClick = {},
        )
    }
}
