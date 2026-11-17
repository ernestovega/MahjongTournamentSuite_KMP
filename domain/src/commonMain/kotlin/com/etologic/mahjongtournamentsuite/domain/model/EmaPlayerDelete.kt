package com.etologic.mahjongtournamentsuite.domain.model

/** DELETED: the player is gone. DISABLED: it has data in tournaments, so it is deactivated instead. */
enum class EmaPlayerDeleteOutcome { DELETED, DISABLED, FAILED }

/** Why an EMA player was neither deleted nor deactivated. Deleting an EMA player never deletes an account. */
enum class EmaPlayerDeleteFailure { NOT_FOUND, UNKNOWN }

/** The result for one EMA player of a bulk delete from the Player base. */
data class EmaPlayerDeleteResult(
    val emaId: String,
    val outcome: EmaPlayerDeleteOutcome,
    val reason: EmaPlayerDeleteFailure? = null,
)

/** A tournament where an EMA player has data. */
data class EmaPlayerTournament(val id: String, val name: String)

/** The tournaments where an EMA player has data. A delete deactivates such a player instead. */
data class EmaPlayerTournaments(val emaId: String, val tournaments: List<EmaPlayerTournament>)
