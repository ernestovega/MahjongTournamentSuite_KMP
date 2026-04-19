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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.etologic.mahjongtournamentsuite.presentation.SignInRoute
import com.etologic.mahjongtournamentsuite.presentation.SplashRoute
import com.etologic.mahjongtournamentsuite.presentation.TournamentsRoute
import com.etologic.mahjongtournamentsuite.presentation.presenter.AuthPresenter
import org.koin.compose.koinInject

@Composable
fun SplashScreen(
    navController: NavHostController,
) {
    val authPresenter = koinInject<AuthPresenter>()

    LaunchedEffect(authPresenter) {
        val nextRoute = if (authPresenter.hasActiveSession()) TournamentsRoute else SignInRoute

        navController.navigate(nextRoute) {
            popUpTo(SplashRoute) { inclusive = true }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text(
            text = "Mahjong Tournament Suite",
            style = MaterialTheme.typography.headlineMedium,
        )
        CircularProgressIndicator()
    }
}
