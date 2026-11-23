package com.etologic.mahjongtournamentsuite.presentation.presenter

import com.etologic.mahjongtournamentsuite.domain.model.AdminStatus
import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.GlobalUserRole
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.repository.AdminRepository
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerRepository
import com.etologic.mahjongtournamentsuite.domain.repository.StatisticsRepository
import com.etologic.mahjongtournamentsuite.domain.repository.TournamentRepository
import com.etologic.mahjongtournamentsuite.domain.usecase.CalculateStatisticsUseCase
import com.etologic.mahjongtournamentsuite.domain.usecase.LoadStatisticsUseCase
import com.etologic.mahjongtournamentsuite.domain.usecase.LoadStoredStatisticsUseCase
import kotlinx.coroutines.test.runTest
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Every statistic is public data for every role (Ernesto, 19-nov). A super admin reads everything with the account;
 * any other role reads its own tournaments with the account and the rest through the public routes, like Player.
 */
class StatisticsPresenterTest {
    private fun tournament(id: String) = Tournament(id = id, name = id, isTeams = false, numPlayers = 16, numRounds = 4)

    private val calls = mutableListOf<String>()

    /** A repository that answers [answers] by method name, records the calls and fails the rest. */
    private inline fun <reified T> fake(name: String, answers: Map<String, Any>): T = Proxy.newProxyInstance(
        T::class.java.classLoader,
        arrayOf(T::class.java),
    ) { _, method, _ ->
        calls += "$name.${method.name}"
        answers[method.name] ?: AppResult.Failure(AppError.Unexpected("$name.${method.name}"))
    } as T

    private fun presenter(role: GlobalUserRole, own: List<Tournament>, public: List<Tournament>): StatisticsPresenter {
        val accountTournaments = fake<TournamentRepository>("account", mapOf("listTournaments" to AppResult.Success(own)))
        val publicTournaments = fake<TournamentRepository>("public", mapOf("listTournaments" to AppResult.Success(public)))
        val accountPlayers = fake<PlayerRepository>("accountPlayers", emptyMap())
        val publicPlayers = fake<PlayerRepository>("publicPlayers", emptyMap())
        val statistics = fake<StatisticsRepository>("statistics", emptyMap())
        val admin = fake<AdminRepository>("admin", mapOf("whoAmI" to AppResult.Success(AdminStatus(uid = "u", role = role))))
        return StatisticsPresenter(
            loadStatistics = LoadStatisticsUseCase(accountTournaments, accountPlayers, CalculateStatisticsUseCase()),
            loadOverview = LoadStoredStatisticsUseCase(statistics, accountTournaments, accountPlayers),
            tournamentRepository = accountTournaments,
            playerRepository = accountPlayers,
            adminRepository = admin,
            publicLoadStatistics = LoadStatisticsUseCase(publicTournaments, publicPlayers, CalculateStatisticsUseCase()),
            publicLoadOverview = LoadStoredStatisticsUseCase(statistics, publicTournaments, publicPlayers),
            publicTournamentRepository = publicTournaments,
        )
    }

    @Test
    fun anEditorSeesItsOwnTournamentsAndEveryPublicOneOnce() = runTest {
        val presenter = presenter(GlobalUserRole.EDITOR, own = listOf(tournament("own"), tournament("both")), public = listOf(tournament("both"), tournament("other")))

        val list = assertIs<AppResult.Success<List<Tournament>>>(presenter.tournaments()).value

        assertEquals(listOf("both", "other", "own"), list.map { it.id })
    }

    @Test
    fun aClubAdminSeesTheTournamentsOfItsClubsAndEveryPublicOne() = runTest {
        val presenter = presenter(GlobalUserRole.CLUB_ADMIN, own = listOf(tournament("club")), public = listOf(tournament("other")))

        assertEquals(listOf("other", "club"), assertIs<AppResult.Success<List<Tournament>>>(presenter.tournaments()).value.map { it.id })
    }

    @Test
    fun aSuperAdminListsWithTheAccountOnly() = runTest {
        val presenter = presenter(GlobalUserRole.SUPER_ADMIN, own = listOf(tournament("own")), public = listOf(tournament("other")))

        assertEquals(listOf("own"), assertIs<AppResult.Success<List<Tournament>>>(presenter.tournaments()).value.map { it.id })
        assertEquals(listOf("account.listTournaments", "admin.whoAmI"), calls)
    }

    @Test
    fun withoutThePublicListAnEditorStillSeesItsOwnTournaments() = runTest {
        val accountTournaments = fake<TournamentRepository>("account", mapOf("listTournaments" to AppResult.Success(listOf(tournament("own")))))
        val failing = fake<TournamentRepository>("public", emptyMap())
        val players = fake<PlayerRepository>("players", emptyMap())
        val presenter = StatisticsPresenter(
            loadStatistics = LoadStatisticsUseCase(accountTournaments, players, CalculateStatisticsUseCase()),
            loadOverview = LoadStoredStatisticsUseCase(fake("statistics", emptyMap()), accountTournaments, players),
            tournamentRepository = accountTournaments,
            playerRepository = players,
            adminRepository = fake("admin", mapOf("whoAmI" to AppResult.Success(AdminStatus(uid = "u", role = GlobalUserRole.EDITOR)))),
            publicTournamentRepository = failing,
        )

        assertEquals(listOf("own"), assertIs<AppResult.Success<List<Tournament>>>(presenter.tournaments()).value.map { it.id })
    }

    @Test
    fun anEditorReadsItsOwnTournamentWithTheAccountAndAnyOtherOneThroughThePublicRoutes() = runTest {
        val presenter = presenter(GlobalUserRole.EDITOR, own = listOf(tournament("own")), public = listOf(tournament("other")))

        presenter.tournament("own")
        assertEquals("accountPlayers.listPlayers", calls.last(), "its own tournament is read with the account")

        calls.clear()
        presenter.tournament("other")
        assertEquals("publicPlayers.listPlayers", calls.last(), "a tournament of others is read through the public routes")
        assertEquals(0, calls.count { it == "admin.whoAmI" }, "who the account is was asked once, the first time")
    }

    @Test
    fun aSuperAdminReadsEveryTournamentWithTheAccount() = runTest {
        val presenter = presenter(GlobalUserRole.SUPER_ADMIN, own = listOf(tournament("own"), tournament("other")), public = listOf(tournament("other")))

        presenter.tournament("other")

        assertEquals(0, calls.count { it.startsWith("public") }, "nothing goes to the public routes: $calls")
    }
}
