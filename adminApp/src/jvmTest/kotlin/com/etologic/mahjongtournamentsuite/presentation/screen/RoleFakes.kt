package com.etologic.mahjongtournamentsuite.presentation.screen

import com.etologic.mahjongtournamentsuite.domain.model.AdminStatus
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.AuthSession
import com.etologic.mahjongtournamentsuite.domain.model.GlobalUserRole
import com.etologic.mahjongtournamentsuite.domain.model.ManagedUser
import com.etologic.mahjongtournamentsuite.domain.model.Player
import com.etologic.mahjongtournamentsuite.domain.model.PlayerAccountActionResult
import com.etologic.mahjongtournamentsuite.domain.model.PlayerAccountOutcome
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

    override suspend fun whoAmI(refreshMode: RefreshMode) = AppResult.Success(AdminStatus("me", role))
    override suspend fun lookupUser(identifier: String, tournamentId: String?): AppResult<UserProfile> = error("unused")
    override suspend fun listUsers(refreshMode: RefreshMode) = AppResult.Success(users)
    override suspend fun createUser(
        email: String,
        alias: String,
        role: GlobalUserRole,
        tournamentAssignments: List<TournamentAssignment>,
        emaId: String?,
    ): AppResult<ManagedUser> = error("unused")

    override suspend fun updateUser(user: ManagedUser): AppResult<ManagedUser> {
        calls += "update ${user.uid}"
        return AppResult.Success(user)
    }

    override suspend fun setUserDisabled(uid: String, disabled: Boolean): AppResult<ManagedUser> {
        calls += "disable $uid $disabled"
        return AppResult.Success(users.first { it.uid == uid }.copy(disabled = disabled))
    }

    override suspend fun updateUserPhoto(uid: String, contentType: String, bytes: ByteArray): AppResult<ManagedUser> {
        calls += "photo $uid"
        return AppResult.Success(users.first { it.uid == uid }.copy(photoUrl = "https://example.test/$uid.png", hasOwnPhoto = true))
    }

    override suspend fun removeUserPhoto(uid: String): AppResult<ManagedUser> {
        calls += "remove photo $uid"
        return AppResult.Success(users.first { it.uid == uid }.copy(photoUrl = null, hasOwnPhoto = false))
    }

    override suspend fun loadUsageReport(days: Int): AppResult<com.etologic.mahjongtournamentsuite.domain.model.UsageReport> =
        AppResult.Failure(com.etologic.mahjongtournamentsuite.domain.model.AppError.Unexpected("No usage report"))
}

/** Records what the editor sends to players. A test can set the result of a player. */
internal class FakePlayerAccountRepository : PlayerAccountRepository {
    val sent = mutableListOf<List<Int>>()
    val results = mutableMapOf<Int, PlayerAccountActionResult>()

    override suspend fun send(tournamentId: String, playerIds: List<Int>): AppResult<List<PlayerAccountActionResult>> {
        sent += playerIds
        return AppResult.Success(playerIds.map { results[it] ?: PlayerAccountActionResult(it, PlayerAccountOutcome.INVITED) })
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

