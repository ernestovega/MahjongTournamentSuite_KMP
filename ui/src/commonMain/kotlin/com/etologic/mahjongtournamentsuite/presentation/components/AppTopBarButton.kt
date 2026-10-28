package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme

/** False on a narrow screen. [AppScaffold] sets it for its top bar, and the top bar buttons then show only their icons. */
val LocalTopBarLabels = staticCompositionLocalOf { true }

@Composable
fun AppTopBarButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
    enabled: Boolean = true,
    textColor: Color = Color.White,
    iconContent: (@Composable () -> Unit)? = null,
    /** When false, the button shows only the icon. The text stays as the description and in the menu. */
    showLabel: Boolean = true,
    /**
     * The button looks disabled but still takes the click. The caller then explains why the action is not possible.
     * A plain disabled button gives no click, so the user would not learn the reason.
     */
    looksDisabled: Boolean = false,
) {
    val buttonEnabled = enabled && LocalAppButtonsEnabled.current
    val dim = if (looksDisabled) Modifier.alpha(DisabledTopBarButtonAlpha) else Modifier
    val menu = LocalTopBarMenu.current
    val labelVisible = showLabel && LocalTopBarLabels.current
    if (menu != null) {
        DropdownMenuItem(
            text = { Text(text) },
            leadingIcon = { if (iconContent == null) Icon(imageVector = icon, contentDescription = null) else iconContent() },
            enabled = buttonEnabled,
            modifier = dim,
            onClick = {
                menu.dismiss()
                onClick()
            },
        )
        return
    }
    if (!labelVisible) {
        // An icon button has its own 12 dp padding and no outer halo, so the icon lines up with the 16 dp of the content.
        IconButton(
            onClick = onClick,
            enabled = buttonEnabled,
            modifier = (if (focusRequester == null) Modifier else Modifier.focusRequester(focusRequester)).then(dim),
        ) {
            if (iconContent == null) {
                Icon(imageVector = icon, contentDescription = text, tint = textColor)
            } else {
                iconContent()
            }
        }
        return
    }
    AppTextButton(
        onClick = onClick,
        enabled = enabled,
        focusRequester = focusRequester,
    ) {
        Column(
            modifier = Modifier.alpha(if (buttonEnabled) 1f else DisabledTopBarButtonAlpha).then(dim),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (iconContent == null) {
                Icon(
                    imageVector = icon,
                    contentDescription = if (labelVisible) null else text,
                    tint = textColor,
                )
            } else {
                iconContent()
            }
            if (labelVisible) Text(text = text, color = textColor)
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
