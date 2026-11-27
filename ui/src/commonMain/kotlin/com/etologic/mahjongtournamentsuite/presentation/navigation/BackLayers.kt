package com.etologic.mahjongtournamentsuite.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.currentCompositeKeyHash
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavBackStackEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * A step back inside a screen, that the back takes before it leaves the screen: a tab other than the first one of the
 * start screen, a team open in its tab, a dialog. [owner] is the id of the entry of the back stack that shows it, or
 * null for one over every screen, like a dialog outside the `NavHost`, which goes after the top screen.
 */
class BackLayer internal constructor(val owner: String?, internal val key: Int, onBack: () -> Unit) {
    internal var onBack: () -> Unit = onBack
}

/**
 * The back layers of the page. On the web each one is an entry of the browser history right after the entry of its
 * screen, so the browser back and the back gesture of a phone close it before they leave the screen, and before they
 * leave the app.
 */
class BackLayers {
    private val mutable = MutableStateFlow<List<BackLayer>>(emptyList())

    /** The layers in the order they opened. */
    val layers: StateFlow<List<BackLayer>> = mutable.asStateFlow()

    /** Adds the layer [key] of [owner], or gives a new [onBack] to the one that is already there. */
    internal fun put(owner: String?, key: Int, onBack: () -> Unit) {
        val existing = mutable.value.firstOrNull { it.owner == owner && it.key == key }
        if (existing != null) existing.onBack = onBack else mutable.update { it + BackLayer(owner, key, onBack) }
    }

    internal fun remove(owner: String?, key: Int) = mutable.update { layers -> layers.filterNot { it.owner == owner && it.key == key } }

    /** Forgets the layers of the screens that left the back stack, which has the entries [owners]. */
    internal fun keepOnly(owners: Set<String>) {
        fun kept(layer: BackLayer) = layer.owner == null || layer.owner in owners
        if (!mutable.value.all(::kept)) mutable.update { layers -> layers.filter(::kept) }
    }

    companion object {
        /** The ones of the page. The web has one app, with one back stack, on each page. */
        val page = BackLayers()
    }
}

/**
 * While [enabled], the back runs [onBack] instead of leaving the screen. Inside a screen of a `NavHost` the screen
 * keeps its layer while another screen covers it, so the back returns to it with the layer. Outside one, the layer
 * goes over the top screen.
 */
@Composable
fun BackLayerEffect(enabled: Boolean, onBack: () -> Unit, layers: BackLayers = BackLayers.page) {
    val entry = LocalLifecycleOwner.current as? NavBackStackEntry
    val owner = entry?.id
    val key = currentCompositeKeyHash
    val currentOnBack by rememberUpdatedState(onBack)
    DisposableEffect(owner, key, enabled, layers) {
        if (enabled) layers.put(owner, key) { currentOnBack() } else layers.remove(owner, key)
        onDispose {
            // When another screen covers this one, its layer stays, so the back returns to the screen with it. The
            // history binding forgets it when the screen leaves the back stack. Anything else closes the layer.
            val state = entry?.lifecycle?.currentState
            val covered = state != null && state != Lifecycle.State.DESTROYED && !state.isAtLeast(Lifecycle.State.RESUMED)
            if (!covered) layers.remove(owner, key)
        }
    }
}
