package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.IdCardProofRequest
import com.etologic.mahjongtournamentsuite.domain.model.TournamentRoundSchedule
import com.etologic.mahjongtournamentsuite.domain.repository.TournamentRepository
import com.etologic.mahjongtournamentsuite.presentation.platform.chooseSaveFile
import com.etologic.mahjongtournamentsuite.presentation.presenter.IdCardProofPresenter
import com.etologic.mahjongtournamentsuite.presentation.screen.FakeTournamentRepository
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import com.etologic.mahjongtournamentsuite.presentation.util.UiText
import kotlinx.coroutines.CompletableDeferred
import org.koin.compose.KoinContext
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals

/** The button that makes a proof of the ID card: it waits while the server makes the PDF, and a failure goes to the dialog of the form. */
@OptIn(ExperimentalTestApi::class)
class TournamentIdCardPreviewButtonUiTest {
    @Test
    fun theButtonWaitsForTheProofAndAFailureGoesToTheForm() = runComposeUiTest {
        val answer = CompletableDeferred<AppResult<ByteArray>>()
        val requests = mutableListOf<IdCardProofRequest>()
        val errors = mutableListOf<UiText>()
        val server: TournamentRepository = object : TournamentRepository by FakeTournamentRepository() {
            override suspend fun generateIdCardProof(request: IdCardProofRequest): AppResult<ByteArray> {
                requests += request
                return answer.await()
            }
        }
        val koin = koinApplication { modules(module { single { IdCardProofPresenter(server) } }) }.koin
        setContent {
            KoinContext(koin) {
                MtsTheme(useDarkTheme = false) {
                    TournamentIdCardPreviewButton(
                        shortName = "OPEN",
                        primaryColor = "#123456",
                        year = "2026",
                        associationLogoContentType = null,
                        associationLogoBytes = null,
                        associationLogoUrl = null,
                        roundSchedules = listOf(TournamentRoundSchedule(1, "2026-10-01", "10:00")),
                        onError = { errors += it },
                    )
                }
            }
        }
        onNodeWithText("Generate ID card preview").performClick()
        waitUntil(timeoutMillis = 5_000) { requests.isNotEmpty() }
        onNode(hasClickAction()).assertIsNotEnabled()
        assertEquals("OPEN", requests.single().shortName)

        answer.complete(AppResult.Failure(AppError.Network))
        waitUntil(timeoutMillis = 5_000) { errors.isNotEmpty() }
        onNodeWithText("Generate ID card preview").assertExists()
    }

    @Test
    fun theProofIsSavedWithTheShortNameAndTheYear() = runComposeUiTest {
        val server: TournamentRepository = object : TournamentRepository by FakeTournamentRepository() {
            override suspend fun generateIdCardProof(request: IdCardProofRequest): AppResult<ByteArray> = AppResult.Success(byteArrayOf(7))
        }
        val folder = createTempDirectory("proof").toFile()
        val names = mutableListOf<String>()
        val system = chooseSaveFile
        chooseSaveFile = { _, name -> File(folder, name).also { names += name } }
        try {
            val koin = koinApplication { modules(module { single { IdCardProofPresenter(server) } }) }.koin
            setContent {
                KoinContext(koin) {
                    MtsTheme(useDarkTheme = false) {
                        TournamentIdCardPreviewButton(
                            shortName = "  ",
                            primaryColor = "#123456",
                            year = "",
                            associationLogoContentType = null,
                            associationLogoBytes = null,
                            associationLogoUrl = null,
                            onError = {},
                        )
                    }
                }
            }
            onNodeWithText("Generate ID card preview").performClick()
            waitUntil(timeoutMillis = 5_000) { names.isNotEmpty() }
            assertEquals("tournament-proof-id-card-proof.pdf", names.single())
        } finally {
            chooseSaveFile = system
            folder.deleteRecursively()
        }
    }
}
