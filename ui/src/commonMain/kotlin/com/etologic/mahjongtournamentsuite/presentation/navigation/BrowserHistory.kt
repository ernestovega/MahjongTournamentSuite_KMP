package com.etologic.mahjongtournamentsuite.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController

/**
 * On the web, ties the back stack of [navController] to the history of the browser: the back and forward buttons of
 * the browser, and the back gesture of a phone, move through the screens like the back of the app. The address of the
 * page does not change. On the other platforms it does nothing.
 *
 * Call it after the `NavHost` of [navController].
 */
@Composable
expect fun BrowserHistory(navController: NavHostController)

/**
 * One entry of the browser history that the app wrote: the routes of the back stack, from the bottom to the top, and
 * [stepsBack], how many entries right behind it hold the same back stack with one screen less each time. An app back
 * of up to [stepsBack] screens then goes back in the browser history too, so the browser forward opens them again.
 */
data class HistoryEntry(val routes: List<String>, val stepsBack: Int) {
    fun encode(): String = (listOf(MARK, stepsBack.toString()) + routes).joinToString("\n")

    companion object {
        private const val MARK = "mts-nav"

        /** Reads an entry that [encode] wrote. Any other state of the browser history gives null. */
        fun decode(text: String?): HistoryEntry? {
            val lines = text?.split('\n') ?: return null
            if (lines.size < 3 || lines[0] != MARK) return null
            val stepsBack = lines[1].toIntOrNull()?.takeIf { it >= 0 } ?: return null
            return HistoryEntry(lines.drop(2), stepsBack)
        }
    }
}

/** A change to the browser history. */
sealed interface HistoryStep {
    /** Adds [entry] after the current entry. */
    data class Push(val entry: HistoryEntry) : HistoryStep

    /** Changes the current entry to [entry]. */
    data class Replace(val entry: HistoryEntry) : HistoryStep

    /** Goes [count] entries back, to the entry that already holds [entry]. */
    data class Back(val count: Int, val entry: HistoryEntry) : HistoryStep
}

private val HistoryStep.entry: HistoryEntry
    get() = when (this) {
        is HistoryStep.Push -> entry
        is HistoryStep.Replace -> entry
        is HistoryStep.Back -> entry
    }

/** The entry that the browser shows after [this] steps, or [current] when there are none. */
fun List<HistoryStep>.resultingEntry(current: HistoryEntry?): HistoryEntry? = lastOrNull()?.entry ?: current

/**
 * The changes to the browser history after the app changed its back stack to [routes], while the browser showed
 * [current]. Opening screens adds one entry for each. Going back goes back in the history when the entries behind
 * hold those screens. Anything else changes the current entry, so the browser back never returns to a screen that the
 * app left for good, like the sign-in or the splash.
 */
fun historySteps(current: HistoryEntry?, routes: List<String>): List<HistoryStep> {
    if (routes.isEmpty()) return emptyList()
    // The first screens of the page, also the ones that a reload restores, get one entry each, so the browser back
    // closes them one by one.
    if (current == null) return stepsFrom(routes, common = 0, stepsBack = 0)
    val old = current.routes
    if (old == routes) return emptyList()
    val common = commonPrefix(old, routes)
    val removed = old.size - common
    val added = routes.size - common
    return when {
        removed == 0 -> (1..added).map { HistoryStep.Push(HistoryEntry(routes.take(common + it), current.stepsBack + it)) }
        added == 0 && removed <= current.stepsBack ->
            listOf(HistoryStep.Back(removed, HistoryEntry(routes, current.stepsBack - removed)))
        added == 0 -> listOf(HistoryStep.Replace(HistoryEntry(routes, 0)))
        else -> {
            // The top screen changed: the entries behind still hold the screens below it when only the top one left.
            stepsFrom(routes, common, stepsBack = if (removed == 1) current.stepsBack else 0)
        }
    }
}

/** Changes the current entry to the first screen after [common], with [stepsBack], and adds one entry for each next one. */
private fun stepsFrom(routes: List<String>, common: Int, stepsBack: Int): List<HistoryStep> {
    val first = HistoryStep.Replace(HistoryEntry(routes.take(common + 1), stepsBack))
    val next = (common + 2..routes.size).map { HistoryStep.Push(HistoryEntry(routes.take(it), stepsBack + it - common - 1)) }
    return listOf(first) + next
}

/**
 * How the app reaches the back stack [target] of a browser entry from its back stack [current]: it closes [pops]
 * screens from the top, opens [reset] in place of the only screen left when the bottom changes too, and then opens
 * [open] in order.
 */
data class NavigationPlan(val pops: Int, val reset: String?, val open: List<String>)

fun navigationPlan(current: List<String>, target: List<String>): NavigationPlan? {
    if (target.isEmpty() || current.isEmpty() || current == target) return null
    val common = commonPrefix(current, target)
    return if (common == 0) {
        NavigationPlan(pops = current.size - 1, reset = target.first(), open = target.drop(1))
    } else {
        NavigationPlan(pops = current.size - common, reset = null, open = target.drop(common))
    }
}

private fun commonPrefix(a: List<String>, b: List<String>): Int {
    var index = 0
    while (index < a.size && index < b.size && a[index] == b[index]) index++
    return index
}
