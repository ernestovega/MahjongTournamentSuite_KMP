package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import mahjongtournamentsuite.composeapp.generated.resources.Res
import mahjongtournamentsuite.composeapp.generated.resources.app_logo
import org.jetbrains.compose.resources.painterResource

@Composable
fun AppLogo(
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
) {
    Image(
        painter = painterResource(Res.drawable.app_logo),
        contentDescription = "Mahjong Tournament Suite logo",
        contentScale = ContentScale.Fit,
        modifier = modifier
            .size(size)
            .semantics { contentDescription = "Mahjong Tournament Suite logo" },
    )
}

@Composable
fun AppBackground(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(Res.drawable.app_logo),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .align(Alignment.Center)
                .size(420.dp)
                .alpha(0.10f),
        )
        content()
    }
}
