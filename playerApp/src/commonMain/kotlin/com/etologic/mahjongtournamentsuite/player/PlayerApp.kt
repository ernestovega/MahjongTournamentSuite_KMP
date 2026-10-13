package com.etologic.mahjongtournamentsuite.player

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.etologic.mahjongtournamentsuite.data.di.dataModule
import com.etologic.mahjongtournamentsuite.domain.usecase.CalculateTournamentRankingsUseCase
import com.etologic.mahjongtournamentsuite.presentation.components.LocalAppLogo
import com.etologic.mahjongtournamentsuite.presentation.theme.LocalThemeController
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import com.etologic.mahjongtournamentsuite.presentation.theme.rememberThemeController
import mahjongtournamentsuite.playerapp.generated.resources.Res
import mahjongtournamentsuite.playerapp.generated.resources.app_logo
import org.jetbrains.compose.resources.painterResource
import kotlinx.serialization.Serializable
import org.koin.compose.KoinApplication
import org.koin.dsl.koinConfiguration
import org.koin.dsl.module

@Serializable
data object SplashRoute

@Serializable
data class SignInRoute(val inviteToken: String? = null, val openTournamentId: String? = null)

@Serializable
data class InviteRoute(val token: String)

@Serializable
data object TournamentsRoute

@Serializable
data class TournamentRoute(val tournamentId: String)

@Serializable
data class PlayerTablesRoute(val tournamentId: String, val slotId: Int)

@Serializable
data class TableRoute(val tournamentId: String, val roundId: Int, val tableId: Int)

private val playerModule = module {
    factory { PlayerSessionPresenter(get(), get(), get(), get()) }
    factory { CalculateTournamentRankingsUseCase() }
    factory { PlayerTournamentPresenter(get(), get(), get(), get()) }
    factory { PlayerTablePresenter(get()) }
}

/**
 * The player application.
 * [inviteToken] is set when the player opened the link of an invitation email.
 * [openTournamentId] is set when the player opened the link of a registration email.
 */
@Composable
fun PlayerApp(inviteToken: String? = null, openTournamentId: String? = null) {
    KoinApplication(configuration = koinConfiguration { modules(dataModule, playerModule) }) {
        val themeController = rememberThemeController()
        CompositionLocalProvider(
            LocalThemeController provides themeController,
            LocalAppLogo provides painterResource(Res.drawable.app_logo),
        ) {
            MtsTheme(useDarkTheme = themeController.isDarkTheme) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    PlayerNavigation(inviteToken, openTournamentId)
                }
            }
        }
    }
}

@Composable
private fun PlayerNavigation(inviteToken: String?, openTournamentId: String?) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = SplashRoute) {
        composable<SplashRoute> { SplashScreen(navController, inviteToken, openTournamentId) }
        composable<SignInRoute> { entry ->
            val route = entry.toRoute<SignInRoute>()
            SignInScreen(navController, route.inviteToken, route.openTournamentId)
        }
        composable<InviteRoute> { entry -> InviteScreen(navController, entry.toRoute<InviteRoute>().token) }
        composable<TournamentsRoute> { TournamentsScreen(navController) }
        composable<TournamentRoute> { entry ->
            TournamentScreen(navController, entry.toRoute<TournamentRoute>().tournamentId)
        }
        composable<PlayerTablesRoute> { entry ->
            val route = entry.toRoute<PlayerTablesRoute>()
            PlayerTablesScreen(navController, route.tournamentId, route.slotId)
        }
        composable<TableRoute> { entry ->
            val route = entry.toRoute<TableRoute>()
            TableScreen(navController, route.tournamentId, route.roundId, route.tableId)
        }
    }
}
