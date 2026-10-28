package com.etologic.mahjongtournamentsuite.presentation.util

import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.ImportFileErrorCode
import mahjongtournamentsuite.ui.generated.resources.Res
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
import org.jetbrains.compose.resources.StringResource

/** Gives the text for the screen. Call [asString] on it while the screen draws. */
fun AppError.toUiText(): UiText = when (this) {
    AppError.Network -> UiText.of(Res.string.error_network)
    is AppError.Conflict -> UiText.Plain(message)
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
    is AppError.Unexpected -> UiText.of(message.toFriendlyErrorResource())
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
