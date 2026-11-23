package com.etologic.mahjongtournamentsuite.domain.model

/** An account of the app that can get a staff ID card. The backend sends only its name, never its email. */
data class IdCardStaffCandidate(
    val uid: String,
    val name: String,
    val role: GlobalUserRole,
    /** True when the account is an editor or an admin of the tournament. */
    val inTournament: Boolean,
)
