package com.etologic.mahjongtournamentsuite.presentation.presenter

import com.etologic.mahjongtournamentsuite.domain.model.AdminStatus
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.GlobalUserRole
import com.etologic.mahjongtournamentsuite.domain.model.ManagedUser
import com.etologic.mahjongtournamentsuite.domain.model.PasswordResetFailure
import com.etologic.mahjongtournamentsuite.domain.model.PasswordResetOutcome
import com.etologic.mahjongtournamentsuite.domain.model.PasswordResetResult
import com.etologic.mahjongtournamentsuite.domain.model.Player
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.TournamentAssignment
import com.etologic.mahjongtournamentsuite.domain.repository.AdminRepository
import com.etologic.mahjongtournamentsuite.domain.repository.AuthRepository
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerRepository
import com.etologic.mahjongtournamentsuite.domain.repository.RefreshMode
import com.etologic.mahjongtournamentsuite.domain.repository.TournamentRepository
import com.etologic.mahjongtournamentsuite.domain.telemetry.NoTelemetry
import com.etologic.mahjongtournamentsuite.domain.telemetry.Telemetry
import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryAction
import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryEvent
import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryParam
import com.etologic.mahjongtournamentsuite.domain.telemetry.trackAction

class UsersPresenter(
    private val tournamentRepository: TournamentRepository,
    private val adminRepository: AdminRepository,
    private val authRepository: AuthRepository,
    private val playerRepository: PlayerRepository,
    private val telemetry: Telemetry = NoTelemetry,
) {
    suspend fun loadAdminStatus(force: Boolean = false): AppResult<AdminStatus> = adminRepository.whoAmI(force.mode())

    suspend fun loadUsers(force: Boolean = false): AppResult<List<ManagedUser>> = adminRepository.listUsers(force.mode())

    suspend fun createUser(
        email: String,
        alias: String,
        role: GlobalUserRole,
        tournamentAssignments: List<TournamentAssignment>,
        emaId: String? = null,
    ): AppResult<ManagedUser> = adminRepository.createUser(email, alias, role, tournamentAssignments, emaId)
        .trackAction(telemetry, TelemetryAction.ACCOUNT_CREATED)

    /** The shared EMA player list. The account dialog uses it to pick an EMA ID. */
    suspend fun loadEmaPlayers(force: Boolean = false): AppResult<List<Player>> = playerRepository.listPlayers(force.mode())

    suspend fun updateUser(user: ManagedUser): AppResult<ManagedUser> = adminRepository.updateUser(user).trackAction(telemetry, TelemetryAction.ACCOUNT_UPDATED)

    /** Only admins can set the photo of another account. */
    suspend fun updateUserPhoto(uid: String, contentType: String, bytes: ByteArray): AppResult<ManagedUser> = adminRepository.updateUserPhoto(uid, contentType, bytes)
        .trackAction(telemetry, TelemetryAction.ACCOUNT_PHOTO_UPDATED)

    suspend fun removeUserPhoto(uid: String): AppResult<ManagedUser> = adminRepository.removeUserPhoto(uid).trackAction(telemetry, TelemetryAction.ACCOUNT_PHOTO_REMOVED)

    suspend fun setUserDisabled(uid: String, disabled: Boolean): AppResult<ManagedUser> = adminRepository.setUserDisabled(uid, disabled)
        .trackAction(telemetry, if (disabled) TelemetryAction.ACCOUNT_DISABLED else TelemetryAction.ACCOUNT_ENABLED)

    /** Sends the password reset email to many accounts. Only admins call it. */
    suspend fun sendPasswordResets(uids: List<String>): AppResult<List<PasswordResetResult>> {
        // The server takes up to 200 accounts in one request, so a longer list goes in several requests.
        val results = mutableListOf<PasswordResetResult>()
        for (chunk in uids.chunked(MAX_PASSWORD_RESETS_PER_REQUEST)) {
            when (val result = adminRepository.sendPasswordResets(chunk)) {
                is AppResult.Success -> results += result.value
                // A request that fails after others went out must not hide them. Its accounts show as failed.
                is AppResult.Failure -> {
                    if (results.isEmpty()) return result
                    results += chunk.map { PasswordResetResult(it, PasswordResetOutcome.FAILED, PasswordResetFailure.UNKNOWN) }
                }
            }
        }
        return AppResult.Success(results).also {
            telemetry.event(TelemetryEvent.PASSWORD_RESET_REQUESTED, mapOf(TelemetryParam.KIND to "bulk", TelemetryParam.PLAYERS to uids.size))
        }
    }

    suspend fun requestPasswordReset(email: String): AppResult<Unit> = authRepository.requestPasswordReset(email)

    suspend fun loadTournaments(force: Boolean = false): AppResult<List<Tournament>> = tournamentRepository.listTournaments(force.mode())
}

private fun Boolean.mode() = if (this) RefreshMode.FORCE else RefreshMode.IF_CHANGED

/** The server takes up to 200 accounts in one password reset request. */
internal const val MAX_PASSWORD_RESETS_PER_REQUEST = 200
