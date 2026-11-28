package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.PersonTournamentDigest
import com.etologic.mahjongtournamentsuite.domain.model.StatisticsGroup
import com.etologic.mahjongtournamentsuite.domain.model.StatisticsGroupFilter
import com.etologic.mahjongtournamentsuite.domain.model.StatisticsOverview
import com.etologic.mahjongtournamentsuite.presentation.StatisticsRoute
import com.etologic.mahjongtournamentsuite.presentation.components.StatisticsScope
import com.etologic.mahjongtournamentsuite.presentation.screenshots.adminModules
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import com.etologic.mahjongtournamentsuite.screenshots.fake.ScreenshotStatisticsRepository
import org.koin.compose.KoinContext
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test

/** When the statistics do not load, the screen says why, for a person, for a tournament and for a group. */
@OptIn(ExperimentalTestApi::class)
class StatisticsScreenFailuresUiTest {
    private class FailingStatistics : ScreenshotStatisticsRepository() {
        override suspend fun loadOverview(): AppResult<StatisticsOverview> = AppResult.Failure(AppError.Network)
        override suspend fun loadPerson(emaId: String?): AppResult<List<PersonTournamentDigest>> = AppResult.Failure(AppError.Network)
        override suspend fun loadGroup(filter: StatisticsGroupFilter): AppResult<StatisticsGroup> = AppResult.Failure(AppError.Network)
    }

    private fun ComposeUiTest.showsAnError(route: StatisticsRoute) {
        setContent {
            val koin = koinApplication {
                modules(module { single { Logger.withTag("ui-test") } })
                modules(adminModules(statistics = FailingStatistics()))
            }.koin
            KoinContext(koin) {
                MtsTheme(useDarkTheme = false) {
                    val navController = rememberNavController()
                    NavHost(navController, startDestination = "screen") {
                        composable("screen") { StatisticsScreen(navController, route) }
                    }
                }
            }
        }
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("Error").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun aPersonThatDoesNotLoadShowsTheError() = runComposeUiTest {
        showsAnError(StatisticsRoute(StatisticsScope.PERSON.name, emaId = "04000001"))
    }

    @Test
    fun aGroupThatDoesNotLoadShowsTheError() = runComposeUiTest {
        showsAnError(StatisticsRoute(StatisticsScope.TOURNAMENT.name))
    }

    @Test
    fun aTournamentThatDoesNotLoadShowsTheError() = runComposeUiTest {
        showsAnError(StatisticsRoute(StatisticsScope.TOURNAMENT.name, tournamentId = "missing"))
    }
}
