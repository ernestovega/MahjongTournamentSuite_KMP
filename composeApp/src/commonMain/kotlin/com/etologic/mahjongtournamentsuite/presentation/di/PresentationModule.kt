package com.etologic.mahjongtournamentsuite.presentation.di

import com.etologic.mahjongtournamentsuite.presentation.presenter.AuthPresenter
import com.etologic.mahjongtournamentsuite.presentation.presenter.CreateTournamentPresenter
import com.etologic.mahjongtournamentsuite.presentation.presenter.MembersPresenter
import com.etologic.mahjongtournamentsuite.presentation.presenter.PlayersPresenter
import com.etologic.mahjongtournamentsuite.presentation.presenter.TableManagerPresenter
import com.etologic.mahjongtournamentsuite.presentation.presenter.TablesPresenter
import com.etologic.mahjongtournamentsuite.presentation.presenter.TournamentsPresenter
import com.etologic.mahjongtournamentsuite.presentation.store.AppMemoryStore
import com.etologic.mahjongtournamentsuite.domain.usecase.GenerateTournamentScheduleBruteForceParallelUseCase
import org.koin.dsl.module

val presentationModule = module {
    single { AppMemoryStore() }
    factory { AuthPresenter(get(), get()) }
    factory { TournamentsPresenter(get(), get(), get()) }
    factory { GenerateTournamentScheduleBruteForceParallelUseCase() }
    factory { CreateTournamentPresenter(get(), get(), get()) }
    factory { MembersPresenter(get(), get(), get()) }
    factory { PlayersPresenter(get(), get()) }
    factory { TablesPresenter(get(), get()) }
    factory { TableManagerPresenter(get(), get()) }
}
