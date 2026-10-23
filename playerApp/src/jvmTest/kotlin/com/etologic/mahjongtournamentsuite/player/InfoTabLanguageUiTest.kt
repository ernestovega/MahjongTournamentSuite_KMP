package com.etologic.mahjongtournamentsuite.player

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.TournamentPlayer
import com.etologic.mahjongtournamentsuite.domain.model.TournamentRankings
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.Test

/** The Info tab draws the same cards in English and in Spanish. One test body checks both languages. */
@OptIn(ExperimentalTestApi::class)
class InfoTabLanguageUiTest {
    private val original = Locale.getDefault()

    @AfterTest
    fun restoreLocale() = Locale.setDefault(original)

    private val tournament = Tournament(id = "t", name = "T", isTeams = false, numPlayers = 4, numRounds = 1)
    private val slots = (1..4).map { TournamentPlayer(id = it, name = "", team = 0) }
    private val rankings = TournamentRankings(emptyList(), emptyList(), emptyList(), emptyList())

    /** Texts of the progress card: the title and the three labels. */
    private val progressTexts = mapOf(
        "en" to listOf("Progress", "Rounds", "Tables", "Ongoing"),
        "es" to listOf("Progreso", "Rondas", "Mesas", "En curso"),
    )

    @Test
    fun theProgressCardFollowsTheLanguage() {
        for ((language, texts) in progressTexts) {
            runComposeUiTest {
                Locale.setDefault(Locale.forLanguageTag(language))
                val view = buildPlayerTournamentView(tournament, slots, emptyList(), emptyList(), rankings, myEmaId = null)
                setContent { MtsTheme(useDarkTheme = false) { TournamentInfoTab(view) } }
                waitUntil(timeoutMillis = 5_000) { onAllNodesWithText(texts.first()).fetchSemanticsNodes().isNotEmpty() }
                texts.forEach { onNodeWithText(it).assertExists() }
            }
        }
    }
}
