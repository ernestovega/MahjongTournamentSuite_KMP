package com.etologic.mahjongtournamentsuite.presentation.presenter

import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.GlobalUserRole
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.usecase.CalculateTournamentRankingsUseCase
import com.etologic.mahjongtournamentsuite.domain.usecase.DrawTournamentScheduleUseCase
import com.etologic.mahjongtournamentsuite.presentation.screen.FakeAdminRepository
import com.etologic.mahjongtournamentsuite.presentation.screen.FakePlayerAccountRepository
import com.etologic.mahjongtournamentsuite.presentation.screen.FakePlayerRepository
import com.etologic.mahjongtournamentsuite.presentation.screen.FakeSignedInAuthRepository
import com.etologic.mahjongtournamentsuite.presentation.screen.FakeTournamentRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** The presenters that only pass a call on: with their default arguments they give what the repository answers. */
class PresenterPassThroughTest {
    private val logger = Logger.withTag("test")
    private val tournaments = FakeTournamentRepository().apply {
        this.tournaments = listOf(Tournament(id = "t1", name = "Open", isTeams = true, numPlayers = 4, numRounds = 1))
    }
    private val auth = FakeSignedInAuthRepository()

    @Test
    fun theTournamentsPresenterPassesTheProfileCallsOn() = runTest {
        val presenter = TournamentsPresenter(tournaments, FakeAdminRepository(GlobalUserRole.SUPER_ADMIN), auth)
        assertIs<AppResult.Success<*>>(presenter.resolveVenueLink("https://maps.example.test/x"))
        assertTrue((presenter.updatePhoto("image/png", byteArrayOf(1)) as AppResult.Success).value.hasOwnPhoto)
        assertIs<AppResult.Success<*>>(presenter.removePhoto())
        assertIs<AppResult.Success<*>>(presenter.setPublicPhoto(true))
        assertIs<AppResult.Success<*>>(presenter.setPublicPhoto(false))
        runCatching { presenter.lookupUser("me@example.test") }
    }

    @Test
    fun theAuthPresenterPassesTheSavedCredentialsAndTheProfileOn() = runTest {
        val presenter = AuthPresenter(auth, logger)
        assertEquals(null, presenter.savedCredentials())
        presenter.clearSavedCredentials()
        assertEquals("me", (presenter.loadProfile() as AppResult.Success).value.uid)
    }

    @Test
    fun theTablePresentersUseTheHeldDataByDefault() = runTest {
        val tables = TablesPresenter(tournaments, FakePlayerRepository(), logger)
        assertIs<AppResult.Success<*>>(tables.loadRounds("t1"))
        assertIs<AppResult.Success<*>>(tables.loadPlayers("t1"))
        runCatching { tables.loadTables("t1", roundId = 1) }
        val manager = TableManagerPresenter(tournaments, logger)
        runCatching { manager.loadTableWithHands("t1", 1, 1) }
        runCatching { manager.loadRoundTablesWithHands("t1", 1) }
        val players = PlayersPresenter(tournaments, FakePlayerRepository(), FakePlayerAccountRepository(), logger)
        assertIs<AppResult.Success<*>>(players.loadPlayers("t1"))
        val create = CreateTournamentPresenter(tournaments, logger, DrawTournamentScheduleUseCase())
        assertIs<AppResult.Success<*>>(create.resolveVenueLink("https://maps.example.test/x"))
    }

    @Test
    fun theRankingStopsAtTheFirstListThatFails() = runTest {
        val ranking = RankingPresenter(tournaments, FakePlayerRepository(), CalculateTournamentRankingsUseCase(), logger)
        tournaments.failTeams = true
        assertEquals(AppResult.Failure(AppError.Network), ranking.load("t1"))
        tournaments.failPlayers = true
        assertEquals(AppResult.Failure(AppError.Network), ranking.load("t1"))
        tournaments.tournamentsError = AppError.Network
        assertEquals(AppResult.Failure(AppError.Network), ranking.load("t1"))
    }
}
