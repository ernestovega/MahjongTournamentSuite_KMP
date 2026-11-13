package com.etologic.mahjongtournamentsuite.presentation.components

import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.presentation.util.UiText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BackgroundExportsTest {
    private val label = UiText.Plain("ID cards")
    private val file = ExportFile("cards.pdf", byteArrayOf(1, 2, 3), "application/pdf", "Save")

    @Test
    fun anExportRunsUntilTheFileIsReady() = runTest {
        val exports = BackgroundExports(backgroundScope)
        val answer = CompletableDeferred<AppResult<ExportFile>>()

        assertTrue(exports.start("id-cards:t1", label) { answer.await() })
        runCurrent()
        assertTrue(exports.isRunning("id-cards:t1"))
        assertIs<BackgroundExports.Status.Running>(exports.tasks.single().status)

        answer.complete(AppResult.Success(file))
        runCurrent()
        val ready = assertIs<BackgroundExports.Status.Ready>(exports.tasks.single().status)
        assertEquals("cards.pdf", ready.file.fileName)
        assertFalse(exports.isRunning("id-cards:t1"))
    }

    @Test
    fun theSameExportDoesNotStartTwiceWhileItRuns() = runTest {
        val exports = BackgroundExports(backgroundScope)
        val answer = CompletableDeferred<AppResult<ExportFile>>()
        var runs = 0

        val work: suspend () -> AppResult<ExportFile> = {
            runs++
            answer.await()
        }

        exports.start("id-cards:t1", label, work)
        val second = exports.start("id-cards:t1", label, work)
        runCurrent()

        assertFalse(second)
        assertEquals(1, runs)
        assertEquals(1, exports.tasks.size)
    }

    @Test
    fun otherExportsRunAtTheSameTime() = runTest {
        val exports = BackgroundExports(backgroundScope)
        val answer = CompletableDeferred<AppResult<ExportFile>>()

        exports.start("id-cards:t1", label) { answer.await() }
        exports.start("id-list:t1", UiText.Plain("ID list")) { answer.await() }
        exports.start("id-cards:t2", label) { answer.await() }
        runCurrent()

        assertEquals(3, exports.tasks.size)
    }

    @Test
    fun aFailureStaysUntilTheUserClosesIt() = runTest {
        val exports = BackgroundExports(backgroundScope)

        exports.start("ema-report:t1", label) { AppResult.Failure(AppError.Network) }
        runCurrent()
        val task = exports.tasks.single()
        assertIs<BackgroundExports.Status.Failed>(task.status)

        exports.dismiss(task)
        assertTrue(exports.tasks.isEmpty())
    }

    @Test
    fun anExceptionBecomesAFailure() = runTest {
        val exports = BackgroundExports(backgroundScope)

        exports.start("ema-report:t1", label) { error("broken") }
        runCurrent()

        assertIs<BackgroundExports.Status.Failed>(exports.tasks.single().status)
    }

    @Test
    fun startingAgainReplacesAFinishedExport() = runTest {
        val exports = BackgroundExports(backgroundScope)

        exports.start("id-list:t1", label) { AppResult.Success(file) }
        runCurrent()
        exports.start("id-list:t1", label) { AppResult.Failure(AppError.Network) }
        runCurrent()

        assertIs<BackgroundExports.Status.Failed>(exports.tasks.single().status)
    }
}
