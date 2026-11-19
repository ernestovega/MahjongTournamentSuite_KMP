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
        assertFalse(status.isClubAdmin)
        assertFalse(status.canManageStaff)
        assertFalse(status.canCreateTournaments)
        assertFalse(status.has(Permission.TOURNAMENT_DELETE))
        assertFalse(status.canCreateEmaPlayers)
        assertFalse(status.canDisableUsers)
        assertFalse(status.canUseDevMenu)
    }

    @Test
    fun clubAdminManagesItsOrganizationsButNotTheWholeApp() {
        val status = AdminStatus(uid = "ca", role = GlobalUserRole.CLUB_ADMIN, adminOrganizations = setOf("org-es"))

        assertTrue(status.isClubAdmin)
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
        assertTrue(status.canChangeTournamentOrganizers)
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
        assertTrue(status.canChangeTournamentOrganizers)
        assertFalse(status.canRequestRemoval)
    }

    @Test
    fun aClubAdminChangesTheOrganizersOnlyWhenItsClubIsTheMainOrganizer() {
        val status = AdminStatus(uid = "ca", role = GlobalUserRole.CLUB_ADMIN, adminOrganizations = setOf("madrid"))

        assertTrue(status.canChangeOrganizersOf(listOf("madrid", "paris")))
        assertFalse(status.canChangeOrganizersOf(listOf("paris", "madrid")), "a co-organizer does not")
        assertFalse(status.canChangeOrganizersOf(emptyList()))
        assertTrue(status.canKeepOrganizers(listOf("paris", "madrid")))
        assertFalse(status.canKeepOrganizers(listOf("paris")), "it never gives the tournament away")
    }

    @Test
    fun aSuperAdminChangesAnyOrganizersAndAnEditorNone() {
        val superAdmin = AdminStatus(uid = "sa", role = GlobalUserRole.SUPER_ADMIN)
        assertTrue(superAdmin.canChangeOrganizersOf(listOf("paris")))
        assertTrue(superAdmin.canKeepOrganizers(listOf("lisbon")))

        val editor = AdminStatus(uid = "ed", role = GlobalUserRole.EDITOR, adminOrganizations = setOf("madrid"))
        assertFalse(editor.canChangeOrganizersOf(listOf("madrid")))

        val withoutPermission = AdminStatus(
            uid = "ca",
            role = GlobalUserRole.CLUB_ADMIN,
            adminOrganizations = setOf("madrid"),
            permissions = Permission.defaultsOf(GlobalUserRole.CLUB_ADMIN) - Permission.TOURNAMENTS_CHANGE_ORGANIZERS,
        )
        assertFalse(withoutPermission.canChangeOrganizersOf(listOf("madrid")))
    }

    @Test
    fun aClubPlaceIsOptionalButNeedsALinkOrAnAddress() {
        assertTrue(OrganizationPlace().isEmpty)
        assertTrue(OrganizationPlace().isComplete)
        assertTrue(OrganizationPlace(mapUrl = "https://maps.app.goo.gl/x").isComplete)
        assertTrue(OrganizationPlace(address = "Calle Mayor 1").isComplete)
        assertFalse(OrganizationPlace(name = "Club room").isComplete)
        assertFalse(OrganizationPlace(city = "Madrid").isComplete)
        assertFalse(OrganizationPlace(city = "Madrid").isEmpty)
    }

    @Test
    fun theTournamentPermissionsDecideInsideATournament() {
        val status = AdminStatus(uid = "ca", role = GlobalUserRole.CLUB_ADMIN, adminOrganizations = setOf("org-es"))

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
