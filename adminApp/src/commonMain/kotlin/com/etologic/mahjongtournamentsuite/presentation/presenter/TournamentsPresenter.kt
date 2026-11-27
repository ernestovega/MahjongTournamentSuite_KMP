package com.etologic.mahjongtournamentsuite.presentation.presenter

import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryParam
import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryProfileAction
import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryProperty
import com.etologic.mahjongtournamentsuite.presentation.platform.preventBrowserAutoSignIn
import com.etologic.mahjongtournamentsuite.domain.model.AdminStatus
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.Country
import com.etologic.mahjongtournamentsuite.domain.model.MatchEndMode
import com.etologic.mahjongtournamentsuite.domain.model.ResolvedVenueLink
import com.etologic.mahjongtournamentsuite.domain.model.Organization
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.TournamentAgendaItem
import com.etologic.mahjongtournamentsuite.domain.model.TournamentPlayer
import com.etologic.mahjongtournamentsuite.domain.model.TournamentRoundSchedule
import com.etologic.mahjongtournamentsuite.domain.model.TournamentVenue
import com.etologic.mahjongtournamentsuite.domain.model.UserProfile
import com.etologic.mahjongtournamentsuite.domain.repository.AdminRepository
import com.etologic.mahjongtournamentsuite.domain.repository.AuthRepository
import com.etologic.mahjongtournamentsuite.domain.repository.RefreshMode
import com.etologic.mahjongtournamentsuite.domain.repository.TournamentRepository
import com.etologic.mahjongtournamentsuite.domain.telemetry.NoTelemetry
import com.etologic.mahjongtournamentsuite.domain.telemetry.Telemetry
import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryAction
import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryEvent
import com.etologic.mahjongtournamentsuite.domain.telemetry.track
import com.etologic.mahjongtournamentsuite.domain.telemetry.trackAction

class TournamentsPresenter(
    private val tournamentRepository: TournamentRepository,
    private val adminRepository: AdminRepository,
    private val authRepository: AuthRepository,
    private val telemetry: Telemetry = NoTelemetry,
) {
    suspend fun loadProfile(force: Boolean = false): AppResult<UserProfile> = authRepository.getMe(force.toRefreshMode())

    suspend fun resolveVenueLink(url: String): AppResult<ResolvedVenueLink> = tournamentRepository.resolveVenueLink(url)

    suspend fun updateAlias(alias: String): AppResult<UserProfile> = authRepository.updateAlias(alias).profileUpdated(TelemetryProfileAction.ALIAS)

    suspend fun updatePhoto(contentType: String, bytes: ByteArray): AppResult<UserProfile> = authRepository.updatePhoto(contentType, bytes)
        .profileUpdated(TelemetryProfileAction.PHOTO_UPDATED)

    suspend fun removePhoto(): AppResult<UserProfile> = authRepository.removePhoto().profileUpdated(TelemetryProfileAction.PHOTO_REMOVED)

    suspend fun setPublicPhoto(visible: Boolean): AppResult<UserProfile> = authRepository.setPublicPhoto(visible)
        .profileUpdated(if (visible) TelemetryProfileAction.PHOTO_SHOWN else TelemetryProfileAction.PHOTO_HIDDEN)

    /** Deletes the own account. On success the session ends. */
    suspend fun deleteAccount(): AppResult<Unit> = authRepository.deleteAccount().profileUpdated(TelemetryProfileAction.ACCOUNT_DELETED)

    private fun <T> AppResult<T>.profileUpdated(action: String) = track(telemetry, TelemetryEvent.PROFILE_UPDATED, mapOf(TelemetryParam.ACTION to action))

    suspend fun loadAdminStatus(force: Boolean = false): AppResult<AdminStatus> = adminRepository.whoAmI(force.toRefreshMode())

    suspend fun loadTournaments(force: Boolean = false): AppResult<List<Tournament>> = tournamentRepository.listTournaments(force.toRefreshMode())

    /** The clubs and federations, to choose the organizers of a tournament. */
    suspend fun loadOrganizations(): AppResult<List<Organization>> = adminRepository.listOrganizations()

    suspend fun loadCountries(force: Boolean = false): AppResult<List<Country>> = tournamentRepository.listCountries(force.toRefreshMode())

    suspend fun loadPlayers(tournamentId: String, force: Boolean = false): AppResult<List<TournamentPlayer>> = tournamentRepository.listTournamentPlayers(tournamentId, force.toRefreshMode())

    suspend fun lookupUser(identifier: String): AppResult<UserProfile> = adminRepository.lookupUser(identifier)

    suspend fun deleteTournament(tournamentId: String): AppResult<Unit> = tournamentRepository.deleteTournament(tournamentId).track(telemetry, TelemetryEvent.TOURNAMENT_DELETED)

    suspend fun renameTournament(tournamentId: String, name: String): AppResult<Unit> = tournamentRepository.renameTournament(tournamentId, name)
        .trackAction(telemetry, TelemetryAction.TOURNAMENT_RENAMED)

    suspend fun updateTournamentSettings(
        tournamentId: String,
        name: String,
        shortName: String,
        organizerIds: List<String>?,
        eventStartDate: String,
        eventEndDate: String,
        hostCountry: String,
        hostCity: String,
        venue: TournamentVenue,
        roundSchedules: List<TournamentRoundSchedule>,
        agendaItems: List<TournamentAgendaItem>,
        countBestHands: Boolean? = null,
        countChickenHands: Boolean? = null,
        matchDurationMinutes: Int? = null,
        matchEndMode: MatchEndMode? = null,
        matchExtraMinutes: Int? = null,
        timeZone: String? = null,
        isPublic: Boolean? = null,
        showTableDiffs: Boolean? = null,
        hideResultsFromRound: Int? = null,
    ): AppResult<Tournament> = tournamentRepository.updateTournamentSettings(
        tournamentId = tournamentId,
        name = name,
        shortName = shortName,
        organizerIds = organizerIds,
        eventStartDate = eventStartDate,
        eventEndDate = eventEndDate,
        hostCountry = hostCountry,
        hostCity = hostCity,
        venue = venue,
        roundSchedules = roundSchedules,
        agendaItems = agendaItems,
        countBestHands = countBestHands,
        countChickenHands = countChickenHands,
        matchDurationMinutes = matchDurationMinutes,
        matchEndMode = matchEndMode,
        matchExtraMinutes = matchExtraMinutes,
        timeZone = timeZone,
        isPublic = isPublic,
        showTableDiffs = showTableDiffs,
        hideResultsFromRound = hideResultsFromRound,
    ).trackAction(telemetry, TelemetryAction.TOURNAMENT_SETTINGS_UPDATED)

    /** The prize giving: reveals the hidden results of the tournament, or hides them again after a reveal by mistake. */
    suspend fun setResultsRevealed(tournamentId: String, revealed: Boolean): AppResult<Unit> =
        tournamentRepository.setResultsRevealed(tournamentId, revealed).trackAction(telemetry, TelemetryAction.RESULTS_VISIBILITY_CHANGED)

    suspend fun signOut() {
        authRepository.signOut()
        preventBrowserAutoSignIn()
        telemetry.event(TelemetryEvent.LOGOUT)
        telemetry.userProperty(TelemetryProperty.ROLE, null)
    }
}

private fun Boolean.toRefreshMode() = if (this) RefreshMode.FORCE else RefreshMode.IF_CHANGED
