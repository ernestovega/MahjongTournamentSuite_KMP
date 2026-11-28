package com.etologic.mahjongtournamentsuite.presentation.screen

import com.etologic.mahjongtournamentsuite.domain.model.EmaPlayerDeleteFailure
import com.etologic.mahjongtournamentsuite.domain.model.PasswordResetFailure
import com.etologic.mahjongtournamentsuite.domain.model.UserDeleteFailure
import mahjongtournamentsuite.adminapp.generated.resources.Res
import mahjongtournamentsuite.adminapp.generated.resources.delete_failure_last_admin
import mahjongtournamentsuite.adminapp.generated.resources.delete_failure_self
import mahjongtournamentsuite.adminapp.generated.resources.delete_failure_unknown
import mahjongtournamentsuite.adminapp.generated.resources.ema_player_delete_failure_not_found
import mahjongtournamentsuite.adminapp.generated.resources.ema_player_delete_failure_unknown
import mahjongtournamentsuite.adminapp.generated.resources.reset_failure_disabled
import mahjongtournamentsuite.adminapp.generated.resources.reset_failure_email_failed
import mahjongtournamentsuite.adminapp.generated.resources.reset_failure_no_email
import mahjongtournamentsuite.adminapp.generated.resources.reset_failure_not_found
import mahjongtournamentsuite.adminapp.generated.resources.reset_failure_unknown
import kotlin.test.Test
import kotlin.test.assertEquals

/** Each reason why a bulk delete or a password reset failed has its own text, and an unknown reason the general one. */
class FailureTextsTest {
    @Test
    fun eachUserDeleteFailureHasItsText() {
        assertEquals(Res.string.reset_failure_not_found, UserDeleteFailure.NOT_FOUND.textRes())
        assertEquals(Res.string.delete_failure_self, UserDeleteFailure.SELF.textRes())
        assertEquals(Res.string.delete_failure_last_admin, UserDeleteFailure.LAST_SUPER_ADMIN.textRes())
        assertEquals(Res.string.delete_failure_unknown, UserDeleteFailure.UNKNOWN.textRes())
        assertEquals(Res.string.delete_failure_unknown, (null as UserDeleteFailure?).textRes())
    }

    @Test
    fun eachPasswordResetFailureHasItsText() {
        assertEquals(Res.string.reset_failure_not_found, PasswordResetFailure.NOT_FOUND.textRes())
        assertEquals(Res.string.reset_failure_no_email, PasswordResetFailure.NO_EMAIL.textRes())
        assertEquals(Res.string.reset_failure_disabled, PasswordResetFailure.DISABLED.textRes())
        assertEquals(Res.string.reset_failure_email_failed, PasswordResetFailure.EMAIL_FAILED.textRes())
        assertEquals(Res.string.reset_failure_unknown, PasswordResetFailure.UNKNOWN.textRes())
        assertEquals(Res.string.reset_failure_unknown, (null as PasswordResetFailure?).textRes())
    }

    @Test
    fun eachEmaPlayerDeleteFailureHasItsText() {
        assertEquals(Res.string.ema_player_delete_failure_not_found, EmaPlayerDeleteFailure.NOT_FOUND.textRes())
        assertEquals(Res.string.ema_player_delete_failure_unknown, EmaPlayerDeleteFailure.UNKNOWN.textRes())
        assertEquals(Res.string.ema_player_delete_failure_unknown, (null as EmaPlayerDeleteFailure?).textRes())
    }
}
