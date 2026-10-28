package com.etologic.mahjongtournamentsuite.presentation.presenter

import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.ImportFileErrorCode
import com.etologic.mahjongtournamentsuite.domain.model.PlayerExportFile
import com.etologic.mahjongtournamentsuite.domain.model.PlayerImportPreview
import com.etologic.mahjongtournamentsuite.domain.model.PlayerImportResult
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerImportRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class PlayerImportPresenterTest {
    private val preview = PlayerImportPreview(
        rows = emptyList(), valid = true, isTeams = false, tournamentStarted = false,
        existingPlayers = 0, importedPlayers = 0, hasEmailColumn = false, hasTeamColumn = false,
    )
    private val result = PlayerImportResult(players = 1, changedSlots = 2, emptiedSlots = 3)

    private class RecordingRepository(
        val calls: MutableList<String>,
        val preview: PlayerImportPreview,
        val result: PlayerImportResult,
        val applyResult: AppResult<PlayerImportResult>? = null,
    ) : PlayerImportRepository {
        override suspend fun export(tournamentId: String): AppResult<PlayerExportFile> {
            calls += "export $tournamentId"
            return AppResult.Success(PlayerExportFile("players.xlsx", byteArrayOf(1, 2)))
        }

        override suspend fun preview(tournamentId: String, fileName: String, bytes: ByteArray): AppResult<PlayerImportPreview> {
            calls += "preview $tournamentId $fileName ${bytes.size}"
            return AppResult.Success(preview)
        }

        override suspend fun apply(tournamentId: String, fileName: String, bytes: ByteArray): AppResult<PlayerImportResult> {
            calls += "apply $tournamentId $fileName ${bytes.size}"
            return applyResult ?: AppResult.Success(result)
        }
    }

    @Test
    fun passesTheFileToTheRepositoryForThePreviewAndForTheApply() = runTest {
        val calls = mutableListOf<String>()
        val presenter = PlayerImportPresenter(RecordingRepository(calls, preview, result), Logger.withTag("test"))

        assertEquals(AppResult.Success(preview), presenter.preview("t1", "a.csv", byteArrayOf(1, 2)))
        assertEquals(AppResult.Success(result), presenter.apply("t1", "a.csv", byteArrayOf(1)))

        assertEquals(listOf("preview t1 a.csv 2", "apply t1 a.csv 1"), calls)
    }

    @Test
    fun givesTheRefusalOfTheServerBackUnchanged() = runTest {
        val refusal = AppResult.Failure(AppError.ImportFile(ImportFileErrorCode.TOURNAMENT_STARTED))
        val presenter = PlayerImportPresenter(RecordingRepository(mutableListOf(), preview, result, refusal), Logger.withTag("test"))

        assertEquals(refusal, presenter.apply("t1", "a.csv", byteArrayOf(1)))
    }

    @Test
    fun exportsThePlayersOfTheTournament() = runTest {
        val calls = mutableListOf<String>()
        val presenter = PlayerImportPresenter(RecordingRepository(calls, preview, result), Logger.withTag("test"))

        val file = (presenter.export("t9") as AppResult.Success).value

        assertEquals(listOf("export t9"), calls)
        assertEquals("players.xlsx", file.fileName)
        assertEquals(listOf<Byte>(1, 2), file.bytes.toList())
    }
}
