package com.etologic.mahjongtournamentsuite.presentation.presenter

import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.IdCardProofRequest
import com.etologic.mahjongtournamentsuite.domain.repository.TournamentRepository

class IdCardProofPresenter(
    private val tournamentRepository: TournamentRepository,
) {
    suspend fun generate(request: IdCardProofRequest): AppResult<ByteArray> =
        tournamentRepository.generateIdCardProof(request)
}
