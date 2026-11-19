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
    val place: OrganizationPlace = OrganizationPlace(),
    val logoUrl: String? = null,
    /** True when the signed-in account may edit it: a super admin, or a club admin of it. */
    val canEdit: Boolean = false,
    /** True when the signed-in account may delete it: only a super admin. */
    val canDelete: Boolean = false,
)

const val DEFAULT_ORGANIZATION_COLOR = "#02B16B"

/**
 * The premises of a club, optional: some clubs have their own place, which is not the venue of their tournaments.
 * The form reads it from a Google Maps link, like the venue of a tournament. An unknown field is an empty text.
 */
data class OrganizationPlace(
    val mapUrl: String = "",
    val name: String = "",
    val address: String = "",
    val city: String = "",
) {
    val isEmpty: Boolean get() = mapUrl.isBlank() && name.isBlank() && address.isBlank() && city.isBlank()

    /** A place with some field needs a Google Maps link or an address, so people can find it. */
    val isComplete: Boolean get() = isEmpty || mapUrl.isNotBlank() || address.isNotBlank()
}

/** What the create and the edit of an organization send. A null logo keeps the current one. */
data class OrganizationDraft(
    val name: String,
    val type: OrganizationType,
    val country: String,
    val primaryColor: String,
    val place: OrganizationPlace = OrganizationPlace(),
    val logoContentType: String? = null,
    val logoBytes: ByteArray? = null,
    val removeLogo: Boolean = false,
)
