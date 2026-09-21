package com.etologic.mahjongtournamentsuite.domain.repository

import com.etologic.mahjongtournamentsuite.domain.model.AdminStatus
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.GlobalUserRole
import com.etologic.mahjongtournamentsuite.domain.model.ManagedUser
import com.etologic.mahjongtournamentsuite.domain.model.TournamentAssignment
import com.etologic.mahjongtournamentsuite.domain.model.UserProfile

interface AdminRepository {
    suspend fun whoAmI(): AppResult<AdminStatus>

    suspend fun lookupUser(identifier: String, tournamentId: String? = null): AppResult<UserProfile>

    suspend fun listUsers(): AppResult<List<ManagedUser>>

    suspend fun createUser(
        email: String,
        alias: String,
        role: GlobalUserRole,
        tournamentAssignments: List<TournamentAssignment>,
    ): AppResult<ManagedUser>

    suspend fun updateUser(user: ManagedUser): AppResult<ManagedUser>

    suspend fun setUserDisabled(uid: String, disabled: Boolean): AppResult<ManagedUser>
}
