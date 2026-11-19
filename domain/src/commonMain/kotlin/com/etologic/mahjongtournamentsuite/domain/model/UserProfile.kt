package com.etologic.mahjongtournamentsuite.domain.model

data class UserProfile(
    val uid: String,
    val email: String,
    val alias: String = "",
    /** EMA number of the player this account represents. Only player accounts have one. */
    val emaId: String? = null,
    /** The own photo of the account. Without it, the photo of the EMA player of the account. Can be null. */
    val photoUrl: String? = null,
    /** True when [photoUrl] is the own photo of the account and not the photo of the EMA player. */
    val hasOwnPhoto: Boolean = false,
    val roles: Set<GlobalUserRole> = emptySet(),
)

/** The roles of an account. An account may hold several, see the backend `models/globalRole.ts`. */
enum class GlobalUserRole {
    EDITOR,
    COUNTRY_ADMIN,
    SUPER_ADMIN,
    PLAYER,
}

data class TournamentAssignment(
    val tournamentId: String,
    val tournamentName: String,
    /** The host country of the tournament, as a two-letter code. Empty when the answer did not say. */
    val country: String = "",
)

data class ManagedUser(
    val uid: String,
    val email: String,
    val alias: String,
    /** The highest capability of the account. */
    val role: GlobalUserRole,
    val disabled: Boolean,
    val tournamentAssignments: List<TournamentAssignment>,
    val roles: Set<GlobalUserRole> = setOf(role),
    val emaId: String? = null,
    /** The own photo of the account. Without it, the photo of the EMA player of the account. Can be null. */
    val photoUrl: String? = null,
    /** True when [photoUrl] is the own photo of the account and not the photo of the EMA player. */
    val hasOwnPhoto: Boolean = false,
    /** The countries of a country admin, as two-letter codes. */
    val adminCountries: Set<String> = emptySet(),
    /** True when the signed-in account may change the role, the countries or the tournaments of this account. */
    val canManage: Boolean = false,
    /** True when the account has countries or tournaments that the signed-in account does not manage. */
    val hasGrantsOutside: Boolean = false,
    /** True when the email is hidden from the signed-in account: a player of tournaments that it does not manage. */
    val emailHidden: Boolean = false,
    /** The custom role that the account uses as an editor or as a country admin, by level. */
    val customRoleIds: Map<GlobalUserRole, String> = emptyMap(),
    /** The single permissions that the account has more or less than its role, by level. */
    val permissionChanges: Map<GlobalUserRole, PermissionChange> = emptyMap(),
    /** What the account may do as an editor and as a country admin, with its custom roles and its changes. */
    val rolePermissions: Map<GlobalUserRole, Set<Permission>> = emptyMap(),
)
