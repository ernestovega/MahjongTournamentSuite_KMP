package com.etologic.mahjongtournamentsuite.player

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.etologic.mahjongtournamentsuite.player.screenshots.PlayerFrame
import com.etologic.mahjongtournamentsuite.player.screenshots.playerModules
import com.etologic.mahjongtournamentsuite.screenshots.fake.ScreenshotData
import com.etologic.mahjongtournamentsuite.screenshots.fake.ScreenshotData.ONGOING
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/** The tabs of the tables of a tournament, for a guest, an account without a player and a player. */
@OptIn(ExperimentalTestApi::class)
class TournamentTablesTabsUiTest {
    private val original = Locale.getDefault()

    @BeforeTest
    fun english() = Locale.setDefault(Locale.ENGLISH)

    @AfterTest
    fun restore() = Locale.setDefault(original)

    private fun ComposeUiTest.show(account: com.etologic.mahjongtournamentsuite.domain.model.UserProfile?, myTables: Boolean = false) {
        setContent { PlayerFrame({ playerModules(account = account) }, dark = false) { nav -> TournamentScreen(nav, ONGOING, myTables = myTables) } }
        waitForText("Madrid Open 2026", substring = true)
    }

    private fun ComposeUiTest.waitForText(text: String, substring: Boolean = false) =
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty() }

    private fun ComposeUiTest.tap(text: String) {
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText(text) and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }
        onAllNodes(hasText(text) and hasClickAction()).onFirst().performClick()
        waitForIdle()
    }

    @Test
    fun aGuestIsAskedToSignInForTheOwnAndTheTeamTables() = runComposeUiTest {
        show(account = null)
        tap("Tables")
        tap("Me")
        waitForText("Sign in to see your tables in this tournament.", substring = true)
        tap("My team")
        waitForText("Sign in to see the tables of your team.", substring = true)
    }

    @Test
    fun anAccountWithoutAPlayerHasNoOwnTables() = runComposeUiTest {
        show(account = ScreenshotData.playerAccount.copy(emaId = null))
        tap("Tables")
        tap("Me")
        waitForText("Your account is not linked to a player of this tournament.", substring = true)
    }

    @Test
    fun theQrOfTheTournamentOpensTheOwnTablesAndThePlayerMovesToTheOthers() = runComposeUiTest {
        show(account = ScreenshotData.playerAccount, myTables = true)
        waitForText("Me")
        tap("My team")
        tap("All")
    }
}
