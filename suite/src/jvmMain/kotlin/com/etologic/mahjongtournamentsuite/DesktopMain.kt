package com.etologic.mahjongtournamentsuite

import com.etologic.mahjongtournamentsuite.data.platform.DesktopPlatform
import com.etologic.mahjongtournamentsuite.data.session.storedSessionUid
import com.etologic.mahjongtournamentsuite.presentation.platform.DesktopSuite
import javax.swing.SwingUtilities

/**
 * The desktop app (macOS, Windows and Linux): the player part and the admin part, like the Android and iOS apps. See
 * [SuiteRoot] and docs/desktop-release.md.
 */
fun main() {
    DesktopSuite.isSuite = true
    desktopApplication {
        SuiteRoot(
            initial = null,
            startRequest = null,
            onStartHandled = {},
            post = { action -> SwingUtilities.invokeLater(action) },
            storedSessionUid = ::storedSessionUid,
            onPartShown = { part -> DesktopPlatform.currentApp = part },
            // The computer has a keyboard, so the score of a hand is typed in the text field.
            numpadEntry = false,
        )
        DesktopUpdateCheck()
    }
}
