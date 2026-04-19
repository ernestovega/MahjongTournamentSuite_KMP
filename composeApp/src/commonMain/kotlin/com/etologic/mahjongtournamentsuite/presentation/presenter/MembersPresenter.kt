package com.etologic.mahjongtournamentsuite.presentation.presenter

import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.domain.model.AdminStatus
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.TournamentMember
import com.etologic.mahjongtournamentsuite.domain.model.TournamentRole
import com.etologic.mahjongtournamentsuite.domain.model.UserProfile
import com.etologic.mahjongtournamentsuite.domain.repository.AdminRepository
import com.etologic.mahjongtournamentsuite.domain.repository.TournamentRepository

class MembersPresenter(
    private val tournamentRepository: TournamentRepository,
    private val adminRepository: AdminRepository,
    private val logger: Logger,
) {
    suspend fun loadAdminStatus(): AppResult<AdminStatus> = adminRepository.whoAmI()

    suspend fun lookupUser(identifier: String): AppResult<UserProfile> {
        logger.i { "Looking up user." }
        return adminRepository.lookupUser(identifier)
    }

    suspend fun loadMembers(tournamentId: String): AppResult<List<TournamentMember>> =
        tournamentRepository.listTournamentMembers(tournamentId)

    suspend fun upsertMember(
        tournamentId: String,
        uid: String,
        role: TournamentRole,
    ): AppResult<Unit> {
        logger.i { "Upserting tournament member." }
        return tournamentRepository.upsertTournamentMember(
            tournamentId = tournamentId,
            uid = uid,
            role = role,
        )
    }

    suspend fun removeMember(
        tournamentId: String,
        uid: String,
    ): AppResult<Unit> {
        logger.i { "Removing tournament member." }
        return tournamentRepository.removeTournamentMember(
            tournamentId = tournamentId,
            uid = uid,
        )
    }
}

