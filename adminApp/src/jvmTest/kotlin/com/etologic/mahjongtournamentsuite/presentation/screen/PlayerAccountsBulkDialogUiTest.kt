package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import com.etologic.mahjongtournamentsuite.domain.model.PlayerAccountActionResult
import com.etologic.mahjongtournamentsuite.domain.model.PlayerAccountFailure
import com.etologic.mahjongtournamentsuite.domain.model.PlayerAccountOutcome
import com.etologic.mahjongtournamentsuite.domain.model.TournamentPlayer
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import com.etologic.mahjongtournamentsuite.presentation.util.localizedDateTimeOrNull
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The editor sees who has an account, filters, selects, and sends. The server picks the action for each player:
 * an invitation for a player with no account, a reminder for a player with one.
 */
@OptIn(ExperimentalTestApi::class)
class PlayerAccountsBulkDialogUiTest {
    private val players = listOf(
        TournamentPlayer(id = 1, name = "P1", team = 0, assignedEmaId = "11", hasEmail = true),
        TournamentPlayer(id = 2, name = "P2", team = 0, assignedEmaId = "22", accountUid = "u2", hasAccount = true, hasEmail = true),
        TournamentPlayer(id = 3, name = "P3", team = 0, assignedEmaId = "33", hasEmail = false),
        TournamentPlayer(id = 4, name = "P4", team = 0, assignedEmaId = "44", accountUid = "u4", hasAccount = true, hasEmail = true),
    )

    private fun ComposeUiTest.show(
        results: List<PlayerAccountActionResult> = emptyList(),
        busy: Boolean = false,
        list: List<TournamentPlayer> = players,
        onSend: (List<Int>, Map<Int, String>) -> Unit = { _, _ -> },
    ) = setContent {
        MtsTheme(useDarkTheme = false) {
            PlayerAccountsBulkDialog(
                players = list,
                nameOf = { it.name },
                results = results,
                busy = busy,
                errorMessage = null,
                onSend = onSend,
                onDismiss = {},
            )
        }
    }

    // ---------------------------------------------------------------- groups, selection, search

    @Test
    fun theDefaultGroupIsThePlayersWithNoAccount() = runComposeUiTest {
        show()

        onNodeWithText("All (4)").assertIsDisplayed()
        onNodeWithText("No account (2)").assertIsDisplayed()
        onNodeWithText("Has account (2)").assertIsDisplayed()
        onNodeWithText("P1").assertIsDisplayed()
        onNodeWithText("P3").assertIsDisplayed()
        onAllNodesWithText("P2").assertCountEquals(0)
    }

    @Test
    fun theSearchNarrowsTheList() = runComposeUiTest {
        show()

        onNodeWithText("All (4)").performClick()
        onNodeWithText("Search by name, number, email, EMA number or country").performTextInput("P4")

        onAllNodesWithText("P1").assertCountEquals(0)
        onAllNodesWithText("P3").assertCountEquals(0)
        onNodeWithText("Select all shown (1)").assertIsDisplayed()
    }

    @Test
    fun theListHasColumnTitlesThatStayWhileTheRowsScroll() = runComposeUiTest {
        val many = (1..60).map { TournamentPlayer(id = it, name = "Player $it", team = 0, assignedEmaId = "E$it", hasEmail = true) }
        show(list = many)

        listOf("ID", "Country", "EMA ID", "Name", "Email", "Account").forEach { onNodeWithText(it).assertIsDisplayed() }
        onNodeWithText("Player 1").assertIsDisplayed()
        onNodeWithTag("account_list").performScrollToIndex(50)
        waitForIdle()

        listOf("ID", "Country", "EMA ID", "Name", "Email", "Account").forEach { onNodeWithText(it).assertIsDisplayed() }
        onAllNodesWithText("Player 1").assertCountEquals(0)
    }

    @Test
    fun theStateOfEachPlayerIsACheckOrACross() = runComposeUiTest {
        show()

        onNodeWithText("All (4)").performClick()

        onAllNodesWithContentDescription("Has account").assertCountEquals(2)
        onAllNodesWithContentDescription("No account").assertCountEquals(2)
    }

    @Test
    fun aPlayerWithNoAccountAndNoEmailShowsAWarning() = runComposeUiTest {
        show()

        onNodeWithText("No email").assertIsDisplayed()
    }

    @Test
    fun sendsOnlyTheSelectedPlayersAndTheSelectionClearsWhenTheFilterChanges() = runComposeUiTest {
        var sent: List<Int> = emptyList()
        show(onSend = { ids, _ -> sent = ids })

        onNodeWithText("All (4)").performClick()
        onNodeWithText("Select all shown (4)").performClick()
        onNodeWithText("Clear").performClick()
        onNodeWithText("Send (0)").assertIsNotEnabled()
        onNodeWithText("Has account (2)").performClick()
        onNodeWithText("Send (0)").assertIsNotEnabled()

        assertEquals(emptyList(), sent)
    }

    @Test
    fun theSendButtonIsOnWhenSomeoneIsSelected() = runComposeUiTest {
        show()

        onNodeWithText("P1").performClick()
        onNodeWithText("Select all shown (2)").performClick()

        onNodeWithText("Send (2)").assertIsEnabled()
    }

    // ---------------------------------------------------------------- sending

    @Test
    fun sendsToEveryPlayerWithNoAccountAtOnceAfterTheMissingEmailIsTyped() = runComposeUiTest {
        var sent: List<Int> = emptyList()
        var typed: Map<Int, String> = emptyMap()
        show(onSend = { ids, emails -> sent = ids; typed = emails })

        onNodeWithText("Select all shown (2)").performClick()
        onNodeWithText("New accounts: 2 · Reminders: 0").assertIsDisplayed()
        onNodeWithText("Send (2)").performClick()
        onNodeWithText("Emails are missing").assertIsDisplayed()
        onNodeWithText("3 · P3").performTextInput("p3@example.test")
        onNodeWithText("Send (1)").performClick()

        assertEquals(listOf(1, 3), sent)
        assertEquals(mapOf(3 to "p3@example.test"), typed)
    }

    @Test
    fun aPlayerLeftBlankInTheMissingEmailsDialogIsNotSent() = runComposeUiTest {
        var sent: List<Int>? = null
        var typed: Map<Int, String> = emptyMap()
        val list = listOf(
            TournamentPlayer(id = 3, name = "P3", team = 0, assignedEmaId = "33"),
            TournamentPlayer(id = 5, name = "P5", team = 0, assignedEmaId = "55"),
            players[0],
        )
        show(list = list, onSend = { ids, emails -> sent = ids; typed = emails })

        onNodeWithText("Select all shown (3)").performClick()
        onNodeWithText("Send (3)").performClick()
        onNodeWithText("5 · P5").performTextInput("p5@example.test")
        onNodeWithText("Send (1)").performClick()

        assertEquals(listOf(5, 1), sent)
        assertEquals(mapOf(5 to "p5@example.test"), typed)
    }

    @Test
    fun theMissingEmailsDialogNeedsAValidEmailAndCanBeCancelled() = runComposeUiTest {
        var sent = false
        show(onSend = { _, _ -> sent = true })

        onNodeWithText("Select all shown (2)").performClick()
        onNodeWithText("Send (2)").performClick()
        onNodeWithText("Send (0)").assertIsNotEnabled()
        onNodeWithText("3 · P3").performTextInput("not-an-email")
        onNodeWithText("Send (1)").assertIsNotEnabled()
        onAllNodesWithText("Cancel").onLast().performClick()
        waitForIdle()

        onAllNodesWithText("Emails are missing").assertCountEquals(0)
        onNodeWithText("2 selected").assertIsDisplayed()
        assertEquals(false, sent)
    }

    @Test
    fun noEmailsAreAskedWhenEverySelectedPlayerHasAnAccountOrAnEmail() = runComposeUiTest {
        var sent: List<Int> = emptyList()
        var typed: Map<Int, String> = mapOf(0 to "x")
        show(list = listOf(players[0], players[1]), onSend = { ids, emails -> sent = ids; typed = emails })

        onNodeWithText("All (2)").performClick()
        onNodeWithText("Select all shown (2)").performClick()
        onNodeWithText("Send (2)").performClick()

        onAllNodesWithText("Emails are missing").assertCountEquals(0)
        assertEquals(listOf(1, 2), sent)
        assertEquals(emptyMap(), typed)
    }

    @Test
    fun sendsToEveryPlayerWithAnAccountAtOnce() = runComposeUiTest {
        var sent: List<Int> = emptyList()
        show(onSend = { ids, _ -> sent = ids })

        onNodeWithText("Has account (2)").performClick()
        onNodeWithText("Select all shown (2)").performClick()
        onNodeWithText("New accounts: 0 · Reminders: 2").assertIsDisplayed()
        onNodeWithText("Send (2)").performClick()

        assertEquals(listOf(2, 4), sent)
    }

    @Test
    fun aMixedSelectionGoesInOneSendAndTheSummaryCountsBothKinds() = runComposeUiTest {
        var sent: List<Int> = emptyList()
        show(onSend = { ids, _ -> sent = ids })

        onNodeWithText("All (4)").performClick()
        onNodeWithText("Select all shown (4)").performClick()
        onNodeWithText("New accounts: 2 · Reminders: 2").assertIsDisplayed()
        onNodeWithText("Send (4)").performClick()
        onNodeWithText("3 · P3").performTextInput("p3@example.test")
        onNodeWithText("Send (1)").performClick()

        assertEquals(listOf(1, 2, 3, 4), sent)
    }

    // ---------------------------------------------------------------- results on the rows

    @Test
    fun showsTheResultOfEachPlayerAfterTheSend() = runComposeUiTest {
        show(
            results = listOf(
                PlayerAccountActionResult(1, PlayerAccountOutcome.INVITED),
                PlayerAccountActionResult(3, PlayerAccountOutcome.FAILED, PlayerAccountFailure.NO_EMAIL),
            ),
        )

        onNodeWithText("Invited").assertIsDisplayed()
        onNodeWithText("The player has no email. Add an email and try again.").assertIsDisplayed()
    }

    @Test
    fun aReminderAndAnInvitationShowTheDateAndTimeOfTheEmail() = runComposeUiTest {
        val reminderAt = "2026-10-28T13:05:00.000Z"
        val invitationAt = "2026-10-29T08:30:00.000Z"
        show(
            results = listOf(
                PlayerAccountActionResult(2, PlayerAccountOutcome.REMINDED, sentAt = reminderAt),
                PlayerAccountActionResult(1, PlayerAccountOutcome.INVITED, sentAt = invitationAt),
            ),
            list = players.take(2),
        )

        onNodeWithText("All (2)").performClick()

        onNodeWithText("Reminder sent").assertIsDisplayed()
        onNodeWithText(localizedDateTimeOrNull(reminderAt)!!).assertIsDisplayed()
        onNodeWithText("Invited").assertIsDisplayed()
        onNodeWithText(localizedDateTimeOrNull(invitationAt)!!).assertIsDisplayed()
    }

    @Test
    fun aFailureShowsNoDateAndNeitherDoesAResultWithoutTime() = runComposeUiTest {
        show(
            results = listOf(
                PlayerAccountActionResult(1, PlayerAccountOutcome.FAILED, PlayerAccountFailure.NO_EMAIL, sentAt = "2026-10-28T13:05:00.000Z"),
                PlayerAccountActionResult(3, PlayerAccountOutcome.INVITED),
            ),
        )

        onNodeWithText("The player has no email. Add an email and try again.").assertIsDisplayed()
        onNodeWithText("Invited").assertIsDisplayed()
        onAllNodesWithText(localizedDateTimeOrNull("2026-10-28T13:05:00.000Z")!!).assertCountEquals(0)
    }

    @Test
    fun showsReminderAndEmailInUseResults() = runComposeUiTest {
        show(
            results = listOf(
                PlayerAccountActionResult(2, PlayerAccountOutcome.REMINDED),
                PlayerAccountActionResult(1, PlayerAccountOutcome.FAILED, PlayerAccountFailure.EMAIL_IN_USE),
            ),
            list = players.take(2),
        )

        onNodeWithText("All (2)").performClick()

        onNodeWithText("Reminder sent").assertIsDisplayed()
        onNodeWithText("Another account or another player already uses this email.").assertIsDisplayed()
    }

    // ---------------------------------------------------------------- feedback while and after the send

    @Test
    fun theSendButtonIsOffWhileTheAppIsBusy() = runComposeUiTest {
        show(busy = true)

        onNodeWithText("Select all shown (2)").assertIsNotEnabled()
        onNodeWithText("Send (0)").assertIsNotEnabled()
    }

    /** A host like the players screen: a send holds [busy] and then fills [results], like the real one. */
    private class Host {
        var busy by mutableStateOf(false)
        var results by mutableStateOf<List<PlayerAccountActionResult>>(emptyList())
    }

    private fun ComposeUiTest.showHost(host: Host, list: List<TournamentPlayer> = players) = setContent {
        MtsTheme(useDarkTheme = false) {
            PlayerAccountsBulkDialog(
                players = list,
                nameOf = { it.name },
                results = host.results,
                busy = host.busy,
                errorMessage = null,
                onSend = { _, _ -> },
                onDismiss = {},
            )
        }
    }

    @Test
    fun aLoadingDialogShowsWhileTheSendRunsAndGoesAwayWhenItEnds() = runComposeUiTest {
        val host = Host()
        showHost(host)

        host.busy = true
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Sending").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("The app is sending the emails. Wait until it finishes.").assertIsDisplayed()
        host.results = listOf(PlayerAccountActionResult(1, PlayerAccountOutcome.INVITED))
        host.busy = false
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Sending").fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun whenTheSendEndsASummaryShowsWhatHappenedAndListsTheFailures() = runComposeUiTest {
        val host = Host()
        showHost(host)

        host.busy = true
        waitForIdle()
        host.results = listOf(
            PlayerAccountActionResult(1, PlayerAccountOutcome.INVITED),
            PlayerAccountActionResult(2, PlayerAccountOutcome.REMINDED),
            PlayerAccountActionResult(4, PlayerAccountOutcome.REMINDED),
            PlayerAccountActionResult(3, PlayerAccountOutcome.FAILED, PlayerAccountFailure.EMAIL_IN_USE),
        )
        host.busy = false
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Done").fetchSemanticsNodes().isNotEmpty() }

        onNodeWithText("Accounts created and invitations sent: 1").assertIsDisplayed()
        onNodeWithText("Reminders sent: 2").assertIsDisplayed()
        onNodeWithText("Not sent: 1").assertIsDisplayed()
        onNodeWithText("• 3 · P3: Another account or another player already uses this email.").assertIsDisplayed()
    }

    @Test
    fun theSummaryClosesWithOkAndDoesNotComeBackOnItsOwn() = runComposeUiTest {
        val host = Host()
        showHost(host)
        host.busy = true
        waitForIdle()
        host.results = listOf(PlayerAccountActionResult(1, PlayerAccountOutcome.INVITED))
        host.busy = false
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Done").fetchSemanticsNodes().isNotEmpty() }

        onNodeWithText("OK").performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Done").fetchSemanticsNodes().isEmpty() }
        waitForIdle()

        onAllNodesWithText("Done").assertCountEquals(0)
    }

    @Test
    fun aSendWithoutResultsShowsNoSummary() = runComposeUiTest {
        val host = Host()
        showHost(host)

        host.busy = true
        waitForIdle()
        host.busy = false
        waitForIdle()

        onAllNodesWithText("Done").assertCountEquals(0)
        onAllNodesWithText("Sending").assertCountEquals(0)
    }
}
