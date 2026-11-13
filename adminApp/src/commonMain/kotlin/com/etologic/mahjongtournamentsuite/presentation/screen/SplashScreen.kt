package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavHostController
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.presentation.SignInRoute
import com.etologic.mahjongtournamentsuite.presentation.SplashRoute
import com.etologic.mahjongtournamentsuite.presentation.TournamentsRoute
import com.etologic.mahjongtournamentsuite.presentation.components.AppLoading
import com.etologic.mahjongtournamentsuite.presentation.platform.leaveAdminForPlayerApp
import com.etologic.mahjongtournamentsuite.presentation.presenter.AuthPresenter
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

@Composable
fun SplashScreen(
    navController: NavHostController,
    /** The screens that were open before a page refresh, from the first to the last. */
    restoredRoutes: List<Any> = emptyList(),
) {
    val authPresenter = koinInject<AuthPresenter>()

    LaunchedEffect(authPresenter) {
        var hasSession = authPresenter.hasActiveSession()
        if (hasSession) {
            // Only a loaded profile of an admin or an editor passes. When the profile cannot load, the session stays.
            val manager = authPresenter.isManager()
            if (manager is AppResult.Success && !manager.value) {
                // On the web the session belongs to the whole site: a player keeps it and goes to the player app.
                if (leaveAdminForPlayerApp(signIn = false)) return@LaunchedEffect
                authPresenter.signOut()
            }
            hasSession = manager is AppResult.Success && manager.value
        }
        // On the web the site has one sign-in, the one of the player app. It then offers this app to admins and editors.
        if (!hasSession && leaveAdminForPlayerApp(signIn = true)) return@LaunchedEffect
        val nextRoute = if (hasSession) TournamentsRoute else SignInRoute

        // The check above can suspend. The navigation must run on the main thread, also in the UI tests.
        withContext(Dispatchers.Main) {
            navController.navigate(nextRoute) {
                popUpTo(SplashRoute) { inclusive = true }
            }
            if (hasSession) {
                restoredRoutes.filterNot { it == TournamentsRoute }.forEach { navController.navigate(it) }
            }
        }
    }

    AppLoading()
}

@Preview(device = Devices.DESKTOP)
@Composable
private fun SplashScreenPreview() {
    MtsTheme(useDarkTheme = false) {
        AppLoading()
    }
}
