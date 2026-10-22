package com.etologic.mahjongtournamentsuite.presentation.util

import com.etologic.mahjongtournamentsuite.domain.model.AppError
import kotlin.test.Test
import kotlin.test.assertEquals

class AppErrorUiTest {
    private fun message(raw: String) = AppError.Unexpected(raw).toUiMessage()

    @Test
    fun networkErrorHasItsOwnMessage() {
        assertEquals("Network request failed.", AppError.Network.toUiMessage())
    }

    @Test
    fun wrongCredentialsAreExplained() {
        assertEquals("The email or password is incorrect.", message("""Backend error 401: {"error":"unauthenticated","message":"INVALID_LOGIN_CREDENTIALS"}"""))
    }

    @Test
    fun disabledAccountIsExplainedBeforeThePermissionMessage() {
        assertEquals(
            "This account is disabled. Ask an administrator for help.",
            message("""Backend error 403: {"error":"forbidden","message":"Forbidden: this account is disabled"}"""),
        )
    }

    @Test
    fun forbiddenAloneIsAPermissionMessage() {
        assertEquals("You do not have permission to perform this action.", message("""{"message":"Forbidden"}"""))
    }

    @Test
    fun accountWithoutPlayerIsExplained() {
        assertEquals(
            "Your account is not linked to a player yet. Ask the organizers to link your EMA number.",
            message("Your account is not linked to a player yet."),
        )
    }

    @Test
    fun unknownErrorsGetTheGenericMessage() {
        assertEquals("Something went wrong. Please try again.", message("boom"))
    }
}
