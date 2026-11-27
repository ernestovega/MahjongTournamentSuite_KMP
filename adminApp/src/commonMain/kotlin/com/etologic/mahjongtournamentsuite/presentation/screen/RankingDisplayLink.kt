package com.etologic.mahjongtournamentsuite.presentation.screen

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/*
 * The projected ranking and its control screen (Ernesto, 2026-11-27). The room sees the ranking always as it plays,
 * with no buttons; the organizer runs it from "Ranking control" in another window or tab of the same computer.
 * Nothing goes through the server: on the desktop both windows share this object, and on the web the tabs talk through
 * a BroadcastChannel ([RankingDisplayCrossWindow]).
 */

const val DEFAULT_ROWS_PER_SCROLL = 20
const val MIN_ROWS_PER_SCROLL = 16
const val MAX_ROWS_PER_SCROLL = 50
const val DEFAULT_INTERVAL_SECONDS = 7
const val MIN_INTERVAL_SECONDS = 1

/** What the control screen sets. The projected ranking follows it. */
@Serializable
data class RankingDisplayControl(
    val playing: Boolean = true,
    val rowsPerScroll: Int = DEFAULT_ROWS_PER_SCROLL,
    val intervalSeconds: Int = DEFAULT_INTERVAL_SECONDS,
    val showTopThree: Boolean = false,
    /** How many places of the hidden top three each list shows, from the third up, by page key. */
    val revealed: Map<String, Int> = emptyMap(),
    /** While the public cannot see the results, the room sees the ranking only after the organizer allows it. */
    val showHiddenResults: Boolean = false,
    val showOrganizerLogo: Boolean = true,
    val showEmaLogo: Boolean = true,
    /** Null: the theme of the computer. */
    val dark: Boolean? = null,
    /** The list the organizer asked to show: the projection goes to it each time [pageRequest] grows. */
    val pageKey: String? = null,
    val pageRequest: Int = 0,
) {
    /** The place (3, 2 or 1) that the next reveal of [page] shows, or null when its top three shows. */
    fun nextReveal(page: RankingDisplayPage): Int? {
        if (showTopThree) return null
        val shown = (revealed[page.key] ?: 0).coerceIn(0, 3)
        return minOf(page.podium, 3 - shown).takeIf { it > 0 }
    }

    /** Shows one more place of [pageKey]: the projection goes to that list, stops paging and shows its top. */
    fun reveal(pageKey: String): RankingDisplayControl = copy(
        revealed = revealed + (pageKey to ((revealed[pageKey] ?: 0) + 1).coerceAtMost(3)),
        playing = false,
        pageKey = pageKey,
        pageRequest = pageRequest + 1,
    )

    fun showPage(pageKey: String): RankingDisplayControl = copy(pageKey = pageKey, pageRequest = pageRequest + 1)

    fun withShowTopThree(show: Boolean): RankingDisplayControl = copy(showTopThree = show, revealed = emptyMap())
}

/** One list of the projected ranking, as the control screen sees it. */
@Serializable
data class RankingDisplayPage(val key: String, val title: String, val podium: Int)

/** What the projected ranking shows now. The control screen reads it. */
@Serializable
data class RankingDisplayStatus(
    val pages: List<RankingDisplayPage> = emptyList(),
    val pageIndex: Int = 0,
    val remainingSeconds: Int = 0,
    val maxRowsPerScroll: Int = MAX_ROWS_PER_SCROLL,
    /** The tournament hides its results now, so the room needs [RankingDisplayControl.showHiddenResults]. */
    val resultsHidden: Boolean = false,
    val loading: Boolean = false,
)

@Serializable
internal data class RankingDisplayMessage(
    val tournamentId: String,
    val control: RankingDisplayControl? = null,
    val status: RankingDisplayStatus? = null,
    /** A projection that closed: the control screen says so. */
    val closed: Boolean = false,
    /** A window that opened and asks the others for what they hold. */
    val hello: Boolean = false,
)

/** Carries the messages to the other windows or tabs of this computer that run the app. */
internal expect object RankingDisplayCrossWindow {
    fun post(message: String)
    fun listen(onMessage: (String) -> Unit)
}

/**
 * Whether the projected ranking leaves its controls to the control screen. On Android and iOS the ranking opens over
 * the app, so it keeps its own controls there.
 */
expect val rankingControlsInSeparateScreen: Boolean

object RankingDisplayLink {
    private val json = Json { ignoreUnknownKeys = true }
    private val controls = MutableStateFlow<Map<String, RankingDisplayControl>>(emptyMap())
    private val statuses = MutableStateFlow<Map<String, RankingDisplayStatus>>(emptyMap())
    private var listening = false

    val control: StateFlow<Map<String, RankingDisplayControl>> get() = start().let { controls.asStateFlow() }
    val status: StateFlow<Map<String, RankingDisplayStatus>> get() = start().let { statuses.asStateFlow() }

    fun control(tournamentId: String): RankingDisplayControl = control.value[tournamentId] ?: RankingDisplayControl()

    fun setControl(tournamentId: String, control: RankingDisplayControl) {
        start()
        controls.update { it + (tournamentId to control) }
        send(RankingDisplayMessage(tournamentId, control = control))
    }

    fun updateControl(tournamentId: String, change: (RankingDisplayControl) -> RankingDisplayControl) = setControl(tournamentId, change(control(tournamentId)))

    fun setStatus(tournamentId: String, status: RankingDisplayStatus) {
        start()
        if (statuses.value[tournamentId] == status) return
        statuses.update { it + (tournamentId to status) }
        send(RankingDisplayMessage(tournamentId, status = status))
    }

    fun closeStatus(tournamentId: String) {
        statuses.update { it - tournamentId }
        send(RankingDisplayMessage(tournamentId, closed = true))
    }

    /** A window that opens asks the others for the control and the status of [tournamentId]. */
    fun hello(tournamentId: String) {
        start()
        send(RankingDisplayMessage(tournamentId, hello = true))
    }

    /** Forgets everything, between tests. */
    internal fun reset() {
        controls.value = emptyMap()
        statuses.value = emptyMap()
    }

    internal fun receive(text: String) {
        val message = runCatching { json.decodeFromString<RankingDisplayMessage>(text) }.getOrNull() ?: return
        val id = message.tournamentId
        message.control?.let { control -> controls.update { it + (id to control) } }
        message.status?.let { status -> statuses.update { it + (id to status) } }
        if (message.closed) statuses.update { it - id }
        if (message.hello) {
            controls.value[id]?.let { send(RankingDisplayMessage(id, control = it)) }
            statuses.value[id]?.let { send(RankingDisplayMessage(id, status = it)) }
        }
    }

    private fun send(message: RankingDisplayMessage) = RankingDisplayCrossWindow.post(json.encodeToString(message))

    private fun start() {
        if (listening) return
        listening = true
        RankingDisplayCrossWindow.listen(::receive)
    }
}
