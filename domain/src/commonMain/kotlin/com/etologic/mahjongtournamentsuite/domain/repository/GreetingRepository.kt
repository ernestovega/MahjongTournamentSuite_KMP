package com.etologic.mahjongtournamentsuite.domain.repository

import com.etologic.mahjongtournamentsuite.domain.model.AppResult

interface GreetingRepository {
    suspend fun getGreeting(): AppResult<String>
}
