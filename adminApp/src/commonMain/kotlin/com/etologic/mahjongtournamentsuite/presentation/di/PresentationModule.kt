package com.etologic.mahjongtournamentsuite.presentation.di

import com.etologic.mahjongtournamentsuite.presentation.presenter.AuthPresenter
import com.etologic.mahjongtournamentsuite.presentation.presenter.CreateTournamentPresenter
import com.etologic.mahjongtournamentsuite.presentation.presenter.UsersPresenter
import com.etologic.mahjongtournamentsuite.presentation.presenter.PlayerImportPresenter
import com.etologic.mahjongtournamentsuite.presentation.presenter.PlayersPresenter
import com.etologic.mahjongtournamentsuite.presentation.presenter.RegistrationsPresenter
import com.etologic.mahjongtournamentsuite.presentation.presenter.PlayerCountPresenter
import com.etologic.mahjongtournamentsuite.presentation.presenter.TeamsPresenter
import com.etologic.mahjongtournamentsuite.presentation.presenter.OrganizationsPresenter
import com.etologic.mahjongtournamentsuite.presentation.presenter.PlayerBasePresenter
import com.etologic.mahjongtournamentsuite.presentation.presenter.TableManagerPresenter
import com.etologic.mahjongtournamentsuite.presentation.presenter.TablesPresenter
import com.etologic.mahjongtournamentsuite.presentation.presenter.TournamentsPresenter
import com.etologic.mahjongtournamentsuite.presentation.presenter.RankingPresenter
import com.etologic.mahjongtournamentsuite.presentation.presenter.StatisticsPresenter
import com.etologic.mahjongtournamentsuite.presentation.presenter.ActivityPresenter
import com.etologic.mahjongtournamentsuite.presentation.presenter.ErrorsPresenter
import com.etologic.mahjongtournamentsuite.presentation.presenter.IdCardProofPresenter
import com.etologic.mahjongtournamentsuite.presentation.store.AppMemoryStore
import com.etologic.mahjongtournamentsuite.domain.usecase.DrawTournamentScheduleUseCase
import com.etologic.mahjongtournamentsuite.domain.repository.TournamentRepository
import com.etologic.mahjongtournamentsuite.domain.usecase.LoadStatisticsUseCase
import com.etologic.mahjongtournamentsuite.domain.usecase.LoadStoredStatisticsUseCase
import com.etologic.mahjongtournamentsuite.data.di.AdminPublicReads
import com.etologic.mahjongtournamentsuite.data.di.adminRepositoriesModule
import com.etologic.mahjongtournamentsuite.data.di.dataModule
import com.etologic.mahjongtournamentsuite.data.di.telemetry
import com.etologic.mahjongtournamentsuite.data.di.useCasesModule
import org.koin.dsl.module

/** The presenters of the admin app. The data comes from dataModule, adminRepositoriesModule and useCasesModule. */
val presentationModule get() = module {
    single { AppMemoryStore() }
    factory { AuthPresenter(get(), get(), telemetry()) }
    factory { TournamentsPresenter(get(), get(), get(), telemetry()) }
    factory { DrawTournamentScheduleUseCase() }
    factory { CreateTournamentPresenter(get(), get(), get(), telemetry(), get()) }
    factory { UsersPresenter(get(), get(), get(), get(), telemetry()) }
    factory { PlayersPresenter(get(), get(), get(), get(), telemetry()) }
    factory { PlayerImportPresenter(get(), get(), telemetry()) }
    factory { TeamsPresenter(get(), get(), get(), telemetry()) }
    factory { RegistrationsPresenter(get(), get(), get(), get(), telemetry()) }
    factory { PlayerCountPresenter(get(), get(), get(), get(), get(), telemetry()) }
    factory { PlayerBasePresenter(get(), get(), telemetry()) }
    factory { OrganizationsPresenter(get(), telemetry(), get()) }
    factory { TablesPresenter(get(), get(), get()) }
    factory { TableManagerPresenter(get(), get(), telemetry()) }
    factory {
        StatisticsPresenter(
            loadStatistics = get(),
            loadOverview = get(),
            tournamentRepository = get(),
            playerRepository = get(),
            adminRepository = get(),
            publicLoadStatistics = LoadStatisticsUseCase(get(AdminPublicReads), get(), get()),
            publicLoadOverview = LoadStoredStatisticsUseCase(get(AdminPublicReads), get(AdminPublicReads), get()),
            publicTournamentRepository = get(AdminPublicReads),
            telemetry = telemetry(),
        )
    }
    factory { RankingPresenter(get(), get(), get(), get(), telemetry()) }
    factory { IdCardProofPresenter(get(), telemetry()) }
    factory { ErrorsPresenter(get()) }
    factory { ActivityPresenter(get()) { get<TournamentRepository>().listTournaments() } }
}

/** The whole Koin graph of the admin app. Each read builds new modules, so two graphs share no instance (see playerAppModules). */
val adminAppModules get() = listOf(dataModule, adminRepositoriesModule, useCasesModule, presentationModule)
