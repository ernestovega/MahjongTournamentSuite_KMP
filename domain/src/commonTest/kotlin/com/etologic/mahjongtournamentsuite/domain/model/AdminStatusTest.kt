package com.etologic.mahjongtournamentsuite.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AdminStatusTest {
    @Test
    fun editorCreatesPlayersButDoesNotManageStaffOrTournaments() {
        val status = AdminStatus(uid = "editor", role = GlobalUserRole.EDITOR)

        assertTrue(status.canManageUsers)
        assertTrue(status.has(Permission.ACCOUNTS_CREATE_PLAYER))
        assertTrue(status.has(Permission.TOURNAMENT_ROUND_CONTROL))
        assertFalse(status.isSuperAdmin)
        assertFalse(status.isCountryAdmin)
        assertFalse(status.canManageStaff)
        assertFalse(status.canCreateTournaments)
        assertFalse(status.has(Permission.TOURNAMENT_DELETE))
        assertFalse(status.canCreateEmaPlayers)
        assertFalse(status.canDisableUsers)
        assertFalse(status.canUseDevMenu)
    }

    @Test
    fun countryAdminManagesItsCountriesButNotTheWholeApp() {
        val status = AdminStatus(uid = "ca", role = GlobalUserRole.COUNTRY_ADMIN, adminCountries = setOf("ES"))

        assertTrue(status.isCountryAdmin)
        assertTrue(status.canCreateTournaments)
        assertTrue(status.canManageStaff)
        assertTrue(status.canCreateEmaPlayers)
        assertTrue(status.canRequestRemoval)
        assertTrue(status.has(Permission.TOURNAMENT_DELETE))
        assertFalse(status.isSuperAdmin)
        assertFalse(status.canEditEmaPlayers)
        assertFalse(status.canDeleteEmaPlayers)
        assertFalse(status.canDisableUsers)
        assertFalse(status.canDeleteUsers)
        assertFalse(status.canChangeEmails)
        assertFalse(status.canChangeEmaIds)
        assertFalse(status.canChangeTournamentCountry)
        assertFalse(status.canReviewRequests)
        assertFalse(status.canUseDevMenu)
    }

    @Test
    fun superAdminHasEveryPermissionButTheRemovalRequest() {
        val status = AdminStatus(uid = "sa", role = GlobalUserRole.SUPER_ADMIN)

        assertTrue(status.isSuperAdmin)
        assertTrue(status.canDisableUsers)
        assertTrue(status.canDeleteUsers)
        assertTrue(status.canEditEmaPlayers)
        assertTrue(status.canReviewRequests)
        assertTrue(status.canUseDevMenu)
        assertTrue(status.canChangeTournamentCountry)
        assertFalse(status.canRequestRemoval)
    }

    @Test
    fun theTournamentPermissionsDecideInsideATournament() {
        val status = AdminStatus(uid = "ca", role = GlobalUserRole.COUNTRY_ADMIN, adminCountries = setOf("ES"))

        // A tournament of another country where the account is an editor: no delete there.
        assertFalse(status.can(Permission.TOURNAMENT_DELETE, setOf(Permission.TOURNAMENT_VIEW, Permission.TOURNAMENT_EDIT)))
        // A tournament without the list falls back to the permissions of the account.
        assertTrue(status.can(Permission.TOURNAMENT_DELETE, emptySet()))
    }

    @Test
    fun theKeysOfThePermissionsAreTheOnesOfTheServer() {
        assertEquals(setOf(Permission.TOURNAMENT_ROUND_CONTROL), Permission.fromKeys(listOf("tournament.roundControl", "unknown.key")))
    }
}
