package com.etologic.mahjongtournamentsuite

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.etologic.mahjongtournamentsuite.presentation.components.LocalAdaptiveTopBar
import com.etologic.mahjongtournamentsuite.presentation.components.LocalAppLogo
import com.etologic.mahjongtournamentsuite.presentation.components.LocalFeatureFlags
import com.etologic.mahjongtournamentsuite.domain.model.FeatureFlags
import com.etologic.mahjongtournamentsuite.domain.repository.FeatureFlagsRepository
import androidx.compose.runtime.produceState
import org.koin.compose.koinInject
import mahjongtournamentsuite.adminapp.generated.resources.Res
import mahjongtournamentsuite.adminapp.generated.resources.app_logo
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.etologic.mahjongtournamentsuite.data.di.dataModule
import com.etologic.mahjongtournamentsuite.presentation.RoutePersistence
import com.etologic.mahjongtournamentsuite.presentation.UsersRoute
import com.etologic.mahjongtournamentsuite.presentation.screen
import com.etologic.mahjongtournamentsuite.presentation.platform.LocalScrollPositionStore
import com.etologic.mahjongtournamentsuite.presentation.platform.ScrollPositionStore
import com.etologic.mahjongtournamentsuite.presentation.platform.SessionStateStorage
import com.etologic.mahjongtournamentsuite.presentation.PlayersRoute
import com.etologic.mahjongtournamentsuite.presentation.TeamsRoute
import com.etologic.mahjongtournamentsuite.presentation.PlayerBaseRoute
import com.etologic.mahjongtournamentsuite.presentation.RankingsRoute
import com.etologic.mahjongtournamentsuite.presentation.SignInRoute
import com.etologic.mahjongtournamentsuite.presentation.SplashRoute
import com.etologic.mahjongtournamentsuite.presentation.StatisticsRoute
import com.etologic.mahjongtournamentsuite.presentation.UsageRoute
import com.etologic.mahjongtournamentsuite.presentation.TablesRoute
import com.etologic.mahjongtournamentsuite.presentation.TimerRoute
import com.etologic.mahjongtournamentsuite.presentation.TournamentRoute
import com.etologic.mahjongtournamentsuite.presentation.TournamentsRoute
import com.etologic.mahjongtournamentsuite.presentation.di.presentationModule
import com.etologic.mahjongtournamentsuite.presentation.screen.GlobalUsersScreen
import com.etologic.mahjongtournamentsuite.presentation.screen.PlayersScreen
import com.etologic.mahjongtournamentsuite.presentation.screen.TeamsScreen
import com.etologic.mahjongtournamentsuite.presentation.screen.PlayerBaseScreen
import com.etologic.mahjongtournamentsuite.presentation.screen.RankingStandaloneScreen
import com.etologic.mahjongtournamentsuite.presentation.screen.SignInScreen
import com.etologic.mahjongtournamentsuite.presentation.screen.SplashScreen
import com.etologic.mahjongtournamentsuite.presentation.screen.StatisticsScreen
import com.etologic.mahjongtournamentsuite.presentation.screen.UsageScreen
import com.etologic.mahjongtournamentsuite.presentation.screen.TablesScreen
import com.etologic.mahjongtournamentsuite.presentation.screen.TimerStandaloneScreen
import com.etologic.mahjongtournamentsuite.presentation.screen.TournamentScreen
import com.etologic.mahjongtournamentsuite.presentation.screen.TournamentsScreen
import com.etologic.mahjongtournamentsuite.presentation.theme.LocalThemeController
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import com.etologic.mahjongtournamentsuite.presentation.theme.rememberThemeController
import org.koin.compose.KoinApplication
import org.koin.dsl.koinConfiguration

@Composable
@Preview
fun App(sessionStorage: SessionStateStorage? = null) {
    AppWithModules {
        MahjongTournamentSuiteApp(sessionStorage)
    }
}

@Composable
fun RankingApp(tournamentId: String) {
    AppWithModules {
        val themeController = rememberThemeController()
        CompositionLocalProvider(LocalThemeController provides themeController) {
            MtsTheme(useDarkTheme = themeController.isDarkTheme) {
                RankingStandaloneScreen(tournamentId)
            }
        }
    }
}

@Composable
private fun AppWithModules(content: @Composable () -> Unit) {
    KoinApplication(
        configuration = koinConfiguration {
            modules(dataModule, presentationModule)
        },
    ) {
        CompositionLocalProvider(
            LocalAppLogo provides painterResource(Res.drawable.app_logo),
            LocalAdaptiveTopBar provides true,
            LocalFeatureFlags provides koinInject<FeatureFlagsRepository>().let { repository ->
                produceState(FeatureFlags()) { value = repository.load() }.value
            },
        ) {
            content()
        }
    }
}

@Composable
fun TimerApp(initialRound: Int = 1) {
    TimerStandaloneScreen(initialRound = initialRound)
}

@Composable
private fun MahjongTournamentSuiteApp(sessionStorage: SessionStateStorage?) {
    val navController = rememberNavController()
    val themeController = rememberThemeController()
    val routePersistence = remember(sessionStorage) { sessionStorage?.let { RoutePersistence(it) } }
    // The saved routes are read one time, before the first navigation changes them.
    val restoredRoutes = remember(routePersistence) { routePersistence?.load().orEmpty() }
    val scrollPositions = remember(sessionStorage) { sessionStorage?.let { ScrollPositionStore(it) } }

    LaunchedEffect(navController, routePersistence) {
        routePersistence ?: return@LaunchedEffect
        navController.currentBackStack.collect { routePersistence.save(it) }
    }

    CompositionLocalProvider(
        LocalThemeController provides themeController,
        LocalScrollPositionStore provides scrollPositions,
    ) {
        MtsTheme(useDarkTheme = themeController.isDarkTheme) {
            NavHost(
                navController = navController,
                startDestination = SplashRoute,
                modifier = Modifier.fillMaxSize(),
            ) {
                screen<SplashRoute> { SplashScreen(navController = navController, restoredRoutes = restoredRoutes) }
                screen<SignInRoute> { SignInScreen(navController = navController) }
                screen<TournamentsRoute> { TournamentsScreen(navController = navController) }
                screen<PlayerBaseRoute> { PlayerBaseScreen(navController = navController) }
                screen<TournamentRoute> { backStackEntry ->
                    val args = backStackEntry.toRoute<TournamentRoute>()
                    TournamentScreen(
                        navController = navController,
                        tournamentId = args.tournamentId,
                    )
                }
                screen<UsersRoute> { GlobalUsersScreen(navController = navController) }
                screen<PlayersRoute> { backStackEntry ->
                    val args = backStackEntry.toRoute<PlayersRoute>()
                    PlayersScreen(
                        navController = navController,
                        tournamentId = args.tournamentId,
                    )
                }
                screen<TeamsRoute> { backStackEntry ->
                    val args = backStackEntry.toRoute<TeamsRoute>()
                    TeamsScreen(
                        navController = navController,
                        tournamentId = args.tournamentId,
                    )
                }
                screen<TablesRoute> { backStackEntry ->
                    val args = backStackEntry.toRoute<TablesRoute>()
                    TablesScreen(
                        navController = navController,
                        tournamentId = args.tournamentId,
                    )
                }
                screen<TimerRoute> { TimerStandaloneScreen() }
                screen<UsageRoute> { UsageScreen(navController) }
                screen<StatisticsRoute> { backStackEntry ->
                    StatisticsScreen(navController, backStackEntry.toRoute<StatisticsRoute>())
                }
                screen<RankingsRoute> { backStackEntry ->
                    val args = backStackEntry.toRoute<RankingsRoute>()
                    RankingStandaloneScreen(
                        tournamentId = args.tournamentId,
                    )
                }
            }
        }
    }
}
