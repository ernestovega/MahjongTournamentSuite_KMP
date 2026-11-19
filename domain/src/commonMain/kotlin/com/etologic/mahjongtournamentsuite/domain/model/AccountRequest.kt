package com.etologic.mahjongtournamentsuite.domain.model

/** What a country admin asks the super admins to do with a whole account. */
enum class AccountRequestKind { DISABLE, DELETE }

/**
 * A request of a country admin to disable or delete an account for everyone. The country admin already took the
 * account out of its countries; a super admin approves or rejects the rest in the Accounts screen.
 */
data class AccountRequest(
    val id: String,
    val kind: AccountRequestKind,
    val targetUid: String,
    val targetEmail: String,
    val reason: String,
    val requestedByEmail: String,
    val createdAtMillis: Long,
)
