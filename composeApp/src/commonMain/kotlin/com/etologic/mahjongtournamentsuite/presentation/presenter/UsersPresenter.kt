package com.etologic.mahjongtournamentsuite.presentation.presenter

import com.etologic.mahjongtournamentsuite.domain.model.AdminStatus
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.GlobalUserRole
import com.etologic.mahjongtournamentsuite.domain.model.ManagedUser
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.TournamentAssignment
import com.etologic.mahjongtournamentsuite.domain.repository.AdminRepository
import com.etologic.mahjongtournamentsuite.domain.repository.AuthRepository
import com.etologic.mahjongtournamentsuite.domain.repository.TournamentRepository

class UsersPresenter(
    private val tournamentRepository: TournamentRepository,
    private val adminRepository: AdminRepository,
    private val authRepository: AuthRepository,
) {
    suspend fun loadAdminStatus(): AppResult<AdminStatus> = adminRepository.whoAmI()

    suspend fun loadUsers(): AppResult<List<ManagedUser>> = adminRepository.listUsers()

    suspend fun createUser(
        email: String,
        alias: String,
        role: GlobalUserRole,
        tournamentAssignments: List<TournamentAssignment>,
    ): AppResult<ManagedUser> = adminRepository.createUser(email, alias, role, tournamentAssignments)

    suspend fun updateUser(user: ManagedUser): AppResult<ManagedUser> = adminRepository.updateUser(user)

    suspend fun setUserDisabled(uid: String, disabled: Boolean): AppResult<ManagedUser> =
        adminRepository.setUserDisabled(uid, disabled)

    suspend fun requestPasswordReset(email: String): AppResult<Unit> =
        authRepository.requestPasswordReset(email)

    suspend fun loadTournaments(): AppResult<List<Tournament>> = tournamentRepository.listTournaments()
}
