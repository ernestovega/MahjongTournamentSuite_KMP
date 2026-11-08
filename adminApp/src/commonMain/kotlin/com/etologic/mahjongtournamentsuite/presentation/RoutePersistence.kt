package com.etologic.mahjongtournamentsuite.presentation

import androidx.compose.runtime.Composable
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.toRoute
import com.etologic.mahjongtournamentsuite.presentation.components.ScrollScope
import com.etologic.mahjongtournamentsuite.presentation.telemetry.TrackedScreen
import com.etologic.mahjongtournamentsuite.presentation.platform.SessionStateStorage
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

/** Adds a screen to the graph. The screen also gets its own scope for the saved scroll positions. */
internal inline fun <reified T : Any> NavGraphBuilder.screen(
    crossinline content: @Composable (NavBackStackEntry) -> Unit,
) {
    // The name of the route class, without "Route", is the name of the screen in the analytics.
    val screenName = T::class.simpleName.orEmpty().removeSuffix("Route")
    composable<T> { entry ->
        TrackedScreen(screenName) {
            ScrollScope(entry.destination.route.orEmpty()) { content(entry) }
        }
    }
}

private class RouteKind<T : Any>(
    val name: String,
    val serializer: KSerializer<T>,
    val read: (NavBackStackEntry) -> T?,
)

private inline fun <reified T : Any> kind(): RouteKind<T> = RouteKind(
    name = T::class.simpleName.orEmpty(),
    serializer = serializer<T>(),
    read = { entry -> if (entry.destination.hasRoute<T>()) entry.toRoute<T>() else null },
)

private val savedKinds: List<RouteKind<*>> = listOf(
    kind<TournamentsRoute>(),
    kind<PlayerBaseRoute>(),
    kind<TournamentRoute>(),
    kind<UsersRoute>(),
    kind<PlayersRoute>(),
    kind<TeamsRoute>(),
    kind<TablesRoute>(),
    kind<TimerRoute>(),
    kind<RankingsRoute>(),
    kind<StatisticsRoute>(),
    kind<ErrorsRoute>(),
)

/**
 * Saves the screens of the back stack, so that a page refresh opens the same screen again.
 * The splash and sign-in screens are not saved.
 */
internal class RoutePersistence(private val storage: SessionStateStorage) {
    private val json = Json { ignoreUnknownKeys = true }

    /** Returns the saved routes, from the bottom of the back stack to the top. */
    fun load(): List<Any> {
        val text = storage.read(STORAGE_NAME) ?: return emptyList()
        return text.lineSequence().mapNotNull { line ->
            val name = line.substringBefore(' ')
            val value = line.substringAfter(' ', missingDelimiterValue = "")
            val kind = savedKinds.firstOrNull { it.name == name } ?: return@mapNotNull null
            runCatching { json.decodeFromString(kind.serializer, value) }.getOrNull()
        }.toList()
    }

    fun save(backStack: List<NavBackStackEntry>) {
        val entries = backStack.filter { it.destination !is NavGraph }
        // The first moment of the app shows only the splash screen. It must not erase the saved routes.
        if (entries.isEmpty() || entries.last().destination.hasRoute<SplashRoute>()) return
        val lines = entries.mapNotNull { entry ->
            savedKinds.firstNotNullOfOrNull { kind -> encode(kind, entry) }
        }
        storage.write(STORAGE_NAME, lines.joinToString("\n"))
    }

    private fun <T : Any> encode(kind: RouteKind<T>, entry: NavBackStackEntry): String? {
        val route = kind.read(entry) ?: return null
        return kind.name + " " + json.encodeToString(kind.serializer, route)
    }

    private companion object {
        const val STORAGE_NAME = "routes"
    }
}
