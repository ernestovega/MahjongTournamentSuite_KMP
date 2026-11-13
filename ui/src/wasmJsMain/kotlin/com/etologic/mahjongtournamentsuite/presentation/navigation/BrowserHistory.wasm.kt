package com.etologic.mahjongtournamentsuite.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph
import androidx.navigation.NavHostController
import androidx.savedstate.read
import androidx.savedstate.savedState
import kotlinx.browser.window
import org.w3c.dom.PopStateEvent
import org.w3c.dom.events.Event

@Composable
actual fun BrowserHistory(navController: NavHostController) {
    LaunchedEffect(navController) { BrowserHistoryBinding(navController).run() }
}

private class BrowserHistoryBinding(private val navController: NavHostController) {
    /** What the current entry of the browser history holds. A reload keeps it, with the back stack it restores. */
    private var shown: HistoryEntry? = HistoryEntry.decode(window.history.state?.toString())

    /** The entries that a back of the app asked the browser to go back, whose answer has not arrived yet. */
    private var pendingBack = 0

    private val onPopState: (Event) -> Unit = { event -> popState(HistoryEntry.decode((event as? PopStateEvent)?.state?.toString())) }

    suspend fun run() {
        window.addEventListener("popstate", onPopState)
        try {
            navController.currentBackStack.collect { stack -> follow(stack) }
        } finally {
            window.removeEventListener("popstate", onPopState)
        }
    }

    /** Writes a change of the back stack of the app to the browser history. */
    private fun follow(stack: List<NavBackStackEntry>) {
        val routes = routesOf(stack) ?: return
        val steps = historySteps(shown, routes)
        steps.forEach { step ->
            when (step) {
                is HistoryStep.Push -> window.history.pushState(step.entry.encode().toJsString(), "", null)
                is HistoryStep.Replace -> window.history.replaceState(step.entry.encode().toJsString(), "", null)
                is HistoryStep.Back -> {
                    pendingBack++
                    window.history.go(-step.count)
                }
            }
        }
        shown = steps.resultingEntry(shown)
    }

    /** The browser moved to [entry] with its back or forward: the app opens that back stack. */
    private fun popState(entry: HistoryEntry?) {
        // Entries that the app did not write, like a change of the address, are left alone.
        entry ?: return
        shown = entry
        if (pendingBack > 0) {
            // The answer to a back of the app. The app may have moved again meanwhile, so the history follows it.
            pendingBack--
            follow(navController.currentBackStack.value)
            return
        }
        val current = routesOf(navController.currentBackStack.value) ?: return
        val plan = navigationPlan(current, entry.routes) ?: return
        val applied = runCatching {
            repeat(plan.pops) { navController.popBackStack() }
            plan.reset?.let { route ->
                val bottom = navController.currentBackStack.value.last { it.destination !is NavGraph }
                navController.navigate(route) { popUpTo(bottom.destination.id) { inclusive = true } }
            }
            plan.open.forEach { navController.navigate(it) }
        }
        // A screen that cannot open again leaves the app where it is, and the history shows that.
        if (applied.isFailure) follow(navController.currentBackStack.value)
    }

    /**
     * The routes of the screens of [stack], or null while the app only shows its first screen, the splash, that
     * restores the screens of a reload and must not take the place of their entry.
     */
    private fun routesOf(stack: List<NavBackStackEntry>): List<String>? {
        val entries = stack.filter { it.destination !is NavGraph }
        if (entries.isEmpty()) return null
        if (entries.size == 1 && entries.first().destination.id == navController.graph.startDestinationId) return null
        return entries.map { it.routeWithArguments() ?: return null }
    }
}

private val argumentPlaceholder = Regex("""\{.*?\}""")

/** The route of the entry with its values, like `TournamentRoute/abc`, that `navigate` opens again. */
private fun NavBackStackEntry.routeWithArguments(): String? {
    val route = destination.route ?: return null
    if (!route.contains(argumentPlaceholder)) return route
    val arguments = arguments ?: savedState()
    val values = destination.arguments.mapValues { (name, argument) ->
        argument.type.serializeAsValue(argument.type[arguments, name])
    }
    return route.replace(argumentPlaceholder) { match ->
        val name = match.value.trim('{', '}')
        // The values of the types come encoded for an address already. Values without a type are plain texts.
        values[name] ?: encodeURIComponent(arguments.read { getStringOrNull(name) } ?: "")
    }
}

private external fun encodeURIComponent(value: String): String
