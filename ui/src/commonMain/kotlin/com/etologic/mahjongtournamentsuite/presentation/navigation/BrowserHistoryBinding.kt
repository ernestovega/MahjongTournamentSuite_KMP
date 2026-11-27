package com.etologic.mahjongtournamentsuite.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph
import androidx.navigation.NavHostController
import androidx.savedstate.read
import androidx.savedstate.savedState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** The history of the browser tab, as the binding needs it. The web gives the one of the window. */
interface BrowserHistoryApi {
    /** The state of the current entry. */
    val state: String?

    fun push(state: String)

    fun replace(state: String)

    /** Moves [delta] entries. The browser answers later with a pop state. */
    fun go(delta: Int)

    /** Calls [listener] with the state of each entry that the browser back or forward opens, until it is removed. */
    fun addPopStateListener(listener: (String?) -> Unit)

    fun removePopStateListener(listener: (String?) -> Unit)
}

/**
 * Ties the back stack of [navController], with the [layers] of its screens, to [history]. Call it after the `NavHost`
 * of [navController].
 */
@Composable
fun BrowserHistory(navController: NavHostController, history: BrowserHistoryApi, layers: BackLayers = BackLayers.page) {
    LaunchedEffect(navController, history, layers) {
        coroutineScope { BrowserHistoryBinding(navController, history, layers, this).run() }
    }
}

/** The name of a back layer in the routes of an entry of the history, after the route of its screen. */
private const val LAYER_ROUTE = "~back"

/** One element of the history entry of the app: a screen, or a back layer of the screen before it. */
private class Step(val route: String, val layer: BackLayer?)

private class BrowserHistoryBinding(
    private val navController: NavHostController,
    private val history: BrowserHistoryApi,
    private val layers: BackLayers,
    private val scope: CoroutineScope,
) {
    /** What the current entry of the browser history holds. A reload keeps it, with the back stack it restores. */
    private var shown: HistoryEntry? = HistoryEntry.decode(history.state)

    /** The entries that a back of the app asked the browser to go back, whose answer has not arrived yet. */
    private var pendingBack = 0

    private val onPopState: (String?) -> Unit = { state -> popState(HistoryEntry.decode(state)) }

    suspend fun run() {
        history.addPopStateListener(onPopState)
        try {
            combine(navController.currentBackStack, layers.layers) { stack, _ -> stack }.collect { stack -> follow(stack) }
        } finally {
            history.removePopStateListener(onPopState)
        }
    }

    /** Writes a change of the back stack of the app to the browser history. */
    private fun follow(stack: List<NavBackStackEntry> = navController.currentBackStack.value) {
        val routes = stepsOf(stack)?.map { it.route } ?: return
        val steps = historySteps(shown, routes)
        steps.forEach { step ->
            when (step) {
                is HistoryStep.Push -> history.push(step.entry.encode())
                is HistoryStep.Replace -> history.replace(step.entry.encode())
                is HistoryStep.Back -> {
                    pendingBack++
                    history.go(-step.count)
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
            follow()
            return
        }
        val current = stepsOf(navController.currentBackStack.value) ?: return
        val plan = navigationPlan(current.map { it.route }, entry.routes) ?: return
        var closedLayer = false
        val applied = runCatching {
            current.takeLast(plan.pops).asReversed().forEach { step ->
                val layer = step.layer
                if (layer == null) {
                    navController.popBackStack()
                } else {
                    closedLayer = true
                    layer.onBack()
                }
            }
            plan.reset?.let { route ->
                val bottom = navController.currentBackStack.value.last { it.destination !is NavGraph }
                navController.navigate(route) { popUpTo(bottom.destination.id) { inclusive = true } }
            }
            // A layer closed for good: the browser forward cannot open it again.
            plan.open.forEach { route -> if (route.startsWith(LAYER_ROUTE)) error("A layer does not open again") else navController.navigate(route) }
        }
        // A screen that cannot open again leaves the app where it is, and the history shows that.
        if (applied.isFailure) follow()
        // A layer closes when its screen draws again, or stays, like a screen that does not let the user leave while it
        // works. The history then shows it again, after the next frames.
        if (closedLayer) {
            scope.launch {
                repeat(2) { withFrameNanos { } }
                follow()
            }
        }
    }

    /**
     * The screens of [stack], each one followed by its back layers, or null while the app only shows its first screen,
     * the splash, that restores the screens of a reload and must not take the place of their entry.
     */
    private fun stepsOf(stack: List<NavBackStackEntry>): List<Step>? {
        val entries = stack.filter { it.destination !is NavGraph }
        if (entries.isEmpty()) return null
        if (entries.size == 1 && entries.first().destination.id == navController.graph.startDestinationId) return null
        layers.keepOnly(entries.mapTo(mutableSetOf()) { it.id })
        val open = layers.layers.value
        return entries.flatMap { entry ->
            val screen = Step(entry.routeWithArguments() ?: return null, layer = null)
            val own = open.filter { it.owner == entry.id }
            listOf(screen) + own.mapIndexed { index, layer -> Step("$LAYER_ROUTE${index + 1}", layer) }
        }
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
        // The values of the types come encoded for an address already.
        values[name] ?: arguments.read { getStringOrNull(name) }.orEmpty()
    }
}
