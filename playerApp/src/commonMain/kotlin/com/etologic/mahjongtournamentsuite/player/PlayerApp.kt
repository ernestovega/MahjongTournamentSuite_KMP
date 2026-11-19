package com.etologic.mahjongtournamentsuite.player

import com.etologic.mahjongtournamentsuite.domain.AppVersion
import com.etologic.mahjongtournamentsuite.domain.repository.SupportRepository
import com.etologic.mahjongtournamentsuite.presentation.components.LocalAppVersion
import com.etologic.mahjongtournamentsuite.presentation.components.SupportForm
import com.etologic.mahjongtournamentsuite.presentation.language.AiTranslationNotice
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
import com.etologic.mahjongtournamentsuite.presentation.navigation.BrowserHistory
import com.etologic.mahjongtournamentsuite.data.di.dataModule
import com.etologic.mahjongtournamentsuite.data.di.publicRepositoriesModule
import com.etologic.mahjongtournamentsuite.data.di.useCasesModule
import com.etologic.mahjongtournamentsuite.presentation.di.AppKoin
import com.etologic.mahjongtournamentsuite.data.di.telemetry
import com.etologic.mahjongtournamentsuite.presentation.components.LocalAdaptiveTopBar
import com.etologic.mahjongtournamentsuite.presentation.components.LocalAppLogo
import com.etologic.mahjongtournamentsuite.presentation.components.LocalAppLogoOnDark
import com.etologic.mahjongtournamentsuite.presentation.components.LocalFeatureFlags
import com.etologic.mahjongtournamentsuite.domain.model.FeatureFlags
import com.etologic.mahjongtournamentsuite.domain.repository.FeatureFlagsRepository
import androidx.compose.runtime.produceState
import com.etologic.mahjongtournamentsuite.presentation.telemetry.TrackPreferenceChanges
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

/** The screen of one player of a tournament. [tab] is a [PlayerTab]: the tables or the statistics of the player. */
@Serializable
data class TournamentPlayerRoute(val tournamentId: String, val slotId: Int, val tab: Int = PlayerTab.TABLES)

@Serializable
data class TableRoute(val tournamentId: String, val roundId: Int, val tableId: Int)

/** The list of the combinations of the rules, opened from a table. */
@Serializable
data object CombinationsRoute

/** The usual penalties of the rules, opened from a table. */
@Serializable
data object PenaltiesRoute

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

/** The presenters of the player app. The data comes from dataModule, publicRepositoriesModule and useCasesModule. */
internal val playerModule get() = module {
    factory { PlayerSessionPresenter(get(), get(), telemetry()) }
    factory { PlayerTournamentPresenter(get(), get(), get(), get()) }
    factory { PlayerTablePresenter(get(), telemetry()) }
    factory { MyTableReminderPresenter(get()) }
    factory { PlayerStatisticsPresenter(get(), get(), get(), get(), get(), telemetry()) }
}

/**
 * The whole Koin graph of the player app. Each read builds new modules: Koin keeps the single instances in the module
 * objects, so two graphs made from the same objects would share them, and closing one graph would close the network
 * client of the other (the player and admin parts of the Android app, or a player part started again).
 */
internal val playerAppModules get() = listOf(dataModule, publicRepositoriesModule, useCasesModule, playerModule)

/**
 * The player application.
 * [openTournamentId] is set when the player opened the link of a registration email.
 * [sessionStorage] keeps the open screen and the scroll positions through a page refresh.
 */
@Composable
fun PlayerApp(
    openTournamentId: String? = null,
    sessionStorage: SessionStateStorage? = null,
    appVersion: String = AppVersion.admin,
    /** True when the admin app sent the user here to sign in. */
    openSignIn: Boolean = false,
    /** True when the address was `/support`: the app opens the support form over the tournaments. */
    openSupport: Boolean = false,
    /** True on a phone or a tablet app: the score of a hand is typed on a numpad of the app, not on the system keyboard. */
    numpadEntry: Boolean = false,
    /** True in the Android app of the suite, which also holds the admin part. See [AppKoin]. */
    isolatedKoin: Boolean = false,
) {
    AppKoin(isolatedKoin, playerAppModules) {
        val themeController = rememberThemeController()
        CompositionLocalProvider(
            LocalTelemetry provides (getKoin().getOrNull<Telemetry>() ?: NoTelemetry),
            LocalThemeController provides themeController,
            LocalAppVersion provides appVersion,
            LocalScrollPositionStore provides remember(sessionStorage) { sessionStorage?.let { ScrollPositionStore(it) } },
            LocalAppLogo provides painterResource(Res.drawable.app_logo),
            LocalAppLogoOnDark provides painterResource(Res.drawable.app_logo_dark),
            LocalAdaptiveTopBar provides true,
            LocalNumpadEntry provides numpadEntry,
            LocalFeatureFlags provides koinInject<FeatureFlagsRepository>().let { repository ->
                produceState(FeatureFlags()) { value = repository.load() }.value
            },
        ) {
            ProvideAppLanguage {
                // The user cannot enter before accepting the anonymous analytics.
                ConsentGate {
                    TrackPreferenceChanges()
                    MtsTheme(useDarkTheme = themeController.isDarkTheme) {
                        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                            PlayerNavigation(openTournamentId, sessionStorage, openSignIn, openSupport)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerNavigation(
    openTournamentId: String?,
    sessionStorage: SessionStateStorage?,
    openSignIn: Boolean,
    openSupport: Boolean,
) {
    val navController = rememberNavController()
    val routePersistence = remember(sessionStorage) { sessionStorage?.let { RoutePersistence(it) } }
    // The saved routes are read one time, before the first navigation changes them.
    val restoredRoutes = remember(routePersistence) { routePersistence?.load().orEmpty() }
    LaunchedEffect(navController, routePersistence) {
        routePersistence ?: return@LaunchedEffect
        navController.currentBackStack.collect { routePersistence.save(it) }
    }

    Box(Modifier.fillMaxSize()) {
        PlayerNavHost(navController, openTournamentId, restoredRoutes, openSignIn, openSupport)
        // On the web the back and forward of the browser move through the screens like the back of the app.
        BrowserHistory(navController)
        // Over every screen: the table of the player when a round of the player starts soon or is in play.
        MyTableReminder(navController, sessionStorage)
        // One time, when the app shows a language that was translated with AI.
        AiTranslationNotice(onContact = { navController.navigate(SupportRoute()) })
    }
}

@Composable
private fun PlayerNavHost(
    navController: NavHostController,
    openTournamentId: String?,
    restoredRoutes: List<Any>,
    openSignIn: Boolean,
    openSupport: Boolean,
) {
    NavHost(navController = navController, startDestination = SplashRoute) {
        screen<SplashRoute> { SplashScreen(navController, openTournamentId, restoredRoutes, openSignIn, openSupport) }
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
        screen<TournamentRoute> { entry -> TournamentScreen(navController, entry.toRoute<TournamentRoute>().tournamentId) }
        screen<TournamentPlayerRoute> { entry -> TournamentPlayerScreen(navController, entry.toRoute<TournamentPlayerRoute>()) }
        screen<TableRoute> { entry ->
            val route = entry.toRoute<TableRoute>()
            TableScreen(navController, route.tournamentId, route.roundId, route.tableId)
        }
        screen<StatisticsRoute> { entry -> StatisticsScreen(navController, entry.toRoute<StatisticsRoute>()) }
        screen<CombinationsRoute> { CombinationsScreen(navController) }
        screen<PenaltiesRoute> { PenaltiesScreen(navController) }
    }
}
