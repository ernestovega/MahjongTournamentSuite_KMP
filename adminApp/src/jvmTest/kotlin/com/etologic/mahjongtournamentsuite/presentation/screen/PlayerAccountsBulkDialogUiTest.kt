package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import com.etologic.mahjongtournamentsuite.domain.model.InvitationStatus
import com.etologic.mahjongtournamentsuite.domain.model.PlayerAccountAction
import com.etologic.mahjongtournamentsuite.domain.model.PlayerAccountActionItem
import com.etologic.mahjongtournamentsuite.domain.model.TournamentPlayer
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class PlayerAccountsBulkDialogUiTest {
    private val players = listOf(
        TournamentPlayer(id = 1, name = "P1", team = 0, assignedEmaId = "11"),
        TournamentPlayer(id = 2, name = "P2", team = 0, assignedEmaId = "22", accountUid = "u2", invitationStatus = InvitationStatus.ACCEPTED),
        TournamentPlayer(
            id = 3, name = "P3", team = 0, assignedEmaId = "33",
            invitationStatus = InvitationStatus.INVITED, email = "p3@example.test", contactedAt = "2026-10-12T10:00:00Z",
        ),
        TournamentPlayer(
            id = 4, name = "P4", team = 0, assignedEmaId = "44", accountUid = "u4",
            invitationStatus = InvitationStatus.ACCEPTED, contactedAt = "2026-10-12T11:00:00Z",
        ),
        TournamentPlayer(id = 5, name = "P5", team = 0),
    )

    @Test
    fun filtersByContactStateAndRunsTheActionOnTheSelectedPlayersOnly() = runComposeUiTest {
        var action: PlayerAccountAction? = null
        var items: List<PlayerAccountActionItem> = emptyList()
        setContent {
            MtsTheme(useDarkTheme = false) {
                PlayerAccountsBulkDialog(
                    players = players,
                    nameOf = { it.name },
                    results = emptyList(),
                    busy = false,
                    errorMessage = null,
                    onAction = { a, i -> action = a; items = i },
                    onDismiss = {},
                )
            }
        }

        // The default group is "Not invited yet": a new player and a linked player that was not notified.
        onNodeWithText("Not invited yet (2)").assertIsDisplayed()
        onNodeWithText("P1").assertIsDisplayed()
        onNodeWithText("P2").assertIsDisplayed()
        onNodeWithText("Select all shown (2)").performClick()
        onNodeWithText("Invite or remind (2)").performClick()

        // P1 has no saved email, so the dialog asks for it first. P2 is linked and needs none.
        onNodeWithText("Emails are missing").assertIsDisplayed()
        onNodeWithText("1 · P1").performTextInput("p1@example.test")
        onNodeWithText("Invite or remind (1)").performClick()
        assertEquals(PlayerAccountAction.SEND, action)
        assertEquals(listOf(1, 2), items.map { it.playerId })
        assertEquals("p1@example.test", items.first().email)

        // The group "Invited, waiting" lists only the player with a pending invitation.
        onNodeWithText("Invited, waiting (1)").performClick()
        onNodeWithText("P3").assertIsDisplayed()
        onNodeWithText("Select all shown (1)").performClick()
        onNodeWithText("Resend (1)").performClick()
        assertEquals(PlayerAccountAction.RESEND, action)
        assertEquals(listOf(3), items.map { it.playerId })

        onNodeWithText("Contacted (2)").performClick()
        onNodeWithText("Select all shown (2)").performClick()
        onNodeWithText("Mark as not notified (2)").performClick()
        assertEquals(PlayerAccountAction.REVOKE, action)
        assertEquals(listOf(3, 4), items.map { it.playerId })
    }
}
