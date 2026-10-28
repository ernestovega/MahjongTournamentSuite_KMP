package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import com.etologic.mahjongtournamentsuite.domain.model.PlayerAccountActionResult
import com.etologic.mahjongtournamentsuite.domain.model.PlayerAccountFailure
import com.etologic.mahjongtournamentsuite.domain.model.PlayerAccountOutcome
import com.etologic.mahjongtournamentsuite.domain.model.TournamentPlayer
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/** The editor sees who has an account, filters, selects, and sends. The server picks the action for each player. */
@OptIn(ExperimentalTestApi::class)
class PlayerAccountsBulkDialogUiTest {
    private val players = listOf(
        TournamentPlayer(id = 1, name = "P1", team = 0, assignedEmaId = "11", hasEmail = true),
        TournamentPlayer(id = 2, name = "P2", team = 0, assignedEmaId = "22", accountUid = "u2", hasAccount = true, hasEmail = true),
        TournamentPlayer(id = 3, name = "P3", team = 0, assignedEmaId = "33", hasEmail = false),
        TournamentPlayer(id = 4, name = "P4", team = 0, assignedEmaId = "44", accountUid = "u4", hasAccount = true, hasEmail = true),
    )

    private fun androidx.compose.ui.test.ComposeUiTest.show(
        results: List<PlayerAccountActionResult> = emptyList(),
        busy: Boolean = false,
        list: List<TournamentPlayer> = players,
        onSend: (List<Int>) -> Unit = {},
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
    fun sendsToEveryPlayerWithNoAccountAtOnce() = runComposeUiTest {
        var sent: List<Int> = emptyList()
        show(onSend = { sent = it })

        onNodeWithText("Select all shown (2)").performClick()
        onNodeWithText("New accounts: 2 · Password resets: 0").assertIsDisplayed()
        onNodeWithText("Send (2)").performClick()

        assertEquals(listOf(1, 3), sent)
    }

    @Test
    fun sendsToEveryPlayerWithAnAccountAtOnce() = runComposeUiTest {
        var sent: List<Int> = emptyList()
        show(onSend = { sent = it })

        onNodeWithText("Has account (2)").performClick()
        onNodeWithText("Select all shown (2)").performClick()
        onNodeWithText("New accounts: 0 · Password resets: 2").assertIsDisplayed()
        onNodeWithText("Send (2)").performClick()

        assertEquals(listOf(2, 4), sent)
    }

    @Test
    fun aMixedSelectionGoesInOneSendAndTheSummaryCountsBothKinds() = runComposeUiTest {
        var sent: List<Int> = emptyList()
        show(onSend = { sent = it })

        onNodeWithText("All (4)").performClick()
        onNodeWithText("Select all shown (4)").performClick()
        onNodeWithText("New accounts: 2 · Password resets: 2").assertIsDisplayed()
        onNodeWithText("Send (4)").performClick()

        assertEquals(listOf(1, 2, 3, 4), sent)
    }

    @Test
    fun sendsOnlyTheSelectedPlayersAndTheSelectionClearsWhenTheFilterChanges() = runComposeUiTest {
        var sent: List<Int> = emptyList()
        show(onSend = { sent = it })

        onNodeWithText("All (4)").performClick()
        onNodeWithText("Select all shown (4)").performClick()
        onNodeWithText("Clear").performClick()
        onNodeWithText("Send (0)").assertIsNotEnabled()
        onNodeWithText("Has account (2)").performClick()
        onNodeWithText("Send (0)").assertIsNotEnabled()

        assertEquals(emptyList(), sent)
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
    fun aPlayerWithNoAccountAndNoEmailShowsAWarning() = runComposeUiTest {
        show()

        onNodeWithText("No email").assertIsDisplayed()
    }

    @Test
    fun showsTheResultOfEachPlayerAfterTheSend() = runComposeUiTest {
        show(
            results = listOf(
                PlayerAccountActionResult(1, PlayerAccountOutcome.INVITED),
                PlayerAccountActionResult(3, PlayerAccountOutcome.FAILED, PlayerAccountFailure.NO_EMAIL),
            ),
        )

        onNodeWithText("Invited").assertIsDisplayed()
        onNodeWithText("The player has no email. Import a file with the email.").assertIsDisplayed()
    }

    @Test
    fun showsResetAndEmailInUseResults() = runComposeUiTest {
        show(
            results = listOf(
                PlayerAccountActionResult(2, PlayerAccountOutcome.RESET_SENT),
                PlayerAccountActionResult(1, PlayerAccountOutcome.FAILED, PlayerAccountFailure.EMAIL_IN_USE),
            ),
            list = players.take(2),
        )

        onNodeWithText("All (2)").performClick()

        onNodeWithText("Password reset sent").assertIsDisplayed()
        onNodeWithText("Another account already uses this email.").assertIsDisplayed()
    }

    @Test
    fun theSendButtonIsOffWhileTheAppIsBusy() = runComposeUiTest {
        show(busy = true)

        onNodeWithText("Select all shown (2)").assertIsNotEnabled()
        onNodeWithText("Send (0)").assertIsNotEnabled()
    }

    @Test
    fun theSendButtonIsOnWhenSomeoneIsSelected() = runComposeUiTest {
        show()

        onNodeWithText("P1").performClick()
        onNodeWithText("Select all shown (2)").performClick()

        onNodeWithText("Send (2)").assertIsEnabled()
    }
}
