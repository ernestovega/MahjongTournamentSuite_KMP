package com.etologic.mahjongtournamentsuite.presentation.presenter

import com.etologic.mahjongtournamentsuite.domain.model.AdminStatus
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.GlobalUserRole
import com.etologic.mahjongtournamentsuite.domain.model.ManagedUser
import com.etologic.mahjongtournamentsuite.domain.model.Player
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.TournamentAssignment
import com.etologic.mahjongtournamentsuite.domain.repository.AdminRepository
import com.etologic.mahjongtournamentsuite.domain.repository.AuthRepository
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerRepository
import com.etologic.mahjongtournamentsuite.domain.repository.TournamentRepository
import com.etologic.mahjongtournamentsuite.domain.repository.RefreshMode

class UsersPresenter(
    private val tournamentRepository: TournamentRepository,
    private val adminRepository: AdminRepository,
    private val authRepository: AuthRepository,
    private val playerRepository: PlayerRepository,
) {
    suspend fun loadAdminStatus(force: Boolean = false): AppResult<AdminStatus> = adminRepository.whoAmI(force.mode())

    suspend fun loadUsageReport(days: Int): AppResult<com.etologic.mahjongtournamentsuite.domain.model.UsageReport> =
        adminRepository.loadUsageReport(days)

    suspend fun loadUsers(force: Boolean = false): AppResult<List<ManagedUser>> = adminRepository.listUsers(force.mode())

    suspend fun createUser(
        email: String,
        alias: String,
        role: GlobalUserRole,
        tournamentAssignments: List<TournamentAssignment>,
        emaId: String? = null,
    ): AppResult<ManagedUser> = adminRepository.createUser(email, alias, role, tournamentAssignments, emaId)

    /** The shared EMA player list. The account dialog uses it to pick an EMA ID. */
    suspend fun loadEmaPlayers(force: Boolean = false): AppResult<List<Player>> = playerRepository.listPlayers(force.mode())

    suspend fun updateUser(user: ManagedUser): AppResult<ManagedUser> = adminRepository.updateUser(user)

    /** Only admins can set the photo of another account. */
    suspend fun updateUserPhoto(uid: String, contentType: String, bytes: ByteArray): AppResult<ManagedUser> =
        adminRepository.updateUserPhoto(uid, contentType, bytes)

    suspend fun removeUserPhoto(uid: String): AppResult<ManagedUser> = adminRepository.removeUserPhoto(uid)

    suspend fun setUserDisabled(uid: String, disabled: Boolean): AppResult<ManagedUser> =
        adminRepository.setUserDisabled(uid, disabled)

    suspend fun requestPasswordReset(email: String): AppResult<Unit> =
        authRepository.requestPasswordReset(email)

    suspend fun loadTournaments(force: Boolean = false): AppResult<List<Tournament>> = tournamentRepository.listTournaments(force.mode())
}

private fun Boolean.mode() = if (this) RefreshMode.FORCE else RefreshMode.IF_CHANGED
