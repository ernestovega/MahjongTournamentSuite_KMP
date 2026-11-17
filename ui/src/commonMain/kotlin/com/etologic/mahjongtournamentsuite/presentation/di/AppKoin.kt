package com.etologic.mahjongtournamentsuite.presentation.di

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import org.koin.compose.KoinApplication
import org.koin.compose.KoinIsolatedContext
import org.koin.core.module.Module
import org.koin.dsl.koinApplication
import org.koin.dsl.koinConfiguration

/**
 * Starts the Koin graph of an app with [modules]. Normally it is the global graph of Koin, which the other windows of
 * the app share. The Android app of the suite holds the player part and the admin part, which need different graphs,
 * so each part gets its own [isolated] graph, closed when the part leaves the screen. Closing it closes the network
 * client, the caches and the telemetry of that graph.
 */
@Composable
fun AppKoin(isolated: Boolean, modules: List<Module>, content: @Composable () -> Unit) {
    if (!isolated) {
        KoinApplication(configuration = koinConfiguration { modules(modules) }, content = content)
        return
    }
    val application = remember { koinApplication { modules(modules) } }
    DisposableEffect(application) { onDispose { application.close() } }
    KoinIsolatedContext(application, content)
}
