package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.presentation.presenter.PlayersPresenter
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The dialog of the table cards: each choice of cards per sheet tells the size of a card, and the PDF is named after the tournament. */
@OptIn(ExperimentalTestApi::class)
class TablesIdsDialogUiTest {
    private val tournament = Tournament(id = "t1", name = "Madrid Open Riichi", isTeams = false, numPlayers = 16, numRounds = 2)

    private fun androidx.compose.ui.test.ComposeUiTest.choice(option: String) = onNode(hasText(option) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))

    @Test
    fun eachChoiceOfCardsPerSheetTellsTheSizeOfACard() = runComposeUiTest {
        val downloads = mutableListOf<Int>()
        setContent {
            MtsTheme(useDarkTheme = false) {
                TablesIdsDialogContent(tournament = tournament, site = "https://play.test", onDownload = { downloads += it }, onDismiss = {})
            }
        }
        listOf("1" to "297 × 210 mm", "2" to "210 × 148.5 mm", "6" to "148.5 × 70 mm", "8" to "105 × 74 mm", "4" to "148.5 × 105 mm").forEach { (option, size) ->
            choice(option).performClick()
            waitForIdle()
            onNodeWithTag("table_qr_card_size").assertTextEquals("Size of each card: $size")
        }
        choice("2").performClick()
        onNodeWithText("Create PDF").performClick()
        waitForIdle()
        assertEquals(listOf(2), downloads)
    }

    @Test
    fun theFileOfTheCardsIsNamedAfterTheTournament() = runTest {
        val server = FakeTournamentRepository().apply { writesSucceed = true }
        val presenter = PlayersPresenter(server, FakePlayerRepository(), FakePlayerAccountRepository(), Logger.withTag("test"))
        val named = exportTableQr(presenter, tournament.copy(shortName = "MOR", eventStartDate = "2026-10-22"), 4, "Save") as AppResult.Success
        assertTrue(named.value.fileName.startsWith("MOR-2026-table-qr-"), named.value.fileName)
        val unnamed = exportTableQr(presenter, tournament, 4, "Save") as AppResult.Success
        assertTrue(unnamed.value.fileName.startsWith("Madrid-Ope-table-qr-"), unnamed.value.fileName)
    }
}
