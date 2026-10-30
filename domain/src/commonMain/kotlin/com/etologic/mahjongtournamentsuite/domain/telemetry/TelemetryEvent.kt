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

    /**
     * An administrator or editor tried to change or export something. [TelemetryParam.ACTION] is one word of
     * [TelemetryAction]. [TelemetryParam.OUTCOME] tells if the request worked.
     */
    const val ADMIN_ACTION = "admin_action"

    /** The user opened a dialog. */
    const val DIALOG_VIEW = "dialog_view"

    /** The user opened a tab. */
    const val TAB_VIEW = "tab_view"

    /** How long a screen, a dialog or a tab stayed open. */
    const val VIEW_TIME = "view_time"

    const val HAND_PREVIEWED = "hand_previewed"
    const val HAND_RECORDED = "hand_recorded"
    const val PENALTY_ADDED = "penalty_added"

    /** Every value of this object. A test checks it against the rules of GA4. */
    val all: List<String> = listOf(
        LOGIN,
        LOGOUT,
        PASSWORD_RESET_REQUESTED,
        ACTION_FAILED,
        APP_ERROR,
        APP_CRASH,
        TOURNAMENT_CREATED,
        TOURNAMENT_DELETED,
        TABLE_SAVED,
        PLAYERS_IMPORTED,
        PLAYERS_EXPORTED,
        ACCOUNTS_SENT,
        STATISTICS_VIEWED,
        ADMIN_ACTION,
        DIALOG_VIEW,
        TAB_VIEW,
        VIEW_TIME,
        HAND_PREVIEWED,
        HAND_RECORDED,
        PENALTY_ADDED,
    )
}

object TelemetryParam {
    const val ACTION = "action"
    const val OUTCOME = "outcome"
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

    /** Every value of this object. A test checks it against the rules of GA4. */
    val all: List<String> = listOf(
        ACTION,
        OUTCOME,
        REASON,
        SCOPE,
        FORMAT,
        PLAYERS,
        ROUNDS,
        IS_TEAMS,
        HANDS,
        KIND,
        MESSAGE,
        SCREEN,
        VIEW,
        DIALOG,
        TAB,
        SECONDS,
        REPORT_ID,
    )
}

/** The values of [TelemetryParam.OUTCOME] in `admin_action`. */
object TelemetryOutcome {
    const val OK = "ok"
    const val FAILED = "failed"

    /** Every value of this object. A test checks it against the rules of GA4. */
    val all: List<String> = listOf(
        OK,
        FAILED,
    )
}

/** The values of [TelemetryParam.ACTION] in `admin_action`. One word for each kind of work in the admin app. */
object TelemetryAction {
    const val TOURNAMENT_RENAMED = "tournament_renamed"
    const val TOURNAMENT_SETTINGS_UPDATED = "tournament_settings_updated"
    const val PLAYER_ASSIGNED = "player_assigned"
    const val PLAYER_UNASSIGNED = "player_unassigned"
    const val PLAYER_SUBSTITUTED = "player_substituted"
    const val SUBSTITUTION_CLEARED = "substitution_cleared"
    const val TEAM_UPDATED = "team_updated"
    const val EMAILS_SAVED = "emails_saved"
    const val ID_CARDS_EXPORTED = "id_cards_exported"
    const val ID_LIST_EXPORTED = "id_list_exported"
    const val ID_CARD_PROOF_CREATED = "id_card_proof_created"
    const val EMA_REPORT_EXPORTED = "ema_report_exported"
    const val ACCOUNT_CREATED = "account_created"
    const val ACCOUNT_UPDATED = "account_updated"
    const val ACCOUNT_DISABLED = "account_disabled"
    const val ACCOUNT_ENABLED = "account_enabled"
    const val ACCOUNT_PHOTO_UPDATED = "account_photo_updated"
    const val ACCOUNT_PHOTO_REMOVED = "account_photo_removed"
    const val REGISTRY_PLAYER_CREATED = "registry_player_created"
    const val REGISTRY_PLAYER_UPDATED = "registry_player_updated"
    const val REGISTRY_PHOTO_UPDATED = "registry_photo_updated"

    /** Every value of this object. A test checks it against the rules of GA4. */
    val all: List<String> = listOf(
        TOURNAMENT_RENAMED,
        TOURNAMENT_SETTINGS_UPDATED,
        PLAYER_ASSIGNED,
        PLAYER_UNASSIGNED,
        PLAYER_SUBSTITUTED,
        SUBSTITUTION_CLEARED,
        TEAM_UPDATED,
        EMAILS_SAVED,
        ID_CARDS_EXPORTED,
        ID_LIST_EXPORTED,
        ID_CARD_PROOF_CREATED,
        EMA_REPORT_EXPORTED,
        ACCOUNT_CREATED,
        ACCOUNT_UPDATED,
        ACCOUNT_DISABLED,
        ACCOUNT_ENABLED,
        ACCOUNT_PHOTO_UPDATED,
        ACCOUNT_PHOTO_REMOVED,
        REGISTRY_PLAYER_CREATED,
        REGISTRY_PLAYER_UPDATED,
        REGISTRY_PHOTO_UPDATED,
    )
}

object TelemetryProperty {
    const val APP = "app"
    const val PLATFORM = "platform"
    const val ENVIRONMENT = "env"
    const val ROLE = "role"
    const val LANGUAGE = "language"

    /** Every value of this object. A test checks it against the rules of GA4. */
    val all: List<String> = listOf(
        APP,
        PLATFORM,
        ENVIRONMENT,
        ROLE,
        LANGUAGE,
    )
}

/** Counts [event] when the request worked. A failure is already counted by the log writer of the data module. */
fun <T> AppResult<T>.track(telemetry: Telemetry, event: String, params: Map<String, Any> = emptyMap()): AppResult<T> {
    if (this is AppResult.Success) telemetry.event(event, params)
    return this
}

/**
 * Counts one `admin_action` for every try, with the outcome `ok` or `failed`. The reason of a failure is in the
 * `action_failed` event that the log writer of the data module sends.
 */
fun <T> AppResult<T>.trackAction(telemetry: Telemetry, action: String): AppResult<T> {
    val outcome = if (this is AppResult.Success) TelemetryOutcome.OK else TelemetryOutcome.FAILED
    telemetry.event(TelemetryEvent.ADMIN_ACTION, mapOf(TelemetryParam.ACTION to action, TelemetryParam.OUTCOME to outcome))
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
