package com.etologic.mahjongtournamentsuite.e2e

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import org.junit.Test

class GlobalScreensTest : AdminE2eTest() {
    private val hasEmaNumber = SemanticsMatcher("an EMA number") { node ->
        node.config.getOrNull(SemanticsProperties.Text)?.any { Regex("^\\d{8}$").matches(it.text) } == true
    }

    @Test
    fun appUsersListsEveryAccountWithRoleAndStatus() {
        start(E2eAccounts.ADMIN)
        c.clickButton("Users")
        c.waitForText("11 of 11 accounts")
        // The list is lazy, so the test scrolls to each account.
        listOf("admin@e2e.test", "editor@e2e.test", "outsider@e2e.test", "disabled@e2e.test", "player@e2e.test", "stranger@e2e.test")
            .forEach { c.scrollListTo(it) }
        c.scrollListTo("Disabled")
        c.waitForText("New user")
    }

    @Test
    fun appUsersShowsEmaIdOfLinkedPlayers() {
        start(E2eAccounts.ADMIN)
        c.clickButton("Users")
        // The backend links the player accounts to the EMA players that the seed seats, so the ids follow the seed.
        // The row of a linked account shows an EMA number of 8 digits.
        c.scrollListTo("player@e2e.test")
        c.waitUntil(20_000) { c.onAllNodes(hasEmaNumber).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun appUsersSearchFiltersByEmail() {
        start(E2eAccounts.ADMIN)
        c.clickButton("Users")
        c.waitForText("11 of 11 accounts")
        // The search also matches the role, so "editor" would find every editor. "outsider" is only in one email.
        c.field("Search").performTextInputText("outsider@")
        c.waitForText("1 of 11 accounts")
        c.waitForText("outsider@e2e.test")
        c.assertNoText("admin@e2e.test")
    }

    @Test
    fun appUsersBackReturnsToTheList() {
        start(E2eAccounts.ADMIN)
        c.clickButton("Users")
        // The screen title is "Users", the same text as the button of the list, so the test waits for its own button.
        c.waitForText("New user")
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
