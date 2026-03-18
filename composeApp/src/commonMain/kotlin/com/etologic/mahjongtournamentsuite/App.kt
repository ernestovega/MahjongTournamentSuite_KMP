package com.etologic.mahjongtournamentsuite

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.data.di.dataModule
import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.AppVersion
import com.etologic.mahjongtournamentsuite.domain.usecase.GetGreetingMessageUseCase
import kotlinx.serialization.Serializable
import mahjongtournamentsuite.composeapp.generated.resources.Res
import mahjongtournamentsuite.composeapp.generated.resources.compose_multiplatform
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.KoinApplication
import org.koin.compose.koinInject
import org.koin.dsl.module
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

private val presentationModule = module {
    factory { GetGreetingMessageUseCase(get()) }
    factory { HomePresenter(get(), get()) }
}

@Composable
private fun MahjongTournamentSuiteApp() {
    val navController = rememberNavController()

    MaterialTheme {
        NavHost(
            navController = navController,
            startDestination = HomeRoute,
            modifier = Modifier.fillMaxSize(),
        ) {
            composable<HomeRoute> {
                HomeScreen(navController = navController)
            }
            composable<AboutRoute> {
                AboutScreen(navController = navController)
            }
        }
    }
}

@Serializable
data object HomeRoute

@Serializable
data object AboutRoute

private data class HomeUiState(
    val isLoading: Boolean = true,
    val greeting: String? = null,
    val errorMessage: String? = null,
)

private class HomePresenter(
    private val getGreetingMessageUseCase: GetGreetingMessageUseCase,
    private val logger: Logger,
) {
    suspend fun loadGreeting(): HomeUiState {
        logger.i { "Loading greeting for the home screen." }

        return when (val result = getGreetingMessageUseCase()) {
            is AppResult.Success -> HomeUiState(greeting = result.value)
            is AppResult.Failure -> HomeUiState(errorMessage = result.error.toUiMessage())
        }
    }
}

@Composable
private fun HomeScreen(
    navController: NavHostController,
) {
    val presenter = koinInject<HomePresenter>()
    var uiState by remember { mutableStateOf(HomeUiState()) }

    LaunchedEffect(presenter) {
        uiState = presenter.loadGreeting()
    }

    Column(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.primaryContainer)
            .safeContentPadding()
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text(
            text = "Mahjong Tournament Suite",
            style = MaterialTheme.typography.headlineMedium,
        )

        Image(
            painter = painterResource(Res.drawable.compose_multiplatform),
            contentDescription = null,
        )

        AnimatedVisibility(uiState.isLoading) {
            CircularProgressIndicator()
        }

        uiState.greeting?.let { greeting ->
            Text(
                text = greeting,
                style = MaterialTheme.typography.bodyLarge,
            )
        }

        uiState.errorMessage?.let { errorMessage ->
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Button(onClick = { navController.navigate(AboutRoute) }) {
            Text("About stack")
        }
    }
}

@Composable
private fun AboutScreen(
    navController: NavHostController,
) {
    Column(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .safeContentPadding()
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Text(
            text = "Configured baseline",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text("Koin + Navigation + Coroutines")
        Text("Kotlin Serialization + Ktor Client")
        Text("Kermit logging + kotlinx-datetime")
        Text("Version ${AppVersion.name} (${AppVersion.code})")

        Button(onClick = { navController.popBackStack() }) {
            Text("Back")
        }
    }
}

private fun AppError.toUiMessage(): String = when (this) {
    AppError.Network -> "Network request failed."
    is AppError.Unexpected -> message ?: "Something went wrong."
}
