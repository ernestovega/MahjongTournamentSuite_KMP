package com.etologic.mahjongtournamentsuite.domain.model

enum class UserDeleteOutcome { DELETED, FAILED }

/** Why an account of a bulk delete was not deleted. */
enum class UserDeleteFailure { NOT_FOUND, SELF, LAST_ADMIN, UNKNOWN }

data class UserDeleteResult(
    val uid: String,
    val outcome: UserDeleteOutcome,
    val reason: UserDeleteFailure? = null,
)
