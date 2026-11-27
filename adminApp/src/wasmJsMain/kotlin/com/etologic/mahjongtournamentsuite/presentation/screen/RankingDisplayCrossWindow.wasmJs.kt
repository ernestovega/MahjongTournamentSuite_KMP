package com.etologic.mahjongtournamentsuite.presentation.screen

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun(
    """(message) => {
        if (typeof BroadcastChannel === 'undefined') return;
        if (!globalThis.__mtsRankingChannel) globalThis.__mtsRankingChannel = new BroadcastChannel('mts-ranking-display');
        globalThis.__mtsRankingChannel.postMessage(message);
    }""",
)
private external fun postRankingMessage(message: String)

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun(
    """(onMessage) => {
        if (typeof BroadcastChannel === 'undefined') return;
        if (!globalThis.__mtsRankingChannel) globalThis.__mtsRankingChannel = new BroadcastChannel('mts-ranking-display');
        globalThis.__mtsRankingChannel.addEventListener('message', (event) => {
            if (typeof event.data === 'string') onMessage(event.data);
        });
    }""",
)
private external fun listenRankingMessages(onMessage: (String) -> Unit)

/** The projected ranking opens in another tab: the tabs of the app talk through a BroadcastChannel of the browser. */
internal actual object RankingDisplayCrossWindow {
    actual fun post(message: String) = postRankingMessage(message)

    actual fun listen(onMessage: (String) -> Unit) = listenRankingMessages(onMessage)
}

actual val rankingControlsInSeparateScreen: Boolean = true
