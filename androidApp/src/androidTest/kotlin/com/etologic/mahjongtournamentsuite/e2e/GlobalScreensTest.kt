package com.etologic.mahjongtournamentsuite.e2e

import org.junit.Test

class GlobalScreensTest : AdminE2eTest() {
    @Test
    fun appUsersListsEveryAccountWithRoleAndStatus() {
        start(E2eAccounts.ADMIN)
        c.clickButton("App Users")
        c.waitForText("9 of 9 accounts")
        listOf("admin@e2e.test", "editor@e2e.test", "outsider@e2e.test", "disabled@e2e.test", "player@e2e.test", "stranger@e2e.test")
            .forEach { c.waitForText(it) }
        c.waitForText("Disabled")
        c.waitForText("New user")
    }

    @Test
    fun appUsersShowsEmaIdOfLinkedPlayers() {
        start(E2eAccounts.ADMIN)
        c.clickButton("App Users")
        c.waitForText("10000138")
        c.waitForText("10000137")
    }

    @Test
    fun appUsersSearchFiltersByEmail() {
        start(E2eAccounts.ADMIN)
        c.clickButton("App Users")
        c.waitForText("9 of 9 accounts")
        c.field("Search by email").performTextInputText("editor@")
        c.waitForText("1 of 9 accounts")
        c.waitForText("editor@e2e.test")
        c.assertNoText("admin@e2e.test")
    }

    @Test
    fun appUsersBackReturnsToTheList() {
        start(E2eAccounts.ADMIN)
        c.clickButton("App Users")
        c.waitForText("App users")
        c.clickButton("Back")
        c.waitForText("Test - Live ongoing tournament")
    }

    @Test
    fun editorSeesAppUsersScreen() {
        start(E2eAccounts.EDITOR)
        c.clickButton("App Users")
        c.waitForText("editor@e2e.test")
    }

    @Test
    fun usageScreenCountsTournamentsAndShowsEmptyActivity() {
        start(E2eAccounts.ADMIN)
        c.clickButton("App usage")
        c.waitForText("Active users")
        c.waitForText("No saves in the last 30 days.")
        c.waitForText("Conflict rate")
        c.waitForText("Show all 13 tournaments")
    }

    @Test
    fun usageScreenShowsCompletionOfTournaments() {
        start(E2eAccounts.ADMIN)
        c.clickButton("App usage")
        c.waitForText("4 of 4 tables completed. 16 of 16 players assigned.", substring = true)
        c.waitForText("17 of 18 tables completed. 24 of 24 players assigned.", substring = true)
    }

    @Test
    fun emaPlayersListsTheRegistry() {
        start(E2eAccounts.ADMIN)
        c.clickButton("EMA players")
        c.waitForText("160 players · 8 countries")
        c.waitForText("New player")
    }

    @Test
    fun emaPlayersSearchByNameAndNumber() {
        start(E2eAccounts.ADMIN)
        c.clickButton("EMA players")
        c.waitForText("160 players · 8 countries")
        c.field("Search by name or EMA number").performTextInputText("10000119")
        c.waitForText("CARMEN ALVAREZ")
        c.assertNoText("ANA ALVAREZ")
    }

    @Test
    fun editorSeesEmaPlayersWithoutNewPlayerButton() {
        start(E2eAccounts.EDITOR)
        c.clickButton("EMA players")
        c.waitForText("160 players · 8 countries")
        c.assertNoText("New player")
    }

    @Test
    fun globalStatisticsOpensForAdmin() {
        start(E2eAccounts.ADMIN)
        c.clickButton("Statistics")
        c.waitForText("Records")
        c.waitForText("Highest hand score")
        c.waitForText("All tournaments")
    }

    @Test
    fun themeButtonCyclesTheTheme() {
        start(E2eAccounts.ADMIN)
        c.clickButton("System")
        c.waitForText("Light")
    }
}
