package com.etologic.mahjongtournamentsuite.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

class RolesCatalogTest {
    private val referee = CustomRole("referee", "Head referee", GlobalUserRole.EDITOR, setOf(Permission.TOURNAMENT_VIEW, Permission.TOURNAMENT_EDIT))
    private val smallAdmin = CustomRole("small", "Small admin", GlobalUserRole.CLUB_ADMIN, setOf(Permission.TOURNAMENT_VIEW))
    private val catalog = RolesCatalog(
        standard = mapOf(GlobalUserRole.EDITOR to setOf(Permission.TOURNAMENT_VIEW, Permission.TOURNAMENT_ROUND_CONTROL)),
        custom = listOf(referee, smallAdmin),
        grantable = emptyMap(),
        required = setOf(Permission.TOURNAMENT_VIEW),
    )

    @Test
    fun aCustomRoleGivesItsPermissionsOnlyAtItsOwnLevel() {
        assertEquals(referee.permissions, catalog.permissionsOf(GlobalUserRole.EDITOR, "referee"))
        assertEquals(Permission.defaultsOf(GlobalUserRole.CLUB_ADMIN), catalog.permissionsOf(GlobalUserRole.CLUB_ADMIN, "referee"))
    }

    @Test
    fun withoutACustomRoleTheStandardOneCountsAndThenTheAppDefaults() {
        assertEquals(setOf(Permission.TOURNAMENT_VIEW, Permission.TOURNAMENT_ROUND_CONTROL), catalog.permissionsOf(GlobalUserRole.EDITOR, null))
        assertEquals(setOf(Permission.TOURNAMENT_VIEW, Permission.TOURNAMENT_ROUND_CONTROL), catalog.permissionsOf(GlobalUserRole.EDITOR, "deleted"))
        assertEquals(Permission.defaultsOf(GlobalUserRole.CLUB_ADMIN), catalog.permissionsOf(GlobalUserRole.CLUB_ADMIN, null))
    }

    @Test
    fun listsTheCustomRolesOfEachBase() {
        assertEquals(listOf(referee), catalog.customFor(GlobalUserRole.EDITOR))
        assertEquals(listOf(smallAdmin), catalog.customFor(GlobalUserRole.CLUB_ADMIN))
        assertEquals(listOf(GlobalUserRole.EDITOR, GlobalUserRole.CLUB_ADMIN), RolesCatalog.CUSTOMIZABLE)
    }
}
