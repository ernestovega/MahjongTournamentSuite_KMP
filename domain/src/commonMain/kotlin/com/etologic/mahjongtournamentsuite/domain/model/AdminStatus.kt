package com.etologic.mahjongtournamentsuite.domain.model

data class AdminStatus(
    val uid: String,
    val isAdmin: Boolean,
    val isSuperadmin: Boolean,
) {
    val canEditPlayers: Boolean get() = isAdmin || isSuperadmin
}
