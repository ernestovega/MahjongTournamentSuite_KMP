package com.etologic.mahjongtournamentsuite.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryLink
import com.etologic.mahjongtournamentsuite.presentation.language.LocalLanguageController
import com.etologic.mahjongtournamentsuite.presentation.platform.IosViewControllers
import com.etologic.mahjongtournamentsuite.presentation.telemetry.LocalTelemetry
import com.etologic.mahjongtournamentsuite.presentation.telemetry.linkOpened
import platform.Foundation.NSURL
import platform.SafariServices.SFSafariViewController

private const val SITE = "https://mts.mahjongmadrid.com"

/** Opens the edition of the language of the app in Safari inside the app, which shows and saves PDFs. */
@Composable
internal actual fun rememberOpenGreenBook(): () -> Unit {
    val telemetry = LocalTelemetry.current
    val edition = greenBookEdition(LocalLanguageController.current.language)
    return remember(telemetry, edition) {
        {
            telemetry.linkOpened(TelemetryLink.GREEN_BOOK)
            val address = greenBookAddress(edition).let { if (it.startsWith("/")) SITE + it else it }
            NSURL.URLWithString(address)?.let { IosViewControllers.present(SFSafariViewController(uRL = it)) }
        }
    }
}
