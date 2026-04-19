package com.etologic.mahjongtournamentsuite.domain.repository

import com.etologic.mahjongtournamentsuite.domain.model.AdminStatus
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.UserProfile

interface AdminRepository {
    suspend fun whoAmI(): AppResult<AdminStatus>

    suspend fun lookupUser(identifier: String): AppResult<UserProfile>
}
