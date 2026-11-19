package com.etologic.mahjongtournamentsuite.domain.model

/**
 * The actions that a role may or may not do. The backend checks them by name (`models/permissions.ts`) and sends
 * the ones of the signed-in account in `whoami`, and the ones of each tournament in the tournament list.
 */
enum class Permission(val key: String) {
    TOURNAMENT_VIEW("tournament.view"),
    TOURNAMENT_EDIT("tournament.edit"),
    TOURNAMENT_ROUND_CONTROL("tournament.roundControl"),
    TOURNAMENT_EDIT_COMPLETED("tournament.editCompleted"),
    TOURNAMENT_CONFIGURE("tournament.configure"),
    TOURNAMENT_DELETE("tournament.delete"),
    TOURNAMENT_RESET_TABLE("tournament.resetTable"),
    TOURNAMENT_MEMBERS("tournament.members"),
    TOURNAMENT_ACTIVITY("tournament.activity"),
    TOURNAMENT_PLAYER_EMAILS("tournament.playerEmails"),
    TOURNAMENTS_CREATE("tournaments.create"),
    TOURNAMENTS_CHANGE_COUNTRY("tournaments.changeCountry"),
    TOURNAMENTS_DELETE_FINISHED("tournaments.deleteFinished"),
    ACCOUNTS_VIEW("accounts.view"),
    ACCOUNTS_CREATE_PLAYER("accounts.createPlayer"),
    ACCOUNTS_MANAGE_STAFF("accounts.manageStaff"),
    ACCOUNTS_MANAGE_SUPER_ADMINS("accounts.manageSuperAdmins"),
    ACCOUNTS_CHANGE_EMAIL("accounts.changeEmail"),
    ACCOUNTS_CHANGE_EMA_ID("accounts.changeEmaId"),
    ACCOUNTS_PASSWORD_RESET("accounts.passwordReset"),
    ACCOUNTS_PHOTO("accounts.photo"),
    ACCOUNTS_DISABLE("accounts.disable"),
    ACCOUNTS_DELETE("accounts.delete"),
    ACCOUNTS_REQUEST_REMOVAL("accounts.requestRemoval"),
    ACCOUNTS_REVIEW_REQUESTS("accounts.reviewRequests"),
    EMA_CREATE("ema.create"),
    EMA_EDIT("ema.edit"),
    EMA_DELETE("ema.delete"),
    TOOLS_VENUES("tools.venues"),
    TOOLS_ID_CARDS("tools.idCards"),
    DEV_ACTIVITY("dev.activity"),
    DEV_ERRORS("dev.errors"),
    ;

    val inTournament: Boolean get() = key.startsWith("tournament.")

    companion object {
        private val byKey = entries.associateBy { it.key }

        /** The known permissions of a list from the backend. A name that this app does not know yet is left out. */
        fun fromKeys(keys: Collection<String>): Set<Permission> = keys.mapNotNullTo(mutableSetOf()) { byKey[it] }

        private val editor = setOf(
            TOURNAMENT_VIEW, TOURNAMENT_EDIT, TOURNAMENT_ROUND_CONTROL, TOURNAMENT_PLAYER_EMAILS,
            ACCOUNTS_VIEW, ACCOUNTS_CREATE_PLAYER, TOOLS_VENUES, TOOLS_ID_CARDS,
        )
        private val countryAdmin = editor + setOf(
            TOURNAMENT_EDIT_COMPLETED, TOURNAMENT_CONFIGURE, TOURNAMENT_DELETE, TOURNAMENT_RESET_TABLE,
            TOURNAMENT_MEMBERS, TOURNAMENT_ACTIVITY, TOURNAMENTS_CREATE, ACCOUNTS_MANAGE_STAFF,
            ACCOUNTS_PASSWORD_RESET, ACCOUNTS_REQUEST_REMOVAL, EMA_CREATE,
        )

        /**
         * The permissions of each role, a copy of the table of the backend. The app uses the list from the backend;
         * this copy is only for an account whose answer has no list, like the fakes of the tests.
         */
        fun defaultsOf(role: GlobalUserRole): Set<Permission> = when (role) {
            GlobalUserRole.PLAYER -> emptySet()
            GlobalUserRole.EDITOR -> editor
            GlobalUserRole.COUNTRY_ADMIN -> countryAdmin
            GlobalUserRole.SUPER_ADMIN -> entries.toSet() - ACCOUNTS_REQUEST_REMOVAL
        }
    }
}
