package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.etologic.mahjongtournamentsuite.presentation.SignInRoute
import com.etologic.mahjongtournamentsuite.presentation.SplashRoute
import com.etologic.mahjongtournamentsuite.presentation.TournamentsRoute
import com.etologic.mahjongtournamentsuite.presentation.components.AppBackground
import com.etologic.mahjongtournamentsuite.presentation.presenter.AuthPresenter
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import mahjongtournamentsuite.adminapp.generated.resources.Res
import mahjongtournamentsuite.adminapp.generated.resources.mahjong_tournament_suite
import org.jetbrains.compose.resources.stringResource
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
            if (manager is AppResult.Success && !manager.value) authPresenter.signOut()
            hasSession = manager is AppResult.Success && manager.value
        }
        val nextRoute = if (hasSession) TournamentsRoute else SignInRoute

        navController.navigate(nextRoute) {
            popUpTo(SplashRoute) { inclusive = true }
        }
        if (hasSession) {
            restoredRoutes.filterNot { it == TournamentsRoute }.forEach { navController.navigate(it) }
        }
    }

    AppBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            Text(
                text = stringResource(Res.string.mahjong_tournament_suite),
                style = MaterialTheme.typography.headlineMedium,
            )
            CircularProgressIndicator()
        }
    }
}

@Preview(device = Devices.DESKTOP)
@Composable
private fun SplashScreenPreview() {
    MtsTheme(useDarkTheme = false) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            Text(
                text = stringResource(Res.string.mahjong_tournament_suite),
                style = MaterialTheme.typography.headlineMedium,
            )
            CircularProgressIndicator()
        }
    }
}
