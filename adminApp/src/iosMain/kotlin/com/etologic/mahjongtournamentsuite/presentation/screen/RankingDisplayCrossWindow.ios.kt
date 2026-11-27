package com.etologic.mahjongtournamentsuite.presentation.screen

/** The windows of this app share one process, so [RankingDisplayLink] already reaches them all. */
internal actual object RankingDisplayCrossWindow {
    actual fun post(message: String) = Unit

    actual fun listen(onMessage: (String) -> Unit) = Unit
}

actual val rankingControlsInSeparateScreen: Boolean = false
