package com.etologic.mahjongtournamentsuite.e2e

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.After
import org.junit.Before
import org.junit.Test

/** Invitations create the account of a player from the email of the import. The local backend keeps the emails in memory. */
class InvitationsTest : AdminE2eTest() {
    private val rowCheckbox = SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState) and hasClickAction()

    @Before
    fun emptyMailBox() = E2eApi.clearEmails()

    @After
    fun restoreData() {
        E2eApi.reset("test-live-ongoing")
        E2eApi.clearEmails()
    }

    /** Opens the invitations dialog and selects the last player of the list "No account". */
    private fun openDialogWithOnePlayerSelected() {
        start(E2eAccounts.ADMIN, ready = "Logout")
        c.openTournament("Test - Live ongoing tournament")
        c.waitForText("Players & results")
        c.clickButton("Players")
        c.waitForText("Tournament players")
        c.clickButton("Invitations")
        c.waitForText("Invitations and reminders")
        c.onAllNodes(rowCheckbox).onLast().performClick()
        c.waitForText("1 selected")
    }

    @Test
    fun aPlayerWithoutEmailShowsAWarningAndTheSendAsksForTheEmail() {
        openDialogWithOnePlayerSelected()
        c.waitForText("No email", substring = true)
        c.clickButton("Send (1)")
        c.waitForText("Emails are missing")
        check(E2eApi.sentEmails().isEmpty()) { "An email left although the player has no email." }
    }

    @Test
    fun theTypedEmailIsSavedAndTheInvitationGoesOut() {
        openDialogWithOnePlayerSelected()
        c.clickButton("Send (1)")
        c.waitForText("Emails are missing")
        c.onAllNodes(androidx.compose.ui.test.hasSetTextAction() and androidx.compose.ui.test.hasText("")).onLast()
            .performTextInput("typed.guest@example.test")
        c.clickLastButton("Send (1)")
        c.waitForText("Invited")
        val mails = E2eApi.sentEmails()
        check(mails.size == 1 && mails[0].getString("to") == "typed.guest@example.test") { "Unexpected emails: $mails" }
    }

    @Test
    fun aPlayerWithAnEmailGetsTheAccountAndTheResetEmail() {
        E2eApi.setSlotEmails("test-live-ongoing")
        openDialogWithOnePlayerSelected()
        c.waitForText("New accounts: 1 · Reminders: 0")
        c.clickButton("Send (1)")
        c.waitForText("Invited")
        val mails = E2eApi.sentEmails()
        check(mails.size == 1) { "Expected one email, found ${mails.size}." }
        check(mails[0].getString("to").endsWith("@example.test")) { "The email went to ${mails[0].getString("to")}." }
        check(mails[0].getString("subject").contains("password", ignoreCase = true)) { "The email does not ask the player to choose a password." }
    }

    @Test
    fun anEmailThatTheMailboxRefusesShowsTheFailureAndKeepsTheAccount() {
        E2eApi.setSlotEmails("test-live-ongoing", prefix = "fail.")
        openDialogWithOnePlayerSelected()
        c.clickButton("Send (1)")
        c.waitForText("The email could not be sent. Try again.")
        check(E2eApi.sentEmails().isEmpty()) { "An email left although the mailbox refused it." }
    }
}
