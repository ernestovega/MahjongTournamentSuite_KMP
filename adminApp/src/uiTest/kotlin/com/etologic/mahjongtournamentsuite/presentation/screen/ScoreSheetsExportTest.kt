package com.etologic.mahjongtournamentsuite.presentation.screen

import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.presentation.presenter.PlayersPresenter
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

/** The file of the score sheets: named after the short name (or the name) and the year of the tournament. */
class ScoreSheetsExportTest {
    private val server = FakeTournamentRepository().apply { writesSucceed = true }
    private val presenter = PlayersPresenter(server, FakePlayerRepository(), FakePlayerAccountRepository(), Logger.withTag("test"))
    private val tournament = Tournament(id = "t1", name = "Madrid Open Riichi", isTeams = false, numPlayers = 8, numRounds = 2)

    private suspend fun fileName(tournament: Tournament, perTable: Boolean): String = (exportScoreSheets(presenter, tournament, perTable, emaLogo = true, saveTitle = "Save") as AppResult.Success).value.fileName

    @Test
    fun theBlankSheetsUseTheShortNameAndTheYear() = runTest {
        val name = fileName(tournament.copy(shortName = "MOR", eventStartDate = "2026-10-22"), perTable = false)
        assertTrue(name.startsWith("MOR-2026-score-sheets-") && name.endsWith(".pdf"), name)
    }

    @Test
    fun theSheetsOfTheTablesWithoutAShortNameOrADateUseTheName() = runTest {
        val name = fileName(tournament.copy(eventStartDate = "soon"), perTable = true)
        assertTrue(name.startsWith("Madrid-Ope-score-sheets-tables-"), name)
    }
}
