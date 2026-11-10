package com.etologic.mahjongtournamentsuite.player

import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.HandPreview
import com.etologic.mahjongtournamentsuite.domain.model.HandProposal
import com.etologic.mahjongtournamentsuite.domain.model.PenaltyEntry
import com.etologic.mahjongtournamentsuite.domain.model.Player
import com.etologic.mahjongtournamentsuite.domain.model.RankingTable
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.UserProfile
import com.etologic.mahjongtournamentsuite.domain.repository.AuthRepository
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerHandRepository
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerRepository
import com.etologic.mahjongtournamentsuite.domain.repository.RefreshMode
import com.etologic.mahjongtournamentsuite.domain.repository.TournamentRepository
import com.etologic.mahjongtournamentsuite.domain.telemetry.NoTelemetry
import com.etologic.mahjongtournamentsuite.domain.telemetry.Telemetry
import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryEvent
import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryParam
import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryProperty
import com.etologic.mahjongtournamentsuite.domain.telemetry.telemetryRole
import com.etologic.mahjongtournamentsuite.domain.telemetry.track
import com.etologic.mahjongtournamentsuite.domain.usecase.CalculateTournamentRankingsUseCase
import com.etologic.mahjongtournamentsuite.domain.usecase.LoadStatisticsUseCase
import com.etologic.mahjongtournamentsuite.domain.usecase.LoadStoredStatisticsUseCase
import com.etologic.mahjongtournamentsuite.domain.usecase.PersonStatisticsData
import com.etologic.mahjongtournamentsuite.domain.usecase.Standing
import com.etologic.mahjongtournamentsuite.domain.usecase.TournamentStatisticsData
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class PlayerSessionPresenter(
    private val authRepository: AuthRepository,
    private val logger: Logger,
    private val telemetry: Telemetry = NoTelemetry,
) {
    suspend fun hasSession(): Boolean = authRepository.currentSession() != null

    /** The EMA number of the signed-in player, or null without an account or without a linked player. */
    suspend fun myEmaId(refreshMode: RefreshMode = RefreshMode.IF_CHANGED): String? {
        if (!hasSession()) return null
        return (authRepository.getMe(refreshMode) as? AppResult.Success)?.value?.emaId?.takeIf { it.isNotBlank() }
    }

    suspend fun signIn(email: String, password: String): AppResult<Unit> = when (val result = authRepository.signIn(email.trim(), password).track(telemetry, TelemetryEvent.LOGIN)) {
        is AppResult.Success -> AppResult.Success(Unit)
        is AppResult.Failure -> result
    }

    suspend fun loadProfile(): AppResult<UserProfile> = authRepository.getMe(RefreshMode.FORCE).also { result ->
        if (result is AppResult.Success) telemetry.userProperty(TelemetryProperty.ROLE, result.value.roles.telemetryRole())
    }

    suspend fun updateAlias(alias: String): AppResult<UserProfile> = authRepository.updateAlias(alias)

    suspend fun updatePhoto(contentType: String, bytes: ByteArray): AppResult<UserProfile> = authRepository.updatePhoto(contentType, bytes)

    suspend fun removePhoto(): AppResult<UserProfile> = authRepository.removePhoto()

    suspend fun resetPassword(email: String): AppResult<Unit> = authRepository.requestPasswordReset(email.trim()).track(telemetry, TelemetryEvent.PASSWORD_RESET_REQUESTED)

    suspend fun signOut() {
        logger.i { "Player signs out." }
        authRepository.signOut()
        telemetry.event(TelemetryEvent.LOGOUT)
        telemetry.userProperty(TelemetryProperty.ROLE, null)
    }
}

class PlayerTournamentPresenter(
    private val tournamentRepository: TournamentRepository,
    private val playerRepository: PlayerRepository,
    private val authRepository: AuthRepository,
    private val calculateRankings: CalculateTournamentRankingsUseCase,
) {
    suspend fun listTournaments(refreshMode: RefreshMode = RefreshMode.IF_CHANGED): AppResult<List<Tournament>> = tournamentRepository.listTournaments(refreshMode)

    /** The ids of the tournaments where the signed-in account plays. Empty without an account. */
    suspend fun myTournamentIds(): Set<String> =
        (tournamentRepository.listMyTournamentSlots() as? AppResult.Success)?.value.orEmpty().mapTo(mutableSetOf()) { it.tournamentId }

    /**
     * Loads the tournament. The repositories check the revision first and load only what changed.
     * The requests run at the same time, so the screen waits for the slowest one and not for all of them in a row.
     */
    suspend fun load(
        tournamentId: String,
        refreshMode: RefreshMode = RefreshMode.IF_CHANGED,
    ): AppResult<PlayerTournamentView> = coroutineScope {
        // One request answers the revision checks of all the reads below, so it goes first. CACHED reads do not check.
        if (refreshMode != RefreshMode.CACHED) tournamentRepository.prefetchRevisions(tournamentId)
        val tournamentsRequest = async { tournamentRepository.listTournaments(refreshMode) }
        val slotsRequest = async { tournamentRepository.listTournamentPlayers(tournamentId, refreshMode) }
        val registryRequest = async { playerRepository.listPlayers(refreshMode) }
        val tablesRequest = async { tournamentRepository.getAllTablesWithHands(tournamentId, refreshMode) }
        // Without an account nobody is marked as the own player.
        val meRequest = async { if (authRepository.currentSession() == null) null else authRepository.getMe(refreshMode) }

        val tournament = when (val result = tournamentsRequest.await()) {
            is AppResult.Success -> result.value.firstOrNull { it.id == tournamentId }
                ?: return@coroutineScope AppResult.Failure(AppError.Unexpected("Tournament not found"))
            is AppResult.Failure -> return@coroutineScope result
        }
        // Only a team tournament has teams, so this request waits for the tournament.
        val teamsRequest = async {
            if (tournament.isTeams) {
                (tournamentRepository.listTournamentTeams(tournamentId, refreshMode) as? AppResult.Success)?.value.orEmpty()
            } else {
                emptyList()
            }
        }
        val slots = when (val result = slotsRequest.await()) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> return@coroutineScope result
        }
        val registry = when (val result = registryRequest.await()) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> return@coroutineScope result
        }
        val tables = when (val result = tablesRequest.await()) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> return@coroutineScope result
        }
        val teams = teamsRequest.await()
        val myEmaId = (meRequest.await() as? AppResult.Success)?.value?.emaId
        // A table whose time is over counts as completed, even when nobody marked its last hand.
        val expiredTables = tournament.closeExpiredTables(tables, tournament.currentVenueDateTime())
        val rankings = calculateRankings(
            slots,
            expiredTables.map { (table, hands) -> RankingTable(table, hands) },
            tournament.isTeams,
        )
        AppResult.Success(buildPlayerTournamentView(tournament, slots, registry, expiredTables, rankings, myEmaId, teams))
    }
}

class PlayerTablePresenter(
    private val handRepository: PlayerHandRepository,
    private val telemetry: Telemetry = NoTelemetry,
) {
    suspend fun addPenalties(
        tournamentId: String,
        roundId: Int,
        tableId: Int,
        penalties: List<PenaltyEntry>,
        expectedVersion: Long,
    ): AppResult<Unit> = handRepository.addPenalties(tournamentId, roundId, tableId, penalties, expectedVersion)
        .track(telemetry, TelemetryEvent.PENALTY_ADDED, mapOf(TelemetryParam.HANDS to penalties.size))

    suspend fun preview(tournamentId: String, roundId: Int, tableId: Int, proposal: HandProposal): AppResult<HandPreview> = handRepository.previewHand(tournamentId, roundId, tableId, proposal).track(telemetry, TelemetryEvent.HAND_PREVIEWED)

    suspend fun record(
        tournamentId: String,
        roundId: Int,
        tableId: Int,
        proposal: HandProposal,
        expectedVersion: Long,
    ): AppResult<HandPreview> = handRepository.recordHand(tournamentId, roundId, tableId, proposal, expectedVersion)
        .track(telemetry, TelemetryEvent.HAND_RECORDED)
}

class PlayerStatisticsPresenter(
    private val loadStatistics: LoadStatisticsUseCase,
    private val loadStored: LoadStoredStatisticsUseCase,
    private val authRepository: AuthRepository,
    private val playerRepository: PlayerRepository,
    private val telemetry: Telemetry = NoTelemetry,
) {
    suspend fun tournament(
        tournamentId: String,
        refreshMode: RefreshMode = RefreshMode.IF_CHANGED,
    ): AppResult<TournamentStatisticsData> = loadStatistics.tournament(tournamentId, refreshMode).viewed("tournament")

    /** The statistics of the signed-in player over all tournaments where the player is registered. */
    suspend fun me(refreshMode: RefreshMode = RefreshMode.IF_CHANGED): AppResult<PersonStatisticsData> {
        val emaId = when (val result = authRepository.getMe(refreshMode)) {
            is AppResult.Success -> result.value.emaId
            is AppResult.Failure -> return result
        } ?: return AppResult.Failure(AppError.Unexpected("Your account is not linked to a player yet."))
        // The statistics are public, so the own ones are read by the EMA number like those of anyone else.
        return loadStored.person(emaId, own = false, refreshMode = refreshMode).viewed("me")
    }

    /** The players of the EMA registry. */
    suspend fun players(refreshMode: RefreshMode = RefreshMode.IF_CHANGED): AppResult<List<Player>> = playerRepository.listPlayers(refreshMode)

    /** The statistics of another player, in the public tournaments. */
    suspend fun person(emaId: String, refreshMode: RefreshMode = RefreshMode.IF_CHANGED): AppResult<PersonStatisticsData> = loadStored.person(emaId, own = false, refreshMode = refreshMode).viewed("person")

    /** The final places of a player in some tournaments. The list shows them for the tournaments on screen. */
    suspend fun standings(emaId: String, tournamentIds: List<String>): Map<String, Standing> =
        if (emaId.isBlank()) emptyMap() else (loadStored.standings(emaId, own = false, tournamentIds) as? AppResult.Success)?.value.orEmpty()

    private fun <T> AppResult<T>.viewed(scope: String) = track(telemetry, TelemetryEvent.STATISTICS_VIEWED, mapOf(TelemetryParam.SCOPE to scope))
}
