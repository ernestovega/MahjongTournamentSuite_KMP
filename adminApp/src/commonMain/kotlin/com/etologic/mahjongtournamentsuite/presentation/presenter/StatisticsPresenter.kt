package com.etologic.mahjongtournamentsuite.presentation.presenter

import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.Player
import com.etologic.mahjongtournamentsuite.domain.model.StatisticsGroupFilter
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.repository.AdminRepository
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerRepository
import com.etologic.mahjongtournamentsuite.domain.repository.RefreshMode
import com.etologic.mahjongtournamentsuite.domain.repository.TournamentRepository
import com.etologic.mahjongtournamentsuite.domain.telemetry.NoTelemetry
import com.etologic.mahjongtournamentsuite.domain.telemetry.Telemetry
import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryEvent
import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryParam
import com.etologic.mahjongtournamentsuite.domain.telemetry.track
import com.etologic.mahjongtournamentsuite.domain.usecase.GroupStatisticsData
import com.etologic.mahjongtournamentsuite.domain.usecase.LoadStatisticsUseCase
import com.etologic.mahjongtournamentsuite.domain.usecase.LoadStoredStatisticsUseCase
import com.etologic.mahjongtournamentsuite.domain.usecase.OverviewStatisticsData
import com.etologic.mahjongtournamentsuite.domain.usecase.PersonStatisticsData
import com.etologic.mahjongtournamentsuite.domain.usecase.Standing
import com.etologic.mahjongtournamentsuite.domain.usecase.TournamentStatisticsData

/**
 * Statistics are public data: every role sees them all. An administrator reads everything with the account. Any other
 * role reads the own tournaments with the account and every other tournament through the public routes, like the
 * player app.
 */
class StatisticsPresenter(
    private val loadStatistics: LoadStatisticsUseCase,
    private val loadOverview: LoadStoredStatisticsUseCase,
    private val tournamentRepository: TournamentRepository,
    private val playerRepository: PlayerRepository,
    private val adminRepository: AdminRepository,
    private val publicLoadStatistics: LoadStatisticsUseCase = loadStatistics,
    private val publicLoadOverview: LoadStoredStatisticsUseCase = loadOverview,
    private val publicTournamentRepository: TournamentRepository = tournamentRepository,
    private val telemetry: Telemetry = NoTelemetry,
) {
    /** One tournament with its hands. It has the table details. */
    suspend fun tournament(tournamentId: String): AppResult<TournamentStatisticsData> {
        val useCase = if (readsWithAccount() || tournamentId in ownTournamentIds()) loadStatistics else publicLoadStatistics
        return useCase.tournament(tournamentId, RefreshMode.IF_CHANGED).viewed("tournament")
    }

    /** All tournaments together. The server keeps the sums, so no hands load here. */
    suspend fun overview(): AppResult<OverviewStatisticsData> = loadOverview.overview(RefreshMode.IF_CHANGED).viewed("overview")

    /** The statistics of a group of tournaments and players. */
    suspend fun group(filter: StatisticsGroupFilter): AppResult<GroupStatisticsData> = storedStatistics().group(filter).viewed("group")

    /** Statistics of one EMA player over every tournament where the player has a result. */
    suspend fun person(emaId: String): AppResult<PersonStatisticsData> = storedStatistics().person(emaId, refreshMode = RefreshMode.IF_CHANGED).viewed("person")

    /** The final places of one EMA player in some tournaments. The screen asks for the tournaments that it shows. */
    suspend fun standings(emaId: String, tournamentIds: List<String>): Map<String, Standing> = (storedStatistics().standings(emaId, own = false, tournamentIds) as? AppResult.Success)?.value.orEmpty()

    /** The tournaments to choose from: all of them for an administrator, the own and the public ones for any other role. */
    suspend fun tournaments(): AppResult<List<Tournament>> {
        val own = tournamentRepository.listTournaments(RefreshMode.IF_CHANGED)
        if (readsWithAccount()) return own
        val public = when (val result = publicTournamentRepository.listTournaments(RefreshMode.IF_CHANGED)) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> return own
        }
        val ownList = (own as? AppResult.Success)?.value.orEmpty()
        val publicIds = public.map { it.id }.toSet()
        return AppResult.Success(public + ownList.filter { it.id !in publicIds })
    }

    suspend fun registry(): AppResult<List<Player>> = playerRepository.listPlayers(RefreshMode.IF_CHANGED)

    private var isSuperAdmin: Boolean? = null

    /**
     * True for a super admin, whose account reads every tournament. An editor or a club admin reads with the account
     * only the tournaments that it manages, so the others come from the public routes. (It was any account that sees
     * accounts, editors too, so an editor saw the statistics of its own tournaments only.)
     */
    private suspend fun readsWithAccount(): Boolean = isSuperAdmin
        ?: ((adminRepository.whoAmI() as? AppResult.Success)?.value?.isSuperAdmin == true).also { isSuperAdmin = it }

    private suspend fun ownTournamentIds(): Set<String> = (tournamentRepository.listTournaments(RefreshMode.IF_CHANGED) as? AppResult.Success)?.value.orEmpty().map { it.id }.toSet()

    private suspend fun storedStatistics(): LoadStoredStatisticsUseCase = if (readsWithAccount()) loadOverview else publicLoadOverview

    private fun <T> AppResult<T>.viewed(scope: String) = track(telemetry, TelemetryEvent.STATISTICS_VIEWED, mapOf(TelemetryParam.SCOPE to scope))
}
