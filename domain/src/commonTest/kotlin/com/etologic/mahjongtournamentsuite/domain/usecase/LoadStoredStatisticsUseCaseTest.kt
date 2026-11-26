package com.etologic.mahjongtournamentsuite.domain.usecase

import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.EmaPlayerDeleteResult
import com.etologic.mahjongtournamentsuite.domain.model.EmaPlayerTournaments
import com.etologic.mahjongtournamentsuite.domain.model.GroupTables
import com.etologic.mahjongtournamentsuite.domain.model.PersonTournamentDigest
import com.etologic.mahjongtournamentsuite.domain.model.Player
import com.etologic.mahjongtournamentsuite.domain.model.PlayerTotals
import com.etologic.mahjongtournamentsuite.domain.model.StatisticsGroup
import com.etologic.mahjongtournamentsuite.domain.model.StatisticsGroupFilter
import com.etologic.mahjongtournamentsuite.domain.model.StatisticsOverview
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.groupPersonOf
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerRepository
import com.etologic.mahjongtournamentsuite.domain.repository.RefreshMode
import com.etologic.mahjongtournamentsuite.domain.repository.StatisticsRepository
import com.etologic.mahjongtournamentsuite.domain.repository.UnusedTournamentRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** The statistics that the server keeps, read with the registry of players, and what a failed read gives. */
class LoadStoredStatisticsUseCaseTest {
    private val failure = AppResult.Failure(AppError.Unexpected("offline"))
    private val registry = listOf(Player(emaId = "1", firstName = "Ana", lastName = "Ruiz", country = "ES"))
    private val tournament = Tournament(id = "a", name = "Open a", isTeams = false, numPlayers = 16, numRounds = 4, eventStartDate = "2026-01-10")

    private var players: AppResult<List<Player>> = AppResult.Success(registry)
    private var tournaments: AppResult<List<Tournament>> = AppResult.Success(listOf(tournament))
    private var overview: AppResult<StatisticsOverview> = AppResult.Success(StatisticsOverview(1, 2, 2, 20, 1, 19, 300, 0, 0, 40, emptyList()))
    private var group: AppResult<StatisticsGroup> = AppResult.Success(StatisticsGroup(listOf("a"), GroupTables(tablesCompleted = 2), emptyList()))
    private var person: AppResult<List<PersonTournamentDigest>> = AppResult.Success(listOf(PersonTournamentDigest("a", emptyList())))
    private var failStandingsAfter = Int.MAX_VALUE
    private val personAsked = mutableListOf<String?>()
    private val standingsAsked = mutableListOf<Int>()

    private val useCase = LoadStoredStatisticsUseCase(
        statisticsRepository = object : StatisticsRepository {
            override suspend fun loadOverview() = overview

            override suspend fun loadPerson(emaId: String?): AppResult<List<PersonTournamentDigest>> {
                personAsked += emaId
                return person
            }

            override suspend fun loadGroup(filter: StatisticsGroupFilter) = group

            override suspend fun loadStandings(emaId: String?, tournamentIds: List<String>): AppResult<Map<String, Standing>> {
                standingsAsked += tournamentIds.size
                if (standingsAsked.size > failStandingsAfter) return failure
                return AppResult.Success(tournamentIds.associateWith { Standing(rank = 1, of = 16, points = 4.0, score = 100) })
            }
        },
        tournamentRepository = object : UnusedTournamentRepository() {
            override suspend fun listTournaments(refreshMode: RefreshMode) = tournaments
        },
        playerRepository = object : PlayerRepository {
            override suspend fun listPlayers(refreshMode: RefreshMode) = players

            override suspend fun createPlayer(player: Player): AppResult<Player> = error("Not used by this test")

            override suspend fun updatePlayer(previousEmaId: String, player: Player): AppResult<Unit> = error("Not used by this test")

            override suspend fun updatePlayerPhoto(emaId: String, contentType: String, bytes: ByteArray): AppResult<Player> = error("Not used by this test")

            override suspend fun deleteEmaPlayers(emaIds: List<String>): AppResult<List<EmaPlayerDeleteResult>> = error("Not used by this test")

            override suspend fun tournamentsOfEmaPlayers(emaIds: List<String>): AppResult<List<EmaPlayerTournaments>> = error("Not used by this test")
        },
    )

    @Test
    fun theOverviewNeedsTheRegistryAndTheSums() = runTest {
        assertEquals(1, assertIs<AppResult.Success<OverviewStatisticsData>>(useCase.overview()).value.tournamentCount)

        overview = failure
        assertEquals(failure, useCase.overview())
        players = failure
        assertEquals(failure, useCase.overview())
    }

    @Test
    fun aGroupNeedsTheRegistryAndItsSummary() = runTest {
        val filter = StatisticsGroupFilter(tournamentCountry = "ES")
        assertEquals(listOf("a"), assertIs<AppResult.Success<GroupStatisticsData>>(useCase.group(filter)).value.tournamentIds)

        group = failure
        assertEquals(failure, useCase.group(filter))
        players = failure
        assertEquals(failure, useCase.group(filter))
    }

    @Test
    fun aPersonNeedsTheTournamentsTheRegistryAndTheDigests() = runTest {
        val data = assertIs<AppResult.Success<PersonStatisticsData>>(useCase.person("1")).value
        assertEquals(listOf("a"), data.tournaments.map { it.first.id })
        useCase.person("1", own = true)
        assertEquals(listOf("1", null), personAsked, "the own statistics are read by the account, not by the number")

        person = failure
        assertEquals(failure, useCase.person("1"))
        players = failure
        assertEquals(failure, useCase.person("1"))
        tournaments = failure
        assertEquals(failure, useCase.person("1"))
    }

    @Test
    fun theStandingsGoInCallsOfTwentyFive() = runTest {
        val ids = (1..30).map { "t$it" }

        assertEquals(30, assertIs<AppResult.Success<Map<String, Standing>>>(useCase.standings("1", own = false, ids)).value.size)
        assertEquals(listOf(25, 5), standingsAsked)

        failStandingsAfter = standingsAsked.size + 1
        assertEquals(failure, useCase.standings("1", own = true, ids), "a failed call fails them all")
    }

    @Test
    fun aPersonOutsideTheRegistryIsNamedByTheEmaNumber() {
        val totals = PlayerTotals("9", tables = 1, points = 1.0, score = 10, tablesWithHands = 0, handsPlayed = 0, handsWon = 0, selfDraws = 0, dealIns = 0, penaltyCount = 0)
        val overview = overviewFrom(StatisticsOverview(1, 2, 1, 0, 0, 0, 0, 0, 0, 0, listOf(totals)), registry)
        val group = groupFrom(StatisticsGroupFilter(), StatisticsGroup(listOf("a"), GroupTables(), listOf(groupPersonOf("9", emptyList()), groupPersonOf("1", emptyList()))), registry)

        assertEquals("EMA 9", overview.profiles.getValue(1).name)
        assertEquals(listOf("ANA RUIZ", "EMA 9"), group.profiles.values.map { it.name })
    }

    @Test
    fun tournamentsWithoutADateGoFirst() {
        val undated = tournament.copy(id = "b", eventStartDate = null)

        val data = personFrom("1", listOf(tournament, undated), listOf(PersonTournamentDigest("a", emptyList()), PersonTournamentDigest("b", emptyList())), registry)

        assertEquals(listOf("b", "a"), data.tournaments.map { it.first.id })
    }
}
