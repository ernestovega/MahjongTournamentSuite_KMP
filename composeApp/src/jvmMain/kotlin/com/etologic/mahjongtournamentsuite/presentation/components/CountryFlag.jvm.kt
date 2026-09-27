package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

@Composable
actual fun CountryFlag(
    code: String,
    modifier: Modifier,
    width: Dp,
    contentDescription: String?,
) {
    TextCountryFlag(
        code = code,
        modifier = modifier,
        width = width,
        contentDescription = contentDescription,
    )
}
