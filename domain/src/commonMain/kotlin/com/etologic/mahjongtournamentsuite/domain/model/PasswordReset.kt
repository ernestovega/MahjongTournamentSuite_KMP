package com.etologic.mahjongtournamentsuite.domain.model

enum class PasswordResetOutcome { SENT, FAILED }

/** Why the password reset email did not go to an account. */
enum class PasswordResetFailure { NOT_FOUND, NO_EMAIL, DISABLED, EMAIL_FAILED, UNKNOWN }

data class PasswordResetResult(
    val uid: String,
    val outcome: PasswordResetOutcome,
    val reason: PasswordResetFailure? = null,
)
