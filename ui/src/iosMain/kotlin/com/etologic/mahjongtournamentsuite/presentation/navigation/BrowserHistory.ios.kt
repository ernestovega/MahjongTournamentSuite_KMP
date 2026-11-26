package com.etologic.mahjongtournamentsuite.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController

/** Only the web has a browser history. */
@Composable
actual fun BrowserHistory(navController: NavHostController) = Unit
