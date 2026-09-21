package com.etologic.mahjongtournamentsuite.domain.model

data class AdminStatus(
    val uid: String,
    val isSuperadmin: Boolean,
) {
    val canEditPlayers: Boolean get() = isSuperadmin
    val canManageUsers: Boolean get() = isSuperadmin
}
