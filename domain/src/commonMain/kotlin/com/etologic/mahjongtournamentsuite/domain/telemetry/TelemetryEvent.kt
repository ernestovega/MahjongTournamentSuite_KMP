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

    /** The app handled an error that points to a bug. It has a [TelemetryParam.REPORT_ID]. */
    const val APP_ERROR = "app_error"

    /** The app stopped because of an error that nobody handled. It has a [TelemetryParam.REPORT_ID]. */
    const val APP_CRASH = "app_crash"

    const val TOURNAMENT_CREATED = "tournament_created"
    const val TOURNAMENT_DELETED = "tournament_deleted"
    const val TABLE_SAVED = "table_saved"
    const val PLAYERS_IMPORTED = "players_imported"
    const val PLAYERS_EXPORTED = "players_exported"
    const val ACCOUNTS_SENT = "accounts_sent"
    const val STATISTICS_VIEWED = "statistics_viewed"

    /** The user opened a dialog. */
    const val DIALOG_VIEW = "dialog_view"

    /** The user opened a tab. */
    const val TAB_VIEW = "tab_view"

    /** How long a screen, a dialog or a tab stayed open. */
    const val VIEW_TIME = "view_time"

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
    const val SCREEN = "screen"
    const val VIEW = "view"
    const val DIALOG = "dialog"
    const val TAB = "tab"
    const val SECONDS = "seconds"

    /** The id of an error. The same id is in the error log of the API. It joins the log with Analytics. */
    const val REPORT_ID = "report_id"
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

/** The kinds of view that the app measures. The value goes in [TelemetryParam.KIND] of `view_time`. */
object TelemetryViewKind {
    const val SCREEN = "screen"
    const val DIALOG = "dialog"
    const val TAB = "tab"
}
