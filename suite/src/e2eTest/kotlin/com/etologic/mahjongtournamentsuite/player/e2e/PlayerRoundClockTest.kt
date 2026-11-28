@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.etologic.mahjongtournamentsuite.player.e2e

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import com.etologic.mahjongtournamentsuite.e2e.E2ePlatform
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * The clock of a round seen from the table of a player while an organizer changes it on the Timer or in Round control:
 * a pause and a resume, Set time and Stop. Round 1 of test-match-extra-time, a match of 30 minutes.
 */
class PlayerRoundClockTest : PlayerE2eTest() {
    private val tournament = "Test - Match time over, extra time running"
    private val adminToken by lazy { E2eApi.signIn(E2eAccounts.ADMIN) }

    @BeforeTest
    fun freshData() = onlyWithBackend { E2eApi.reset("test-match-extra-time") }

    @AfterTest
    fun restoreData() = onlyWithBackend { E2eApi.reset("test-match-extra-time") }

    /** Plays an organizer on the clock of round 1. */
    private fun clock(body: String) {
        val reply = E2eApi.call("POST", "/tournaments/test-match-extra-time/rounds/1/clock", adminToken, body)
        check(reply.status == 200) { "The clock failed: ${reply.status} ${reply.body}" }
    }

    /** A fresh round 1: the gong sounds now, so the table has its 30 minutes. */
    private fun openTheTableOfARoundJustStarted() {
        clock("""{"action":"reset","force":true}""")
        clock("""{"action":"start"}""")
        start(E2eAccounts.EXTRA_TIME, ready = "My tournaments")
        c.openTournament(tournament)
        c.waitForText("Me")
        c.settle()
        c.clickButton("Me")
        c.waitForText("Table 1", substring = true)
        c.onAllNodes(hasText("Table 1", substring = true) and hasClickAction()).onFirst().performClick()
        c.waitForText("Round 1 - Table 1")
        c.waitForText("Add draw", timeoutMs = 30_000)
    }

    /** The time line at the top of the table: "0:29:41", or "Paused · Time left: 0:29:41". */
    private fun clockText(): String = c.onAllNodesWithTag("round-clock", useUnmergedTree = true).fetchSemanticsNodes()
        .firstOrNull()?.config?.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }.orEmpty()

    /** The whole minutes left on the line, or null when it shows no time. */
    private fun minutesLeft(): Int? = Regex("""(\d+):(\d\d):(\d\d)""").find(clockText())?.destructured
        ?.let { (hours, minutes) -> hours.toInt() * 60 + minutes.toInt() }

    @Test
    fun thePlayerSeesThePauseAndTheResumeAndCanStillAddHands() = e2e {
        openTheTableOfARoundJustStarted()

        clock("""{"action":"pause"}""")
        // The app reads the clock every few seconds while the round is in play.
        c.waitUntil(timeoutMillis = 30_000) { clockText().startsWith("Paused · Time left:") }
        val paused = clockText()
        E2ePlatform.sleep(3_000)
        c.settle()
        check(clockText() == paused) { "The time went on in the pause: $paused, then ${clockText()}" }
        // A pause stops the clock, not the table: the players finish the hand in play.
        c.waitForText("Add draw")

        clock("""{"action":"resume"}""")
        c.waitUntil(timeoutMillis = 30_000) { clockText().isNotEmpty() && !clockText().startsWith("Paused") }
        c.waitForText("Add draw")
    }

    @Test
    fun setTimeChangesTheTimeLeftAtTheTable() = e2e {
        openTheTableOfARoundJustStarted()
        c.waitUntil(timeoutMillis = 30_000) { (minutesLeft() ?: 0) >= 28 }

        // Less time: 10 minutes left from now.
        clock("""{"action":"setRemaining","remainingSeconds":600}""")
        c.waitUntil(timeoutMillis = 30_000) { minutesLeft() in 9..10 }

        // More time: 40 minutes from now, longer than the match itself.
        clock("""{"action":"setRemaining","remainingSeconds":2400}""")
        c.waitUntil(timeoutMillis = 30_000) { minutesLeft() in 39..40 }
        c.waitForText("Add draw")
    }

    @Test
    fun stopEndsTheMatchAtTheTableAndTakesTheHandButtonsAway() = e2e {
        openTheTableOfARoundJustStarted()

        // Stop skips the extra time: the match is over now, and the server closes the tables with hands.
        clock("""{"action":"stop"}""")
        c.waitForNoText("Add draw", timeoutMs = 30_000)
        c.assertNoText("Add win")
        val table = E2eApi.call("GET", "/public/tournaments/test-match-extra-time/tables/1/1").json().getJSONObject("table")
        check(table.optBoolean("isCompleted")) { "The table of a stopped round is still open: $table" }
    }
}
