package com.etologic.mahjongtournamentsuite.player

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.TournamentPlayer
import com.etologic.mahjongtournamentsuite.domain.model.TournamentRankings
import com.etologic.mahjongtournamentsuite.domain.model.TournamentVenue
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** The buttons of the venue on the Info tab open its map and the directions to it. */
@OptIn(ExperimentalTestApi::class)
class VenueButtonsUiTest {
    private val original = Locale.getDefault()

    @BeforeTest
    fun english() = Locale.setDefault(Locale.ENGLISH)

    @AfterTest
    fun restoreLocale() = Locale.setDefault(original)

    @Test
    fun theMapAndTheDirectionsOpenTheirLinks() = runComposeUiTest {
        val opened = mutableListOf<String>()
        val uris = object : UriHandler {
            override fun openUri(uri: String) {
                opened += uri
            }
        }
        val tournament = Tournament(
            id = "t",
            name = "T",
            isTeams = false,
            numPlayers = 4,
            numRounds = 1,
            hostCity = "Madrid",
            hostCountry = "ES",
            venue = TournamentVenue(name = "Club", address = "Calle 1", mapUrl = "https://maps.example/club"),
        )
        val slots = (1..4).map { TournamentPlayer(id = it, name = "", team = 0) }
        val view = buildPlayerTournamentView(tournament, slots, emptyList(), emptyList(), TournamentRankings(emptyList(), emptyList(), emptyList(), emptyList()), myEmaId = null)
        setContent { CompositionLocalProvider(LocalUriHandler provides uris) { MtsTheme(useDarkTheme = false) { TournamentInfoTab(view) } } }
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Google Maps").fetchSemanticsNodes().isNotEmpty() }

        onNode(hasClickAction() and (hasText("Google Maps") or hasContentDescription("Open the venue in Google Maps"))).performScrollTo().performClick()
        onNode(hasClickAction() and (hasText("Directions") or hasContentDescription("Directions", substring = true))).performScrollTo().performClick()
        waitForIdle()
        assertEquals(2, opened.size)
        assertEquals("https://maps.example/club", opened.first())
    }
}
