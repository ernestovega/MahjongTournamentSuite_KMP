package com.etologic.mahjongtournamentsuite.player

import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.UserProfile
import com.etologic.mahjongtournamentsuite.domain.repository.AuthRepository
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

/** A repository whose every call gives [answer]. */
private inline fun <reified T : Any> answering(crossinline answer: (name: String) -> Any?): T = Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { proxy, method, args ->
    when (method.name) {
        "toString" -> "fake ${T::class.simpleName}"
        "hashCode" -> System.identityHashCode(proxy)
        "equals" -> proxy === args?.firstOrNull()
        else -> answer(method.name)
    }
} as T

/** The own statistics of the player: they need an account linked to a player. */
class PlayerStatisticsMeTest {
    private val failing: (String) -> Any? = { AppResult.Failure(AppError.Network) }
    private val tournaments = answering<TournamentRepository>(failing)
    private val players = answering<PlayerRepository>(failing)
    private val statistics = answering<StatisticsRepository>(failing)

    private fun presenter(me: AppResult<UserProfile>) = PlayerStatisticsPresenter(
        loadStatistics = LoadStatisticsUseCase(tournaments, players, CalculateStatisticsUseCase()),
        loadStored = LoadStoredStatisticsUseCase(statistics, tournaments, players),
        authRepository = answering<AuthRepository> { me },
        playerRepository = players,
        tournamentRepository = tournaments,
    )

    @Test
    fun aFailedAccountGivesItsError() = runTest {
        assertEquals(AppError.Network, assertIs<AppResult.Failure>(presenter(AppResult.Failure(AppError.Network)).me()).error)
    }

    @Test
    fun anAccountWithoutAPlayerHasNoStatistics() = runTest {
        val error = assertIs<AppResult.Failure>(presenter(AppResult.Success(UserProfile(uid = "u1", email = "p@x.es"))).me()).error
        assertIs<AppError.Unexpected>(error)
    }

    @Test
    fun aLinkedAccountAsksForTheStatisticsOfItsPlayer() = runTest {
        val result = presenter(AppResult.Success(UserProfile(uid = "u1", email = "p@x.es", emaId = "04000001"))).me(track = false)
        assertEquals(AppError.Network, assertIs<AppResult.Failure>(result).error)
    }
}
