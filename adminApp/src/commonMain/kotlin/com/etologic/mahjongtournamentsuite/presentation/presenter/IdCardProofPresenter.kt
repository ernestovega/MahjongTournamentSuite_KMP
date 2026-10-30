package com.etologic.mahjongtournamentsuite.presentation.presenter

import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.IdCardProofRequest
import com.etologic.mahjongtournamentsuite.domain.repository.TournamentRepository
import com.etologic.mahjongtournamentsuite.domain.telemetry.NoTelemetry
import com.etologic.mahjongtournamentsuite.domain.telemetry.Telemetry
import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryAction
import com.etologic.mahjongtournamentsuite.domain.telemetry.trackAction

class IdCardProofPresenter(
    private val tournamentRepository: TournamentRepository,
    private val telemetry: Telemetry = NoTelemetry,
) {
    suspend fun generate(request: IdCardProofRequest): AppResult<ByteArray> = tournamentRepository.generateIdCardProof(request).trackAction(telemetry, TelemetryAction.ID_CARD_PROOF_CREATED)
}
