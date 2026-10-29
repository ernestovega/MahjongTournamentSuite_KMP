package com.etologic.mahjongtournamentsuite.domain.telemetry

import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.GlobalUserRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TelemetryEventTest {
    @Test
    fun screenNameKeepsTheClassAndDropsTheIds() {
        val telemetry = RecordingTelemetry()
        telemetry.screenFromRoute("com.etologic.mahjongtournamentsuite.presentation.TournamentRoute/{tournamentId}")
        telemetry.screenFromRoute("com.etologic.mahjongtournamentsuite.presentation.StatisticsRoute?tournamentId={tournamentId}")
        telemetry.screenFromRoute("com.etologic.mahjongtournamentsuite.presentation.TournamentsRoute")
        telemetry.screenFromRoute(null)
        assertEquals(listOf("Tournament", "Statistics", "Tournaments"), telemetry.screens)
    }

    @Test
    fun successCountsTheEventAndFailureDoesNot() {
        val telemetry = RecordingTelemetry()
        AppResult.Success(1).track(telemetry, TelemetryEvent.LOGIN)
        AppResult.Failure(AppError.Network).track(telemetry, TelemetryEvent.LOGIN)
        assertEquals(listOf(TelemetryEvent.LOGIN), telemetry.events.map { it.first })
    }

    @Test
    fun strongestRoleWins() {
        assertEquals("admin", setOf(GlobalUserRole.EDITOR, GlobalUserRole.ADMIN).telemetryRole())
        assertEquals("editor", setOf(GlobalUserRole.EDITOR, GlobalUserRole.PLAYER).telemetryRole())
        assertTrue(setOf(GlobalUserRole.PLAYER).telemetryRole() == "player")
    }
}
