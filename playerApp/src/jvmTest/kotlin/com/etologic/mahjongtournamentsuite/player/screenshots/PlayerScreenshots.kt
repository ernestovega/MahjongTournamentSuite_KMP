package com.etologic.mahjongtournamentsuite.player.screenshots

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.LocalSystemTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.SystemTheme
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.HandPreview
import com.etologic.mahjongtournamentsuite.domain.model.HandProposal
import com.etologic.mahjongtournamentsuite.domain.model.PenaltyEntry
import com.etologic.mahjongtournamentsuite.domain.model.UserProfile
import com.etologic.mahjongtournamentsuite.domain.repository.AuthRepository
import com.etologic.mahjongtournamentsuite.domain.repository.FeatureFlagsRepository
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerHandRepository
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerRepository
import com.etologic.mahjongtournamentsuite.domain.repository.StatisticsRepository
import com.etologic.mahjongtournamentsuite.domain.repository.SupportRepository
import com.etologic.mahjongtournamentsuite.domain.repository.TournamentRepository
import com.etologic.mahjongtournamentsuite.domain.usecase.CalculateStatisticsUseCase
import com.etologic.mahjongtournamentsuite.domain.usecase.CalculateTournamentRankingsUseCase
import com.etologic.mahjongtournamentsuite.domain.usecase.LoadStatisticsUseCase
import com.etologic.mahjongtournamentsuite.domain.usecase.LoadStoredStatisticsUseCase
import com.etologic.mahjongtournamentsuite.player.MyTableReminderPresenter
import com.etologic.mahjongtournamentsuite.player.PlayerSessionPresenter
import com.etologic.mahjongtournamentsuite.player.PlayerStatisticsPresenter
import com.etologic.mahjongtournamentsuite.player.PlayerTablePresenter
import com.etologic.mahjongtournamentsuite.player.PlayerTournamentPresenter
import com.etologic.mahjongtournamentsuite.presentation.components.LocalAdaptiveTopBar
import com.etologic.mahjongtournamentsuite.presentation.components.LocalAppLogo
import com.etologic.mahjongtournamentsuite.presentation.components.LocalAppLogoOnDark
import com.etologic.mahjongtournamentsuite.presentation.theme.LocalThemeController
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import com.etologic.mahjongtournamentsuite.presentation.theme.ThemeController
import com.etologic.mahjongtournamentsuite.presentation.theme.ThemePreference
import com.etologic.mahjongtournamentsuite.screenshots.Variant
import com.etologic.mahjongtournamentsuite.screenshots.allVariants
import com.etologic.mahjongtournamentsuite.screenshots.fake.ScreenshotAuthRepository
import com.etologic.mahjongtournamentsuite.screenshots.fake.ScreenshotData
import com.etologic.mahjongtournamentsuite.screenshots.fake.ScreenshotFeatureFlagsRepository
import com.etologic.mahjongtournamentsuite.screenshots.fake.ScreenshotPlayerRepository
import com.etologic.mahjongtournamentsuite.screenshots.fake.ScreenshotStatisticsRepository
import com.etologic.mahjongtournamentsuite.screenshots.fake.ScreenshotSupportRepository
import com.etologic.mahjongtournamentsuite.screenshots.fake.ScreenshotTournamentRepository
import com.etologic.mahjongtournamentsuite.screenshots.screenshots
import mahjongtournamentsuite.playerapp.generated.resources.Res
import mahjongtournamentsuite.playerapp.generated.resources.app_logo
import mahjongtournamentsuite.playerapp.generated.resources.app_logo_dark
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.KoinContext
import org.koin.core.module.Module
import org.koin.dsl.koinApplication
import org.koin.dsl.module

private class NoHands : PlayerHandRepository {
    private fun <T> no(): AppResult<T> = AppResult.Failure(AppError.Unexpected("Screenshots do not write"))
    override suspend fun addPenalties(tournamentId: String, roundId: Int, tableId: Int, penalties: List<PenaltyEntry>, expectedVersion: Long): AppResult<Unit> = no()
    override suspend fun previewHand(tournamentId: String, roundId: Int, tableId: Int, proposal: HandProposal): AppResult<HandPreview> = no()
    override suspend fun recordHand(tournamentId: String, roundId: Int, tableId: Int, proposal: HandProposal, expectedVersion: Long): AppResult<HandPreview> = no()
    override suspend fun assignSeats(tournamentId: String, roundId: Int, tableId: Int, seats: List<Int>, expectedVersion: Long): AppResult<Unit> = no()
}

/** The presenters of the player app over the fake data. [account] is the signed-in account, or a guest when null. */
internal fun playerModules(
    account: UserProfile? = ScreenshotData.playerAccount,
    tournaments: TournamentRepository = ScreenshotTournamentRepository(),
): List<Module> = listOf(
    module {
        single { Logger.withTag("screenshot") }
        single<TournamentRepository> { tournaments }
        single<PlayerRepository> { ScreenshotPlayerRepository() }
        single<AuthRepository> { ScreenshotAuthRepository(account) }
        single<StatisticsRepository> { ScreenshotStatisticsRepository() }
        single<FeatureFlagsRepository> { ScreenshotFeatureFlagsRepository() }
        single<SupportRepository> { ScreenshotSupportRepository() }
        single<PlayerHandRepository> { NoHands() }
        factory { PlayerSessionPresenter(get(), get()) }
        factory { CalculateTournamentRankingsUseCase() }
        factory { CalculateStatisticsUseCase() }
        factory { LoadStatisticsUseCase(get(), get(), get()) }
        factory { LoadStoredStatisticsUseCase(get(), get(), get()) }
        factory { PlayerTournamentPresenter(get(), get(), get(), get()) }
        factory { PlayerTablePresenter(get()) }
        factory { MyTableReminderPresenter(get()) }
        factory { PlayerStatisticsPresenter(get(), get(), get(), get()) }
    },
)

/** Screenshots of a player screen with the frame of the player app. The images go to playerApp/screenshots/<name>/. */
@OptIn(ExperimentalTestApi::class, InternalComposeUiApi::class)
internal fun playerScreenshots(
    name: String,
    modules: () -> List<Module> = { playerModules() },
    variants: List<Variant> = allVariants,
    ready: ComposeUiTest.() -> Unit = {},
    content: @Composable (NavHostController) -> Unit,
) = screenshots(name, variants, ready) { variant ->
    val koin = remember { koinApplication { modules(modules()) }.koin }
    val preference = if (variant.dark) ThemePreference.Dark else ThemePreference.Light
    KoinContext(koin) {
        CompositionLocalProvider(
            LocalThemeController provides ThemeController(preference, variant.dark, {}),
            // The Timer and the rankings keep a theme of their own that starts from the system theme.
            LocalSystemTheme provides if (variant.dark) SystemTheme.Dark else SystemTheme.Light,
            LocalAppLogo provides painterResource(Res.drawable.app_logo),
            LocalAppLogoOnDark provides painterResource(Res.drawable.app_logo_dark),
            LocalAdaptiveTopBar provides true,
        ) {
            MtsTheme(useDarkTheme = variant.dark) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val navController = rememberNavController()
                    NavHost(navController, startDestination = "screen") {
                        composable("screen") { content(navController) }
                    }
                }
            }
        }
    }
}

/** Waits until [text] is on the screen: the data of the screen is loaded. */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.waitForText(text: String, substring: Boolean = false) {
    waitUntil(timeoutMillis = 10_000) { onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty() }
}
