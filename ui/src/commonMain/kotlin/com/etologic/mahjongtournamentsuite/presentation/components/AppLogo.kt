package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.luminance
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

/** The white logo for a dark theme. Without it, the dark theme shows [LocalAppLogo]. */
val LocalAppLogoOnDark = staticCompositionLocalOf<Painter?> { null }

/** The logo that fits the current theme: green on a light background, white on a dark one. */
@Composable
private fun themedAppLogo(): Painter? {
    val onDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return (if (onDark) LocalAppLogoOnDark.current else null) ?: LocalAppLogo.current
}

@Composable
fun AppLogo(
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
) {
    val logo = themedAppLogo() ?: return
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
    val logo = themedAppLogo()
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
