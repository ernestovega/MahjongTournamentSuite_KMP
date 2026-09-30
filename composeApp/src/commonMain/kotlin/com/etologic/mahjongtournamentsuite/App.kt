package com.etologic.mahjongtournamentsuite

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.etologic.mahjongtournamentsuite.data.di.dataModule
import com.etologic.mahjongtournamentsuite.presentation.UsersRoute
import com.etologic.mahjongtournamentsuite.presentation.PlayersRoute
import com.etologic.mahjongtournamentsuite.presentation.TeamsRoute
import com.etologic.mahjongtournamentsuite.presentation.PlayerBaseRoute
import com.etologic.mahjongtournamentsuite.presentation.RankingsRoute
import com.etologic.mahjongtournamentsuite.presentation.SignInRoute
import com.etologic.mahjongtournamentsuite.presentation.SplashRoute
import com.etologic.mahjongtournamentsuite.presentation.TableRoute
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
import com.etologic.mahjongtournamentsuite.presentation.screen.TableManagerScreen
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
fun App() {
    KoinApplication(
        configuration = koinConfiguration {
            modules(dataModule, presentationModule)
        },
    ) {
        MahjongTournamentSuiteApp()
    }
}

@Composable
fun RankingApp(
    tournamentId: String,
    tournamentName: String?,
) {
    KoinApplication(
        configuration = koinConfiguration {
            modules(dataModule, presentationModule)
        },
    ) {
        val themeController = rememberThemeController()
        CompositionLocalProvider(LocalThemeController provides themeController) {
            MtsTheme(useDarkTheme = themeController.isDarkTheme) {
                RankingStandaloneScreen(
                    tournamentId = tournamentId,
                    tournamentName = tournamentName,
                )
            }
        }
    }
}

@Composable
fun TimerApp(initialRound: Int = 1) {
    TimerStandaloneScreen(initialRound = initialRound)
}

@Composable
private fun MahjongTournamentSuiteApp() {
    val navController = rememberNavController()
    val themeController = rememberThemeController()

    CompositionLocalProvider(LocalThemeController provides themeController) {
        MtsTheme(useDarkTheme = themeController.isDarkTheme) {
            NavHost(
                navController = navController,
                startDestination = SplashRoute,
                modifier = Modifier.fillMaxSize(),
            ) {
                composable<SplashRoute> { SplashScreen(navController = navController) }
                composable<SignInRoute> { SignInScreen(navController = navController) }
                composable<TournamentsRoute> { TournamentsScreen(navController = navController) }
                composable<PlayerBaseRoute> { PlayerBaseScreen(navController = navController) }
                composable<TournamentRoute> { backStackEntry ->
                    val args = backStackEntry.toRoute<TournamentRoute>()
                    TournamentScreen(
                        navController = navController,
                        tournamentId = args.tournamentId,
                        tournamentName = args.tournamentName,
                    )
                }
                composable<UsersRoute> { GlobalUsersScreen(navController = navController) }
                composable<PlayersRoute> { backStackEntry ->
                    val args = backStackEntry.toRoute<PlayersRoute>()
                    PlayersScreen(
                        navController = navController,
                        tournamentId = args.tournamentId,
                    )
                }
                composable<TeamsRoute> { backStackEntry ->
                    val args = backStackEntry.toRoute<TeamsRoute>()
                    TeamsScreen(
                        navController = navController,
                        tournamentId = args.tournamentId,
                    )
                }
                composable<TablesRoute> { backStackEntry ->
                    val args = backStackEntry.toRoute<TablesRoute>()
                    TablesScreen(
                        navController = navController,
                        tournamentId = args.tournamentId,
                    )
                }
                composable<TableRoute> { backStackEntry ->
                    val args = backStackEntry.toRoute<TableRoute>()
                    TableManagerScreen(
                        navController = navController,
                        tournamentId = args.tournamentId,
                        roundId = args.roundId,
                        tableId = args.tableId,
                    )
                }
                composable<TimerRoute> { TimerStandaloneScreen() }
                composable<RankingsRoute> { backStackEntry ->
                    val args = backStackEntry.toRoute<RankingsRoute>()
                    RankingStandaloneScreen(
                        tournamentId = args.tournamentId,
                        tournamentName = args.tournamentName,
                    )
                }
            }
        }
    }
}
