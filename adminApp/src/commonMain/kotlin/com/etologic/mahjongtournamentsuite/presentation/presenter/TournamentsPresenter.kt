package com.etologic.mahjongtournamentsuite.presentation.presenter

import com.etologic.mahjongtournamentsuite.domain.model.AdminStatus
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.Country
import com.etologic.mahjongtournamentsuite.domain.model.MatchEndMode
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.TournamentPlayer
import com.etologic.mahjongtournamentsuite.domain.model.TournamentAgendaItem
import com.etologic.mahjongtournamentsuite.domain.model.TournamentRoundSchedule
import com.etologic.mahjongtournamentsuite.domain.model.TournamentVenue
import com.etologic.mahjongtournamentsuite.domain.model.UserProfile
import com.etologic.mahjongtournamentsuite.domain.repository.AdminRepository
import com.etologic.mahjongtournamentsuite.domain.repository.AuthRepository
import com.etologic.mahjongtournamentsuite.domain.repository.TournamentRepository
import com.etologic.mahjongtournamentsuite.domain.repository.RefreshMode

class TournamentsPresenter(
    private val tournamentRepository: TournamentRepository,
    private val adminRepository: AdminRepository,
    private val authRepository: AuthRepository,
) {
    suspend fun loadProfile(force: Boolean = false): AppResult<UserProfile> = authRepository.getMe(force.toRefreshMode())

    suspend fun resolveVenueLink(url: String): AppResult<String> = tournamentRepository.resolveVenueLink(url)

    suspend fun updateAlias(alias: String): AppResult<UserProfile> = authRepository.updateAlias(alias)

    suspend fun updatePhoto(contentType: String, bytes: ByteArray): AppResult<UserProfile> =
        authRepository.updatePhoto(contentType, bytes)

    suspend fun removePhoto(): AppResult<UserProfile> = authRepository.removePhoto()

    suspend fun loadAdminStatus(force: Boolean = false): AppResult<AdminStatus> = adminRepository.whoAmI(force.toRefreshMode())

    suspend fun loadTournaments(force: Boolean = false): AppResult<List<Tournament>> = tournamentRepository.listTournaments(force.toRefreshMode())

    suspend fun loadCountries(force: Boolean = false): AppResult<List<Country>> = tournamentRepository.listCountries(force.toRefreshMode())

    suspend fun loadPlayers(tournamentId: String, force: Boolean = false): AppResult<List<TournamentPlayer>> =
        tournamentRepository.listTournamentPlayers(tournamentId, force.toRefreshMode())

    suspend fun lookupUser(identifier: String): AppResult<UserProfile> = adminRepository.lookupUser(identifier)

    suspend fun deleteTournament(tournamentId: String): AppResult<Unit> =
        tournamentRepository.deleteTournament(tournamentId)

    suspend fun renameTournament(tournamentId: String, name: String): AppResult<Unit> =
        tournamentRepository.renameTournament(tournamentId, name)

    suspend fun updateTournamentSettings(
        tournamentId: String,
        name: String,
        shortName: String,
        primaryColor: String,
        eventStartDate: String,
        eventEndDate: String,
        hostCountry: String,
        hostCity: String,
        venue: TournamentVenue,
        associationLogoContentType: String?,
        associationLogoBytes: ByteArray?,
        associationLogoSourceTournamentId: String?,
        removeAssociationLogo: Boolean,
        roundSchedules: List<TournamentRoundSchedule>,
        agendaItems: List<TournamentAgendaItem>,
        countBestHands: Boolean? = null,
        countChickenHands: Boolean? = null,
        matchDurationMinutes: Int? = null,
        matchEndMode: MatchEndMode? = null,
        matchExtraMinutes: Int? = null,
        timeZone: String? = null,
    ): AppResult<Tournament> = tournamentRepository.updateTournamentSettings(
        tournamentId = tournamentId,
        name = name,
        shortName = shortName,
        primaryColor = primaryColor,
        eventStartDate = eventStartDate,
        eventEndDate = eventEndDate,
        hostCountry = hostCountry,
        hostCity = hostCity,
        venue = venue,
        associationLogoContentType = associationLogoContentType,
        associationLogoBytes = associationLogoBytes,
        associationLogoSourceTournamentId = associationLogoSourceTournamentId,
        removeAssociationLogo = removeAssociationLogo,
        roundSchedules = roundSchedules,
        agendaItems = agendaItems,
        countBestHands = countBestHands,
        countChickenHands = countChickenHands,
        matchDurationMinutes = matchDurationMinutes,
        matchEndMode = matchEndMode,
        matchExtraMinutes = matchExtraMinutes,
        timeZone = timeZone,
    )

    suspend fun signOut() = authRepository.signOut()
}

private fun Boolean.toRefreshMode() = if (this) RefreshMode.FORCE else RefreshMode.IF_CHANGED
