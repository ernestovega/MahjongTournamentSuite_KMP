package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.Image
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource

@Composable
fun CountryFlag(
    code: String,
    modifier: Modifier = Modifier,
    width: Dp = 24.dp,
    contentDescription: String? = null,
) {
    val normalizedCode = normalizedFlagCountryCode(code)
    Box(
        modifier = modifier.width(width).aspectRatio(4f / 3f),
        contentAlignment = Alignment.Center,
    ) {
        val resource = normalizedCode?.let(::flagResourceFor)
        when {
            resource != null -> Image(
                painter = painterResource(resource),
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
            normalizedCode != null -> Text(
                text = normalizedCode,
                modifier = Modifier.clearAndSetSemantics {
                    if (contentDescription != null) this.contentDescription = contentDescription
                },
            )
            else -> Icon(
                imageVector = Icons.Default.Public,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

internal fun normalizedFlagCountryCode(code: String): String? {
    val normalized = code.trim().uppercase()
    return normalized.takeIf {
        it != "EU" && it.length == 2 && it.all { character -> character in 'A'..'Z' }
    }
}
