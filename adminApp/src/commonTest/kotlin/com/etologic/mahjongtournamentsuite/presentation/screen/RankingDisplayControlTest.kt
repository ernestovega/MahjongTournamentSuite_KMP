package com.etologic.mahjongtournamentsuite.presentation.screen

import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryRankingControlAction
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What Ranking control sets, the reveal of the podium, the words of its analytics and the link between windows. */
class RankingDisplayControlTest {
    private val players = RankingDisplayPage("players", "Players", podium = 3)

    @BeforeTest
    @AfterTest
    fun forget() = RankingDisplayLink.reset()

    @Test
    fun theTopThreeShowsByDefaultUntilThePodiumIsSecretAndTheOrganizerCanChoose() {
        val control = RankingDisplayControl()
        assertTrue(control.showsTopThree(podiumSecret = false))
        assertFalse(control.showsTopThree(podiumSecret = true))
        assertTrue(control.withShowTopThree(true).showsTopThree(podiumSecret = true))
        assertFalse(control.withShowTopThree(false).showsTopThree(podiumSecret = false))
    }

    @Test
    fun thePodiumIsRevealedFromTheThirdPlaceUpAndThenNothingIsLeft() {
        var control = RankingDisplayControl(playing = true)
        assertNull(control.nextReveal(players, podiumSecret = false), "the top three shows, so there is nothing to reveal")
        assertEquals(3, control.nextReveal(players, podiumSecret = true))
        control = control.reveal("players")
        // A reveal stops the paging and asks the projection to show that list.
        assertFalse(control.playing)
        assertEquals("players", control.pageKey)
        assertEquals(1, control.pageRequest)
        assertEquals(2, control.nextReveal(players, podiumSecret = true))
        control = control.reveal("players").reveal("players")
        assertNull(control.nextReveal(players, podiumSecret = true))
        // One more press does not count past the first place.
        assertEquals(3, control.reveal("players").revealed["players"])
    }

    @Test
    fun aListWithTwoPlacesStartsItsRevealAtTheSecondPlace() {
        val short = RankingDisplayPage("teams", "Teams", podium = 2)
        val control = RankingDisplayControl(showTopThree = false)
        assertEquals(2, control.nextReveal(short, podiumSecret = false))
        // The first press shows the second place at once: the projection hides the places under 3 - revealed.
        val once = control.reveal("teams", podium = 2)
        assertEquals(2, once.revealed["teams"])
        assertEquals(1, once.nextReveal(short, podiumSecret = false))
        assertNull(once.reveal("teams", podium = 2).nextReveal(short, podiumSecret = false))
        // A list with one place shows it with one press.
        assertEquals(3, control.reveal("best-hands", podium = 1).revealed["best-hands"])
    }

    @Test
    fun aChoiceOfTheTopThreeStartsTheRevealAgain() {
        val control = RankingDisplayControl().reveal("players").withShowTopThree(false)
        assertEquals(emptyMap(), control.revealed)
        assertEquals(3, control.nextReveal(players, podiumSecret = false))
    }

    @Test
    fun eachControlGivesItsAnalyticsWord() {
        val start = RankingDisplayControl(playing = true)
        fun words(after: RankingDisplayControl, before: RankingDisplayControl = start, secret: Boolean = false) = rankingControlActions(before, after, secret)

        assertEquals(listOf(TelemetryRankingControlAction.PAUSED), words(start.copy(playing = false)))
        assertEquals(listOf(TelemetryRankingControlAction.PLAYED), words(start, before = start.copy(playing = false)))
        assertEquals(listOf(TelemetryRankingControlAction.LIST_SHOWN), words(start.showPage("teams")))
        // The reveal also pauses and shows its list: one word for it.
        assertEquals(listOf(TelemetryRankingControlAction.PLACE_REVEALED), words(start.reveal("players"), secret = true))
        assertEquals(listOf(TelemetryRankingControlAction.TOP_THREE_HIDDEN), words(start.withShowTopThree(false)))
        assertEquals(listOf(TelemetryRankingControlAction.TOP_THREE_SHOWN), words(start.withShowTopThree(true), secret = true))
        // Choosing what already shows by default changes nothing on the screen.
        assertEquals(emptyList(), words(start.withShowTopThree(true), secret = false))
        assertEquals(listOf(TelemetryRankingControlAction.HIDDEN_RESULTS_SHOWN), words(start.copy(showHiddenResults = true)))
        assertEquals(listOf(TelemetryRankingControlAction.HIDDEN_RESULTS_HIDDEN), words(start, before = start.copy(showHiddenResults = true)))
        // The sliders, the logos and the theme send nothing.
        assertEquals(emptyList(), words(start.copy(rowsPerScroll = 30, intervalSeconds = 3, showOrganizerLogo = false, showEmaLogo = false, dark = true)))
    }

    @Test
    fun theLinkTakesTheControlAndTheStatusOfAnotherWindow() {
        val control = RankingDisplayControl(playing = false, rowsPerScroll = 30)
        val status = RankingDisplayStatus(pages = listOf(players), remainingSeconds = 4)
        RankingDisplayLink.receive(Json.encodeToString(RankingDisplayMessage("t1", control = control, status = status)))
        assertEquals(control, RankingDisplayLink.control("t1"))
        assertEquals(status, RankingDisplayLink.status.value["t1"])
        // Another tournament keeps its defaults.
        assertEquals(RankingDisplayControl(), RankingDisplayLink.control("t2"))

        // A projection that closes: the control screen says so.
        RankingDisplayLink.receive(Json.encodeToString(RankingDisplayMessage("t1", closed = true)))
        assertNull(RankingDisplayLink.status.value["t1"])
        assertEquals(control, RankingDisplayLink.control("t1"))
    }

    @Test
    fun aMessageThatIsNotOursOrAHelloChangesNothing() {
        RankingDisplayLink.setControl("t1", RankingDisplayControl(intervalSeconds = 3))
        RankingDisplayLink.receive("not json")
        RankingDisplayLink.receive("""{"other":1}""")
        RankingDisplayLink.receive(Json.encodeToString(RankingDisplayMessage("t1", hello = true)))
        RankingDisplayLink.receive(Json.encodeToString(RankingDisplayMessage("t2", hello = true)))
        assertEquals(RankingDisplayControl(intervalSeconds = 3), RankingDisplayLink.control("t1"))
        assertNull(RankingDisplayLink.status.value["t2"])
    }

    @Test
    fun theLocalWindowUpdatesTheControlAndTheStatus() {
        RankingDisplayLink.updateControl("t1") { it.copy(playing = false) }
        assertFalse(RankingDisplayLink.control("t1").playing)
        val status = RankingDisplayStatus(part = 2, parts = 3)
        RankingDisplayLink.setStatus("t1", status)
        RankingDisplayLink.setStatus("t1", status)
        assertEquals(status, RankingDisplayLink.status.value["t1"])
        RankingDisplayLink.hello("t1")
        RankingDisplayLink.closeStatus("t1")
        assertNull(RankingDisplayLink.status.value["t1"])
    }
}
