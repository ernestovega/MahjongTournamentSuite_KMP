package com.etologic.mahjongtournamentsuite.player.e2e

import android.util.Log
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import com.etologic.mahjongtournamentsuite.player.PLAYER_CHOICE_TAG
import com.etologic.mahjongtournamentsuite.player.SCORE_NUMPAD_TAG
import com.etologic.mahjongtournamentsuite.player.SEAT_OPTION_TAG
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

/**
 * The automatic player of an emulator in a rehearsal (docs/rehearsal.md). It is not a check of the app: it plays one
 * slot of a real tournament in staging with the real screens, as a person would, until the tournament ends or the time
 * is over. Without the rehearsal arguments it does nothing, so the normal test runs skip it.
 *
 * At its table it writes the hands only when its slot is the lowest of the table, as the bots do. Otherwise it stays
 * on the table and follows the hands that another phone writes.
 *
 * Arguments: e2eBackendUrl (the staging API), rehearsalEmail, rehearsalPassword, rehearsalTournament (the name),
 * rehearsalMinutes (default 150) and rehearsalHandSeconds (default 60-90). Logs: adb logcat -s REHEARSAL
 */
class RehearsalPlayerTest : PlayerE2eTest() {
    private val args = InstrumentationRegistry.getArguments()
    private val email = args.getString("rehearsalEmail").orEmpty()
    private val password = args.getString("rehearsalPassword").orEmpty()
    private val tournamentName = args.getString("rehearsalTournament").orEmpty()
    private val minutes = args.getString("rehearsalMinutes")?.toLongOrNull() ?: 150L
    private val handSeconds = (args.getString("rehearsalHandSeconds") ?: "60-90").split("-").map { it.trim().toInt() }

    private var tournamentId = ""
    private var playerId = -1
    private var handsWritten = 0
    private var failures = 0

    private fun log(message: String) = Log.i(TAG, "[$email] $message")

    private fun signIn(): JSONObject {
        val body = JSONObject().put("email", email).put("password", password).toString()
        repeat(6) { attempt ->
            val reply = E2eApi.call("POST", "/auth/signIn", body = body)
            if (reply.status == 200) return reply.json()
            log("sign-in answered ${reply.status}, attempt ${attempt + 1}")
            Thread.sleep(5_000L * (attempt + 1))
        }
        error("The account $email could not sign in")
    }

    private fun json(path: String): JSONObject? {
        val reply = runCatching { E2eApi.call("GET", path) }.getOrNull() ?: return null
        return if (reply.status == 200) reply.json() else null
    }

    /** Lets the app run in real time: its own timers move with the test clock. */
    private fun idle(ms: Long) {
        val until = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < until) {
            runCatching {
                c.mainClock.advanceTimeBy(500)
                c.waitForIdle()
            }
            Thread.sleep(500)
        }
    }

    private data class MyTable(val roundId: Int, val tableId: Int, val isScorer: Boolean, val seatsSet: Boolean, val isCompleted: Boolean)

    /** Where the player sits now, read from the public API as another phone would see it. */
    private fun myTable(): MyTable? {
        val clocks = json("/public/tournaments/$tournamentId/round-clocks")?.optJSONArray("roundClocks")
        val round = (0 until (clocks?.length() ?: 0)).maxOfOrNull { clocks!!.getJSONObject(it).getInt("roundId") } ?: 1
        val tables = json("/public/tournaments/$tournamentId/tables?roundId=$round")?.optJSONArray("tables") ?: return null
        for (index in 0 until tables.length()) {
            val table = tables.getJSONObject(index)
            val ids = table.getJSONArray("playerIds").let { list -> (0 until list.length()).map { list.getInt(it) } }
            if (playerId !in ids) continue
            val tableId = table.getInt("tableId")
            val detail = json("/public/tournaments/$tournamentId/tables/$round/$tableId")?.optJSONObject("table") ?: return null
            val seatsSet = listOf("playerEastId", "playerSouthId", "playerWestId", "playerNorthId")
                .all { detail.optString(it).let { id -> id.isNotBlank() && id != "null" } }
            return MyTable(round, tableId, ids.min() == playerId, seatsSet, detail.optBoolean("isCompleted"))
        }
        return null
    }

    private fun onTable(table: MyTable) = c.isOnScreen("Round ${table.roundId} - Table ${table.tableId}")

    /** Opens the table as a person does: from the reminder of the round, or from the floating button. */
    private fun goToTable(table: MyTable) {
        when {
            c.isOnScreen("Go to my table") -> c.clickButton("Go to my table")
            c.onAllNodesWithTag("myTableButton").fetchSemanticsNodes().isNotEmpty() -> c.onAllNodesWithTag("myTableButton").onFirst().performClick()
            c.isOnScreen("Back") && !onTable(table) -> c.clickButton("Back")
        }
        idle(2_000)
    }

    private fun setSeats() {
        c.clickButton("Set the seats")
        c.waitForText("Who sits at East?")
        repeat(3) {
            c.onAllNodesWithTag(SEAT_OPTION_TAG).onFirst().performClick()
            idle(700)
        }
        c.clickButton("Save the seats")
        log("seats saved")
    }

    private fun typeScore(score: Int) {
        val numpad = c.onAllNodesWithTag(SCORE_NUMPAD_TAG).fetchSemanticsNodes().isNotEmpty()
        if (!numpad) {
            c.field("Hand score").performTextInput(score.toString())
            return
        }
        score.toString().forEach { key ->
            c.onAllNodes(hasText(key.toString()) and hasClickAction() and hasAnyAncestor(hasTestTag(SCORE_NUMPAD_TAG))).onFirst().performClick()
            idle(300)
        }
    }

    /** One hand from the screens: a draw, a self-drawn win or a win on a discard, with a score from the usual ones. */
    private fun writeHand() {
        val roll = Random.nextDouble()
        if (roll < 0.1) {
            c.clickButton("Add draw")
        } else {
            c.clickButton("Add win")
            c.waitForText("Who won?")
            c.onAllNodesWithTag(PLAYER_CHOICE_TAG)[Random.nextInt(4)].performClick()
            c.waitForText("Who discarded?")
            if (roll < 0.55) c.clickButton("Self-draw") else c.onAllNodesWithTag(PLAYER_CHOICE_TAG)[Random.nextInt(3)].performClick()
            c.waitForText("Hand score")
            typeScore(SCORES.random())
            c.clickButton("Review")
        }
        c.waitForText("Check hand", timeoutMs = 30_000)
        c.clickButton("Confirm")
        c.waitForNoText("Check hand", timeoutMs = 30_000)
        handsWritten++
        log("hand $handsWritten saved")
    }

    /** After a failed step the player closes what is open, as a person would, and tries again later. */
    private fun recover(error: Throwable) {
        failures++
        log("step failed: ${error.message?.lineSequence()?.firstOrNull()}")
        c.dumpScreen("failure $failures")
        listOf("Cancel", "OK", "Close").forEach { label -> if (c.isOnScreen(label)) runCatching { c.clickButton(label) } }
        idle(3_000)
    }

    @Test
    fun playTheRehearsal() {
        assumeTrue("Not a rehearsal run", email.isNotBlank() && password.isNotBlank() && tournamentName.isNotBlank())
        val deadline = System.currentTimeMillis() + minutes * 60_000
        val session = signIn()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.filesDir, "auth_session.json").writeText(
            JSONObject().put("uid", session.getString("uid")).put("idToken", session.getString("idToken"))
                .put("refreshToken", session.getString("refreshToken")).toString(),
        )
        // The emulator can start before the organizer creates the tournament or assigns the player: it waits for both.
        while (playerId < 0) {
            check(System.currentTimeMillis() < deadline) { "$email is not in $tournamentName" }
            val list = json("/public/tournaments")?.optJSONArray("tournaments")
            tournamentId = list?.let { (0 until it.length()).map { index -> it.getJSONObject(index) } }
                ?.firstOrNull { it.getString("name") == tournamentName }?.getString("id").orEmpty()
            val me = E2eApi.call("GET", "/me", token = session.getString("idToken")).takeIf { it.status == 200 }?.json()?.optJSONArray("tournaments")
            playerId = me?.let { (0 until it.length()).map { index -> it.getJSONObject(index) } }
                ?.firstOrNull { it.getString("tournamentId") == tournamentId }?.getInt("playerId") ?: -1
            if (playerId < 0) Thread.sleep(15_000)
        }
        log("slot $playerId of $tournamentName")

        app.launch()
        c.waitForText("My tournaments", timeoutMs = 120_000)
        c.openTournament(tournamentName)
        var nextHandAt = 0L
        var missing = 0
        while (System.currentTimeMillis() < deadline) {
            try {
                val details = json("/public/tournaments/$tournamentId")
                // A deleted tournament (the end of a calibration) ends the play too.
                missing = if (details == null) missing + 1 else 0
                if (details?.optBoolean("isCompleted") == true || missing >= 3) break
                val table = myTable()
                if (table == null) {
                    idle(10_000)
                    continue
                }
                when {
                    table.isCompleted -> idle(15_000)
                    !onTable(table) -> goToTable(table)
                    !table.isScorer -> idle(10_000)
                    !table.seatsSet && c.isOnScreen("Set the seats") -> setSeats()
                    c.isOnScreen("Add win") && System.currentTimeMillis() >= nextHandAt -> {
                        writeHand()
                        nextHandAt = System.currentTimeMillis() + Random.nextLong(handSeconds.first() * 1_000L, (handSeconds.last() + 1) * 1_000L)
                    }
                    else -> idle(5_000)
                }
            } catch (error: Throwable) {
                recover(error)
            }
        }
        log("finished: $handsWritten hands written, $failures failed steps")
    }

    private companion object {
        const val TAG = "REHEARSAL"
        val SCORES = listOf(8, 8, 8, 9, 10, 10, 12, 13, 16, 17, 24, 32, 48, 64, 88)
    }
}
