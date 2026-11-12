package com.etologic.mahjongtournamentsuite.e2e

import org.junit.Test

class TournamentListTest : AdminE2eTest() {
    @Test
    fun adminSeesTheTestTournamentsWithTheirDetails() {
        start(E2eAccounts.ADMIN)
        // The list is lazy and has more tournaments than the screen, so the test scrolls to each one.
        listOf("LIVE", "LASTTABLE", "LARGE", "EXTREMES10", "UPCOMING", "INCOMPL", "BARE").forEach { c.scrollListTo(it) }
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
        // "Admin" is the role under the name. It opens the profile, which holds the theme and the Logout button.
        listOf(PROFILE_BUTTON, "Admin", "Users", "App errors", "Timer", "Statistics", "EMA players", "New tournament")
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

    /** The admin app is for admins and editors. The splash screen signs a player out and shows the sign-in form. */
    @Test
    fun playerSessionIsSignedOutAtTheStart() {
        start(E2eAccounts.PLAYER, ready = "Need help?")
        c.assertNoText(PROFILE_BUTTON)
        c.assertNoText("New tournament")
        c.assertNoText("App errors")
    }

    // A player cannot open the admin screens any more, so the next two tests ask the server directly.

    @Test
    fun playerOpeningAppUsersGetsAPermissionError() {
        val reply = E2eApi.call("GET", "/admin/users", E2eApi.signIn(E2eAccounts.PLAYER))
        check(reply.status == 403) { "A player read the app users: ${reply.status} ${reply.body}" }
    }

    @Test
    fun playerSeesOnlyThePlayersOfOwnTournamentsInTheRegistry() {
        val all = E2eApi.call("GET", "/ema-player-registry", E2eApi.signIn(E2eAccounts.ADMIN)).json().getJSONArray("players").length()
        val reply = E2eApi.call("GET", "/ema-player-registry", E2eApi.signIn(E2eAccounts.PLAYER))
        check(reply.status == 200) { "The registry read failed: ${reply.status} ${reply.body}" }
        val seen = reply.json().getJSONArray("players").length()
        check(seen in 1 until all) { "A player sees $seen of the $all registry players. It must be only the players of own tournaments." }
    }

    @Test
    fun openingARowShowsTheTournament() {
        start(E2eAccounts.ADMIN)
        c.openTournament("Test - Live ongoing tournament")
        c.waitForText("Players & results")
        c.waitForText("Round 4")
    }
}
