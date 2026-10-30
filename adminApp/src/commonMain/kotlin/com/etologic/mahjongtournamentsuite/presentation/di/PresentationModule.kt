package com.etologic.mahjongtournamentsuite.presentation.di

import com.etologic.mahjongtournamentsuite.presentation.presenter.AuthPresenter
import com.etologic.mahjongtournamentsuite.presentation.presenter.CreateTournamentPresenter
import com.etologic.mahjongtournamentsuite.presentation.presenter.UsersPresenter
import com.etologic.mahjongtournamentsuite.presentation.presenter.PlayerImportPresenter
import com.etologic.mahjongtournamentsuite.presentation.presenter.PlayersPresenter
import com.etologic.mahjongtournamentsuite.presentation.presenter.TeamsPresenter
import com.etologic.mahjongtournamentsuite.presentation.presenter.PlayerBasePresenter
import com.etologic.mahjongtournamentsuite.presentation.presenter.TableManagerPresenter
import com.etologic.mahjongtournamentsuite.presentation.presenter.TablesPresenter
import com.etologic.mahjongtournamentsuite.presentation.presenter.TournamentsPresenter
import com.etologic.mahjongtournamentsuite.presentation.presenter.RankingPresenter
import com.etologic.mahjongtournamentsuite.presentation.presenter.StatisticsPresenter
import com.etologic.mahjongtournamentsuite.presentation.presenter.ErrorsPresenter
import com.etologic.mahjongtournamentsuite.presentation.presenter.IdCardProofPresenter
import com.etologic.mahjongtournamentsuite.presentation.store.AppMemoryStore
import com.etologic.mahjongtournamentsuite.domain.usecase.GenerateTournamentScheduleBruteForceParallelUseCase
import com.etologic.mahjongtournamentsuite.domain.usecase.CalculateStatisticsUseCase
import com.etologic.mahjongtournamentsuite.domain.usecase.CalculateTournamentRankingsUseCase
import com.etologic.mahjongtournamentsuite.domain.usecase.LoadStoredStatisticsUseCase
import com.etologic.mahjongtournamentsuite.domain.usecase.LoadStatisticsUseCase
import com.etologic.mahjongtournamentsuite.data.di.telemetry
import org.koin.dsl.module

val presentationModule = module {
    single { AppMemoryStore() }
    factory { AuthPresenter(get(), get(), telemetry()) }
    factory { TournamentsPresenter(get(), get(), get(), telemetry()) }
    factory { GenerateTournamentScheduleBruteForceParallelUseCase() }
    factory { CreateTournamentPresenter(get(), get(), get(), telemetry()) }
    factory { UsersPresenter(get(), get(), get(), get(), telemetry()) }
    factory { PlayersPresenter(get(), get(), get(), get(), telemetry()) }
    factory { PlayerImportPresenter(get(), get(), telemetry()) }
    factory { TeamsPresenter(get(), get(), get(), telemetry()) }
    factory { PlayerBasePresenter(get(), get(), telemetry()) }
    factory { TablesPresenter(get(), get(), get()) }
    factory { TableManagerPresenter(get(), get(), telemetry()) }
    factory { CalculateTournamentRankingsUseCase() }
    factory { CalculateStatisticsUseCase() }
    factory { LoadStatisticsUseCase(get(), get(), get()) }
    factory { LoadStoredStatisticsUseCase(get(), get(), get()) }
    factory { StatisticsPresenter(get(), get(), get(), get(), get(), telemetry()) }
    factory { RankingPresenter(get(), get(), get(), get(), telemetry()) }
    factory { IdCardProofPresenter(get(), telemetry()) }
    factory { ErrorsPresenter(get()) }
}
