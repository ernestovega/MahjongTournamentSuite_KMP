package com.etologic.mahjongtournamentsuite.e2e

import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import org.junit.Test

class GlobalScreensTest : AdminE2eTest() {
    @Test
    fun appUsersListsEveryAccountWithRoleAndStatus() {
        start(E2eAccounts.ADMIN)
        c.clickButton("Users")
        c.waitForText("9 of 9 accounts")
        listOf("admin@e2e.test", "editor@e2e.test", "outsider@e2e.test", "disabled@e2e.test", "player@e2e.test", "stranger@e2e.test")
            .forEach { c.waitForText(it) }
        c.waitForText("Disabled")
        c.waitForText("New user")
    }

    @Test
    fun appUsersShowsEmaIdOfLinkedPlayers() {
        start(E2eAccounts.ADMIN)
        c.clickButton("Users")
        c.waitForText("10000138")
        c.waitForText("10000137")
    }

    @Test
    fun appUsersSearchFiltersByEmail() {
        start(E2eAccounts.ADMIN)
        c.clickButton("Users")
        c.waitForText("9 of 9 accounts")
        c.field("Search").performTextInputText("editor@")
        c.waitForText("1 of 9 accounts")
        c.waitForText("editor@e2e.test")
        c.assertNoText("admin@e2e.test")
    }

    @Test
    fun appUsersBackReturnsToTheList() {
        start(E2eAccounts.ADMIN)
        c.clickButton("Users")
        c.waitForText("App users")
        c.clickButton("Back")
        c.waitForText("Test - Live ongoing tournament")
    }

    @Test
    fun editorSeesAppUsersScreen() {
        start(E2eAccounts.EDITOR)
        c.clickButton("Users")
        c.waitForText("editor@e2e.test")
    }

    @Test
    fun errorsScreenShowsTheEmptyLog() {
        start(E2eAccounts.ADMIN)
        c.clickButton("App errors")
        c.waitForText("No errors are logged yet.")
        c.waitForText("Occurrences")
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

    /** The theme is chosen in the profile, which opens from the name and role in the top bar. */
    @Test
    fun themeChoiceInTheProfileChangesTheTheme() {
        start(E2eAccounts.ADMIN)
        c.clickButton("Admin")
        c.waitForText("Theme")
        val light = hasContentDescription("Light") and hasClickAction()
        c.onAllNodes(light).onLast().assertIsNotSelected()
        c.onAllNodes(light).onLast().performClick()
        c.onAllNodes(light).onLast().assertIsSelected()
    }
}
