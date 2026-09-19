package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
    content: @Composable () -> Unit,
) {
    FocusedTextButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        focusRequester = focusRequester,
        content = { content() },
    )
}

@Preview(device = Devices.DESKTOP)
@Composable
private fun AppTextButtonPreview() {
    MtsTheme(useDarkTheme = false) {
        AppTextButton(onClick = {}) {
            Text("Text button")
        }
    }
}
