package com.etologic.mahjongtournamentsuite.domain.telemetry

import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.GlobalUserRole

/** The events of both apps. A new event needs a line here, so the list stays small and easy to read in GA4. */
object TelemetryEvent {
    const val LOGIN = "login"
    const val LOGOUT = "logout"
    const val PASSWORD_RESET_REQUESTED = "password_reset_requested"

    /** A request failed. The log writer sends it, with [TelemetryParam.ACTION] and [TelemetryParam.REASON]. */
    const val ACTION_FAILED = "action_failed"

    /** The app handled an error that points to a bug. */
    const val APP_ERROR = "app_error"

    /** The app stopped because of an error that nobody handled. */
    const val APP_CRASH = "app_crash"

    const val TOURNAMENT_CREATED = "tournament_created"
    const val TOURNAMENT_DELETED = "tournament_deleted"
    const val TABLE_SAVED = "table_saved"
    const val PLAYERS_IMPORTED = "players_imported"
    const val PLAYERS_EXPORTED = "players_exported"
    const val ACCOUNTS_SENT = "accounts_sent"
    const val STATISTICS_VIEWED = "statistics_viewed"

    const val HAND_PREVIEWED = "hand_previewed"
    const val HAND_RECORDED = "hand_recorded"
    const val PENALTY_ADDED = "penalty_added"
}

object TelemetryParam {
    const val ACTION = "action"
    const val REASON = "reason"
    const val SCOPE = "scope"
    const val FORMAT = "format"
    const val PLAYERS = "players"
    const val ROUNDS = "rounds"
    const val IS_TEAMS = "is_teams"
    const val HANDS = "hands"
    const val KIND = "kind"
    const val MESSAGE = "message"
}

object TelemetryProperty {
    const val APP = "app"
    const val PLATFORM = "platform"
    const val ENVIRONMENT = "env"
    const val ROLE = "role"
    const val LANGUAGE = "language"
}

/** Counts [event] when the request worked. A failure is already counted by the log writer of the data module. */
fun <T> AppResult<T>.track(telemetry: Telemetry, event: String, params: Map<String, Any> = emptyMap()): AppResult<T> {
    if (this is AppResult.Success) telemetry.event(event, params)
    return this
}

/** The role that GA4 uses to split the users. An account with several roles counts as the strongest one. */
fun Collection<GlobalUserRole>.telemetryRole(): String = when {
    GlobalUserRole.ADMIN in this -> "admin"
    GlobalUserRole.EDITOR in this -> "editor"
    else -> "player"
}

/**
 * Counts a visit when the app opens a screen. [route] is the route of the navigation, for example
 * `com.example.TournamentRoute/{tournamentId}`. The screen name keeps the class name and drops the ids.
 */
fun Telemetry.screenFromRoute(route: String?) {
    val name = route?.substringBefore('/')?.substringBefore('?')?.substringAfterLast('.')?.removeSuffix("Route")
    if (!name.isNullOrBlank()) screen(name)
}
