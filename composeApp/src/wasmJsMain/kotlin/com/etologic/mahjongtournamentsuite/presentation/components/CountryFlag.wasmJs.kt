package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage

@Composable
actual fun CountryFlag(
    code: String,
    modifier: Modifier,
    width: Dp,
    contentDescription: String?,
) {
    val normalizedCode = normalizedFlagCountryCode(code)
    Box(
        modifier = modifier.width(width).aspectRatio(4f / 3f),
        contentAlignment = Alignment.Center,
    ) {
        if (normalizedCode == null) {
            Icon(
                imageVector = Icons.Default.Public,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            SubcomposeAsyncImage(
                model = "https://flagcdn.com/40x30/${normalizedCode.lowercase()}.png",
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                loading = { Text(normalizedCode, fontSize = 8.sp) },
                error = {
                    Icon(
                        imageVector = Icons.Default.Public,
                        contentDescription = contentDescription,
                        modifier = Modifier.fillMaxSize(),
                    )
                },
            )
        }
    }
}
