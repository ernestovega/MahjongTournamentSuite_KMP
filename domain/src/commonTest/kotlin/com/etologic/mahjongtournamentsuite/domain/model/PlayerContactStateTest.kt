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
}
