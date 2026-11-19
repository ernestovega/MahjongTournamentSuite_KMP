package com.etologic.mahjongtournamentsuite.domain.usecase

import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.GroupRecords
import com.etologic.mahjongtournamentsuite.domain.model.PersonTournamentDigest
import com.etologic.mahjongtournamentsuite.domain.model.Player
import com.etologic.mahjongtournamentsuite.domain.model.PlayerStatistics
import com.etologic.mahjongtournamentsuite.domain.model.StatisticsGroup
import com.etologic.mahjongtournamentsuite.domain.model.StatisticsGroupFilter
import com.etologic.mahjongtournamentsuite.domain.model.StatisticsOverview
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.TournamentStatistics
import com.etologic.mahjongtournamentsuite.domain.model.displayName
import com.etologic.mahjongtournamentsuite.domain.model.ofTotals
import com.etologic.mahjongtournamentsuite.domain.model.records
import com.etologic.mahjongtournamentsuite.domain.model.toPlayerStatistics
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerRepository
import com.etologic.mahjongtournamentsuite.domain.repository.RefreshMode
import com.etologic.mahjongtournamentsuite.domain.repository.StatisticsRepository
import com.etologic.mahjongtournamentsuite.domain.repository.TournamentRepository

/** The statistics of all tournaments together. Players are the registered players, joined by EMA number. */
data class OverviewStatisticsData(
    /** The number of tournaments with a completed table. */
    val tournamentCount: Int,
    /** The sums of all tournaments. [TournamentStatistics.players] holds only the players of the leaderboards. */
    val statistics: TournamentStatistics,
    /** Display name of every player by player id. */
    val names: Map<Int, String>,
    val profiles: Map<Int, PlayerProfile>,
    /** The number of players with statistics. */
    val playerCount: Int,
)

/** The statistics of a group of tournaments and players. Players are the registered players, joined by EMA number. */
data class GroupStatisticsData(
    val filter: StatisticsGroupFilter,
    /** The tournaments with a completed table that the filter keeps. */
    val tournamentIds: List<String>,
    /** The sums of the tables, and one player for each person of the group, with all the sums of the person. */
    val statistics: TournamentStatistics,
    val records: GroupRecords,
    /** Display name of every player by player id. */
    val names: Map<Int, String>,
    val profiles: Map<Int, PlayerProfile>,
)

/**
 * Statistics that the server keeps. The server updates the sums when a table changes,
 * so a request reads a fixed number of documents, or the tables of one player, and no hands.
 */
class LoadStoredStatisticsUseCase(
    private val statisticsRepository: StatisticsRepository,
    private val tournamentRepository: TournamentRepository,
    private val playerRepository: PlayerRepository,
) {
    suspend fun overview(refreshMode: RefreshMode = RefreshMode.IF_CHANGED): AppResult<OverviewStatisticsData> {
        val registry = when (val result = playerRepository.listPlayers(refreshMode)) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> return result
        }
        val overview = when (val result = statisticsRepository.loadOverview()) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> return result
        }
        return AppResult.Success(overviewFrom(overview, registry))
    }

    /** The statistics of a group of tournaments and players. The server keeps one summary of each tournament. */
    suspend fun group(filter: StatisticsGroupFilter, refreshMode: RefreshMode = RefreshMode.IF_CHANGED): AppResult<GroupStatisticsData> {
        val registry = when (val result = playerRepository.listPlayers(refreshMode)) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> return result
        }
        return when (val result = statisticsRepository.loadGroup(filter)) {
            is AppResult.Success -> AppResult.Success(groupFrom(filter, result.value, registry))
            is AppResult.Failure -> result
        }
    }

    /**
     * The statistics of one EMA player over every tournament that has a result of the player.
     * With [own] the server reads the player of the signed-in account, and [emaId] must be the EMA number of that account.
     */
    suspend fun person(
        emaId: String,
        own: Boolean = false,
        refreshMode: RefreshMode = RefreshMode.IF_CHANGED,
    ): AppResult<PersonStatisticsData> {
        val tournaments = when (val result = tournamentRepository.listTournaments(refreshMode)) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> return result
        }
        val registry = when (val result = playerRepository.listPlayers(refreshMode)) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> return result
        }
        val digests = when (val result = statisticsRepository.loadPerson(if (own) null else emaId)) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> return result
        }
        return AppResult.Success(personFrom(emaId, tournaments, digests, registry))
    }

    /** The final places of a player in some tournaments. A screen asks for the tournaments that it shows. */
    suspend fun standings(emaId: String, own: Boolean, tournamentIds: List<String>): AppResult<Map<String, Standing>> {
        val parts = tournamentIds.chunked(MAX_STANDINGS_PER_CALL)
        val standings = mutableMapOf<String, Standing>()
        parts.forEach { part ->
            when (val result = statisticsRepository.loadStandings(if (own) null else emaId, part)) {
                is AppResult.Success -> standings += result.value
                is AppResult.Failure -> return result
            }
        }
        return AppResult.Success(standings)
    }

    private companion object {
        /** The server accepts at most this many tournaments for each standings call. */
        const val MAX_STANDINGS_PER_CALL = 25
    }
}

private fun Player.toProfile() = PlayerProfile(displayName, emaId, country, photoUrl)

/** Builds the all-tournaments view from the sums of the server. */
internal fun overviewFrom(overview: StatisticsOverview, registry: List<Player>): OverviewStatisticsData {
    val registryByEma = registry.associateBy { it.emaId }
    // A player id is the position of the EMA number in the sorted list. It is stable for one load.
    val emaIds = overview.players.map { it.emaId }.sorted()
    val ids = emaIds.mapIndexed { index, emaId -> emaId to index + 1 }.toMap()
    val players = overview.players.map { PlayerStatistics.ofTotals(ids.getValue(it.emaId), it) }
    val profiles = emaIds.associate { emaId ->
        ids.getValue(emaId) to (registryByEma[emaId]?.toProfile() ?: PlayerProfile("EMA $emaId", emaId, "", null))
    }
    val statistics = TournamentStatistics(
        tablesTotal = overview.tablesCompleted,
        tablesCompleted = overview.tablesCompleted,
        tablesWithHands = overview.tablesWithHands,
        players = players,
        handsPlayed = overview.handsPlayed,
        drawnHands = overview.drawnHands,
        selfDraws = overview.selfDraws,
        averageHandScore = if (overview.winningHands > 0) overview.winningScoreSum.toDouble() / overview.winningHands else null,
        highestHand = overview.highestHand.takeIf { it > 0 },
        chickenHands = overview.chickenHands,
        penaltyCount = overview.penaltyCount,
        tables = emptyList(),
    )
    return OverviewStatisticsData(overview.tournaments, statistics, profiles.mapValues { it.value.name }, profiles, overview.playerCount)
}

/** Builds the view of a group from the summaries of the server. */
internal fun groupFrom(filter: StatisticsGroupFilter, group: StatisticsGroup, registry: List<Player>): GroupStatisticsData {
    val registryByEma = registry.associateBy { it.emaId }
    // A player id is the position of the EMA number in the sorted list. It is stable for one load.
    val persons = group.persons.sortedBy { it.emaId }
    val ids = persons.mapIndexed { index, person -> person.emaId to index + 1 }.toMap()
    val profiles = persons.associate { person ->
        ids.getValue(person.emaId) to (registryByEma[person.emaId]?.toProfile() ?: PlayerProfile("EMA ${person.emaId}", person.emaId, "", null))
    }
    val tables = group.tables
    val statistics = TournamentStatistics(
        tablesTotal = tables.tablesCompleted,
        tablesCompleted = tables.tablesCompleted,
        tablesWithHands = tables.tablesWithHands,
        players = persons.map { it.toPlayerStatistics(ids.getValue(it.emaId)) },
        handsPlayed = tables.handsPlayed,
        drawnHands = tables.drawnHands,
        selfDraws = tables.selfDraws,
        averageHandScore = if (tables.winningHands > 0) tables.winningScoreSum.toDouble() / tables.winningHands else null,
        highestHand = tables.highestHand.takeIf { it > 0 },
        chickenHands = tables.chickenHands,
        penaltyCount = tables.penaltyCount,
        tables = emptyList(),
    )
    return GroupStatisticsData(filter, group.tournamentIds, statistics, group.records(), profiles.mapValues { it.value.name }, profiles)
}

/** The view of one EMA player. [tournaments] are all tournaments of the app. Only those with a digest count. */
internal fun personFrom(
    emaId: String,
    tournaments: List<Tournament>,
    digests: List<PersonTournamentDigest>,
    registry: List<Player>,
): PersonStatisticsData {
    val byId = digests.associateBy { it.tournamentId }
    val parts = tournaments
        .sortedWith(compareBy({ it.eventStartDate ?: "" }, { it.id }))
        .mapNotNull { tournament ->
            val digest = byId[tournament.id] ?: return@mapNotNull null
            tournament to PlayerStatistics(0, digest.results.map { it.result })
        }
    val overall = PlayerStatistics(0, parts.flatMap { it.second.results })
    val profile = registry.firstOrNull { it.emaId == emaId }?.toProfile()
    val opponents = overall.results.flatMap { it.rivals }.map { it.opponent }.toSet()
    val rivalNames = registry.filter { it.emaId in opponents }.associate { it.emaId to it.displayName }
    return PersonStatisticsData(emaId, profile, overall, parts, rivalNames)
}

/** The person in the tournaments that [keep] keeps. The overall statistics are those of these tournaments only. */
fun PersonStatisticsData.only(keep: (Tournament) -> Boolean): PersonStatisticsData {
    val kept = tournaments.filter { keep(it.first) }
    if (kept.size == tournaments.size) return this
    return copy(overall = PlayerStatistics(0, kept.flatMap { it.second.results }), tournaments = kept)
}
