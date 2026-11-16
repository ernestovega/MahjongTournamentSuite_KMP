package com.etologic.mahjongtournamentsuite.domain.model

enum class PlayerDeleteOutcome { DELETED, FAILED }

/** Why an EMA player of a bulk delete was not deleted. A tournament or an account that uses the player keeps it. */
enum class PlayerDeleteFailure { NOT_FOUND, IN_TOURNAMENT, HAS_ACCOUNT, UNKNOWN }

data class PlayerDeleteResult(
    val emaId: String,
    val outcome: PlayerDeleteOutcome,
    val reason: PlayerDeleteFailure? = null,
)
