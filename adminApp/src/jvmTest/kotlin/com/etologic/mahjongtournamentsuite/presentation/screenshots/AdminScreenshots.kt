package com.etologic.mahjongtournamentsuite.presentation.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.LocalSystemTheme
import androidx.compose.ui.SystemTheme
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.presentation.components.LocalAdaptiveTopBar
import com.etologic.mahjongtournamentsuite.presentation.components.LocalAppLogo
import com.etologic.mahjongtournamentsuite.presentation.components.LocalAppLogoOnDark
import com.etologic.mahjongtournamentsuite.presentation.components.ProvideCompactLayout
import com.etologic.mahjongtournamentsuite.presentation.theme.LocalThemeController
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import com.etologic.mahjongtournamentsuite.presentation.theme.ThemeController
import com.etologic.mahjongtournamentsuite.presentation.theme.ThemePreference
import com.etologic.mahjongtournamentsuite.screenshots.Variant
import com.etologic.mahjongtournamentsuite.screenshots.allVariants
import com.etologic.mahjongtournamentsuite.screenshots.screenshots
import mahjongtournamentsuite.adminapp.generated.resources.Res
import mahjongtournamentsuite.adminapp.generated.resources.app_logo
import mahjongtournamentsuite.adminapp.generated.resources.app_logo_dark
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.KoinContext
import org.koin.core.module.Module
import org.koin.dsl.koinApplication
import org.koin.dsl.module

/**
 * Screenshots of an admin screen with the same frame as the app (theme, logo, adaptive top bar, compact layout on a
 * phone) and the fake repositories of [modules]. A Logger is always there. The images go to adminApp/screenshots/<name>/.
 */
@OptIn(ExperimentalTestApi::class, InternalComposeUiApi::class)
internal fun adminScreenshots(
    name: String,
    modules: () -> List<Module> = { adminModules() },
    variants: List<Variant> = allVariants,
    ready: ComposeUiTest.() -> Unit = {},
    content: @Composable (NavHostController) -> Unit,
) = screenshots(name, variants, ready) { variant ->
    val koin = remember {
        koinApplication {
            modules(module { single { Logger.withTag("screenshot") } })
            modules(modules())
        }.koin
    }
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
                ProvideCompactLayout {
                    // A real NavHost, so a screen that reads its back stack works. Screenshots never navigate.
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
