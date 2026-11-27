package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme

@Composable
fun AppTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null,
    colors: ButtonColors = MtsButtons.textColors(),
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
    shape: Shape = ButtonDefaults.textShape,
    content: @Composable () -> Unit,
) {
    FocusedTextButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = colors,
        contentPadding = contentPadding,
        shape = shape,
        focusRequester = focusRequester,
        content = { content() },
    )
}

@Preview(device = Devices.DESKTOP)
@Composable
private fun AppTextButtonPreview() {
    MtsTheme(useDarkTheme = false) {
        AppTextButton(onClick = {}) {
            ButtonText("Text button")
        }
    }
}
