package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme

@Composable
fun AppTopBarButton(
    text: String,
    onClick: () -> Unit,
) {
    AppTextButton(
        onClick = onClick,
    ) {
        Text(
            text = text,
            color = Color.White,
        )
    }
}

@Preview(device = Devices.DESKTOP)
@Composable
private fun AppTopBarButtonPreview() {
    MtsTheme(useDarkTheme = false) {
        AppTopBarButton(
            text = "Refresh",
            onClick = {},
        )
    }
}
