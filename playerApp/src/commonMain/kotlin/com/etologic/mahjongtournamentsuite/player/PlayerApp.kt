package com.etologic.mahjongtournamentsuite.player

import com.etologic.mahjongtournamentsuite.presentation.language.ProvideAppLanguage
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.etologic.mahjongtournamentsuite.presentation.platform.LocalScrollPositionStore
import com.etologic.mahjongtournamentsuite.presentation.platform.ScrollPositionStore
import com.etologic.mahjongtournamentsuite.presentation.platform.SessionStateStorage
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.etologic.mahjongtournamentsuite.data.di.dataModule
import com.etologic.mahjongtournamentsuite.data.di.telemetry
import com.etologic.mahjongtournamentsuite.domain.repository.TournamentRepository
import com.etologic.mahjongtournamentsuite.data.repository.DefaultTournamentRepository
import com.etologic.mahjongtournamentsuite.domain.usecase.CalculateStatisticsUseCase
import com.etologic.mahjongtournamentsuite.domain.usecase.CalculateTournamentRankingsUseCase
import com.etologic.mahjongtournamentsuite.domain.usecase.LoadStatisticsUseCase
import com.etologic.mahjongtournamentsuite.domain.usecase.LoadStoredStatisticsUseCase
import com.etologic.mahjongtournamentsuite.presentation.components.LocalAdaptiveTopBar
import com.etologic.mahjongtournamentsuite.presentation.components.LocalAppLogo
import com.etologic.mahjongtournamentsuite.presentation.components.LocalFeatureFlags
import com.etologic.mahjongtournamentsuite.domain.model.FeatureFlags
import com.etologic.mahjongtournamentsuite.domain.repository.FeatureFlagsRepository
import androidx.compose.runtime.produceState
import org.koin.compose.koinInject
import com.etologic.mahjongtournamentsuite.presentation.theme.LocalThemeController
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import com.etologic.mahjongtournamentsuite.presentation.theme.rememberThemeController
import mahjongtournamentsuite.playerapp.generated.resources.Res
import mahjongtournamentsuite.playerapp.generated.resources.app_logo
import org.jetbrains.compose.resources.painterResource
import kotlinx.serialization.Serializable
import com.etologic.mahjongtournamentsuite.domain.telemetry.NoTelemetry
import com.etologic.mahjongtournamentsuite.domain.telemetry.Telemetry
import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryProperty
import com.etologic.mahjongtournamentsuite.domain.telemetry.screenFromRoute
import org.koin.compose.getKoin
import org.koin.compose.KoinApplication
import org.koin.dsl.koinConfiguration
import org.koin.dsl.module

@Serializable
data object SplashRoute

@Serializable
data class SignInRoute(val openTournamentId: String? = null)

@Serializable
data object TournamentsRoute

@Serializable
data class TournamentRoute(val tournamentId: String)

@Serializable
data class PlayerTablesRoute(val tournamentId: String, val slotId: Int)

@Serializable
data class TableRoute(val tournamentId: String, val roundId: Int, val tableId: Int)

/**
 * The statistics screen. [scope] is the name of a [StatisticsScope]. The other values give the context of the scope.
 * A missing [slotId] means the signed-in player.
 */
@Serializable
data class StatisticsRoute(
    val scope: String,
    val tournamentId: String? = null,
    val roundId: Int? = null,
    val tableId: Int? = null,
    val slotId: Int? = null,
)

private val playerModule = module {
    // In the player app every account is a player, so the list holds only the tournaments where it plays.
    single<TournamentRepository> { DefaultTournamentRepository(get(), get(), get(), get(), get(), playerScope = true) }
    factory { PlayerSessionPresenter(get(), get(), telemetry()) }
    factory { CalculateTournamentRankingsUseCase() }
    factory { CalculateStatisticsUseCase() }
    factory { LoadStatisticsUseCase(get(), get(), get()) }
    factory { LoadStoredStatisticsUseCase(get(), get(), get()) }
    factory { PlayerTournamentPresenter(get(), get(), get(), get()) }
    factory { PlayerTablePresenter(get(), telemetry()) }
    factory { PlayerStatisticsPresenter(get(), get(), get(), get(), telemetry()) }
}

/**
 * The player application.
 * [openTournamentId] is set when the player opened the link of a registration email.
 * [sessionStorage] keeps the open screen and the scroll positions through a page refresh.
 */
@Composable
fun PlayerApp(
    openTournamentId: String? = null,
    sessionStorage: SessionStateStorage? = null,
) {
    KoinApplication(configuration = koinConfiguration { modules(dataModule, playerModule) }) {
        val themeController = rememberThemeController()
        CompositionLocalProvider(
            LocalThemeController provides themeController,
            LocalScrollPositionStore provides remember(sessionStorage) { sessionStorage?.let { ScrollPositionStore(it) } },
            LocalAppLogo provides painterResource(Res.drawable.app_logo),
            LocalAdaptiveTopBar provides true,
            LocalFeatureFlags provides koinInject<FeatureFlagsRepository>().let { repository ->
                produceState(FeatureFlags()) { value = repository.load() }.value
            },
        ) {
            ProvideAppLanguage {
                MtsTheme(useDarkTheme = themeController.isDarkTheme) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        PlayerNavigation(openTournamentId, sessionStorage)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerNavigation(openTournamentId: String?, sessionStorage: SessionStateStorage?) {
    val navController = rememberNavController()
    val routePersistence = remember(sessionStorage) { sessionStorage?.let { RoutePersistence(it) } }
    // The saved routes are read one time, before the first navigation changes them.
    val restoredRoutes = remember(routePersistence) { routePersistence?.load().orEmpty() }
    LaunchedEffect(navController, routePersistence) {
        routePersistence ?: return@LaunchedEffect
        navController.currentBackStack.collect { routePersistence.save(it) }
    }

    // Each screen the user opens counts as one visit. The route gives the name, never an id.
    val telemetry = getKoin().getOrNull<Telemetry>() ?: NoTelemetry
    LaunchedEffect(navController, telemetry) {
        telemetry.userProperty(TelemetryProperty.APP, "player")
        navController.currentBackStackEntryFlow.collect { telemetry.screenFromRoute(it.destination.route) }
    }
    NavHost(navController = navController, startDestination = SplashRoute) {
        screen<SplashRoute> { SplashScreen(navController, openTournamentId, restoredRoutes) }
        screen<SignInRoute> { entry ->
            val route = entry.toRoute<SignInRoute>()
            SignInScreen(navController, route.openTournamentId)
        }
        screen<TournamentsRoute> { TournamentsScreen(navController) }
        screen<TournamentRoute> { entry ->
            TournamentScreen(navController, entry.toRoute<TournamentRoute>().tournamentId)
        }
        screen<PlayerTablesRoute> { entry ->
            val route = entry.toRoute<PlayerTablesRoute>()
            PlayerTablesScreen(navController, route.tournamentId, route.slotId)
        }
        screen<TableRoute> { entry ->
            val route = entry.toRoute<TableRoute>()
            TableScreen(navController, route.tournamentId, route.roundId, route.tableId)
        }
        screen<StatisticsRoute> { entry -> StatisticsScreen(navController, entry.toRoute<StatisticsRoute>()) }
    }
}
