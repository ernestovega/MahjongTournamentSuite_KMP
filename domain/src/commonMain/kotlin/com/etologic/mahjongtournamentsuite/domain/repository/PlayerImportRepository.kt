package com.etologic.mahjongtournamentsuite.domain.repository

import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.PlayerExportFile
import com.etologic.mahjongtournamentsuite.domain.model.PlayerImportPreview
import com.etologic.mahjongtournamentsuite.domain.model.PlayerImportResult

/** Fills the player slots of a tournament from a CSV or Excel file with the columns id, ema id, email and team. */
interface PlayerImportRepository {
    /** Gives an Excel file with the current players. It has the same columns as the import file, so it also works as a template. */
    suspend fun export(tournamentId: String): AppResult<PlayerExportFile>

    /** Reads the file and says what an import would do. It changes nothing. */
    suspend fun preview(tournamentId: String, fileName: String, bytes: ByteArray): AppResult<PlayerImportPreview>

    /**
     * Applies the rows of [preview] without errors, in several requests so that none lasts long.
     * A row that replaces a player is applied only when [replaceExisting] is true.
     * It saves emails as contact emails and sends none.
     * [onProgress] gets the number of rows done and the number of rows to do.
     */
    suspend fun apply(
        tournamentId: String,
        fileName: String,
        bytes: ByteArray,
        preview: PlayerImportPreview,
        replaceExisting: Boolean,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): AppResult<PlayerImportResult>
}
