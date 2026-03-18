package com.etologic.mahjongtournamentsuite.domain.usecase

import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.repository.GreetingRepository
import kotlin.test.Test
import kotlin.test.assertEquals

class GetGreetingMessageUseCaseTest {

    @Test
    fun delegatesToRepository() = kotlinx.coroutines.test.runTest {
        val useCase = GetGreetingMessageUseCase(
            greetingRepository = object : GreetingRepository {
                override suspend fun getGreeting(): AppResult<String> {
                    return AppResult.Success("hello")
                }
            },
        )

        val result = useCase()

        assertEquals(AppResult.Success("hello"), result)
    }
}
