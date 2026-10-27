package com.etologic.mahjongtournamentsuite.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

class PlayerContactStateTest {
    private fun slot(
        ema: String? = "1",
        status: InvitationStatus = InvitationStatus.NONE,
        account: String? = null,
        contactedAt: String? = null,
    ) = TournamentPlayer(
        id = 1, name = "P", team = 0, assignedEmaId = ema, accountUid = account,
        invitationStatus = status, contactedAt = contactedAt,
    )

    @Test
    fun readsTheStateOfEachSlot() {
        assertEquals(PlayerContactState.NO_EMA_ID, slot(ema = null).contactState)
        assertEquals(PlayerContactState.NOT_INVITED, slot().contactState)
        assertEquals(PlayerContactState.LINKED, slot(status = InvitationStatus.ACCEPTED, account = "u").contactState)
        assertEquals(PlayerContactState.INVITED, slot(status = InvitationStatus.INVITED, contactedAt = "t").contactState)
        assertEquals(
            PlayerContactState.CONTACTED,
            slot(status = InvitationStatus.ACCEPTED, account = "u", contactedAt = "t").contactState,
        )
    }

    @Test
    fun anAccountWithNoPasswordThatGotNoEmailYetIsLinked() {
        // The server reports NONE for it: the account exists, but nobody wrote to the person.
        assertEquals(PlayerContactState.LINKED, slot(status = InvitationStatus.NONE, account = "u").contactState)
        assertEquals(PlayerContactState.INVITED, slot(status = InvitationStatus.INVITED, account = "u", contactedAt = "t").contactState)
    }

    @Test
    fun aSlotIsAssignedOnlyWithAnEmaId() {
        assertEquals(true, slot(ema = "1").isAssigned)
        assertEquals(false, slot(ema = null).isAssigned)
        assertEquals(false, slot(ema = " ").isAssigned)
    }

    @Test
    fun theEmailComesFromTheAccount() {
        val linked = slot(account = "u").copy(email = "a@b.es")
        assertEquals("a@b.es", linked.email)
        assertEquals(null, slot().email)
    }
}
