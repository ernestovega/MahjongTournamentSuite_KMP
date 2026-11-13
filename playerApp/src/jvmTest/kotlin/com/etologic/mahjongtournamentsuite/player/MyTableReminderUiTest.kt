package com.etologic.mahjongtournamentsuite.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.MyTournamentSlot
import com.etologic.mahjongtournamentsuite.domain.model.RoundClock
import com.etologic.mahjongtournamentsuite.domain.model.RoundClocks
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.TournamentTable
import com.etologic.mahjongtournamentsuite.domain.repository.TournamentRepository
import com.etologic.mahjongtournamentsuite.presentation.platform.SessionStateStorage
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import org.koin.compose.KoinApplication
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.lang.reflect.Proxy
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.Clock

/** The round 2 of "MO" started 20 minutes ago. The player of slot 6 plays at table 2. */
private fun repositoryInPlay(): TournamentRepository = Proxy.newProxyInstance(
    TournamentRepository::class.java.classLoader,
    arrayOf(TournamentRepository::class.java),
) { _, method, _ ->
    when (method.name) {
        "listMyTournamentSlots" -> AppResult.Success(listOf(MyTournamentSlot("t", 6, 0)))
        "listTournaments" -> AppResult.Success(listOf(Tournament(id = "t", name = "Madrid Open", shortName = "MO", isTeams = false, numPlayers = 8, numRounds = 3)))
        "getRoundClocks" -> AppResult.Success(
            RoundClocks(listOf(RoundClock(2, startedAtMs = Clock.System.now().toEpochMilliseconds() - 20 * 60_000L - 5_000)), 0),
        )
        "listTournamentTables" -> AppResult.Success(listOf(TournamentTable(2, 2, listOf(5, 6, 7, 8), isCompleted = false, useTotalsOnly = false, hasProgress = false)))
        else -> error("Unexpected call ${method.name}")
    }
} as TournamentRepository

private class MemoryStorage : SessionStateStorage {
    val values = mutableMapOf<String, String>()
    override fun read(name: String): String? = values[name]
    override fun write(name: String, value: String) {
        values[name] = value
    }
}

@OptIn(ExperimentalTestApi::class)
class MyTableReminderUiTest {
    private val original = Locale.getDefault()

    @BeforeTest
    fun english() = Locale.setDefault(Locale.ENGLISH)

    @AfterTest
    fun cleanUp() {
        Locale.setDefault(original)
        stopKoin()
    }

    private fun ComposeUiTest.show(storage: SessionStateStorage) = setContent {
        KoinApplication(application = { modules(module { factory { MyTableReminderPresenter(repositoryInPlay()) } }) }) {
            MtsTheme(useDarkTheme = false) {
                val navController = rememberNavController()
                Box(Modifier.fillMaxSize()) {
                    NavHost(navController, startDestination = TournamentsRoute) {
                        composable<TournamentsRoute> { Text("Tournament list") }
                        composable<TableRoute> { entry -> Text("Table ${entry.toRoute<TableRoute>().tableId}") }
                    }
                    MyTableReminder(navController, storage)
                }
            }
        }
    }

    @Test
    fun theReminderSaysSinceWhenTheRoundPlaysAndOpensTheTable() = runComposeUiTest {
        val storage = MemoryStorage()
        show(storage)

        waitUntil(timeoutMillis = 5_000) { runCatching { onNodeWithTag(MY_TABLE_REMINDER_TAG).assertExists() }.isSuccess }
        onNodeWithText("Table 2 · Round 2").assertExists()
        onNodeWithText("Started 20 minutes ago").assertExists()
        onNodeWithText("MO").assertExists()
        onNodeWithText("Go to my table").performClick()

        waitForIdle()
        onNodeWithText("Table 2").assertExists()
        onNodeWithTag(MY_TABLE_REMINDER_TAG).assertDoesNotExist()
        kotlin.test.assertEquals("t/2", storage.values["handledTableReminders"])
    }

    @Test
    fun aClosedReminderDoesNotComeBackInTheSameRound() = runComposeUiTest {
        val storage = MemoryStorage()
        show(storage)

        waitUntil(timeoutMillis = 5_000) { runCatching { onNodeWithTag(MY_TABLE_REMINDER_TAG).assertExists() }.isSuccess }
        onNodeWithText("Later").performClick()
        waitForIdle()
        onNodeWithTag(MY_TABLE_REMINDER_TAG).assertDoesNotExist()
        kotlin.test.assertEquals("t/2", storage.values["handledTableReminders"])
        onNodeWithText("Tournament list").assertExists()
    }
}
