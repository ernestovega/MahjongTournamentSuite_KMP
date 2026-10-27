package com.etologic.mahjongtournamentsuite.domain.usecase

import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.Player
import com.etologic.mahjongtournamentsuite.domain.model.PlayerStatistics
import com.etologic.mahjongtournamentsuite.domain.model.RankingTable
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.TournamentPlayer
import com.etologic.mahjongtournamentsuite.domain.model.TournamentStatistics
import com.etologic.mahjongtournamentsuite.domain.model.displayName
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerRepository
import com.etologic.mahjongtournamentsuite.domain.repository.RefreshMode
import com.etologic.mahjongtournamentsuite.domain.repository.TournamentRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** What a statistics header shows about one player. */
data class PlayerProfile(
    val name: String,
    /** Empty for a player without an EMA number. */
    val emaId: String,
    val country: String,
    val photoUrl: String?,
)

/** The statistics of one tournament with what a screen needs to name the players. */
data class TournamentStatisticsData(
    val tournament: Tournament,
    val slots: List<TournamentPlayer>,
    val statistics: TournamentStatistics,
    /** Display name of every slot by slot id. */
    val names: Map<Int, String>,
    /** Header data of every slot by slot id. */
    val profiles: Map<Int, PlayerProfile>,
    /** Display name of every rival key. See `RivalTally.opponent`. */
    val rivalNames: Map<String, String> = emptyMap(),
)

/** The statistics of one person over every tournament where the person plays. */
data class PersonStatisticsData(
    val emaId: String,
    /** Null when the EMA number is not in the player registry. */
    val profile: PlayerProfile?,
    val overall: PlayerStatistics,
    /** The tournaments of the person in play order, each with the person statistics of that tournament. */
    val tournaments: List<Pair<Tournament, PlayerStatistics>>,
    /** Display name of every rival key. See `RivalTally.opponent`. */
    val rivalNames: Map<String, String> = emptyMap(),
)

/** A place in a tournament ranking. [rank] is the place from 1 to [of]. Equal points and score share a place. */
data class Standing(
    val rank: Int,
    val of: Int,
    val points: Double,
    val score: Int,
    /** The place of the team of the player. Null when the team is not known. */
    val teamRank: Int? = null,
    /** The place of the player by chicken hands. Null when the player won no chicken hand. */
    val chickenRank: Int? = null,
    /** The place of the best hand of the player. Null when the player won no hand. */
    val bestHandRank: Int? = null,
)

class LoadStatisticsUseCase(
    private val tournamentRepository: TournamentRepository,
    private val playerRepository: PlayerRepository,
    private val calculateStatistics: CalculateStatisticsUseCase,
) {
    /** Statistics of one tournament. The repositories check the revision and load only what changed. */
    suspend fun tournament(
        tournamentId: String,
        refreshMode: RefreshMode = RefreshMode.IF_CHANGED,
    ): AppResult<TournamentStatisticsData> {
        val tournament = when (val result = tournamentRepository.listTournaments(refreshMode)) {
            is AppResult.Success -> result.value.firstOrNull { it.id == tournamentId }
                ?: return AppResult.Failure(AppError.Unexpected("Tournament not found"))
            is AppResult.Failure -> return result
        }
        val registry = when (val result = playerRepository.listPlayers(refreshMode)) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> return result
        }
        return tournament(tournament, registry, refreshMode)
    }

    private suspend fun tournament(
        tournament: Tournament,
        registry: List<Player>,
        refreshMode: RefreshMode,
    ): AppResult<TournamentStatisticsData> = coroutineScope {
        val slotsRequest = async { tournamentRepository.listTournamentPlayers(tournament.id, refreshMode) }
        val tablesRequest = async { tournamentRepository.getAllTablesWithHands(tournament.id, refreshMode) }
        val slots = when (val result = slotsRequest.await()) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> return@coroutineScope result
        }
        val tables = when (val result = tablesRequest.await()) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> return@coroutineScope result
        }
        val statistics = calculateStatistics(
            players = slots,
            tables = tables.map { (table, hands) -> RankingTable(table, hands) },
            tournamentId = tournament.id,
        )
        val names = slotNames(slots, registry)
        val rivalNames = slots.associate { slot ->
            slot.assignedEmaId.orEmpty().ifBlank { "${tournament.id}#${slot.id}" } to names.getValue(slot.id)
        }
        AppResult.Success(
            TournamentStatisticsData(tournament, slots, statistics, names, slotProfiles(slots, registry), rivalNames),
        )
    }

    private fun slotProfiles(slots: List<TournamentPlayer>, registry: List<Player>): Map<Int, PlayerProfile> {
        val byEma = registry.associateBy { it.emaId }
        return slots.associate { slot ->
            val member = slot.assignedEmaId?.let(byEma::get)
            slot.id to when {
                member != null -> member.toProfile()
                else -> PlayerProfile(
                    name = slot.name.ifBlank { "Player ${slot.id}" },
                    emaId = slot.assignedEmaId.orEmpty(),
                    country = slot.country,
                    photoUrl = null,
                )
            }
        }
    }

    private fun Player.toProfile() = PlayerProfile(displayName, emaId, country, photoUrl)

    private fun slotNames(slots: List<TournamentPlayer>, registry: List<Player>): Map<Int, String> {
        val byEma = registry.associateBy { it.emaId }
        return slots.associate { slot ->
            slot.id to (
                slot.assignedEmaId?.let(byEma::get)?.displayName
                    ?: "Player ${slot.id}"
                )
        }
    }
}
