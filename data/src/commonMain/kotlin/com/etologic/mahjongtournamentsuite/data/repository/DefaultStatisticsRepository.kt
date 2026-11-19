package com.etologic.mahjongtournamentsuite.data.repository

import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.data.backend.FunctionsBackendApi
import com.etologic.mahjongtournamentsuite.data.backend.PUBLIC_TOKEN
import com.etologic.mahjongtournamentsuite.data.backend.dto.DigestResultDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.StandingDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.StatisticsGroupDto
import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.DigestResult
import com.etologic.mahjongtournamentsuite.domain.model.GroupPerson
import com.etologic.mahjongtournamentsuite.domain.model.GroupTables
import com.etologic.mahjongtournamentsuite.domain.model.HandRun
import com.etologic.mahjongtournamentsuite.domain.model.PersonTournamentDigest
import com.etologic.mahjongtournamentsuite.domain.model.PlayerTotals
import com.etologic.mahjongtournamentsuite.domain.model.RivalTally
import com.etologic.mahjongtournamentsuite.domain.model.StatisticsGroup
import com.etologic.mahjongtournamentsuite.domain.model.StatisticsGroupFilter
import com.etologic.mahjongtournamentsuite.domain.model.StatisticsOverview
import com.etologic.mahjongtournamentsuite.domain.model.TableResult
import com.etologic.mahjongtournamentsuite.domain.repository.AuthRepository
import com.etologic.mahjongtournamentsuite.domain.repository.StatisticsRepository
import com.etologic.mahjongtournamentsuite.domain.usecase.Standing

class DefaultStatisticsRepository(
    private val backendApi: FunctionsBackendApi,
    private val authRepository: AuthRepository,
    private val logger: Logger,
    /**
     * The player app reads the statistics through the public routes, with or without an account. A public read needs
     * the EMA number of the person: it has no "me".
     */
    private val publicReads: Boolean = false,
) : StatisticsRepository {
    override suspend fun loadOverview(): AppResult<StatisticsOverview> = request("Loading the statistics overview") { idToken ->
        val dto = backendApi.statisticsOverview(idToken)
        StatisticsOverview(
            tournaments = dto.tournaments,
            tablesCompleted = dto.tablesCompleted,
            tablesWithHands = dto.tablesWithHands,
            handsPlayed = dto.handsPlayed,
            drawnHands = dto.drawnHands,
            winningHands = dto.winningHands,
            selfDraws = dto.selfDraws,
            winningScoreSum = dto.winningScoreSum,
            chickenHands = dto.chickenHands,
            penaltyCount = dto.penaltyCount,
            highestHand = dto.highestHand,
            playerCount = dto.playerCount,
            players = dto.players.map {
                PlayerTotals(
                    emaId = it.emaId,
                    tables = it.tables,
                    points = it.points,
                    score = it.score,
                    tablesWithHands = it.tablesWithHands,
                    handsPlayed = it.handsPlayed,
                    handsWon = it.handsWon,
                    selfDraws = it.selfDraws,
                    dealIns = it.dealIns,
                    penaltyCount = it.penaltyCount,
                )
            },
        )
    }

    override suspend fun loadGroup(filter: StatisticsGroupFilter): AppResult<StatisticsGroup> = request("Loading the statistics of a group") { idToken ->
        backendApi.statisticsGroup(idToken, filter.toQuery()).toDomain()
    }

    override suspend fun loadPerson(emaId: String?): AppResult<List<PersonTournamentDigest>> = request("Loading the person statistics") { idToken ->
        check(!publicReads || emaId != null) { "A public read needs the EMA number" }
        backendApi.personStatistics(idToken, emaId).tournaments.map { tournament ->
            PersonTournamentDigest(
                tournamentId = tournament.tournamentId,
                results = tournament.results.map { it.toDomain(tournament.tournamentId) },
            )
        }
    }

    override suspend fun loadStandings(emaId: String?, tournamentIds: List<String>): AppResult<Map<String, Standing>> = request("Loading the standings") { idToken ->
        check(!publicReads || emaId != null) { "A public read needs the EMA number" }
        if (tournamentIds.isEmpty()) {
            emptyMap()
        } else {
            backendApi.personStandings(idToken, emaId, tournamentIds).standings
                .associate { it.tournamentId to it.toStanding() }
        }
    }

    private suspend fun <T> request(action: String, block: suspend (String) -> T): AppResult<T> = runCatching {
        if (publicReads) block(PUBLIC_TOKEN) else authRepository.withFreshIdToken(block)
    }.fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { throwable ->
            logger.w(throwable) { "$action failed." }
            AppResult.Failure(throwable.toAppError())
        },
    )

    private fun DigestResultDto.toDomain(tournamentId: String) = DigestResult(
        emaId = emaId,
        slotId = slotId,
        result = TableResult(
            tournamentId = tournamentId,
            roundId = roundId,
            tableId = tableId,
            seat = seat,
            position = position,
            score = score,
            points = points,
            hasHands = hasHands,
            handsPlayed = handsPlayed,
            handsWon = handsWon,
            selfDraws = selfDraws,
            dealIns = dealIns,
            drawnHands = drawnHands,
            winningScoreSum = winningScoreSum,
            bestHand = bestHand,
            chickenHands = chickenHands,
            dealInScoreSum = dealInScoreSum,
            lostToSelfDraws = lostToSelfDraws,
            otherWins = otherWins,
            penaltyCount = penaltyCount,
            penaltyPoints = penaltyPoints,
            maxPenalty = maxPenalty,
            rivals = rivals.map { rival ->
                RivalTally(
                    opponent = rival.opponent,
                    tablesBehind = rival.tablesBehind,
                    discardLosses = rival.discardLosses,
                    selfDrawLosses = rival.selfDrawLosses,
                    othersDiscardWins = rival.othersDiscardWins,
                )
            },
            winRun = HandRun(winRun.best, winRun.start, winRun.end),
            noDealInRun = HandRun(noDealInRun.best, noDealInRun.start, noDealInRun.end),
        ),
    )
}

private fun Throwable.toAppError(): AppError = toRepositoryAppError()

internal fun StandingDto.toStanding() = Standing(
    rank = rank,
    of = of,
    points = points,
    score = score,
    teamRank = teamRank,
    chickenRank = chickenRank,
    bestHandRank = bestHandRank,
    teamOf = teamOf,
    teamPoints = teamPoints,
    chickenHands = chickenHands,
    chickenOf = chickenOf,
    bestHand = bestHand,
    bestHandOf = bestHandOf,
)

/** The query of a group filter, with the names of the server route. */
internal fun StatisticsGroupFilter.toQuery(): Map<String, String> = buildMap {
    tournamentIds?.let { put("tournaments", it.sorted().joinToString(",")) }
    tournamentCountry?.let { put("tournamentCountry", it) }
    playerCountry?.let { put("playerCountry", it.ifEmpty { "none" }) }
    fromYear?.let { put("from", "$it-01-01") }
    toYear?.let { put("to", "$it-12-31") }
}

private fun StatisticsGroupDto.toDomain() = StatisticsGroup(
    tournamentIds = tournamentIds,
    tables = with(tables) {
        GroupTables(tablesCompleted, tablesWithHands, handsPlayed, drawnHands, winningHands, selfDraws, winningScoreSum, chickenHands, penaltyCount, highestHand)
    },
    persons = persons.map {
        GroupPerson(
            emaId = it.emaId, tournaments = it.tournaments, tables = it.tables, points = it.points, score = it.score,
            places = listOf(it.place1, it.place2, it.place3, it.place4),
            tablesWithHands = it.tablesWithHands, handsPlayed = it.handsPlayed, handsWon = it.handsWon, selfDraws = it.selfDraws,
            dealIns = it.dealIns, drawnHands = it.drawnHands, winningScoreSum = it.winningScoreSum, chickenHands = it.chickenHands,
            dealInScoreSum = it.dealInScoreSum, lostToSelfDraws = it.lostToSelfDraws, otherWins = it.otherWins,
            penaltyCount = it.penaltyCount, penaltyPoints = it.penaltyPoints, bestHand = it.bestHand,
            bestTableScore = it.bestTableScore, mostHandsWonAtATable = it.mostHandsWonAtATable, maxPenalty = it.maxPenalty,
            tableWinRun = it.tableWinRun, handWinRun = it.handWinRun, noDealInRun = it.noDealInRun,
        )
    },
)
