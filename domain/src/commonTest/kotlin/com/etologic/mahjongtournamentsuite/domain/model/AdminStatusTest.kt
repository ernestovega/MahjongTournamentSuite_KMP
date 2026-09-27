package com.etologic.mahjongtournamentsuite.domain.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AdminStatusTest {
    @Test
    fun editorCanManageAccountsButCannotConfigureTournaments() {
        val status = AdminStatus(uid = "editor", role = GlobalUserRole.EDITOR)

        assertTrue(status.canManageUsers)
        assertFalse(status.isAdmin)
        assertFalse(status.canCreateTournaments)
        assertFalse(status.canDeleteTournaments)
        assertFalse(status.canConfigureTournaments)
        assertFalse(status.canEditPlayers)
        assertFalse(status.canDisableUsers)
    }

    @Test
    fun adminHasGlobalManagementPermissions() {
        val status = AdminStatus(uid = "admin", role = GlobalUserRole.ADMIN)

        assertTrue(status.canManageUsers)
        assertTrue(status.isAdmin)
        assertTrue(status.canCreateTournaments)
        assertTrue(status.canDeleteTournaments)
        assertTrue(status.canConfigureTournaments)
        assertTrue(status.canEditPlayers)
        assertTrue(status.canDisableUsers)
    }
}
