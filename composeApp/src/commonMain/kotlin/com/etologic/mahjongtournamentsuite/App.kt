package com.etologic.mahjongtournamentsuite

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.etologic.mahjongtournamentsuite.data.di.dataModule
import com.etologic.mahjongtournamentsuite.presentation.CreateTournamentRoute
import com.etologic.mahjongtournamentsuite.presentation.MembersRoute
import com.etologic.mahjongtournamentsuite.presentation.PlayerTablesRoute
import com.etologic.mahjongtournamentsuite.presentation.PlayersRoute
import com.etologic.mahjongtournamentsuite.presentation.RankingsRoute
import com.etologic.mahjongtournamentsuite.presentation.SignInRoute
import com.etologic.mahjongtournamentsuite.presentation.SplashRoute
import com.etologic.mahjongtournamentsuite.presentation.TableRoute
import com.etologic.mahjongtournamentsuite.presentation.TablesRoute
import com.etologic.mahjongtournamentsuite.presentation.TimerRoute
import com.etologic.mahjongtournamentsuite.presentation.TournamentRoute
import com.etologic.mahjongtournamentsuite.presentation.TournamentsRoute
import com.etologic.mahjongtournamentsuite.presentation.di.presentationModule
import com.etologic.mahjongtournamentsuite.presentation.screen.CreateTournamentScreen
import com.etologic.mahjongtournamentsuite.presentation.screen.TournamentMembersScreen
import com.etologic.mahjongtournamentsuite.presentation.screen.PlayerTablesScreen
import com.etologic.mahjongtournamentsuite.presentation.screen.PlayersScreen
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
import com.etologic.mahjongtournamentsuite.presentation.theme.ThemeController
import com.etologic.mahjongtournamentsuite.presentation.theme.ThemePreference
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
private fun MahjongTournamentSuiteApp() {
    val navController = rememberNavController()
    var savedThemePreference by rememberSaveable { mutableStateOf(ThemePreference.Light.name) }
    val themePreference = remember(savedThemePreference) {
        runCatching { ThemePreference.valueOf(savedThemePreference) }
            .getOrDefault(ThemePreference.Light)
    }
    val useDarkTheme = themePreference == ThemePreference.Dark
    val themeController = remember(themePreference, useDarkTheme) {
        ThemeController(
            preference = themePreference,
            isDarkTheme = useDarkTheme,
            onTogglePreference = {
                savedThemePreference = themePreference.next().name
            },
        )
    }

    CompositionLocalProvider(LocalThemeController provides themeController) {
        MtsTheme(useDarkTheme = useDarkTheme) {
            NavHost(
                navController = navController,
                startDestination = SplashRoute,
                modifier = Modifier.fillMaxSize(),
            ) {
                composable<SplashRoute> { SplashScreen(navController = navController) }
                composable<SignInRoute> { SignInScreen(navController = navController) }
                composable<TournamentsRoute> { TournamentsScreen(navController = navController) }
                composable<CreateTournamentRoute> { CreateTournamentScreen(navController = navController) }
                composable<TournamentRoute> { backStackEntry ->
                    val args = backStackEntry.toRoute<TournamentRoute>()
                    TournamentScreen(
                        navController = navController,
                        tournamentId = args.tournamentId,
                        tournamentName = args.tournamentName,
                    )
                }
                composable<MembersRoute> { backStackEntry ->
                    val args = backStackEntry.toRoute<MembersRoute>()
                    if (args.tournamentId == null) {
                        //TODO: SuperMembersScreen
                    } else {
                        TournamentMembersScreen(
                            navController = navController,
                            tournamentId = args.tournamentId,
                        )
                    }
                }
                composable<PlayersRoute> { backStackEntry ->
                    val args = backStackEntry.toRoute<PlayersRoute>()
                    PlayersScreen(
                        navController = navController,
                        tournamentId = args.tournamentId,
                    )
                }
                composable<PlayerTablesRoute> { backStackEntry ->
                    val args = backStackEntry.toRoute<PlayerTablesRoute>()
                    PlayerTablesScreen(
                        navController = navController,
                        tournamentId = args.tournamentId,
                        initialPlayerId = args.initialPlayerId,
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
                        onClose = { navController.popBackStack() },
                    )
                }
            }
        }
    }
}
