package com.etologic.mahjongtournamentsuite.player

import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.etologic.mahjongtournamentsuite.presentation.platform.SessionStateStorage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A page refresh opens the same screens again: the back stack is saved by name and read back as routes. */
@OptIn(ExperimentalTestApi::class)
class RoutePersistenceUiTest {
    private class MemoryStorage : SessionStateStorage {
        val values = mutableMapOf<String, String>()
        override fun read(name: String) = values[name]
        override fun write(name: String, value: String) {
            values[name] = value
        }
    }

    private fun graph(test: (NavHostController) -> Unit) = runComposeUiTest {
        lateinit var controller: NavHostController
        setContent {
            controller = rememberNavController()
            NavHost(controller, startDestination = SplashRoute) {
                composable<SplashRoute> { Text("splash") }
                composable<SignInRoute> { Text("sign in") }
                composable<TournamentsRoute> { Text("list") }
                composable<TournamentRoute> { Text("tournament") }
                composable<TableRoute> { Text("table") }
                composable<StatisticsRoute> { Text("statistics") }
                composable<CombinationsRoute> { Text("combinations") }
            }
        }
        waitForIdle()
        runOnUiThread { test(controller) }
    }

    @Test
    fun theBackStackComesBackInTheSameOrderWithItsValues() = graph { controller ->
        val storage = MemoryStorage()
        val persistence = RoutePersistence(storage)
        controller.navigate(TournamentsRoute)
        controller.navigate(TournamentRoute("t1", myTables = true))
        controller.navigate(TableRoute("t1", roundId = 2, tableId = 7))
        controller.navigate(CombinationsRoute)

        persistence.save(controller.currentBackStack.value)

        assertEquals(
            listOf(TournamentsRoute, TournamentRoute("t1", myTables = true), TableRoute("t1", 2, 7), CombinationsRoute),
            persistence.load(),
        )
        assertEquals(persistence.load(), controller.openRoutes())
    }

    @Test
    fun theSplashScreenDoesNotEraseTheSavedScreensAndSignInIsNotSaved() = graph { controller ->
        val storage = MemoryStorage()
        val persistence = RoutePersistence(storage)
        storage.values["routes"] = """TournamentsRoute {}""" + "\n" + """TournamentRoute {"tournamentId":"t9"}"""

        persistence.save(controller.currentBackStack.value)
        assertEquals(listOf(TournamentsRoute, TournamentRoute("t9")), persistence.load(), "the splash keeps them")

        controller.navigate(SignInRoute())
        persistence.save(controller.currentBackStack.value)
        assertTrue(persistence.load().isEmpty(), "the sign-in screen is not saved")
    }

    @Test
    fun unknownOrBrokenLinesAreSkipped() {
        val storage = MemoryStorage()
        storage.values["routes"] = listOf(
            "TournamentsRoute {}",
            "RemovedRoute {\"x\":1}",
            "TableRoute {broken",
            "TableRoute {\"tournamentId\":\"t1\",\"roundId\":1,\"tableId\":3,\"newField\":true}",
        ).joinToString("\n")

        assertEquals(listOf(TournamentsRoute, TableRoute("t1", 1, 3)), RoutePersistence(storage).load())
        assertTrue(RoutePersistence(MemoryStorage()).load().isEmpty())
    }

    @Test
    fun reopenPutsTheListAtTheBottomOnce() = graph { controller ->
        controller.reopen(listOf(TournamentsRoute, TournamentRoute("t1"), StatisticsRoute(scope = "TOURNAMENT", tournamentId = "t1")))

        assertEquals(listOf(TournamentsRoute, TournamentRoute("t1"), StatisticsRoute(scope = "TOURNAMENT", tournamentId = "t1")), controller.openRoutes())
    }
}
