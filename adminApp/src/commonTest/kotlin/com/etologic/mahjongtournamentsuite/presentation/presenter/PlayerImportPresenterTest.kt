package com.etologic.mahjongtournamentsuite.presentation.presenter

import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.PlayerImportPreview
import com.etologic.mahjongtournamentsuite.domain.model.PlayerImportResult
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerImportRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class PlayerImportPresenterTest {
    private val preview = PlayerImportPreview(emptyList(), emptyList(), false, false, false, false)
    private val result = PlayerImportResult(1, 2, 3, 4, emptyList())

    @Test
    fun passesTheFileAndTheChoiceToTheRepository() = runTest {
        val calls = mutableListOf<String>()
        val progress = mutableListOf<Pair<Int, Int>>()
        val presenter = PlayerImportPresenter(
            object : PlayerImportRepository {
                override suspend fun preview(tournamentId: String, fileName: String, bytes: ByteArray): AppResult<PlayerImportPreview> {
                    calls += "preview $tournamentId $fileName ${bytes.size}"
                    return AppResult.Success(preview)
                }

                override suspend fun apply(
                    tournamentId: String,
                    fileName: String,
                    bytes: ByteArray,
                    preview: PlayerImportPreview,
                    replaceExisting: Boolean,
                    onProgress: (Int, Int) -> Unit,
                ): AppResult<PlayerImportResult> {
                    calls += "apply $tournamentId $fileName $replaceExisting"
                    onProgress(5, 10)
                    return AppResult.Success(result)
                }
            },
            Logger.withTag("test"),
        )

        assertEquals(AppResult.Success(preview), presenter.preview("t1", "a.csv", byteArrayOf(1, 2)))
        assertEquals(AppResult.Success(result), presenter.apply("t1", "a.csv", byteArrayOf(1), preview, true) { d, t -> progress += d to t })

        assertEquals(listOf("preview t1 a.csv 2", "apply t1 a.csv true"), calls)
        assertEquals(listOf(5 to 10), progress)
    }
}
