package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import mahjongtournamentsuite.ui.generated.resources.Res
import mahjongtournamentsuite.ui.generated.resources.app_logo_description
import org.jetbrains.compose.resources.stringResource

/** The logo of the running application. Each application provides its own image at its root. */
val LocalAppLogo = staticCompositionLocalOf<Painter?> { null }

@Composable
fun AppLogo(
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
) {
    val logo = LocalAppLogo.current ?: return
    val logoDescription = stringResource(Res.string.app_logo_description)
    Image(
        painter = logo,
        contentDescription = logoDescription,
        contentScale = ContentScale.Fit,
        modifier = modifier
            .size(size)
            .semantics { contentDescription = logoDescription },
    )
}

@Composable
fun AppBackground(content: @Composable () -> Unit) {
    val logo = LocalAppLogo.current
    Box(modifier = Modifier.fillMaxSize()) {
        if (logo != null) {
            Image(
                painter = logo,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(420.dp)
                    .alpha(0.10f),
            )
        }
        content()
    }
}
