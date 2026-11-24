package com.etologic.mahjongtournamentsuite.domain.model

/**
 * Why the server refused a change with a 409. The server sends it in `details.code`, and the screen writes the reason
 * in the language of the user instead of the English message of the server.
 */
enum class ConflictCode {
    ACCOUNT_HAS_OTHER_EMA,
    ALL_HANDS_DONE,
    ASSIGNMENTS_MISSING,
    CLOCK_NOT_STARTED,
    DUPLICATE_TOURNAMENT,
    EMA_ALREADY_IN_TOURNAMENT,
    EMA_ID_TAKEN,
    EMA_PLAYERS_GONE,
    EMAIL_IN_USE,
    HAND_ENTRY_CLOSED,
    NO_TABLE_IN_ROUND,
    NO_TABLES,
    NO_TEAMS,
    NOT_ASSIGNED,
    PLAYER_IN_TWO_TEAMS,
    REQUEST_RESOLVED,
    ROUND_NOT_STARTED,
    ROUND_TIME_OVER,
    SEATS_CLOSED,
    SEATS_LOCKED,
    SEATS_NOT_ASSIGNED,
    SEATS_NOT_OPEN,
    TABLE_CHANGED,
    TABLE_CHANGED_HAND,
    TABLE_CHANGED_PENALTY,
    TABLE_CHANGED_SEATS,
    TABLE_CHANGED_TOTALS,
    TABLE_COMPLETED,
    TEAM_NAME_TAKEN,
    TEAM_PLAYERS_LOCKED,
    TEAM_SLOTS_MISMATCH,
    TOO_MANY_PENALTIES,
    TOTALS_ONLY,
    TOURNAMENT_COMPLETED,
    TOURNAMENT_GONE,
}
