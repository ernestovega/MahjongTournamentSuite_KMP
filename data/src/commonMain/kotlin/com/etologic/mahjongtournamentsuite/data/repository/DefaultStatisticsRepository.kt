package com.etologic.mahjongtournamentsuite.data.repository

import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.data.backend.FunctionsBackendApi
import com.etologic.mahjongtournamentsuite.data.backend.dto.DigestResultDto
import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.DigestResult
import com.etologic.mahjongtournamentsuite.domain.model.HandRun
import com.etologic.mahjongtournamentsuite.domain.model.PersonTournamentDigest
import com.etologic.mahjongtournamentsuite.domain.model.PlayerTotals
import com.etologic.mahjongtournamentsuite.domain.model.RivalTally
import com.etologic.mahjongtournamentsuite.domain.model.StatisticsOverview
import com.etologic.mahjongtournamentsuite.domain.model.TableResult
import com.etologic.mahjongtournamentsuite.domain.repository.AuthRepository
import com.etologic.mahjongtournamentsuite.domain.repository.StatisticsRepository
import com.etologic.mahjongtournamentsuite.domain.usecase.Standing

class DefaultStatisticsRepository(
    private val backendApi: FunctionsBackendApi,
    private val authRepository: AuthRepository,
    private val logger: Logger,
) : StatisticsRepository {
    override suspend fun loadOverview(): AppResult<StatisticsOverview> =
        request("Loading the statistics overview") { idToken ->
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

    override suspend fun loadPerson(emaId: String?): AppResult<List<PersonTournamentDigest>> =
        request("Loading the person statistics") { idToken ->
            backendApi.personStatistics(idToken, emaId).tournaments.map { tournament ->
                PersonTournamentDigest(
                    tournamentId = tournament.tournamentId,
                    results = tournament.results.map { it.toDomain(tournament.tournamentId) },
                )
            }
        }

    override suspend fun loadStandings(emaId: String?, tournamentIds: List<String>): AppResult<Map<String, Standing>> =
        request("Loading the standings") { idToken ->
            if (tournamentIds.isEmpty()) {
                emptyMap()
            } else {
                backendApi.personStandings(idToken, emaId, tournamentIds).standings
                    .associate { it.tournamentId to Standing(it.rank, it.of) }
            }
        }

    private suspend fun <T> request(action: String, block: suspend (String) -> T): AppResult<T> = runCatching {
        authRepository.withFreshIdToken(block)
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
