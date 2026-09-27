package com.etologic.mahjongtournamentsuite.domain.model

data class AdminStatus(
    val uid: String,
    val role: GlobalUserRole,
) {
    val isAdmin: Boolean get() = role == GlobalUserRole.ADMIN
    val canCreateTournaments: Boolean get() = isAdmin
    val canDeleteTournaments: Boolean get() = isAdmin
    val canConfigureTournaments: Boolean get() = isAdmin
    val canEditPlayers: Boolean get() = isAdmin
    val canManageUsers: Boolean get() = true
    val canDisableUsers: Boolean get() = isAdmin
}
