package com.etologic.mahjongtournamentsuite.presentation.presenter

import com.etologic.mahjongtournamentsuite.domain.model.AdminStatus
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.UserProfile
import com.etologic.mahjongtournamentsuite.domain.repository.AdminRepository
import com.etologic.mahjongtournamentsuite.domain.repository.AuthRepository
import com.etologic.mahjongtournamentsuite.domain.repository.TournamentRepository

class TournamentsPresenter(
    private val tournamentRepository: TournamentRepository,
    private val adminRepository: AdminRepository,
    private val authRepository: AuthRepository,
) {
    suspend fun loadProfile(): AppResult<UserProfile> = authRepository.getMe()

    suspend fun loadAdminStatus(): AppResult<AdminStatus> = adminRepository.whoAmI()

    suspend fun loadTournaments(): AppResult<List<Tournament>> = tournamentRepository.listTournaments()

    suspend fun lookupUser(identifier: String): AppResult<UserProfile> = adminRepository.lookupUser(identifier)

    suspend fun deleteTournament(tournamentId: String): AppResult<Unit> =
        tournamentRepository.deleteTournament(tournamentId)

    suspend fun signOut() = authRepository.signOut()
}
