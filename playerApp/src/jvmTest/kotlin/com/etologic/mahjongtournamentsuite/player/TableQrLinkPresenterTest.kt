package com.etologic.mahjongtournamentsuite.player

import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.RegistrationSettings
import com.etologic.mahjongtournamentsuite.domain.model.RoundClock
import com.etologic.mahjongtournamentsuite.domain.model.RoundClocks
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.TournamentTable
import com.etologic.mahjongtournamentsuite.domain.model.tableQrLink
import com.etologic.mahjongtournamentsuite.domain.repository.TournamentRepository
import kotlinx.coroutines.test.runTest
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Clock

class TableQrLinkPresenterTest {
    private fun tournament(drawPending: Boolean = false) = Tournament(
        id = "t",
        name = "Open",
        isTeams = false,
        numPlayers = 8,
        numRounds = 3,
        drawPending = drawPending,
    )

    private fun table(roundId: Int, tableId: Int, progress: Boolean = false) = TournamentTable(
        roundId = roundId,
        tableId = tableId,
        playerIds = listOf(1, 2, 3, 4),
        isCompleted = false,
        useTotalsOnly = false,
        hasProgress = progress,
    )

    private val allTables = (1..3).flatMap { round -> listOf(table(round, 1), table(round, 2)) }

    /** A repository that answers only what the QR reads. The suspend calls answer at once. */
    private fun repository(tournament: Tournament, clocks: List<RoundClock>, tables: List<TournamentTable>): TournamentRepository = Proxy.newProxyInstance(
        TournamentRepository::class.java.classLoader,
        arrayOf(TournamentRepository::class.java),
    ) { _, method, _ ->
        when (method.name) {
            "listTournaments" -> AppResult.Success(listOf(tournament))
            "getRoundClocks" -> AppResult.Success(RoundClocks(clocks, 0))
            "listTournamentTables" -> AppResult.Success(tables)
            else -> error("Unexpected call ${method.name}")
        }
    } as TournamentRepository

    private fun clockStartedMinutesAgo(roundId: Int, minutes: Int) = RoundClock(roundId, startedAtMs = Clock.System.now().toEpochMilliseconds() - minutes * 60_000L)

    @Test
    fun opensTheTableInTheRoundInPlay() = runTest {
        val presenter = TableQrLinkPresenter(repository(tournament(), listOf(clockStartedMinutesAgo(1, 200), clockStartedMinutesAgo(2, 20)), allTables))

        assertEquals(TableRoute("t", 2, 2), presenter.find("t", 2))
    }

    @Test
    fun betweenRoundsItOpensTheLastRoundThatStarted() = runTest {
        // Round 2 ended long ago and round 3 has no clock yet.
        val presenter = TableQrLinkPresenter(repository(tournament(), listOf(clockStartedMinutesAgo(1, 600), clockStartedMinutesAgo(2, 400)), allTables))

        assertEquals(TableRoute("t", 2, 1), presenter.find("t", 1))
    }

    @Test
    fun withoutClocksTheRoundWithHandsOrTheFirstRound() = runTest {
        val withHands = allTables.map { if (it.roundId == 2 && it.tableId == 1) it.copy(hasProgress = true) else it }

        assertEquals(TableRoute("t", 2, 2), TableQrLinkPresenter(repository(tournament(), emptyList(), withHands)).find("t", 2))
        assertEquals(TableRoute("t", 1, 2), TableQrLinkPresenter(repository(tournament(), emptyList(), allTables)).find("t", 2))
    }

    @Test
    fun aTableThatDoesNotExistOrATournamentWithoutDrawOpenNoTable() = runTest {
        assertNull(TableQrLinkPresenter(repository(tournament(), emptyList(), allTables)).find("t", 3))
        assertNull(TableQrLinkPresenter(repository(tournament(drawPending = true), emptyList(), emptyList())).find("t", 1))
        assertNull(TableQrLinkPresenter(repository(tournament(), emptyList(), allTables)).find("other", 1))
    }

    @Test
    fun theLinkUsesThePublicLinkOfTheTournamentOrItsId() {
        val site = "https://mts.mahjongmadrid.com/"
        assertEquals("https://mts.mahjongmadrid.com/t/t/table/5", tournament().tableQrLink(site, 5))
        assertEquals(
            "https://mts.mahjongmadrid.com/6thmmc/table/5",
            tournament().copy(registration = RegistrationSettings(slug = "6thmmc")).tableQrLink(site, 5),
        )
        // The link of the QR goes back to the same table through the parser of the player app.
        val path = tournament().copy(registration = RegistrationSettings(slug = "6thmmc")).tableQrLink("", 7)
        assertEquals(RegistrationLink(slug = "6thmmc", tableId = 7), registrationLinkOf(path, emptyMap()))
    }
}
