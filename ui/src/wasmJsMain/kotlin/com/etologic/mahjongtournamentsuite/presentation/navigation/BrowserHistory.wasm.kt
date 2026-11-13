package com.etologic.mahjongtournamentsuite.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import kotlinx.browser.window
import org.w3c.dom.PopStateEvent
import org.w3c.dom.events.Event

@Composable
actual fun BrowserHistory(navController: NavHostController) {
    BrowserHistory(navController, remember { WindowHistory() })
}

/** The history of the browser window. The address of the page stays as it is. */
private class WindowHistory : BrowserHistoryApi {
    private val listeners = mutableMapOf<(String?) -> Unit, (Event) -> Unit>()

    override val state: String? get() = window.history.state?.toString()

    override fun push(state: String) = window.history.pushState(state.toJsString(), "", null)

    override fun replace(state: String) = window.history.replaceState(state.toJsString(), "", null)

    override fun go(delta: Int) = window.history.go(delta)

    override fun addPopStateListener(listener: (String?) -> Unit) {
        val callback: (Event) -> Unit = { event -> listener((event as? PopStateEvent)?.state?.toString()) }
        listeners[listener] = callback
        window.addEventListener("popstate", callback)
    }

    override fun removePopStateListener(listener: (String?) -> Unit) {
        listeners.remove(listener)?.let { window.removeEventListener("popstate", it) }
    }
}
