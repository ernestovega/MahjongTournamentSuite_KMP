package com.etologic.mahjongtournamentsuite.e2e

import org.junit.Test

class TournamentListTest : AdminE2eTest() {
    @Test
    fun adminSeesTheTestTournamentsWithTheirDetails() {
        start(E2eAccounts.ADMIN)
        c.waitForText("LIVE")
        c.waitForText("LASTTABLE")
        c.waitForText("LARGE")
        c.waitForText("EXTREMES10")
        c.waitForText("UPCOMING")
        c.waitForText("INCOMPL")
        c.waitForText("BARE")
    }

    @Test
    fun rowsShowTeamsAndFeatureSwitches() {
        start(E2eAccounts.ADMIN)
        c.waitForText("Team tournament")
        c.waitForText("Individual tournament")
        // BARE has both switches off. LASTTABLE counts best hands but not chicken hands.
        c.waitForText("Best hands not counted")
        c.waitForText("Chicken hands not counted")
        c.waitForText("Best hands counted")
        c.waitForText("Chicken hands counted")
    }

    @Test
    fun rowsShowPlayerCounts() {
        start(E2eAccounts.ADMIN)
        c.waitForText("40")
        c.waitForText("24")
        c.waitForText("64")
    }

    @Test
    fun veryLongTournamentNameIsInTheList() {
        start(E2eAccounts.ADMIN)
        c.waitForText("Test - Extreme scores, penalties and a very long tournament name to check wrapping everywhere")
    }

    @Test
    fun tournamentWithoutLogoShowsPlaceholderAndBrokenLogoShowsNothingBroken() {
        start(E2eAccounts.ADMIN)
        c.waitForText("No logo for Test - Live ongoing tournament", substring = false)
        // The address of the broken logo does not load. The row must still be there and open.
        c.waitForText("Test - One table left, broken logo, chicken hands off")
    }

    @Test
    fun adminHasAllTopBarActions() {
        start(E2eAccounts.ADMIN)
        listOf("Logout", "Users", "System", "App errors", "Timer", "Statistics", "EMA players", "New tournament")
            .forEach { c.waitForText(it) }
    }

    @Test
    fun editorSeesOnlyAssignedTournaments() {
        start(E2eAccounts.EDITOR)
        c.waitForText("Test - One table left, broken logo, chicken hands off")
        c.waitForText("Test - Every incomplete table state")
        c.waitForText("Test - Minimum 16 players")
        c.assertNoText("Test - Large team tournament")
        c.assertNoText("Test - Registered, not started")
    }

    @Test
    fun editorHasNoAdminActions() {
        start(E2eAccounts.EDITOR)
        c.waitForText("EMA players")
        c.assertNoText("New tournament")
        c.assertNoText("App errors")
    }

    @Test
    fun editorWithoutTournamentsSeesAnEmptyMessage() {
        start(E2eAccounts.OUTSIDER, ready = "No tournaments yet")
    }

    @Test
    fun playerSeesOnlyOwnTournamentAndNoActions() {
        start(E2eAccounts.PLAYER)
        c.assertNoText("Test - Large team tournament")
        c.assertNoText("New tournament")
        c.assertNoText("App errors")
    }

    @Test
    fun playerOpeningAppUsersGetsAPermissionError() {
        start(E2eAccounts.PLAYER)
        c.clickButton("Users")
        c.waitForText("You do not have permission to perform this action.")
        c.waitForText("0 of 0 accounts")
    }

    @Test
    fun playerSeesOnlyThePlayersOfOwnTournamentsInTheRegistry() {
        start(E2eAccounts.PLAYER)
        c.clickButton("EMA players")
        c.waitForText("30 players · 7 countries")
    }

    @Test
    fun openingARowShowsTheTournament() {
        start(E2eAccounts.ADMIN)
        c.openTournament("Test - Live ongoing tournament")
        c.waitForText("Players & results")
        c.waitForText("Round 4")
    }
}
