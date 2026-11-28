package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.presentation.components.BackgroundExports
import com.etologic.mahjongtournamentsuite.presentation.components.LocalBackgroundExports
import com.etologic.mahjongtournamentsuite.presentation.platform.chooseSaveFile
import com.etologic.mahjongtournamentsuite.presentation.presenter.PlayersPresenter
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.koin.compose.KoinContext
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The dialogs of the score sheets and of the table cards close and make their PDF in the background, which a card then saves. */
@OptIn(ExperimentalTestApi::class)
class DownloadDialogsUiTest {
    private val system = chooseSaveFile
    private val folder = createTempDirectory("pdf").toFile()
    private val saved = mutableListOf<String>()
    private val exports = BackgroundExports(CoroutineScope(Dispatchers.Unconfined))
    private val presenter = PlayersPresenter(
        FakeTournamentRepository().apply { writesSucceed = true },
        FakePlayerRepository(),
        FakePlayerAccountRepository(),
        Logger.withTag("test"),
    )

    @AfterTest
    fun restore() {
        chooseSaveFile = system
        folder.deleteRecursively()
    }

    private fun ComposeUiTest.show(content: @Composable (onDismiss: () -> Unit) -> Unit): MutableList<Unit> {
        val dismissed = mutableListOf<Unit>()
        chooseSaveFile = { _, name -> File(folder, name).also { saved += name } }
        val koin = koinApplication { modules(module { single { presenter } }) }.koin
        setContent {
            KoinContext(koin) {
                CompositionLocalProvider(LocalBackgroundExports provides exports) {
                    MtsTheme(useDarkTheme = false) { content { dismissed += Unit } }
                }
            }
        }
        return dismissed
    }

    private fun ComposeUiTest.saveTheReadyExport() {
        waitUntil(timeoutMillis = 5_000) { exports.tasks.any { it.status is BackgroundExports.Status.Ready } }
        exports.save(exports.tasks.single())
        assertTrue(exports.tasks.isEmpty())
        assertEquals(1, saved.size)
    }

    @Test
    fun theBlankScoreSheetsOfATournamentWithoutTablesAreSaved() = runComposeUiTest {
        val tournament = Tournament(id = "t1", name = "Open", isTeams = false, numPlayers = 0, numRounds = 0)
        val dismissed = show { onDismiss -> ScoreSheetsDialog(tournament, onDismiss) }
        onNodeWithText("Create PDF").performClick()
        waitForIdle()
        assertEquals(1, dismissed.size)
        saveTheReadyExport()
    }

    @Test
    fun theTableCardsAreSaved() = runComposeUiTest {
        val tournament = Tournament(id = "t1", name = "Open", isTeams = false, numPlayers = 16, numRounds = 2)
        val dismissed = show { onDismiss -> TablesIdsDialog(tournament, onDismiss, site = "https://play.test") }
        onNodeWithText("Create PDF").performClick()
        waitForIdle()
        assertEquals(1, dismissed.size)
        saveTheReadyExport()
    }
}
