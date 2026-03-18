package com.etologic.mahjongtournamentsuite.domain.usecase

import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.repository.GreetingRepository

class GetGreetingMessageUseCase(
    private val greetingRepository: GreetingRepository,
) {
    suspend operator fun invoke(): AppResult<String> = greetingRepository.getGreeting()
}
