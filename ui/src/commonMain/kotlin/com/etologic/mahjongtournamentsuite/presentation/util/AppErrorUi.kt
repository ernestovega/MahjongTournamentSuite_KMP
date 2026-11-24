package com.etologic.mahjongtournamentsuite.presentation.util

import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.ConflictCode
import com.etologic.mahjongtournamentsuite.domain.model.ImportFileErrorCode
import com.etologic.mahjongtournamentsuite.domain.model.RegistrationErrorCode
import mahjongtournamentsuite.ui.generated.resources.Res
import mahjongtournamentsuite.ui.generated.resources.conflict_account_has_other_ema
import mahjongtournamentsuite.ui.generated.resources.conflict_all_hands_done
import mahjongtournamentsuite.ui.generated.resources.conflict_assignments_missing
import mahjongtournamentsuite.ui.generated.resources.conflict_clock_not_started
import mahjongtournamentsuite.ui.generated.resources.conflict_duplicate_tournament
import mahjongtournamentsuite.ui.generated.resources.conflict_ema_already_in_tournament
import mahjongtournamentsuite.ui.generated.resources.conflict_ema_id_taken
import mahjongtournamentsuite.ui.generated.resources.conflict_ema_players_gone
import mahjongtournamentsuite.ui.generated.resources.conflict_email_in_use
import mahjongtournamentsuite.ui.generated.resources.conflict_hand_entry_closed
import mahjongtournamentsuite.ui.generated.resources.conflict_no_table_in_round
import mahjongtournamentsuite.ui.generated.resources.conflict_no_tables
import mahjongtournamentsuite.ui.generated.resources.conflict_not_assigned
import mahjongtournamentsuite.ui.generated.resources.conflict_player_in_two_teams
import mahjongtournamentsuite.ui.generated.resources.conflict_request_resolved
import mahjongtournamentsuite.ui.generated.resources.conflict_round_not_started
import mahjongtournamentsuite.ui.generated.resources.conflict_round_time_over
import mahjongtournamentsuite.ui.generated.resources.conflict_seats_closed
import mahjongtournamentsuite.ui.generated.resources.conflict_seats_locked
import mahjongtournamentsuite.ui.generated.resources.conflict_seats_not_assigned
import mahjongtournamentsuite.ui.generated.resources.conflict_seats_not_open
import mahjongtournamentsuite.ui.generated.resources.conflict_table_changed
import mahjongtournamentsuite.ui.generated.resources.conflict_table_changed_hand
import mahjongtournamentsuite.ui.generated.resources.conflict_table_changed_penalty
import mahjongtournamentsuite.ui.generated.resources.conflict_table_changed_seats
import mahjongtournamentsuite.ui.generated.resources.conflict_table_changed_totals
import mahjongtournamentsuite.ui.generated.resources.conflict_table_completed
import mahjongtournamentsuite.ui.generated.resources.conflict_team_players_locked
import mahjongtournamentsuite.ui.generated.resources.conflict_team_slots_mismatch
import mahjongtournamentsuite.ui.generated.resources.conflict_too_many_penalties
import mahjongtournamentsuite.ui.generated.resources.conflict_totals_only
import mahjongtournamentsuite.ui.generated.resources.conflict_tournament_gone
import mahjongtournamentsuite.ui.generated.resources.error_account_disabled
import mahjongtournamentsuite.ui.generated.resources.error_credentials_missing
import mahjongtournamentsuite.ui.generated.resources.error_credentials_wrong
import mahjongtournamentsuite.ui.generated.resources.error_email_invalid
import mahjongtournamentsuite.ui.generated.resources.error_generic
import mahjongtournamentsuite.ui.generated.resources.error_network
import mahjongtournamentsuite.ui.generated.resources.error_not_linked_to_player
import mahjongtournamentsuite.ui.generated.resources.error_permission_denied
import mahjongtournamentsuite.ui.generated.resources.error_schedule_generation
import mahjongtournamentsuite.ui.generated.resources.error_session_expired
import mahjongtournamentsuite.ui.generated.resources.error_tournament_not_found
import mahjongtournamentsuite.ui.generated.resources.error_timeout
import mahjongtournamentsuite.ui.generated.resources.error_too_many_attempts
import mahjongtournamentsuite.ui.generated.resources.import_file_not_excel
import mahjongtournamentsuite.ui.generated.resources.import_file_too_large
import mahjongtournamentsuite.ui.generated.resources.import_file_no_sheet
import mahjongtournamentsuite.ui.generated.resources.import_file_sheet_too_large
import mahjongtournamentsuite.ui.generated.resources.import_file_empty
import mahjongtournamentsuite.ui.generated.resources.import_file_missing_columns
import mahjongtournamentsuite.ui.generated.resources.import_file_no_rows
import mahjongtournamentsuite.ui.generated.resources.import_file_too_many_rows
import mahjongtournamentsuite.ui.generated.resources.import_file_tournament_started
import mahjongtournamentsuite.ui.generated.resources.import_file_has_errors
import mahjongtournamentsuite.ui.generated.resources.registration_error_already_in_team
import mahjongtournamentsuite.ui.generated.resources.registration_error_already_registered
import mahjongtournamentsuite.ui.generated.resources.registration_error_bad_assignments
import mahjongtournamentsuite.ui.generated.resources.registration_error_bad_draw
import mahjongtournamentsuite.ui.generated.resources.registration_error_bad_player_count
import mahjongtournamentsuite.ui.generated.resources.registration_error_consent_required
import mahjongtournamentsuite.ui.generated.resources.registration_error_draw_made
import mahjongtournamentsuite.ui.generated.resources.registration_error_bad_removed_slots
import mahjongtournamentsuite.ui.generated.resources.registration_error_bad_waiting
import mahjongtournamentsuite.ui.generated.resources.registration_error_draw_pending
import mahjongtournamentsuite.ui.generated.resources.registration_error_not_waiting
import mahjongtournamentsuite.ui.generated.resources.registration_error_partial_team
import mahjongtournamentsuite.ui.generated.resources.registration_error_same_player_count
import mahjongtournamentsuite.ui.generated.resources.registration_error_tournament_completed
import mahjongtournamentsuite.ui.generated.resources.registration_error_tournament_started
import mahjongtournamentsuite.ui.generated.resources.registration_error_ema_not_found
import mahjongtournamentsuite.ui.generated.resources.registration_error_empty_file
import mahjongtournamentsuite.ui.generated.resources.registration_error_food_too_long
import mahjongtournamentsuite.ui.generated.resources.registration_error_import_problems
import mahjongtournamentsuite.ui.generated.resources.registration_error_incomplete_teams
import mahjongtournamentsuite.ui.generated.resources.registration_error_invalid_close_date
import mahjongtournamentsuite.ui.generated.resources.registration_error_invalid_email
import mahjongtournamentsuite.ui.generated.resources.registration_error_invalid_max_players
import mahjongtournamentsuite.ui.generated.resources.registration_error_invalid_name
import mahjongtournamentsuite.ui.generated.resources.registration_error_invalid_slug
import mahjongtournamentsuite.ui.generated.resources.registration_error_invalid_team_name
import mahjongtournamentsuite.ui.generated.resources.registration_error_link_expired
import mahjongtournamentsuite.ui.generated.resources.registration_error_not_in_team
import mahjongtournamentsuite.ui.generated.resources.registration_error_no_contact
import mahjongtournamentsuite.ui.generated.resources.registration_error_no_ema_column
import mahjongtournamentsuite.ui.generated.resources.registration_error_no_ema_id
import mahjongtournamentsuite.ui.generated.resources.registration_error_no_registrations
import mahjongtournamentsuite.ui.generated.resources.registration_error_no_team
import mahjongtournamentsuite.ui.generated.resources.registration_error_no_teams
import mahjongtournamentsuite.ui.generated.resources.registration_error_pending
import mahjongtournamentsuite.ui.generated.resources.registration_error_closed
import mahjongtournamentsuite.ui.generated.resources.registration_error_not_found
import mahjongtournamentsuite.ui.generated.resources.registration_error_open
import mahjongtournamentsuite.ui.generated.resources.registration_error_slug_taken
import mahjongtournamentsuite.ui.generated.resources.registration_error_team_full
import mahjongtournamentsuite.ui.generated.resources.registration_error_team_name_taken
import mahjongtournamentsuite.ui.generated.resources.registration_error_too_many_rows
import mahjongtournamentsuite.ui.generated.resources.registration_error_use_leave
import org.jetbrains.compose.resources.StringResource

/** Gives the text for the screen. Call [asString] on it while the screen draws. */
fun AppError.toUiText(): UiText = when (this) {
    AppError.Network -> UiText.of(Res.string.error_network)
    is AppError.Conflict -> code?.let { conflictText(it, args) } ?: UiText.Plain(message)
    is AppError.ImportFile -> when (code) {
        ImportFileErrorCode.NOT_EXCEL -> UiText.of(Res.string.import_file_not_excel)
        ImportFileErrorCode.TOO_LARGE -> UiText.of(Res.string.import_file_too_large)
        ImportFileErrorCode.NO_SHEET -> UiText.of(Res.string.import_file_no_sheet)
        ImportFileErrorCode.SHEET_TOO_LARGE -> UiText.of(Res.string.import_file_sheet_too_large)
        ImportFileErrorCode.EMPTY -> UiText.of(Res.string.import_file_empty)
        ImportFileErrorCode.MISSING_COLUMNS -> UiText.of(Res.string.import_file_missing_columns, args.firstOrNull().orEmpty())
        ImportFileErrorCode.NO_ROWS -> UiText.of(Res.string.import_file_no_rows)
        ImportFileErrorCode.TOO_MANY_ROWS -> UiText.of(Res.string.import_file_too_many_rows, args.firstOrNull().orEmpty())
        ImportFileErrorCode.TOURNAMENT_STARTED -> UiText.of(Res.string.import_file_tournament_started)
        ImportFileErrorCode.FILE_HAS_ERRORS -> UiText.of(Res.string.import_file_has_errors)
    }
    is AppError.Registration -> when (code) {
        RegistrationErrorCode.ALREADY_IN_TEAM -> UiText.of(Res.string.registration_error_already_in_team)
        RegistrationErrorCode.ALREADY_REGISTERED -> UiText.of(Res.string.registration_error_already_registered)
        RegistrationErrorCode.BAD_ASSIGNMENTS -> UiText.of(Res.string.registration_error_bad_assignments)
        RegistrationErrorCode.BAD_DRAW -> UiText.of(Res.string.registration_error_bad_draw)
        RegistrationErrorCode.BAD_PLAYER_COUNT -> UiText.of(Res.string.registration_error_bad_player_count, args.firstOrNull().orEmpty())
        RegistrationErrorCode.BAD_REMOVED_SLOTS -> UiText.of(Res.string.registration_error_bad_removed_slots)
        RegistrationErrorCode.BAD_WAITING -> UiText.of(Res.string.registration_error_bad_waiting)
        RegistrationErrorCode.CONSENT_REQUIRED -> UiText.of(Res.string.registration_error_consent_required)
        RegistrationErrorCode.DRAW_MADE -> UiText.of(Res.string.registration_error_draw_made)
        RegistrationErrorCode.DRAW_PENDING -> UiText.of(Res.string.registration_error_draw_pending)
        RegistrationErrorCode.EMA_NOT_FOUND -> UiText.of(Res.string.registration_error_ema_not_found)
        RegistrationErrorCode.EMPTY_FILE -> UiText.of(Res.string.registration_error_empty_file)
        RegistrationErrorCode.FOOD_TOO_LONG -> UiText.of(Res.string.registration_error_food_too_long)
        RegistrationErrorCode.IMPORT_PROBLEMS -> UiText.of(Res.string.registration_error_import_problems, args.joinToString(", "))
        RegistrationErrorCode.INCOMPLETE_TEAMS -> UiText.of(Res.string.registration_error_incomplete_teams, args.joinToString(", "))
        RegistrationErrorCode.INVALID_CLOSE_DATE -> UiText.of(Res.string.registration_error_invalid_close_date)
        RegistrationErrorCode.INVALID_EMAIL -> UiText.of(Res.string.registration_error_invalid_email)
        RegistrationErrorCode.INVALID_MAX_PLAYERS -> UiText.of(Res.string.registration_error_invalid_max_players)
        RegistrationErrorCode.INVALID_NAME -> UiText.of(Res.string.registration_error_invalid_name)
        RegistrationErrorCode.INVALID_SLUG -> UiText.of(Res.string.registration_error_invalid_slug)
        RegistrationErrorCode.INVALID_TEAM_NAME -> UiText.of(Res.string.registration_error_invalid_team_name)
        RegistrationErrorCode.LINK_EXPIRED -> UiText.of(Res.string.registration_error_link_expired)
        RegistrationErrorCode.NOT_IN_TEAM -> UiText.of(Res.string.registration_error_not_in_team)
        RegistrationErrorCode.NO_CONTACT -> UiText.of(Res.string.registration_error_no_contact)
        RegistrationErrorCode.NO_EMA_COLUMN -> UiText.of(Res.string.registration_error_no_ema_column)
        RegistrationErrorCode.NO_EMA_ID -> UiText.of(Res.string.registration_error_no_ema_id)
        RegistrationErrorCode.NO_REGISTRATIONS -> UiText.of(Res.string.registration_error_no_registrations)
        RegistrationErrorCode.NO_TEAM -> UiText.of(Res.string.registration_error_no_team)
        RegistrationErrorCode.NO_TEAMS -> UiText.of(Res.string.registration_error_no_teams)
        RegistrationErrorCode.NOT_WAITING -> UiText.of(Res.string.registration_error_not_waiting)
        RegistrationErrorCode.PARTIAL_TEAM -> UiText.of(Res.string.registration_error_partial_team)
        RegistrationErrorCode.PENDING -> UiText.of(Res.string.registration_error_pending)
        RegistrationErrorCode.REGISTRATION_CLOSED -> UiText.of(Res.string.registration_error_closed)
        RegistrationErrorCode.REGISTRATION_NOT_FOUND -> UiText.of(Res.string.registration_error_not_found)
        RegistrationErrorCode.REGISTRATION_OPEN -> UiText.of(Res.string.registration_error_open)
        RegistrationErrorCode.SAME_PLAYER_COUNT -> UiText.of(Res.string.registration_error_same_player_count)
        RegistrationErrorCode.SLUG_TAKEN -> UiText.of(Res.string.registration_error_slug_taken)
        RegistrationErrorCode.TEAM_FULL -> UiText.of(Res.string.registration_error_team_full)
        RegistrationErrorCode.TEAM_NAME_TAKEN -> UiText.of(Res.string.registration_error_team_name_taken)
        RegistrationErrorCode.TOO_MANY_ROWS -> UiText.of(Res.string.registration_error_too_many_rows)
        RegistrationErrorCode.TOURNAMENT_COMPLETED -> UiText.of(Res.string.registration_error_tournament_completed)
        RegistrationErrorCode.TOURNAMENT_STARTED -> UiText.of(Res.string.registration_error_tournament_started)
        RegistrationErrorCode.USE_LEAVE -> UiText.of(Res.string.registration_error_use_leave)
    }
    is AppError.Unexpected -> UiText.of(message.toFriendlyErrorResource())
}

/** The translated reason of a refused change. */
private fun conflictText(code: ConflictCode, args: List<String>): UiText = when (code) {
    ConflictCode.ACCOUNT_HAS_OTHER_EMA -> UiText.of(Res.string.conflict_account_has_other_ema)
    ConflictCode.ALL_HANDS_DONE -> UiText.of(Res.string.conflict_all_hands_done)
    ConflictCode.ASSIGNMENTS_MISSING -> UiText.of(Res.string.conflict_assignments_missing, args.firstOrNull().orEmpty())
    ConflictCode.CLOCK_NOT_STARTED -> UiText.of(Res.string.conflict_clock_not_started)
    ConflictCode.DUPLICATE_TOURNAMENT -> UiText.of(Res.string.conflict_duplicate_tournament)
    ConflictCode.EMA_ALREADY_IN_TOURNAMENT -> UiText.of(Res.string.conflict_ema_already_in_tournament)
    ConflictCode.EMA_ID_TAKEN -> UiText.of(Res.string.conflict_ema_id_taken)
    ConflictCode.EMA_PLAYERS_GONE -> UiText.of(Res.string.conflict_ema_players_gone)
    ConflictCode.EMAIL_IN_USE -> UiText.of(Res.string.conflict_email_in_use)
    ConflictCode.HAND_ENTRY_CLOSED -> UiText.of(Res.string.conflict_hand_entry_closed)
    ConflictCode.NO_TABLE_IN_ROUND -> UiText.of(Res.string.conflict_no_table_in_round)
    ConflictCode.NO_TABLES -> UiText.of(Res.string.conflict_no_tables)
    ConflictCode.NOT_ASSIGNED -> UiText.of(Res.string.conflict_not_assigned)
    ConflictCode.PLAYER_IN_TWO_TEAMS -> UiText.of(Res.string.conflict_player_in_two_teams)
    ConflictCode.REQUEST_RESOLVED -> UiText.of(Res.string.conflict_request_resolved)
    ConflictCode.ROUND_NOT_STARTED -> UiText.of(Res.string.conflict_round_not_started)
    ConflictCode.ROUND_TIME_OVER -> UiText.of(Res.string.conflict_round_time_over)
    ConflictCode.SEATS_CLOSED -> UiText.of(Res.string.conflict_seats_closed)
    ConflictCode.SEATS_LOCKED -> UiText.of(Res.string.conflict_seats_locked)
    ConflictCode.SEATS_NOT_ASSIGNED -> UiText.of(Res.string.conflict_seats_not_assigned)
    ConflictCode.SEATS_NOT_OPEN -> UiText.of(Res.string.conflict_seats_not_open)
    ConflictCode.TABLE_CHANGED -> UiText.of(Res.string.conflict_table_changed)
    ConflictCode.TABLE_CHANGED_HAND -> UiText.of(Res.string.conflict_table_changed_hand)
    ConflictCode.TABLE_CHANGED_PENALTY -> UiText.of(Res.string.conflict_table_changed_penalty)
    ConflictCode.TABLE_CHANGED_SEATS -> UiText.of(Res.string.conflict_table_changed_seats)
    ConflictCode.TABLE_CHANGED_TOTALS -> UiText.of(Res.string.conflict_table_changed_totals)
    ConflictCode.TABLE_COMPLETED -> UiText.of(Res.string.conflict_table_completed)
    ConflictCode.TEAM_PLAYERS_LOCKED -> UiText.of(Res.string.conflict_team_players_locked)
    ConflictCode.TEAM_SLOTS_MISMATCH -> UiText.of(Res.string.conflict_team_slots_mismatch)
    ConflictCode.TOO_MANY_PENALTIES -> UiText.of(Res.string.conflict_too_many_penalties)
    ConflictCode.TOTALS_ONLY -> UiText.of(Res.string.conflict_totals_only)
    ConflictCode.TOURNAMENT_GONE -> UiText.of(Res.string.conflict_tournament_gone)
    ConflictCode.NO_TEAMS -> UiText.of(Res.string.registration_error_no_teams)
    ConflictCode.TEAM_NAME_TAKEN -> UiText.of(Res.string.registration_error_team_name_taken)
    ConflictCode.TOURNAMENT_COMPLETED -> UiText.of(Res.string.registration_error_tournament_completed)
}

private fun String?.toFriendlyErrorResource(): StringResource {
    val raw = orEmpty()
    val normalized = raw.lowercase()
    val backendMessage = Regex("\\\"message\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"")
        .find(raw)
        ?.groupValues
        ?.getOrNull(1)
        ?.lowercase()
        .orEmpty()
    val details = "$normalized $backendMessage"

    return when {
        details.contains("missing email") || details.contains("missing identifier") ||
            details.contains("missing password") -> Res.string.error_credentials_missing
        details.contains("invalid_login_credentials") || details.contains("invalid password") ||
            details.contains("email_not_found") || details.contains("user not found") ->
            Res.string.error_credentials_wrong
        details.contains("invalid_email") || details.contains("invalid email") ->
            Res.string.error_email_invalid
        details.contains("too_many_attempts") || details.contains("too many") ->
            Res.string.error_too_many_attempts
        details.contains("not linked to a player") -> Res.string.error_not_linked_to_player
        details.contains("account is disabled") -> Res.string.error_account_disabled
        details.contains("permission denied") || details.contains("forbidden") ->
            Res.string.error_permission_denied
        details.contains("unauthorized") || details.contains("unauthenticated") ->
            Res.string.error_session_expired
        details.contains("timed out") -> Res.string.error_timeout
        details.contains("tournament not found") -> Res.string.error_tournament_not_found
        details.contains("unable to generate") -> Res.string.error_schedule_generation
        else -> Res.string.error_generic
    }
}
