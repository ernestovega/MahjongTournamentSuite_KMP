package com.etologic.mahjongtournamentsuite.presentation.di

import com.etologic.mahjongtournamentsuite.presentation.presenter.AuthPresenter
import com.etologic.mahjongtournamentsuite.presentation.presenter.CreateTournamentPresenter
import com.etologic.mahjongtournamentsuite.presentation.presenter.UsersPresenter
import com.etologic.mahjongtournamentsuite.presentation.presenter.PlayersPresenter
import com.etologic.mahjongtournamentsuite.presentation.presenter.TeamsPresenter
import com.etologic.mahjongtournamentsuite.presentation.presenter.PlayerBasePresenter
import com.etologic.mahjongtournamentsuite.presentation.presenter.TableManagerPresenter
import com.etologic.mahjongtournamentsuite.presentation.presenter.TablesPresenter
import com.etologic.mahjongtournamentsuite.presentation.presenter.TournamentsPresenter
import com.etologic.mahjongtournamentsuite.presentation.presenter.RankingPresenter
import com.etologic.mahjongtournamentsuite.presentation.store.AppMemoryStore
import com.etologic.mahjongtournamentsuite.domain.usecase.GenerateTournamentScheduleBruteForceParallelUseCase
import com.etologic.mahjongtournamentsuite.domain.usecase.CalculateTournamentRankingsUseCase
import org.koin.dsl.module

val presentationModule = module {
    single { AppMemoryStore() }
    factory { AuthPresenter(get(), get()) }
    factory { TournamentsPresenter(get(), get(), get()) }
    factory { GenerateTournamentScheduleBruteForceParallelUseCase() }
    factory { CreateTournamentPresenter(get(), get(), get()) }
    factory { UsersPresenter(get(), get(), get(), get()) }
    factory { PlayersPresenter(get(), get(), get()) }
    factory { TeamsPresenter(get(), get(), get()) }
    factory { PlayerBasePresenter(get(), get()) }
    factory { TablesPresenter(get(), get(), get()) }
    factory { TableManagerPresenter(get(), get()) }
    factory { CalculateTournamentRankingsUseCase() }
    factory { RankingPresenter(get(), get(), get(), get()) }
}
