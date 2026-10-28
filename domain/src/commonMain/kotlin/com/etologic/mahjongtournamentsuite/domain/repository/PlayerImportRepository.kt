package com.etologic.mahjongtournamentsuite.domain.repository

import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.PlayerExportFile
import com.etologic.mahjongtournamentsuite.domain.model.PlayerImportPreview
import com.etologic.mahjongtournamentsuite.domain.model.PlayerImportResult

/** Overwrites all players and teams of a tournament from a CSV or Excel file with the columns id, ema id, email and team. */
interface PlayerImportRepository {
    /** Gives an Excel file with the current players. It has the same columns as the import file, so it also works as a template. */
    suspend fun export(tournamentId: String): AppResult<PlayerExportFile>

    /** Reads the file and checks it. It changes nothing. */
    suspend fun preview(tournamentId: String, fileName: String, bytes: ByteArray): AppResult<PlayerImportPreview>

    /**
     * Overwrites all players and teams with the file, in one request. The server checks the file again and refuses
     * a file with errors and a tournament with table data. It creates no account and sends no email.
     */
    suspend fun apply(tournamentId: String, fileName: String, bytes: ByteArray): AppResult<PlayerImportResult>
}
