package com.etologic.mahjongtournamentsuite.player

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.runComposeUiTest
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.MatchEndMode
import com.etologic.mahjongtournamentsuite.domain.model.RoundClock
import com.etologic.mahjongtournamentsuite.domain.model.TableHand
import com.etologic.mahjongtournamentsuite.domain.model.TableState
import com.etologic.mahjongtournamentsuite.domain.repository.RefreshMode
import com.etologic.mahjongtournamentsuite.player.screenshots.PlayerFrame
import com.etologic.mahjongtournamentsuite.player.screenshots.playerModules
import com.etologic.mahjongtournamentsuite.screenshots.fake.ScreenshotData
import com.etologic.mahjongtournamentsuite.screenshots.fake.ScreenshotData.ONGOING
import com.etologic.mahjongtournamentsuite.screenshots.fake.ScreenshotTournamentRepository
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/** The data of the screenshots, with penalties at every table and a clock in round 3. */
private class ClockAndPenaltiesRepository(clock: RoundClock) : ScreenshotTournamentRepository(
    tournaments = ScreenshotData.tournaments.map {
        if (it.id == ONGOING) {
            it.copy(roundClocks = listOf(clock), matchDurationMinutes = 90, matchEndMode = MatchEndMode.FINISH_HAND, matchExtraMinutes = 30)
        } else {
            it
        }
    },
) {
    private fun Pair<TableState, List<TableHand>>.withPenalties() = first to second.mapIndexed { index, hand ->
        if (index == 0) hand.copy(playerEastPenalty = "-10", playerSouthPenalty = "10") else hand
    } + TableHand(99, "", "", "", false, false, "", "", "-5", "5")

    override suspend fun getTableWithHands(tournamentId: String, roundId: Int, tableId: Int, refreshMode: RefreshMode) =
        AppResult.Success(ScreenshotData.table(tournamentId, roundId, tableId).withPenalties())

    override suspend fun getRoundTablesWithHands(tournamentId: String, roundId: Int, refreshMode: RefreshMode) =
        AppResult.Success((1..4).map { ScreenshotData.table(tournamentId, roundId, it).withPenalties() })

    override suspend fun getAllTablesWithHands(tournamentId: String, refreshMode: RefreshMode) =
        AppResult.Success((1..4).flatMap { round -> (1..4).map { ScreenshotData.table(tournamentId, round, it).withPenalties() } })
}

/** A table of a round with its clock: the time left, paused or over, and the penalty lines between the hands. */
@OptIn(ExperimentalTestApi::class)
class TableClockAndPenaltiesUiTest {
    private val original = Locale.getDefault()

    @BeforeTest
    fun english() = Locale.setDefault(Locale.ENGLISH)

    @AfterTest
    fun restore() = Locale.setDefault(original)

    private fun ComposeUiTest.showTable(clock: RoundClock) {
        setContent {
            PlayerFrame({ playerModules(tournaments = ClockAndPenaltiesRepository(clock)) }, dark = false) { nav ->
                TableScreen(nav, ONGOING, roundId = 3, tableId = 1)
            }
        }
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasTestTag("round-clock")).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun ComposeUiTest.waitFor(text: String) = waitUntil(timeoutMillis = 10_000) {
        onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()
    }

    private val now get() = System.currentTimeMillis()

    @Test
    fun aRunningClockShowsTheTimeLeftAndThePenaltiesShow() = runComposeUiTest {
        showTable(RoundClock(3, startedAtMs = now - 60_000))
        waitFor("Pen.")
    }

    @Test
    fun aPausedClockSaysSo() = runComposeUiTest {
        showTable(RoundClock(3, startedAtMs = now - 60_000, pausedAtMs = now - 30_000))
        waitFor("Paused")
    }

    @Test
    fun aClockThatIsOverSaysSo() = runComposeUiTest {
        // In the extra time to finish the hand: the table is still played.
        showTable(RoundClock(3, startedAtMs = now - 95 * 60_000L))
        waitFor("The match time is over.")
    }
}
