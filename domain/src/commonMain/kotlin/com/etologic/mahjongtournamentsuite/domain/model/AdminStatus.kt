package com.etologic.mahjongtournamentsuite.domain.model

/**
 * The signed-in account of the management app: its roles, the countries of a country admin, and the permissions
 * that it has somewhere. A tournament carries the permissions of the account in that tournament, see [can].
 */
data class AdminStatus(
    val uid: String,
    val role: GlobalUserRole,
    val roles: Set<GlobalUserRole> = setOf(role),
    val adminCountries: Set<String> = emptySet(),
    val permissions: Set<Permission> = roles.flatMapTo(mutableSetOf()) { Permission.defaultsOf(it) },
) {
    val isSuperAdmin: Boolean get() = role == GlobalUserRole.SUPER_ADMIN
    val isCountryAdmin: Boolean get() = GlobalUserRole.COUNTRY_ADMIN in roles

    fun has(permission: Permission): Boolean = permission in permissions

    /**
     * True when the account may use the permission in the tournament. The tournament list carries those permissions.
     * A tournament without the list (an old answer or a fake) falls back to the permissions of the account.
     */
    fun can(permission: Permission, tournamentPermissions: Set<Permission>): Boolean = if (tournamentPermissions.isEmpty()) has(permission) else permission in tournamentPermissions

    val canCreateTournaments: Boolean get() = has(Permission.TOURNAMENTS_CREATE)
    val canManageUsers: Boolean get() = has(Permission.ACCOUNTS_VIEW)
    val canManageStaff: Boolean get() = has(Permission.ACCOUNTS_MANAGE_STAFF)
    val canDisableUsers: Boolean get() = has(Permission.ACCOUNTS_DISABLE)
    val canDeleteUsers: Boolean get() = has(Permission.ACCOUNTS_DELETE)
    val canSendPasswordResets: Boolean get() = has(Permission.ACCOUNTS_PASSWORD_RESET)
    val canEditUserPhotos: Boolean get() = has(Permission.ACCOUNTS_PHOTO)
    val canChangeEmails: Boolean get() = has(Permission.ACCOUNTS_CHANGE_EMAIL)
    val canChangeEmaIds: Boolean get() = has(Permission.ACCOUNTS_CHANGE_EMA_ID)
    val canRequestRemoval: Boolean get() = has(Permission.ACCOUNTS_REQUEST_REMOVAL)
    val canReviewRequests: Boolean get() = has(Permission.ACCOUNTS_REVIEW_REQUESTS)
    val canCreateEmaPlayers: Boolean get() = has(Permission.EMA_CREATE)
    val canEditEmaPlayers: Boolean get() = has(Permission.EMA_EDIT)
    val canDeleteEmaPlayers: Boolean get() = has(Permission.EMA_DELETE)
    val canChangeTournamentCountry: Boolean get() = has(Permission.TOURNAMENTS_CHANGE_COUNTRY)

    /** The DEV menu: the activity of the whole app, the errors and the design system. */
    val canUseDevMenu: Boolean get() = has(Permission.DEV_ERRORS)
}
