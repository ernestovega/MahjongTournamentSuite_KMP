package com.etologic.mahjongtournamentsuite.player

import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.MyTournamentSlot
import com.etologic.mahjongtournamentsuite.domain.model.RegistrationSettings
import com.etologic.mahjongtournamentsuite.domain.model.RoundClock
import com.etologic.mahjongtournamentsuite.domain.model.RoundClocks
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.TournamentTable
import com.etologic.mahjongtournamentsuite.domain.model.myTableQrLink
import com.etologic.mahjongtournamentsuite.domain.model.physicalTableCount
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

    /** Player 6 sits at table 2 in round 1, table 1 in round 2 and table 2 in round 3. */
    private val allTables = listOf(
        table(1, 1, listOf(1, 2, 3, 4)),
        table(1, 2, listOf(5, 6, 7, 8)),
        table(2, 1, listOf(1, 6, 3, 8)),
        table(2, 2, listOf(5, 2, 7, 4)),
        table(3, 1, listOf(1, 2, 7, 8)),
        table(3, 2, listOf(5, 6, 3, 4)),
    )

    private fun table(roundId: Int, tableId: Int, players: List<Int>, completed: Boolean = false) = TournamentTable(
        roundId = roundId,
        tableId = tableId,
        playerIds = players,
        isCompleted = completed,
        useTotalsOnly = false,
        hasProgress = false,
    )

    /** A repository that answers only what the QR reads. The suspend calls answer at once. */
    private fun repository(
        tournament: Tournament,
        clocks: List<RoundClock>,
        tables: List<TournamentTable> = allTables,
        slots: List<MyTournamentSlot> = listOf(MyTournamentSlot("t", 6, 0)),
    ): TournamentRepository = Proxy.newProxyInstance(
        TournamentRepository::class.java.classLoader,
        arrayOf(TournamentRepository::class.java),
    ) { _, method, _ ->
        when (method.name) {
            "listMyTournamentSlots" -> AppResult.Success(slots)
            "listTournaments" -> AppResult.Success(listOf(tournament))
            "getRoundClocks" -> AppResult.Success(RoundClocks(clocks, 0))
            "listTournamentTables" -> AppResult.Success(tables)
            else -> error("Unexpected call ${method.name}")
        }
    } as TournamentRepository

    private fun startedMinutesAgo(roundId: Int, minutes: Int) = RoundClock(roundId, startedAtMs = Clock.System.now().toEpochMilliseconds() - minutes * 60_000L)

    @Test
    fun opensTheTableOfThePlayerInTheRoundInPlay() = runTest {
        val presenter = TableQrLinkPresenter(repository(tournament(), listOf(startedMinutesAgo(1, 200), startedMinutesAgo(2, 20))))

        assertEquals(TableRoute("t", 2, 1), presenter.find("t"))
    }

    @Test
    fun betweenRoundsItOpensTheTableOfTheNextRound() = runTest {
        // Round 2 ended long ago and round 3 has no clock yet.
        val presenter = TableQrLinkPresenter(repository(tournament(), listOf(startedMinutesAgo(1, 600), startedMinutesAgo(2, 400))))

        assertEquals(TableRoute("t", 3, 2), presenter.find("t"))
    }

    @Test
    fun beforeTheFirstRoundItOpensTheFirstTable() = runTest {
        assertEquals(TableRoute("t", 1, 2), TableQrLinkPresenter(repository(tournament(), emptyList())).find("t"))
    }

    @Test
    fun withoutAnAccountOrATableLeftItStaysOnMyTables() = runTest {
        val allPlayed = listOf(startedMinutesAgo(1, 900), startedMinutesAgo(2, 600), startedMinutesAgo(3, 300))

        assertNull(TableQrLinkPresenter(repository(tournament(), emptyList(), slots = emptyList())).find("t"))
        assertNull(TableQrLinkPresenter(repository(tournament(), allPlayed)).find("t"))
        assertNull(TableQrLinkPresenter(repository(tournament(drawPending = true), emptyList(), tables = emptyList())).find("t"))
    }

    @Test
    fun theLinkUsesThePublicLinkOfTheTournamentOrItsId() {
        val site = "https://mts.mahjongmadrid.com/"
        assertEquals("https://mts.mahjongmadrid.com/t/t/my-table", tournament().myTableQrLink(site))
        val withSlug = tournament().copy(registration = RegistrationSettings(slug = "6thmmc"))
        assertEquals("https://mts.mahjongmadrid.com/6thmmc/my-table", withSlug.myTableQrLink(site))
        // The link of the QR goes back through the parser of the player app.
        assertEquals(RegistrationLink(slug = "6thmmc", myTable = true), registrationLinkOf(withSlug.myTableQrLink(""), emptyMap()))
    }

    @Test
    fun aTableAlreadyClosedInTheRoundInPlayOpensTheNextOne() = runTest {
        // Round 2 is in play, but the table of player 6 in round 2 already closed: the QR opens round 3.
        val tables = allTables.map { if (it.roundId == 2 && it.tableId == 1) it.copy(isCompleted = true) else it }
        val presenter = TableQrLinkPresenter(repository(tournament(), listOf(startedMinutesAgo(1, 200), startedMinutesAgo(2, 20)), tables = tables))

        assertEquals(TableRoute("t", 3, 2), presenter.find("t"))
    }

    @Test
    fun aFinishedTournamentOrOneWhereTheAccountHasNoSlotStaysOnMyTables() = runTest {
        val finished = tournament().copy(isCompleted = true)
        assertNull(TableQrLinkPresenter(repository(finished, emptyList())).find("t"))
        // A slot of another tournament is not a slot of this one.
        assertNull(TableQrLinkPresenter(repository(tournament(), emptyList(), slots = listOf(MyTournamentSlot("other", 6, 0)))).find("t"))
        // A player without tables in this tournament (a substitute not seated yet).
        val presenter = TableQrLinkPresenter(repository(tournament(), emptyList(), slots = listOf(MyTournamentSlot("t", 9, 0))))
        assertNull(presenter.find("t"))
    }

    @Test
    fun theTablesOfTheTournamentAreItsPlayersByFour() {
        assertEquals(2, tournament().physicalTableCount)
        assertEquals(15, tournament().copy(numPlayers = 60).physicalTableCount)
    }

    @Test
    fun theLinkWithoutSlugUsesTheIdAndIgnoresABlankSlugAndTheSlashesOfTheSite() {
        val blankSlug = tournament().copy(registration = RegistrationSettings(slug = "   "))
        assertEquals("https://mts.example/t/t/my-table", blankSlug.myTableQrLink("https://mts.example///"))
        assertEquals("https://mts.example/bcn27/my-table", tournament().copy(registration = RegistrationSettings(slug = " bcn27 ")).myTableQrLink("https://mts.example"))
    }
}
