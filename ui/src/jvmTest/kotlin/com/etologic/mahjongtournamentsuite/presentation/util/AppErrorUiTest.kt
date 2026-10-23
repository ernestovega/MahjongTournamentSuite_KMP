package com.etologic.mahjongtournamentsuite.presentation.util

import com.etologic.mahjongtournamentsuite.domain.model.AppError
import mahjongtournamentsuite.ui.generated.resources.Res
import mahjongtournamentsuite.ui.generated.resources.error_account_disabled
import mahjongtournamentsuite.ui.generated.resources.error_credentials_wrong
import mahjongtournamentsuite.ui.generated.resources.error_generic
import mahjongtournamentsuite.ui.generated.resources.error_network
import mahjongtournamentsuite.ui.generated.resources.error_not_linked_to_player
import mahjongtournamentsuite.ui.generated.resources.error_permission_denied
import mahjongtournamentsuite.ui.generated.resources.error_schedule_generation
import mahjongtournamentsuite.ui.generated.resources.error_session_expired
import mahjongtournamentsuite.ui.generated.resources.error_timeout
import mahjongtournamentsuite.ui.generated.resources.error_too_many_attempts
import mahjongtournamentsuite.ui.generated.resources.error_tournament_not_found
import kotlin.test.Test
import kotlin.test.assertEquals

class AppErrorUiTest {
    private fun message(raw: String) = AppError.Unexpected(raw).toUiText()

    @Test
    fun networkErrorHasItsOwnMessage() {
        assertEquals(UiText.of(Res.string.error_network), AppError.Network.toUiText())
    }

    @Test
    fun wrongCredentialsAreExplained() {
        assertEquals(
            UiText.of(Res.string.error_credentials_wrong),
            message("""Backend error 401: {"error":"unauthenticated","message":"INVALID_LOGIN_CREDENTIALS"}"""),
        )
    }

    @Test
    fun disabledAccountIsExplainedBeforeThePermissionMessage() {
        assertEquals(
            UiText.of(Res.string.error_account_disabled),
            message("""Backend error 403: {"error":"forbidden","message":"Forbidden: this account is disabled"}"""),
        )
    }

    @Test
    fun forbiddenAloneIsAPermissionMessage() {
        assertEquals(UiText.of(Res.string.error_permission_denied), message("""{"message":"Forbidden"}"""))
    }

    @Test
    fun accountWithoutPlayerIsExplained() {
        assertEquals(
            UiText.of(Res.string.error_not_linked_to_player),
            message("Your account is not linked to a player yet."),
        )
    }

    @Test
    fun unknownErrorsGetTheGenericMessage() {
        assertEquals(UiText.of(Res.string.error_generic), message("boom"))
    }

    @Test
    fun conflictKeepsItsOwnText() {
        assertEquals(UiText.Plain("Changed"), AppError.Conflict("Changed").toUiText())
    }

    @Test
    fun aMissingTournamentIsExplained() {
        assertEquals(UiText.of(Res.string.error_tournament_not_found), message("Tournament not found"))
    }

    @Test
    fun aFailedScheduleIsExplained() {
        assertEquals(
            UiText.of(Res.string.error_schedule_generation),
            message("Unable to generate rounds/tables locally with the current constraints. Try fewer rounds/players and retry."),
        )
    }

    @Test
    fun sessionTimeAndRateErrorsAreExplained() {
        assertEquals(UiText.of(Res.string.error_session_expired), message("""{"message":"Unauthenticated"}"""))
        assertEquals(UiText.of(Res.string.error_timeout), message("Request timed out"))
        assertEquals(UiText.of(Res.string.error_too_many_attempts), message("""{"message":"TOO_MANY_ATTEMPTS_TRY_LATER"}"""))
    }

    @Test
    fun aNullMessageGivesTheGenericText() {
        assertEquals(UiText.of(Res.string.error_generic), AppError.Unexpected(null).toUiText())
    }
}
