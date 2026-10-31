package com.etologic.mahjongtournamentsuite.presentation.presenter

import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.Player
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
import com.etologic.mahjongtournamentsuite.domain.usecase.LoadStatisticsUseCase
import com.etologic.mahjongtournamentsuite.domain.usecase.LoadStoredStatisticsUseCase
import com.etologic.mahjongtournamentsuite.domain.usecase.OverviewStatisticsData
import com.etologic.mahjongtournamentsuite.domain.usecase.PersonStatisticsData
import com.etologic.mahjongtournamentsuite.domain.usecase.Standing
import com.etologic.mahjongtournamentsuite.domain.usecase.TournamentStatisticsData

class StatisticsPresenter(
    private val loadStatistics: LoadStatisticsUseCase,
    private val loadOverview: LoadStoredStatisticsUseCase,
    private val tournamentRepository: TournamentRepository,
    private val playerRepository: PlayerRepository,
    private val adminRepository: AdminRepository,
    private val telemetry: Telemetry = NoTelemetry,
) {
    /** One tournament with its hands. It has the table details. */
    suspend fun tournament(tournamentId: String): AppResult<TournamentStatisticsData> = loadStatistics.tournament(tournamentId, RefreshMode.IF_CHANGED).viewed("tournament")

    /** True for an administrator. The statistics of all tournaments are only for administrators. */
    suspend fun canSeeOverview(): Boolean = (adminRepository.whoAmI() as? AppResult.Success)?.value?.isAdmin == true

    /** All tournaments together. The server keeps the sums, so no hands load here. */
    suspend fun overview(): AppResult<OverviewStatisticsData> = loadOverview.overview(RefreshMode.IF_CHANGED).viewed("overview")

    /** Statistics of one EMA player over every tournament where the player has a result. */
    suspend fun person(emaId: String): AppResult<PersonStatisticsData> = loadOverview.person(emaId, refreshMode = RefreshMode.IF_CHANGED).viewed("person")

    /** The final places of one EMA player in some tournaments. The screen asks for the tournaments that it shows. */
    suspend fun standings(emaId: String, tournamentIds: List<String>): Map<String, Standing> = (loadOverview.standings(emaId, own = false, tournamentIds) as? AppResult.Success)?.value.orEmpty()

    suspend fun tournaments(): AppResult<List<Tournament>> = tournamentRepository.listTournaments(RefreshMode.IF_CHANGED)

    suspend fun registry(): AppResult<List<Player>> = playerRepository.listPlayers(RefreshMode.IF_CHANGED)

    private fun <T> AppResult<T>.viewed(scope: String) = track(telemetry, TelemetryEvent.STATISTICS_VIEWED, mapOf(TelemetryParam.SCOPE to scope))
}
