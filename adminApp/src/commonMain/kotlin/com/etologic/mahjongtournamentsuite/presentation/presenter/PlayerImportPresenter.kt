package com.etologic.mahjongtournamentsuite.presentation.presenter

import com.etologic.mahjongtournamentsuite.domain.telemetry.NoTelemetry
import com.etologic.mahjongtournamentsuite.domain.telemetry.Telemetry
import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryEvent
import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryParam
import com.etologic.mahjongtournamentsuite.domain.telemetry.track
import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.PlayerExportFile
import com.etologic.mahjongtournamentsuite.domain.model.PlayerImportPreview
import com.etologic.mahjongtournamentsuite.domain.model.PlayerImportResult
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerImportRepository

class PlayerImportPresenter(
    private val repository: PlayerImportRepository,
    private val logger: Logger,
    private val telemetry: Telemetry = NoTelemetry,
) {
    suspend fun export(tournamentId: String): AppResult<PlayerExportFile> {
        logger.i { "Exporting the players." }
        return repository.export(tournamentId).track(telemetry, TelemetryEvent.PLAYERS_EXPORTED)
    }

    suspend fun preview(tournamentId: String, fileName: String, bytes: ByteArray): AppResult<PlayerImportPreview> {
        logger.i { "Previewing a player import." }
        return repository.preview(tournamentId, fileName, bytes)
    }

    suspend fun apply(tournamentId: String, fileName: String, bytes: ByteArray): AppResult<PlayerImportResult> {
        logger.i { "Applying a player import." }
        return repository.apply(tournamentId, fileName, bytes)
            .track(telemetry, TelemetryEvent.PLAYERS_IMPORTED, mapOf(TelemetryParam.FORMAT to fileName.substringAfterLast('.', "").lowercase()))
    }
}
