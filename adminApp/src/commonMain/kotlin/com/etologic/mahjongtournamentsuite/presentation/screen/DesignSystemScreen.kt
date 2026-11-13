package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import com.etologic.mahjongtournamentsuite.presentation.components.AppScaffold
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsCatalog
import mahjongtournamentsuite.adminapp.generated.resources.Res
import mahjongtournamentsuite.adminapp.generated.resources.design_system
import org.jetbrains.compose.resources.stringResource

/** The catalog of the design system, for the review of the tokens. Administrators open it from their profile. */
@Composable
fun DesignSystemScreen(navController: NavHostController) {
    AppScaffold(
        title = stringResource(Res.string.design_system),
        showBackgroundLogo = false,
        onBack = { navController.popBackStack() },
    ) {
        MtsCatalog(modifier = Modifier.fillMaxSize())
    }
}
