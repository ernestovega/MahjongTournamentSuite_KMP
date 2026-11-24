package com.etologic.mahjongtournamentsuite.presentation.screen

import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.TournamentOrganizer
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The texts of the projection and the Table IDs cards (docs/table-qr.md): under "Or just open the app:" goes only the
 * main site, easier to type than the link of the tournament, and the logo without an image is the initial of the
 * main organizer.
 */
class TableQrTextsTest {
    @Test
    fun theMainSiteIsTheHostOfTheLink() {
        assertEquals("mts.mahjongmadrid.com", "https://mts.mahjongmadrid.com/6thmmc/my-table".mainSite())
        assertEquals("mts.mahjongmadrid.com", "https://mts.mahjongmadrid.com".mainSite())
        assertEquals("mts---staging.web.app", "https://mts---staging.web.app/t/abc?x=1".mainSite())
    }

    @Test
    fun theLogoWithoutImageUsesTheMainOrganizerOrElseTheTournament() {
        val tournament = Tournament(id = "t1", name = "Madrid Open", isTeams = false, numPlayers = 16, numRounds = 4)
        assertEquals("Madrid Open", tournament.mainOrganizerName())
        val organized = tournament.copy(
            organizers = listOf(TournamentOrganizer(id = "o1", name = "Mahjong Madrid"), TournamentOrganizer(id = "o2", name = "EMA")),
        )
        assertEquals("Mahjong Madrid", organized.mainOrganizerName())
    }
}
