package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import com.etologic.mahjongtournamentsuite.domain.model.InvitationStatus
import com.etologic.mahjongtournamentsuite.domain.model.PlayerAccountAction
import com.etologic.mahjongtournamentsuite.domain.model.TournamentPlayer
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/** The account dialog of one slot. The email lives only in the account, so a slot without an account asks for it. */
@OptIn(ExperimentalTestApi::class)
class PlayerAccountDialogUiTest {
    private fun androidx.compose.ui.test.ComposeUiTest.show(
        slot: TournamentPlayer,
        onAction: (PlayerAccountAction, String?) -> Unit = { _, _ -> },
        onSaveEmail: (String) -> Unit = {},
    ) = setContent {
        MtsTheme(useDarkTheme = false) {
            PlayerAccountDialog(slot = slot, busy = false, onAction = onAction, onSaveEmail = onSaveEmail, onDismiss = {})
        }
    }

    @Test
    fun aSlotWithoutAnAccountAsksForTheEmailAndSendsOnlyAValidOne() = runComposeUiTest {
        var action: Pair<PlayerAccountAction, String?>? = null
        show(TournamentPlayer(id = 7, name = "", team = 0, assignedEmaId = "11"), onAction = { a, e -> action = a to e })

        onNodeWithText("Account of player 7").assertIsDisplayed()
        onNodeWithText("Send invitation").assertIsNotEnabled()
        onNodeWithText("Save email only").assertIsNotEnabled()

        onNodeWithText("Email").performTextInput("not-an-email")
        onNodeWithText("Send invitation").assertIsNotEnabled()
        onNodeWithText("Email").performTextInput("@")
        onNodeWithText("Send invitation").assertIsNotEnabled()
        onNodeWithText("Email").performTextInput("b.es")
        onNodeWithText("Send invitation").assertIsEnabled()
        onNodeWithText("Send invitation").performClick()

        assertEquals(PlayerAccountAction.SEND to "not-an-email@b.es", action)
    }

    @Test
    fun saveEmailOnlyCreatesTheAccountWithoutSending() = runComposeUiTest {
        var saved: String? = null
        var sent = false
        show(
            TournamentPlayer(id = 7, name = "", team = 0, assignedEmaId = "11"),
            onAction = { _, _ -> sent = true },
            onSaveEmail = { saved = it },
        )

        onNodeWithText("Email").performTextInput("ana@b.es")
        onNodeWithText("Save email only").performClick()

        assertEquals("ana@b.es", saved)
        assertEquals(false, sent)
    }

    @Test
    fun aLinkedSlotShowsTheEmailOfTheAccountAndOffersTheReminder() = runComposeUiTest {
        var action: Pair<PlayerAccountAction, String?>? = null
        show(
            TournamentPlayer(
                id = 7, name = "", team = 0, assignedEmaId = "11", accountUid = "u", invitationStatus = InvitationStatus.ACCEPTED,
                email = "account@b.es",
            ),
            onAction = { a, e -> action = a to e },
        )

        onNodeWithText("account@b.es").assertIsDisplayed()
        onNodeWithText("Send reminder").performClick()

        assertEquals(PlayerAccountAction.SEND to null, action)
        // The account has an email already: there is no field to type another one.
        onAllNodesWithText("Save email only").assertCountEquals(0)
    }

    @Test
    fun anInvitedSlotShowsTheEmailAndOffersToSendAgainAndToRemoveTheLink() = runComposeUiTest {
        var action: Pair<PlayerAccountAction, String?>? = null
        show(
            TournamentPlayer(
                id = 7, name = "", team = 0, assignedEmaId = "11", accountUid = "u", invitationStatus = InvitationStatus.INVITED,
                email = "invited@b.es", contactedAt = "2026-10-12T10:00:00Z",
            ),
            onAction = { a, e -> action = a to e },
        )

        onNodeWithText("invited@b.es").assertIsDisplayed()
        onNodeWithText("Send again").performClick()
        assertEquals(PlayerAccountAction.RESEND to null, action)

        onNodeWithText("Remove account link").performClick()
        assertEquals(PlayerAccountAction.REVOKE to null, action)
    }

    @Test
    fun aSlotWithoutAnEmaIdAsksToAssignAPlayerFirst() = runComposeUiTest {
        show(TournamentPlayer(id = 7, name = "", team = 0))

        onNodeWithText("Assign an EMA player to this slot first.").assertIsDisplayed()
        onAllNodesWithText("Send invitation").assertCountEquals(0)
    }
}
