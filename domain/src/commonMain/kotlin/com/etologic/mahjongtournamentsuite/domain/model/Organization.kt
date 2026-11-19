package com.etologic.mahjongtournamentsuite.domain.model

/** A club or a federation. A federation works like a club: it organizes tournaments and has club admins. */
enum class OrganizationType {
    CLUB,
    FEDERATION,
}

/**
 * An organization that owns tournaments. Each tournament has one or more organizers; the first one is the main
 * organizer, and the tournament shows its logo and its color. The club admins of an organization manage its tournaments.
 */
data class Organization(
    val id: String,
    val name: String,
    val type: OrganizationType = OrganizationType.CLUB,
    /** Two-letter ISO code. */
    val country: String = "",
    val primaryColor: String = DEFAULT_ORGANIZATION_COLOR,
    val logoUrl: String? = null,
    /** True when the signed-in account may edit it: a super admin, or a club admin of it. */
    val canEdit: Boolean = false,
    /** True when the signed-in account may delete it: only a super admin. */
    val canDelete: Boolean = false,
)

const val DEFAULT_ORGANIZATION_COLOR = "#02B16B"

/** What the create and the edit of an organization send. A null logo keeps the current one. */
data class OrganizationDraft(
    val name: String,
    val type: OrganizationType,
    val country: String,
    val primaryColor: String,
    val logoContentType: String? = null,
    val logoBytes: ByteArray? = null,
    val removeLogo: Boolean = false,
)
