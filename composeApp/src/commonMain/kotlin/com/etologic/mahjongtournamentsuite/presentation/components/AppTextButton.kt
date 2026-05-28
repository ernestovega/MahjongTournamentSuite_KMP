package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme

@Composable
fun AppTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = ButtonDefaults.textButtonColors(),
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
