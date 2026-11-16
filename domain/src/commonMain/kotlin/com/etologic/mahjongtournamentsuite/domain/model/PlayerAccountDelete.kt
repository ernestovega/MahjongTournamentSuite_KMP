package com.etologic.mahjongtournamentsuite.domain.model

enum class PlayerAccountDeleteOutcome { DELETED, FAILED }

/** Why the account of an EMA player was not deleted. The EMA player itself is never deleted. */
enum class PlayerAccountDeleteFailure { NO_ACCOUNT, SELF, LAST_ADMIN, UNKNOWN }

/** The result for one EMA player of a bulk delete of accounts from the Player base. */
data class PlayerAccountDeleteResult(
    val emaId: String,
    val outcome: PlayerAccountDeleteOutcome,
    val reason: PlayerAccountDeleteFailure? = null,
)
