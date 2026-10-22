package com.etologic.mahjongtournamentsuite.e2e

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.After
import org.junit.Before
import org.junit.Test

/** Invitations by email. The local backend keeps the emails in memory, so the tests read them from there. */
class InvitationsTest : AdminE2eTest() {
    private val rowCheckbox = SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState) and hasClickAction()

    @Before
    fun emptyMailBox() = E2eApi.clearEmails()

    @After
    fun restoreData() {
        E2eApi.reset("test-live-ongoing")
        E2eApi.clearEmails()
    }

    /** Opens the invitations dialog and selects the first player of the list "Not invited yet". */
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

    private fun typeEmail(email: String) {
        c.onAllNodes(hasSetTextAction() and hasText("")).onLast().performTextInput(email)
    }

    @Test
    fun invitingAPlayerWithoutEmailAsksForTheEmail() {
        openDialogWithOnePlayerSelected()
        c.clickButton("Invite or remind (1)")
        c.waitForText("Emails are missing")
        c.waitForText("These players have no saved email.", substring = true)
    }

    @Test
    fun cancelClosesTheMissingEmailsDialog() {
        openDialogWithOnePlayerSelected()
        c.clickButton("Invite or remind (1)")
        c.waitForText("Emails are missing")
        c.clickLastButton("Cancel")
        c.waitForNoText("Emails are missing")
        c.waitForText("1 selected")
    }

    @Test
    fun confirmStaysOffWhileNoEmailIsTyped() {
        openDialogWithOnePlayerSelected()
        c.clickButton("Invite or remind (1)")
        c.waitForText("Emails are missing")
        c.onAllNodes(hasText("Invite or remind (0)") and hasClickAction()).onLast().assertIsNotEnabled()
    }

    @Test
    fun confirmStaysOffForAnEmailThatIsNotValid() {
        openDialogWithOnePlayerSelected()
        c.clickButton("Invite or remind (1)")
        c.waitForText("Emails are missing")
        typeEmail("not-an-email")
        c.waitForText("Invite or remind (1)", substring = false)
        c.onAllNodes(hasText("Invite or remind (1)") and hasClickAction()).onLast().assertIsNotEnabled()
        check(E2eApi.sentEmails().isEmpty()) { "An email left although the address was not valid." }
    }

    @Test
    fun validEmailSendsTheInvitationAndMarksThePlayerAsInvited() {
        openDialogWithOnePlayerSelected()
        c.clickButton("Invite or remind (1)")
        c.waitForText("Emails are missing")
        typeEmail("guest.one@example.test")
        c.clickLastButton("Invite or remind (1)")
        c.waitForText("Invited, waiting (1)")
        val mails = E2eApi.sentEmails()
        check(mails.size == 1) { "Expected one email, found ${mails.size}." }
        check(mails[0].getString("to") == "guest.one@example.test") { "The email went to ${mails[0].getString("to")}." }
        check(mails[0].getString("text").contains("Open this link to accept:")) { "The email has no invitation link." }
    }

    @Test
    fun anEmailThatTheMailboxRefusesShowsTheFailure() {
        openDialogWithOnePlayerSelected()
        c.clickButton("Invite or remind (1)")
        c.waitForText("Emails are missing")
        typeEmail("fail.me@example.test")
        c.clickLastButton("Invite or remind (1)")
        // The server saves the invitation before it sends the email. The player moves to "Invited, waiting".
        // The row keeps the error, so the administrator can send the email again.
        c.waitForText("Invited, waiting (1)")
        c.clickButton("All (40)")
        c.scrollListTo("failed", substring = true)
        check(E2eApi.sentEmails().isEmpty()) { "An email left although the mailbox refused it." }
    }

    @Test
    fun revokingAnInvitationReturnsThePlayerToNotInvited() {
        openDialogWithOnePlayerSelected()
        c.clickButton("Invite or remind (1)")
        c.waitForText("Emails are missing")
        typeEmail("guest.two@example.test")
        c.clickLastButton("Invite or remind (1)")
        c.waitForText("Invited, waiting (1)")
        c.clickButton("Invited, waiting (1)")
        c.onAllNodes(rowCheckbox).onLast().performClick()
        c.waitForText("1 selected")
        c.clickButton("Revoke (1)")
        c.waitForText("Invited, waiting (0)")
        c.waitForText("Not invited yet (30)")
    }
}
