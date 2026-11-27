package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.etologic.mahjongtournamentsuite.presentation.components.DebouncedIconButton as IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
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
    textColor: Color = MtsTheme.colors.onHeader,
    iconContent: (@Composable () -> Unit)? = null,
    /** When false, the button shows only the icon. The text stays as the description and in the menu. */
    showLabel: Boolean = true,
    /**
     * The button looks disabled but still takes the click. The caller then explains why the action is not possible.
     * A plain disabled button gives no click, so the user would not learn the reason.
     */
    looksDisabled: Boolean = false,
    /**
     * A fill behind the button, the button's own, so its hover still lights up one area. Unspecified (the default)
     * keeps the button flat on the top bar. The switch to the admin app fills it with the purple of that app.
     */
    containerColor: Color = Color.Unspecified,
) {
    val filled = containerColor.isSpecified
    val buttonEnabled = enabled && LocalAppButtonsEnabled.current
    val dim = if (looksDisabled) Modifier.alpha(DisabledTopBarButtonAlpha) else Modifier
    val menu = LocalTopBarMenu.current
    val labelVisible = showLabel && LocalTopBarLabels.current
    if (menu != null) {
        DropdownMenuItem(
            text = { Text(text) },
            leadingIcon = {
                when {
                    iconContent != null -> iconContent()
                    // In the menu the fill is a small circle behind the icon, so the item keeps the look of the menu.
                    filled -> Box(
                        modifier = Modifier.size(MenuIconFillSize).background(containerColor, MaterialTheme.shapes.small),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(imageVector = icon, contentDescription = null, tint = textColor, modifier = Modifier.size(MenuIconSize))
                    }
                    else -> Icon(imageVector = icon, contentDescription = null)
                }
            },
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
            colors = if (filled) {
                IconButtonDefaults.iconButtonColors(containerColor = containerColor, contentColor = textColor)
            } else {
                IconButtonDefaults.iconButtonColors()
            },
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
        colors = if (filled) {
            ButtonDefaults.textButtonColors(containerColor = containerColor, contentColor = textColor)
        } else {
            MtsButtons.textColors()
        },
        // A filled button keeps a gap above and below inside the top bar.
        contentPadding = if (filled) FilledContentPadding else ButtonDefaults.TextButtonContentPadding,
        modifier = if (filled) Modifier.padding(vertical = MtsTheme.spacing.xs) else Modifier,
        // A filled button is a rounded rectangle, not a pill (Ernesto, 2026-11-27).
        shape = if (filled) MaterialTheme.shapes.small else ButtonDefaults.textShape,
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
            if (labelVisible) ButtonText(text = text, color = textColor)
        }
    }
}

private const val DisabledTopBarButtonAlpha = 0.38f
private val MenuIconFillSize = 32.dp
private val MenuIconSize = 20.dp
private val FilledContentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)

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
