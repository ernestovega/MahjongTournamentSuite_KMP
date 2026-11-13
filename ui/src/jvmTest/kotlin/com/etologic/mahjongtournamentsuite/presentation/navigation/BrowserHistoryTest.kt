package com.etologic.mahjongtournamentsuite.presentation.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BrowserHistoryTest {
    private fun entry(vararg routes: String, stepsBack: Int = 0) = HistoryEntry(routes.toList(), stepsBack)

    @Test
    fun `an entry reads back what it wrote and ignores other states`() {
        val entry = entry("Tournaments", "Tournament/a%20b", stepsBack = 1)
        assertEquals(entry, HistoryEntry.decode(entry.encode()))
        assertNull(HistoryEntry.decode(null))
        assertNull(HistoryEntry.decode("something else"))
        assertNull(HistoryEntry.decode("mts-nav\nx\nTournaments"))
    }

    @Test
    fun `the first screen takes the place of the current entry`() {
        assertEquals(
            listOf(HistoryStep.Replace(entry("Tournaments"))),
            historySteps(null, listOf("Tournaments")),
        )
    }

    @Test
    fun `the screens that a reload restores get one entry each`() {
        assertEquals(
            listOf(
                HistoryStep.Replace(entry("Tournaments")),
                HistoryStep.Push(entry("Tournaments", "Tournament/a", stepsBack = 1)),
            ),
            historySteps(null, listOf("Tournaments", "Tournament/a")),
        )
    }

    @Test
    fun `opening screens adds one entry for each`() {
        assertEquals(
            listOf(
                HistoryStep.Push(entry("Tournaments", "Tournament/a", stepsBack = 1)),
                HistoryStep.Push(entry("Tournaments", "Tournament/a", "Player/a/3", stepsBack = 2)),
            ),
            historySteps(entry("Tournaments"), listOf("Tournaments", "Tournament/a", "Player/a/3")),
        )
    }

    @Test
    fun `the back of the app goes back in the browser history`() {
        val current = entry("Tournaments", "Tournament/a", "Player/a/3", stepsBack = 2)
        assertEquals(
            listOf(HistoryStep.Back(1, entry("Tournaments", "Tournament/a", stepsBack = 1))),
            historySteps(current, listOf("Tournaments", "Tournament/a")),
        )
        assertEquals(
            listOf(HistoryStep.Back(2, entry("Tournaments"))),
            historySteps(current, listOf("Tournaments")),
        )
    }

    @Test
    fun `a back past the entries of the app changes the current entry`() {
        val restored = entry("Tournaments", "Tournament/a", "Player/a/3")
        assertEquals(
            listOf(HistoryStep.Replace(entry("Tournaments", "Tournament/a"))),
            historySteps(restored, listOf("Tournaments", "Tournament/a")),
        )
    }

    @Test
    fun `leaving the sign-in for good does not keep it in the history`() {
        assertEquals(
            listOf(HistoryStep.Replace(entry("Tournaments"))),
            historySteps(entry("SignIn"), listOf("Tournaments")),
        )
    }

    @Test
    fun `a change of the top screen keeps the entries behind`() {
        val current = entry("Tournaments", "Player/a/3?tab=0", stepsBack = 1)
        assertEquals(
            listOf(
                HistoryStep.Replace(entry("Tournaments", "Player/a/3?tab=1", stepsBack = 1)),
                HistoryStep.Push(entry("Tournaments", "Player/a/3?tab=1", "Table/a/1/2", stepsBack = 2)),
            ),
            historySteps(current, listOf("Tournaments", "Player/a/3?tab=1", "Table/a/1/2")),
        )
    }

    @Test
    fun `the same back stack changes nothing`() {
        assertEquals(emptyList(), historySteps(entry("Tournaments", stepsBack = 3), listOf("Tournaments")))
    }

    @Test
    fun `the browser back closes screens and the forward opens them again`() {
        assertEquals(
            NavigationPlan(pops = 2, reset = null, open = emptyList()),
            navigationPlan(listOf("Tournaments", "Tournament/a", "Player/a/3"), listOf("Tournaments")),
        )
        assertEquals(
            NavigationPlan(pops = 0, reset = null, open = listOf("Tournament/a", "Player/a/3")),
            navigationPlan(listOf("Tournaments"), listOf("Tournaments", "Tournament/a", "Player/a/3")),
        )
        assertEquals(
            NavigationPlan(pops = 1, reset = null, open = listOf("Table/a/1/2")),
            navigationPlan(listOf("Tournaments", "Player/a/3"), listOf("Tournaments", "Table/a/1/2")),
        )
    }

    @Test
    fun `another bottom screen takes the place of the whole back stack`() {
        assertEquals(
            NavigationPlan(pops = 1, reset = "SignIn", open = emptyList()),
            navigationPlan(listOf("Tournaments", "Tournament/a"), listOf("SignIn")),
        )
        assertNull(navigationPlan(listOf("Tournaments"), listOf("Tournaments")))
    }
}
