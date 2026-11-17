package com.etologic.mahjongtournamentsuite.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.repository.RefreshMode
import com.etologic.mahjongtournamentsuite.presentation.util.UiText
import com.etologic.mahjongtournamentsuite.presentation.util.toUiText
import kotlinx.coroutines.flow.map
import kotlin.time.Duration

/**
 * Loads data for a screen and checks for changes while the screen is open, like [rememberTournamentView].
 * [load] gets the refresh mode and whether it is the first load of the screen. [nextDelay] gets the data on screen.
 * A hidden tab or an app in the background does not check. The return checks at once.
 * A failed check does not show a message when the data is already on screen.
 */
@Composable
internal fun <T : Any> rememberPolledLoad(
    key: Any?,
    load: suspend (mode: RefreshMode, first: Boolean) -> AppResult<T>,
    nextDelay: (T?) -> Duration?,
): PolledLoad<T> {
    var data by remember(key) { mutableStateOf<T?>(null) }
    var message by remember(key) { mutableStateOf<UiText?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(key) {
        var first = true
        pollForChanges(
            isStarted = lifecycle.currentStateFlow.map { it.isAtLeast(Lifecycle.State.STARTED) },
            forceFirst = false,
            load = { mode -> load(mode, first).also { first = false } },
            nextDelay = { nextDelay(data) },
        ) { result ->
            when (result) {
                is AppResult.Success -> {
                    data = result.value
                    message = null
                }
                is AppResult.Failure -> if (data == null) message = result.error.toUiText()
            }
        }
    }
    return PolledLoad(data, message) { message = null }
}

/** What [rememberPolledLoad] holds: the data on screen, the message of a failed load, and how to close that message. */
internal class PolledLoad<T : Any>(val data: T?, val message: UiText?, val dismissMessage: () -> Unit)
