package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

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