package com.etologic.mahjongtournamentsuite.presentation

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

/** A page refresh of the admin opens the same screens again. The web E2E tests use the same lines to open a screen. */
@OptIn(ExperimentalTestApi::class)
class RoutePersistenceUiTest {
    private class MemoryStorage : SessionStateStorage {
        val values = mutableMapOf<String, String>()
        override fun read(name: String) = values[name]
        override fun write(name: String, value: String) {
            values[name] = value
        }
    }

    @Test
    fun theBackStackIsSavedAndReadBack() = runComposeUiTest {
        lateinit var controller: NavHostController
        setContent {
            controller = rememberNavController()
            NavHost(controller, startDestination = SplashRoute) {
                composable<SplashRoute> { Text("splash") }
                composable<SignInRoute> { Text("sign in") }
                composable<TournamentsRoute> { Text("list") }
                composable<TournamentRoute> { Text("tournament") }
                composable<PlayerCountRoute> { Text("count") }
                composable<ActivityRoute> { Text("activity") }
            }
        }
        waitForIdle()
        val storage = MemoryStorage()
        val persistence = RoutePersistence(storage)

        runOnUiThread {
            persistence.save(controller.currentBackStack.value)
            controller.navigate(TournamentsRoute)
            controller.navigate(TournamentRoute("t1"))
            controller.navigate(PlayerCountRoute("t1"))
            controller.navigate(ActivityRoute("t1"))
            persistence.save(controller.currentBackStack.value)
        }

        assertEquals(listOf(TournamentsRoute, TournamentRoute("t1"), PlayerCountRoute("t1"), ActivityRoute("t1")), persistence.load())
        assertEquals(
            """TournamentsRoute {}""" + "\n" + """TournamentRoute {"tournamentId":"t1"}""",
            storage.values.getValue("routes").lines().take(2).joinToString("\n"),
            "the format that the web E2E tests write",
        )

        runOnUiThread {
            // A sign-out clears the back stack and opens the sign-in screen, which is not saved.
            controller.navigate(SignInRoute) { popUpTo(0) { inclusive = true } }
            persistence.save(controller.currentBackStack.value)
        }
        assertTrue(persistence.load().isEmpty(), "a sign-out leaves nothing to open")
    }

    @Test
    fun unknownOrBrokenLinesAreSkipped() {
        val storage = MemoryStorage()
        storage.values["routes"] = "TournamentsRoute {}\nGoneRoute {}\nTournamentRoute {oops\nTournamentRoute {\"tournamentId\":\"t2\"}"

        assertEquals(listOf(TournamentsRoute, TournamentRoute("t2")), RoutePersistence(storage).load())
    }
}
