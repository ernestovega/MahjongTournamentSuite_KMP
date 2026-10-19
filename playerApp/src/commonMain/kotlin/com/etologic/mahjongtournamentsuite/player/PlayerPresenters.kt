package com.etologic.mahjongtournamentsuite.player

import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.HandProposal
import com.etologic.mahjongtournamentsuite.domain.model.PenaltyEntry
import com.etologic.mahjongtournamentsuite.domain.model.HandPreview
import com.etologic.mahjongtournamentsuite.domain.model.Player
import com.etologic.mahjongtournamentsuite.domain.model.RankingTable
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.repository.AuthRepository
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerAccountRepository
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerHandRepository
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerRepository
import com.etologic.mahjongtournamentsuite.domain.repository.RefreshMode
import com.etologic.mahjongtournamentsuite.domain.repository.TournamentRepository
import com.etologic.mahjongtournamentsuite.data.backend.FunctionsBackendApi
import com.etologic.mahjongtournamentsuite.domain.usecase.CalculateTournamentRankingsUseCase
import com.etologic.mahjongtournamentsuite.domain.usecase.LoadStatisticsUseCase
import com.etologic.mahjongtournamentsuite.domain.usecase.LoadStoredStatisticsUseCase
import com.etologic.mahjongtournamentsuite.domain.usecase.Standing
import com.etologic.mahjongtournamentsuite.domain.usecase.PersonStatisticsData
import com.etologic.mahjongtournamentsuite.domain.usecase.TournamentStatisticsData

class PlayerSessionPresenter(
    private val authRepository: AuthRepository,
    private val accountRepository: PlayerAccountRepository,
    private val backendApi: FunctionsBackendApi,
    private val logger: Logger,
) {
    suspend fun backendVersion(): String? = runCatching { backendApi.version().version }
        .onFailure { logger.w(it) { "Could not load backend version." } }
        .getOrNull()

    suspend fun hasSession(): Boolean = authRepository.currentSession() != null

    suspend fun signIn(email: String, password: String): AppResult<Unit> =
        when (val result = authRepository.signIn(email.trim(), password)) {
            is AppResult.Success -> AppResult.Success(Unit)
            is AppResult.Failure -> result
        }

    suspend fun resetPassword(email: String): AppResult<Unit> = authRepository.requestPasswordReset(email.trim())

    suspend fun signOut() {
        logger.i { "Player signs out." }
        authRepository.signOut()
    }

    suspend fun acceptInvitation(token: String, password: String?): AppResult<String> =
        accountRepository.acceptInvitation(token, password)
}

class PlayerTournamentPresenter(
    private val tournamentRepository: TournamentRepository,
    private val playerRepository: PlayerRepository,
    private val authRepository: AuthRepository,
    private val calculateRankings: CalculateTournamentRankingsUseCase,
) {
    suspend fun listTournaments(refreshMode: RefreshMode = RefreshMode.IF_CHANGED): AppResult<List<Tournament>> =
        tournamentRepository.listTournaments(refreshMode)

    /** Loads the tournament. The repositories check the revision first and load only what changed. */
    suspend fun load(
        tournamentId: String,
        refreshMode: RefreshMode = RefreshMode.IF_CHANGED,
    ): AppResult<PlayerTournamentView> {
        val tournament = when (val result = tournamentRepository.listTournaments(refreshMode)) {
            is AppResult.Success -> result.value.firstOrNull { it.id == tournamentId }
                ?: return AppResult.Failure(AppError.Unexpected("Tournament not found"))
            is AppResult.Failure -> return result
        }
        val slots = when (val result = tournamentRepository.listTournamentPlayers(tournamentId, refreshMode)) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> return result
        }
        val registry = when (val result = playerRepository.listPlayers(refreshMode)) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> return result
        }
        val tables = when (val result = tournamentRepository.getAllTablesWithHands(tournamentId, refreshMode)) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> return result
        }
        val teams = if (tournament.isTeams) {
            (tournamentRepository.listTournamentTeams(tournamentId, refreshMode) as? AppResult.Success)?.value.orEmpty()
        } else {
            emptyList()
        }
        val myEmaId = (authRepository.getMe(refreshMode) as? AppResult.Success)?.value?.emaId
        val rankings = calculateRankings(
            slots,
            tables.map { (table, hands) -> RankingTable(table, hands) },
            tournament.isTeams,
        )
        return AppResult.Success(buildPlayerTournamentView(tournament, slots, registry, tables, rankings, myEmaId, teams))
    }
}

class PlayerTablePresenter(
    private val handRepository: PlayerHandRepository,
) {
    suspend fun addPenalties(
        tournamentId: String,
        roundId: Int,
        tableId: Int,
        penalties: List<PenaltyEntry>,
        expectedVersion: Long,
    ): AppResult<Unit> = handRepository.addPenalties(tournamentId, roundId, tableId, penalties, expectedVersion)

    suspend fun preview(tournamentId: String, roundId: Int, tableId: Int, proposal: HandProposal): AppResult<HandPreview> =
        handRepository.previewHand(tournamentId, roundId, tableId, proposal)

    suspend fun record(
        tournamentId: String,
        roundId: Int,
        tableId: Int,
        proposal: HandProposal,
        expectedVersion: Long,
    ): AppResult<HandPreview> = handRepository.recordHand(tournamentId, roundId, tableId, proposal, expectedVersion)
}

class PlayerStatisticsPresenter(
    private val loadStatistics: LoadStatisticsUseCase,
    private val loadStored: LoadStoredStatisticsUseCase,
    private val authRepository: AuthRepository,
    private val playerRepository: PlayerRepository,
) {
    suspend fun tournament(
        tournamentId: String,
        refreshMode: RefreshMode = RefreshMode.IF_CHANGED,
    ): AppResult<TournamentStatisticsData> = loadStatistics.tournament(tournamentId, refreshMode)

    /** The statistics of the signed-in player over all tournaments where the player is registered. */
    suspend fun me(refreshMode: RefreshMode = RefreshMode.IF_CHANGED): AppResult<PersonStatisticsData> {
        val emaId = when (val result = authRepository.getMe(refreshMode)) {
            is AppResult.Success -> result.value.emaId
            is AppResult.Failure -> return result
        } ?: return AppResult.Failure(AppError.Unexpected("Your account is not linked to a player yet."))
        return loadStored.person(emaId, own = true, refreshMode = refreshMode)
    }

    /** The players that the signed-in player plays with, from the tournaments that the account can see. */
    suspend fun players(refreshMode: RefreshMode = RefreshMode.IF_CHANGED): AppResult<List<Player>> =
        playerRepository.listPlayers(refreshMode)

    /** The statistics of another player, in the tournaments that the account can see. */
    suspend fun person(emaId: String, refreshMode: RefreshMode = RefreshMode.IF_CHANGED): AppResult<PersonStatisticsData> =
        loadStored.person(emaId, own = false, refreshMode = refreshMode)

    /** The final places of a player in some tournaments. The list shows them for the tournaments on screen. */
    suspend fun standings(emaId: String, tournamentIds: List<String>, own: Boolean = true): Map<String, Standing> =
        (loadStored.standings(emaId, own = own, tournamentIds) as? AppResult.Success)?.value.orEmpty()
}
