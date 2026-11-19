package com.etologic.mahjongtournamentsuite.domain.model

/**
 * A role that a super admin made. It builds on [base], Editor or Country Admin, which gives its scope (the assigned
 * tournaments or the countries) and the permissions that it may hold. See the backend `services/customPermissions.ts`.
 */
data class CustomRole(
    val id: String,
    val name: String,
    val base: GlobalUserRole,
    val permissions: Set<Permission>,
    /** How many accounts use the role. */
    val accounts: Int = 0,
)

/** The standard roles, the custom ones and what a role that builds on Editor or Country Admin may hold. */
data class RolesCatalog(
    val standard: Map<GlobalUserRole, Set<Permission>>,
    val custom: List<CustomRole>,
    val grantable: Map<GlobalUserRole, Set<Permission>>,
    /** The permissions that every role keeps. */
    val required: Set<Permission>,
) {
    fun customFor(base: GlobalUserRole): List<CustomRole> = custom.filter { it.base == base }

    /** The permissions of the role that an account uses at [level]: its custom role, or the standard one. */
    fun permissionsOf(level: GlobalUserRole, customRoleId: String?): Set<Permission> = custom.firstOrNull { it.id == customRoleId && it.base == level }?.permissions
        ?: standard[level]
        ?: Permission.defaultsOf(level)

    companion object {
        /** The roles whose permissions can change: a super admin always has all of them. */
        val CUSTOMIZABLE: List<GlobalUserRole> = listOf(GlobalUserRole.EDITOR, GlobalUserRole.CLUB_ADMIN)
    }
}

/** The permissions that one account has more or less than its role. */
data class PermissionChange(
    val added: Set<Permission> = emptySet(),
    val removed: Set<Permission> = emptySet(),
)

/**
 * What an account gets at one level: [customRoleId] (null for the standard role) and, only from a super admin, the
 * whole list of [permissions]. The server keeps the difference with the role. Null [permissions] keeps the changes
 * that the account has.
 */
data class LevelPermissions(
    val customRoleId: String?,
    val permissions: Set<Permission>? = null,
)
