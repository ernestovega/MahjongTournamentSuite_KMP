package com.etologic.mahjongtournamentsuite.player

import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.runComposeUiTest
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.etologic.mahjongtournamentsuite.player.screenshots.playerModules
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import com.etologic.mahjongtournamentsuite.screenshots.fake.ScreenshotData
import com.etologic.mahjongtournamentsuite.screenshots.fake.ScreenshotData.ONGOING
import com.etologic.mahjongtournamentsuite.screenshots.fake.ScreenshotData.UPCOMING
import org.koin.compose.KoinContext
import org.koin.dsl.koinApplication
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/** Where the player app goes after the splash: the tournaments, and over them the screen that the start asked for. */
@OptIn(ExperimentalTestApi::class)
class SplashScreenUiTest {
    private val original = Locale.getDefault()

    @BeforeTest
    fun english() = Locale.setDefault(Locale.ENGLISH)

    @AfterTest
    fun restore() = Locale.setDefault(original)

    private fun ComposeUiTest.start(signedIn: Boolean = true, splash: @androidx.compose.runtime.Composable (NavHostController) -> Unit) = setContent {
        val koin = remember { koinApplication { modules(playerModules(account = if (signedIn) ScreenshotData.playerAccount else null)) }.koin }
        KoinContext(koin) {
            MtsTheme(useDarkTheme = false) {
                val nav = rememberNavController()
                NavHost(nav, startDestination = SplashRoute) {
                    composable<SplashRoute> { splash(nav) }
                    composable<TournamentsRoute> { Text("screen: tournaments") }
                    composable<TournamentRoute> { Text("screen: tournament ${it.toRoute<TournamentRoute>().tournamentId}") }
                    composable<SupportRoute> { Text("screen: support") }
                    composable<AppsRoute> { Text("screen: apps") }
                    composable<SignInRoute> { Text("screen: sign-in") }
                    composable<CombinationsRoute> { Text("screen: combinations") }
                }
            }
        }
    }

    private fun ComposeUiTest.waitFor(text: String) = waitUntil(timeoutMillis = 10_000) {
        onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun withNothingAskedTheTournamentsOpen() = runComposeUiTest {
        start { SplashScreen(it, openTournamentId = null) }
        waitFor("screen: tournaments")
    }

    @Test
    fun aSharedTournamentOpensOverTheTournaments() = runComposeUiTest {
        start { SplashScreen(it, openTournamentId = ONGOING) }
        waitFor("screen: tournament $ONGOING")
    }

    @Test
    fun theSupportOpensWhenAsked() = runComposeUiTest {
        start { SplashScreen(it, openTournamentId = null, openSupport = true) }
        waitFor("screen: support")
    }

    @Test
    fun theAppsPageOpensWhenAsked() = runComposeUiTest {
        start { SplashScreen(it, openTournamentId = null, openApps = true) }
        waitFor("screen: apps")
    }

    @Test
    fun theSignInOpensFirstOnlyWithoutAnAccount() = runComposeUiTest {
        start(signedIn = false) { SplashScreen(it, openTournamentId = null, openSignIn = true) }
        waitFor("screen: sign-in")
    }

    @Test
    fun theScreensOfBeforeARefreshOpenAgain() = runComposeUiTest {
        start { SplashScreen(it, openTournamentId = null, restoredRoutes = listOf(TournamentsRoute, CombinationsRoute)) }
        waitFor("screen: combinations")
    }

    @Test
    fun aRegistrationLinkOpensItsTournament() = runComposeUiTest {
        start { SplashScreen(it, openTournamentId = null, openRegistration = RegistrationLink(slug = "madrid-open")) }
        waitFor("screen: tournament $UPCOMING")
    }
}
