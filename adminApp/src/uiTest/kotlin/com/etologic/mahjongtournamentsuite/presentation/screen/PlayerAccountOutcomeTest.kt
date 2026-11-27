package com.etologic.mahjongtournamentsuite.presentation.screen

import com.etologic.mahjongtournamentsuite.domain.model.PlayerAccountActionResult
import com.etologic.mahjongtournamentsuite.domain.model.PlayerAccountFailure
import com.etologic.mahjongtournamentsuite.domain.model.PlayerAccountOutcome
import com.etologic.mahjongtournamentsuite.domain.model.PlayerEmailFailure
import com.etologic.mahjongtournamentsuite.domain.model.PlayerEmailOutcome
import com.etologic.mahjongtournamentsuite.domain.model.PlayerEmailResult
import com.etologic.mahjongtournamentsuite.presentation.util.UiText
import mahjongtournamentsuite.adminapp.generated.resources.Res
import mahjongtournamentsuite.adminapp.generated.resources.failure_unexpected
import mahjongtournamentsuite.adminapp.generated.resources.outcome_invited
import mahjongtournamentsuite.adminapp.generated.resources.outcome_reminded
import kotlin.test.Test
import kotlin.test.assertEquals

/** The sentence of each result of an account action, and each refused email as a failed action. */
class PlayerAccountOutcomeTest {
    @Test
    fun everyFailureHasItsOwnText() {
        val texts = PlayerAccountFailure.entries.map { it.textRes() }

        assertEquals(PlayerAccountFailure.entries.size, texts.toSet().size)
    }

    @Test
    fun aRefusedEmailKeepsItsReasonAndAnUnknownOneIsUnexpected() {
        val reasons = PlayerEmailFailure.entries.associateWith { PlayerEmailResult(1, PlayerEmailOutcome.entries.first(), it).toFailedAction().reason }

        assertEquals(PlayerAccountFailure.PLAYER_NOT_FOUND, reasons[PlayerEmailFailure.PLAYER_NOT_FOUND])
        assertEquals(PlayerAccountFailure.NO_EMA_ID, reasons[PlayerEmailFailure.NO_EMA_ID])
        assertEquals(PlayerAccountFailure.EMAIL_IN_USE, reasons[PlayerEmailFailure.EMAIL_IN_USE])
        assertEquals(PlayerAccountFailure.INVALID_EMAIL, reasons[PlayerEmailFailure.INVALID_EMAIL])
        assertEquals(PlayerAccountFailure.UNEXPECTED, reasons[PlayerEmailFailure.HAS_ACCOUNT])
        assertEquals(PlayerAccountFailure.UNEXPECTED, PlayerEmailResult(1, PlayerEmailOutcome.entries.first(), null).toFailedAction().reason)
    }

    @Test
    fun eachOutcomeSaysWhatHappened() {
        assertEquals(AccountOutcome(UiText.of(Res.string.outcome_invited), isError = false), PlayerAccountActionResult(1, PlayerAccountOutcome.INVITED).toOutcome())
        assertEquals(AccountOutcome(UiText.of(Res.string.outcome_reminded), isError = false), PlayerAccountActionResult(1, PlayerAccountOutcome.REMINDED).toOutcome())
        assertEquals(
            AccountOutcome(UiText.of(Res.string.failure_unexpected), isError = true),
            PlayerAccountActionResult(1, PlayerAccountOutcome.FAILED).toOutcome(),
            "a failure without a reason",
        )
    }
}
