package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import com.etologic.mahjongtournamentsuite.presentation.platform.LocalScrollPositionStore
import com.etologic.mahjongtournamentsuite.presentation.platform.ScrollPosition
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first

/** The name of the screen that owns the scroll states below it. The app sets it for each navigation entry. */
val LocalScrollScope = compositionLocalOf { "" }

@Composable
fun ScrollScope(name: String, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalScrollScope provides name, content = content)
}

/**
 * Like [rememberScrollState]. After a page refresh it scrolls back to the saved position
 * when the content is long enough. Use a different [name] for each scroll state of one screen.
 */
@Composable
fun rememberPersistentScrollState(name: String = ""): ScrollState {
    val state = rememberScrollState()
    val store = LocalScrollPositionStore.current ?: return state
    val key = LocalScrollScope.current + "/" + name
    val restored = remember(store, key) { store.takeRestored(key) }
    LaunchedEffect(state, store, key) {
        if (restored != null && restored.index > 0) {
            // The content can load after the first frame. Wait until it is long enough.
            snapshotFlow { state.maxValue }.first { it >= restored.index }
            state.scrollTo(restored.index)
        }
        // Save only when the user stops scrolling. A change of the content length must not overwrite the saved value.
        snapshotFlow { state.isScrollInProgress }
            .filter { inProgress -> !inProgress }
            .collect { store.put(key, ScrollPosition(state.value)) }
    }
    return state
}

/** Like [rememberLazyListState], with the same refresh behavior as [rememberPersistentScrollState]. */
@Composable
fun rememberPersistentLazyListState(name: String = ""): LazyListState {
    val state = rememberLazyListState()
    val store = LocalScrollPositionStore.current ?: return state
    val key = LocalScrollScope.current + "/" + name
    val restored = remember(store, key) { store.takeRestored(key) }
    LaunchedEffect(state, store, key) {
        if (restored != null && (restored.index > 0 || restored.offset > 0)) {
            snapshotFlow { state.layoutInfo.totalItemsCount }.first { it > restored.index }
            state.scrollToItem(restored.index, restored.offset)
        }
        snapshotFlow { state.isScrollInProgress }
            .distinctUntilChanged()
            .filter { inProgress -> !inProgress }
            .collect { store.put(key, ScrollPosition(state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset)) }
    }
    return state
}

/**
 * A small whole number, such as the selected tab, that a page refresh restores.
 * Use a different [name] for each value of one screen.
 */
@Composable
fun rememberPersistentInt(name: String, default: Int): MutableState<Int> {
    val store = LocalScrollPositionStore.current
    val key = LocalScrollScope.current + "/value:" + name
    val state = remember(store, key) { mutableStateOf(store?.takeRestored(key)?.index ?: default) }
    if (store != null) {
        LaunchedEffect(state, store, key) {
            snapshotFlow { state.value }.collect { store.put(key, ScrollPosition(it)) }
        }
    }
    return state
}

/**
 * Like [rememberSaveable] for an optional non-negative number, such as a selected id.
 * A page refresh also restores it. The screen must check the restored value, because the data can differ.
 */
@Composable
fun rememberPersistentOptionalInt(name: String, vararg inputs: Any?): MutableState<Int?> {
    val store = LocalScrollPositionStore.current
    val key = LocalScrollScope.current + "/value:" + name
    val state = rememberSaveable(*inputs) {
        mutableStateOf(store?.takeRestored(key)?.index?.takeIf { it >= 0 })
    }
    if (store != null) {
        LaunchedEffect(state, store, key) {
            snapshotFlow { state.value }.collect { store.put(key, ScrollPosition(it ?: -1)) }
        }
    }
    return state
}

/**
 * A short optional text, such as a selected id, that a page refresh restores. [default] is the value without a restore.
 * Use a different [name] for each value of one screen. The screen must check the restored value.
 */
@Composable
fun rememberPersistentText(name: String, default: String?): MutableState<String?> {
    val store = LocalScrollPositionStore.current
    val key = LocalScrollScope.current + "/text:" + name
    val state = remember(store, key) {
        // A saved text starts with "+". A saved "no value" is "-".
        val restored = store?.takeRestoredText(key)
        mutableStateOf(
            when {
                restored == null -> default
                restored.startsWith("+") -> restored.drop(1)
                else -> null
            },
        )
    }
    if (store != null) {
        LaunchedEffect(state, store, key) {
            snapshotFlow { state.value }.collect { store.putText(key, it?.let { text -> "+$text" } ?: "-") }
        }
    }
    return state
}
