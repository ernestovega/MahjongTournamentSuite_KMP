package com.etologic.mahjongtournamentsuite.player

import com.etologic.mahjongtournamentsuite.domain.AppVersion
import com.etologic.mahjongtournamentsuite.domain.repository.SupportRepository
import com.etologic.mahjongtournamentsuite.presentation.components.LocalAppVersion
import com.etologic.mahjongtournamentsuite.presentation.components.SupportForm
import com.etologic.mahjongtournamentsuite.presentation.language.ProvideAppLanguage
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.navigation.NavHostController
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
import com.etologic.mahjongtournamentsuite.data.repository.DefaultPlayerRepository
import com.etologic.mahjongtournamentsuite.data.repository.DefaultStatisticsRepository
import com.etologic.mahjongtournamentsuite.data.repository.DefaultPlayerHandRepository
import com.etologic.mahjongtournamentsuite.data.cache.RepositoryCache
import com.etologic.mahjongtournamentsuite.data.cache.SessionRepositoryCache
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerRepository
import com.etologic.mahjongtournamentsuite.domain.repository.StatisticsRepository
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerHandRepository
import org.koin.core.qualifier.named
import com.etologic.mahjongtournamentsuite.domain.usecase.CalculateStatisticsUseCase
import com.etologic.mahjongtournamentsuite.domain.usecase.CalculateTournamentRankingsUseCase
import com.etologic.mahjongtournamentsuite.domain.usecase.LoadStatisticsUseCase
import com.etologic.mahjongtournamentsuite.domain.usecase.LoadStoredStatisticsUseCase
import com.etologic.mahjongtournamentsuite.presentation.components.LocalAdaptiveTopBar
import com.etologic.mahjongtournamentsuite.presentation.components.LocalAppLogo
import com.etologic.mahjongtournamentsuite.presentation.components.LocalAppLogoOnDark
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
import mahjongtournamentsuite.playerapp.generated.resources.app_logo_dark
import org.jetbrains.compose.resources.painterResource
import kotlinx.serialization.Serializable
import com.etologic.mahjongtournamentsuite.domain.telemetry.NoTelemetry
import com.etologic.mahjongtournamentsuite.domain.telemetry.Telemetry
import com.etologic.mahjongtournamentsuite.presentation.telemetry.ConsentGate
import com.etologic.mahjongtournamentsuite.presentation.telemetry.LocalTelemetry
import org.koin.compose.getKoin
import org.koin.compose.KoinApplication
import org.koin.dsl.koinConfiguration
import org.koin.dsl.module

@Serializable
data object SplashRoute

@Serializable
data class SignInRoute(val openTournamentId: String? = null)

/** The "Need help?" form of the sign-in. [email] is the email that the sign-in had, to fill the form. */
@Serializable
data class SupportRoute(val email: String = "")

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

/** The cache of the public answers. They are the same for everyone, so signing in or out keeps them. */
private val PublicCache = named("publicCache")

private val playerModule = module {
    // Everyone reads every public tournament without an account. The account only writes hands and marks the own player.
    single<RepositoryCache>(PublicCache) { SessionRepositoryCache(logger = get(), persisted = get()) }
    single<TournamentRepository> {
        DefaultTournamentRepository(get(), get(), get(), get(PublicCache), get(), persisted = get(), publicReads = true)
    }
    single<PlayerRepository> { DefaultPlayerRepository(get(), get(), get(), get(PublicCache), publicReads = true) }
    single<StatisticsRepository> { DefaultStatisticsRepository(get(), get(), get(), publicReads = true) }
    single<PlayerHandRepository> { DefaultPlayerHandRepository(get(), get(), get(), get(PublicCache)) }
    factory { PlayerSessionPresenter(get(), get(), telemetry()) }
    factory { CalculateTournamentRankingsUseCase() }
    factory { CalculateStatisticsUseCase() }
    factory { LoadStatisticsUseCase(get(), get(), get()) }
    factory { LoadStoredStatisticsUseCase(get(), get(), get()) }
    factory { PlayerTournamentPresenter(get(), get(), get(), get()) }
    factory { PlayerTablePresenter(get(), telemetry()) }
    factory { MyTableReminderPresenter(get()) }
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
    appVersion: String = AppVersion.player,
    /** True when the admin app sent the user here to sign in. */
    openSignIn: Boolean = false,
) {
    KoinApplication(configuration = koinConfiguration { modules(dataModule, playerModule) }) {
        val themeController = rememberThemeController()
        CompositionLocalProvider(
            LocalTelemetry provides (getKoin().getOrNull<Telemetry>() ?: NoTelemetry),
            LocalThemeController provides themeController,
            LocalAppVersion provides appVersion,
            LocalScrollPositionStore provides remember(sessionStorage) { sessionStorage?.let { ScrollPositionStore(it) } },
            LocalAppLogo provides painterResource(Res.drawable.app_logo),
            LocalAppLogoOnDark provides painterResource(Res.drawable.app_logo_dark),
            LocalAdaptiveTopBar provides true,
            LocalFeatureFlags provides koinInject<FeatureFlagsRepository>().let { repository ->
                produceState(FeatureFlags()) { value = repository.load() }.value
            },
        ) {
            ProvideAppLanguage {
                // The user cannot enter before accepting the anonymous analytics.
                ConsentGate {
                    MtsTheme(useDarkTheme = themeController.isDarkTheme) {
                        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                            PlayerNavigation(openTournamentId, sessionStorage, openSignIn)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerNavigation(openTournamentId: String?, sessionStorage: SessionStateStorage?, openSignIn: Boolean) {
    val navController = rememberNavController()
    val routePersistence = remember(sessionStorage) { sessionStorage?.let { RoutePersistence(it) } }
    // The saved routes are read one time, before the first navigation changes them.
    val restoredRoutes = remember(routePersistence) { routePersistence?.load().orEmpty() }
    LaunchedEffect(navController, routePersistence) {
        routePersistence ?: return@LaunchedEffect
        navController.currentBackStack.collect { routePersistence.save(it) }
    }

    Box(Modifier.fillMaxSize()) {
        PlayerNavHost(navController, openTournamentId, restoredRoutes, openSignIn)
        // Over every screen: the table of the player when a round of the player starts soon or is in play.
        MyTableReminder(navController, sessionStorage)
    }
}

@Composable
private fun PlayerNavHost(
    navController: NavHostController,
    openTournamentId: String?,
    restoredRoutes: List<Any>,
    openSignIn: Boolean,
) {
    NavHost(navController = navController, startDestination = SplashRoute) {
        screen<SplashRoute> { SplashScreen(navController, openTournamentId, restoredRoutes, openSignIn) }
        screen<SignInRoute> { entry ->
            val route = entry.toRoute<SignInRoute>()
            SignInScreen(navController, route.openTournamentId)
        }
        screen<SupportRoute> { entry ->
            val supportRepository = koinInject<SupportRepository>()
            SupportForm(
                app = "player",
                initialEmail = entry.toRoute<SupportRoute>().email,
                onSend = supportRepository::send,
                onBack = { navController.popBackStack() },
            )
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
