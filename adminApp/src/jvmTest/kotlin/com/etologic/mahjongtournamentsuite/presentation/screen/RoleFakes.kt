package com.etologic.mahjongtournamentsuite.presentation.screen

import com.etologic.mahjongtournamentsuite.domain.model.AdminStatus
import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.AuthSession
import com.etologic.mahjongtournamentsuite.domain.model.ErrorGroup
import com.etologic.mahjongtournamentsuite.domain.model.ErrorGroupDetail
import com.etologic.mahjongtournamentsuite.domain.model.ErrorGroupsPage
import com.etologic.mahjongtournamentsuite.domain.model.ErrorLogCursor
import com.etologic.mahjongtournamentsuite.domain.model.GlobalUserRole
import com.etologic.mahjongtournamentsuite.domain.model.ManagedUser
import com.etologic.mahjongtournamentsuite.domain.model.PasswordResetOutcome
import com.etologic.mahjongtournamentsuite.domain.model.PasswordResetResult
import com.etologic.mahjongtournamentsuite.domain.model.Player
import com.etologic.mahjongtournamentsuite.domain.model.PlayerAccountActionResult
import com.etologic.mahjongtournamentsuite.domain.model.PlayerAccountOutcome
import com.etologic.mahjongtournamentsuite.domain.model.PlayerEmailItem
import com.etologic.mahjongtournamentsuite.domain.model.PlayerEmailOutcome
import com.etologic.mahjongtournamentsuite.domain.model.PlayerEmailResult
import com.etologic.mahjongtournamentsuite.domain.model.SavedCredentials
import com.etologic.mahjongtournamentsuite.domain.model.TournamentAssignment
import com.etologic.mahjongtournamentsuite.domain.model.UserProfile
import com.etologic.mahjongtournamentsuite.domain.repository.AdminRepository
import com.etologic.mahjongtournamentsuite.domain.repository.AuthRepository
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerAccountRepository
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerRepository
import com.etologic.mahjongtournamentsuite.domain.repository.RefreshMode

/** The signed-in account. Its role decides what the screens show. */
internal class FakeAdminRepository(
    private val role: GlobalUserRole,
    var users: List<ManagedUser> = emptyList(),
) : AdminRepository {
    val calls = mutableListOf<String>()

    /** When true, every account write fails with a network error. */
    var failWrites = false

    private fun <T> write(value: () -> T): AppResult<T> = if (failWrites) AppResult.Failure(AppError.Network) else AppResult.Success(value())

    override suspend fun whoAmI(refreshMode: RefreshMode) = AppResult.Success(AdminStatus("me", role))
    override suspend fun lookupUser(identifier: String, tournamentId: String?): AppResult<UserProfile> = error("unused")
    override suspend fun listUsers(refreshMode: RefreshMode) = AppResult.Success(users)
    override suspend fun createUser(
        email: String,
        alias: String,
        role: GlobalUserRole,
        tournamentAssignments: List<TournamentAssignment>,
        emaId: String?,
    ): AppResult<ManagedUser> = write { ManagedUser("new", email, alias, role, false, tournamentAssignments, emaId = emaId) }

    override suspend fun updateUser(user: ManagedUser): AppResult<ManagedUser> {
        calls += "update ${user.uid}"
        return write { user }
    }

    override suspend fun setUserDisabled(uid: String, disabled: Boolean): AppResult<ManagedUser> {
        calls += "disable $uid $disabled"
        return write { users.first { it.uid == uid }.copy(disabled = disabled) }
    }

    override suspend fun updateUserPhoto(uid: String, contentType: String, bytes: ByteArray): AppResult<ManagedUser> {
        calls += "photo $uid"
        return write { users.first { it.uid == uid }.copy(photoUrl = "https://example.test/$uid.png", hasOwnPhoto = true) }
    }

    override suspend fun removeUserPhoto(uid: String): AppResult<ManagedUser> {
        calls += "remove photo $uid"
        return write { users.first { it.uid == uid }.copy(photoUrl = null, hasOwnPhoto = false) }
    }

    /** The result of a reset by uid. An account with no entry gets SENT. */
    val resetResults = mutableMapOf<String, PasswordResetResult>()

    /** The request (counted from 1) that fails with a network error, and every request after it. */
    var failResetsFromRequest: Int = Int.MAX_VALUE
    val resetRequests = mutableListOf<List<String>>()

    override suspend fun sendPasswordResets(uids: List<String>): AppResult<List<PasswordResetResult>> {
        resetRequests += uids
        calls += "reset ${uids.joinToString(",")}"
        if (resetRequests.size >= failResetsFromRequest) return AppResult.Failure(com.etologic.mahjongtournamentsuite.domain.model.AppError.Network)
        return AppResult.Success(uids.map { resetResults[it] ?: PasswordResetResult(it, PasswordResetOutcome.SENT) })
    }

    var errorGroups: List<ErrorGroup> = emptyList()
    var errorDetails: Map<String, ErrorGroupDetail> = emptyMap()

    /** The groups come in pages of [errorPageSize], like the server. */
    var errorPageSize: Int = Int.MAX_VALUE
    val errorGroupRequests = mutableListOf<Pair<Long?, ErrorLogCursor?>>()

    /** When set, the list of the error log fails with this error. */
    var errorGroupsFailure: AppError? = null

    /** The second page of the reports of a group. The first page is in [errorDetails]. */
    var errorNextReportPages: Map<String, ErrorGroupDetail> = emptyMap()
    val errorDetailRequests = mutableListOf<Pair<String, ErrorLogCursor?>>()

    override suspend fun loadErrorGroups(sinceMillis: Long?, cursor: ErrorLogCursor?): AppResult<ErrorGroupsPage> {
        errorGroupRequests += sinceMillis to cursor
        errorGroupsFailure?.let { return AppResult.Failure(it) }
        val matching = errorGroups.filter { sinceMillis == null || it.lastSeen >= sinceMillis }
        val start = cursor?.id?.toIntOrNull() ?: 0
        val page = matching.drop(start).take(errorPageSize)
        val end = start + page.size
        return AppResult.Success(ErrorGroupsPage(page, if (end < matching.size) ErrorLogCursor(0, end.toString()) else null))
    }

    override suspend fun loadErrorGroup(id: String, reportCursor: ErrorLogCursor?): AppResult<ErrorGroupDetail> {
        errorDetailRequests += id to reportCursor
        val detail = if (reportCursor == null) errorDetails[id] else errorNextReportPages[id]
        return detail?.let { AppResult.Success(it) } ?: AppResult.Failure(AppError.Unexpected("No error group"))
    }
}

/** Records what the editor sends to players. A test can set the result of a player. */
internal class FakePlayerAccountRepository : PlayerAccountRepository {
    val sent = mutableListOf<List<Int>>()
    val results = mutableMapOf<Int, PlayerAccountActionResult>()
    val savedEmails = mutableListOf<List<PlayerEmailItem>>()

    /** The result of a saved email by player. A player with no entry gets SAVED. */
    val emailResults = mutableMapOf<Int, PlayerEmailResult>()

    /** A test sets a gate to hold the send until it completes the gate, so it can look at the screen meanwhile. */
    var sendGate: kotlinx.coroutines.CompletableDeferred<Unit>? = null

    /** When true, the email save and the send fail with a network error. */
    var fails = false

    override suspend fun send(tournamentId: String, playerIds: List<Int>): AppResult<List<PlayerAccountActionResult>> {
        sendGate?.await()
        if (fails) return AppResult.Failure(AppError.Network)
        sent += playerIds
        return AppResult.Success(playerIds.map { results[it] ?: PlayerAccountActionResult(it, PlayerAccountOutcome.INVITED) })
    }

    override suspend fun saveEmails(tournamentId: String, items: List<PlayerEmailItem>): AppResult<List<PlayerEmailResult>> {
        if (fails) return AppResult.Failure(AppError.Network)
        savedEmails += items
        return AppResult.Success(items.map { emailResults[it.playerId] ?: PlayerEmailResult(it.playerId, PlayerEmailOutcome.SAVED) })
    }
}

internal class FakeSignedInAuthRepository : AuthRepository {
    override suspend fun currentSession() = AuthSession("me", "token", "refresh")
    override suspend fun signIn(email: String, password: String): AppResult<AuthSession> = error("unused")
    override suspend fun requestPasswordReset(email: String): AppResult<Unit> = error("unused")
    override suspend fun savedCredentials(): SavedCredentials? = null
    override suspend fun clearSavedCredentials() = Unit
    override suspend fun refreshSession(): AppResult<AuthSession> = error("unused")
    override suspend fun signOut() = Unit
    override suspend fun getMe(refreshMode: RefreshMode) = AppResult.Success(UserProfile("me", "me@example.test", "Me"))
    override suspend fun updateAlias(alias: String) = AppResult.Success(UserProfile("me", "me@example.test", alias))
    override suspend fun updatePhoto(contentType: String, bytes: ByteArray) = AppResult.Success(UserProfile("me", "me@example.test", hasOwnPhoto = true))
    override suspend fun removePhoto() = AppResult.Success(UserProfile("me", "me@example.test"))
}

internal class FakePlayerRepository(private val players: List<Player> = emptyList()) : PlayerRepository {
    override suspend fun listPlayers(refreshMode: RefreshMode) = AppResult.Success(players)
    override suspend fun createPlayer(player: Player): AppResult<Player> = error("unused")
    override suspend fun updatePlayer(previousEmaId: String, player: Player): AppResult<Unit> = error("unused")
    override suspend fun updatePlayerPhoto(emaId: String, contentType: String, bytes: ByteArray): AppResult<Player> = error("unused")
}
