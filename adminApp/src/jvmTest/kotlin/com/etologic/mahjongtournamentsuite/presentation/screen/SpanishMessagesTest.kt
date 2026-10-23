package com.etologic.mahjongtournamentsuite.presentation.screen

import com.etologic.mahjongtournamentsuite.domain.model.FeatureUsage
import com.etologic.mahjongtournamentsuite.domain.model.RankingTable
import com.etologic.mahjongtournamentsuite.domain.model.TableHand
import com.etologic.mahjongtournamentsuite.domain.model.TableKey
import com.etologic.mahjongtournamentsuite.domain.model.TableState
import com.etologic.mahjongtournamentsuite.domain.model.UsageAccounts
import com.etologic.mahjongtournamentsuite.domain.model.UsagePeriod
import com.etologic.mahjongtournamentsuite.domain.model.UsageReport
import com.etologic.mahjongtournamentsuite.presentation.components.RoundScheduleEditorRow
import com.etologic.mahjongtournamentsuite.presentation.components.tournamentScheduleEditorError
import com.etologic.mahjongtournamentsuite.presentation.util.dataStatus
import com.etologic.mahjongtournamentsuite.presentation.util.render
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlin.time.Instant

/** The messages that the editor app builds outside the screens are written in Spanish when the language is Spanish. */
class SpanishMessagesTest {
    private val original = Locale.getDefault()

    @AfterTest
    fun restoreLocale() = Locale.setDefault(original)

    private fun spanish() = Locale.setDefault(Locale.forLanguageTag("es"))

    private fun table(scores: List<String>, completed: Boolean = false, totals: Boolean = false) = RankingTable(
        table = TableState(
            roundId = 2, tableId = 3, playerIds = listOf(1, 2, 3, 4),
            playerEastId = "1", playerSouthId = "2", playerWestId = "3", playerNorthId = "4",
            playerEastScore = scores[0], playerSouthScore = scores[1], playerWestScore = scores[2], playerNorthScore = scores[3],
            isCompleted = completed, useTotalsOnly = totals,
        ),
        hands = emptyList(),
    )

    @Test
    fun theExportErrorNamesTheTableAndTheSeatsInSpanish() = runTest {
        spanish()
        val text = officialResultsExportError(listOf(table(listOf("", "", "", "")))).render()
        assertTrue("No se pueden exportar los resultados oficiales." in text)
        assertTrue("Ronda 2, mesa 3: faltan las puntuaciones finales de Este, Sur, Oeste y Norte" in text, text)
        assertTrue("• " in text)
    }

    @Test
    fun anEmptyTournamentHasASpanishExportError() = runTest {
        spanish()
        assertEquals(
            "No se pueden exportar los resultados oficiales porque el torneo no tiene mesas.",
            officialResultsExportError(emptyList()).render(),
        )
    }

    @Test
    fun theScheduleErrorsAreInSpanish() = runTest {
        spanish()
        val error = tournamentScheduleEditorError(
            roundRows = listOf(RoundScheduleEditorRow(1, "02/10/2026", "9:30")),
            agendaRows = emptyList(), eventStartDate = "2026-10-02", eventEndDate = "2026-10-02",
        )
        assertEquals("La ronda 1 tiene una hora de inicio no válida.", error.render())
    }

    @Test
    fun theDataStatusIsInSpanish() = runTest {
        spanish()
        val moment = Instant.parse("2026-10-11T15:30:12Z")
        val status = dataStatus(42, moment, zone = kotlinx.datetime.TimeZone.UTC).render()
        assertEquals("Resultados r42 · comprobado 15:30:12", status)
    }

    @Test
    fun theHandValidationIsInSpanish() = runTest {
        spanish()
        val hand = HandDraftState.from(
            TableHand(
                handId = 1, playerWinnerId = "1", playerLooserId = "2", handScore = "7", isChickenHand = false, isDone = false,
                playerEastPenalty = "", playerSouthPenalty = "", playerWestPenalty = "", playerNorthPenalty = "",
            ),
        )
        hand.updateHandScore("7")
        assertEquals("Esta mano no es válida y no se puede marcar como hecha ni como completada.", hand.validationErrorMessage.render())
        assertEquals(listOf("La puntuación mínima para ganar una mano es 8."), hand.validationDetailMessages.map { it.render() })
    }

    @Test
    fun theTableUpdateNoticeIsInSpanish() = runTest {
        spanish()
        val notice = TableUpdateNotice(TableKey(2, 5), emptyList())
        assertEquals(
            "Ronda 2 · Mesa 5: otro usuario cambió esta mesa. Ahora muestra los datos más recientes.",
            notice.headline().render(),
        )
        val lost = TableReconciler.lostChangesOf(emptyMap(), mapOf(1 to mapOf("handScore" to "16", "isDone" to true)))
        assertEquals(listOf("Mano 1 · Puntuación de la mano" to "16", "Mano 1 · Hecha" to "Sí"), lost.map { it.label.render() to it.value.render() })
    }

    @Test
    fun theUsageInsightsAreInSpanish() = runTest {
        spanish()
        fun period(saves: Int) = UsagePeriod(
            activeUsers = 0, saves = saves, handChanges = 0, playerSaves = 0, editorSaves = saves,
            actions = 0, activeTournaments = 0, tournamentsCreated = 0, newAccounts = 0, conflicts = 0, resets = 0,
        )
        val feature = FeatureUsage("substitution", "Player substitution", "Players", true, 0, 0, null, null)
        val report = UsageReport(
            generatedAt = "", periodDays = 30, current = period(10), previous = period(20),
            accounts = UsageAccounts(10, 1, 2, 7, 0, 3, 1, 1, 5), daily = emptyList(), heatmap = emptyList(),
            users = emptyList(), features = listOf(feature), funnel = emptyList(), countries = emptyList(), tournaments = emptyList(),
        )
        val texts = usageInsights(report).map { it.text.render() }
        assertTrue(texts.any { it.startsWith("Los guardados de mesa bajaron un 50%") }, texts.toString())
        assertTrue("5 de 10 cuentas nunca iniciaron sesión." in texts, texts.toString())
        assertTrue(texts.any { "Sustitución de jugadores" in it }, texts.toString())
        assertEquals("hoy", agoText(0).render())
        assertEquals("hace 12 días", agoText(12).render())
        assertEquals("Nuevo respecto a los 7 días anteriores.", changeText(3, 0, 7).render())
    }
}
