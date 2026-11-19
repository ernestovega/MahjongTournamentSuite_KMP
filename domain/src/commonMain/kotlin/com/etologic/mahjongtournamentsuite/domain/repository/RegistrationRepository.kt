package com.etologic.mahjongtournamentsuite.domain.repository

import com.etologic.mahjongtournamentsuite.domain.model.AdminRegistration
import com.etologic.mahjongtournamentsuite.domain.model.AdminRegistrations
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.DrawAssignment
import com.etologic.mahjongtournamentsuite.domain.model.NewRegistration
import com.etologic.mahjongtournamentsuite.domain.model.NoEmaNumberRequest
import com.etologic.mahjongtournamentsuite.domain.model.OwnRegistration
import com.etologic.mahjongtournamentsuite.domain.model.RegisterRequest
import com.etologic.mahjongtournamentsuite.domain.model.RegisterResult
import com.etologic.mahjongtournamentsuite.domain.model.RegistrationImport
import com.etologic.mahjongtournamentsuite.domain.model.RegistrationSettings
import com.etologic.mahjongtournamentsuite.domain.model.RegistrationUpdate
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.TournamentPlayer
import com.etologic.mahjongtournamentsuite.domain.model.TournamentRegistrations
import com.etologic.mahjongtournamentsuite.domain.model.TournamentTable

/**
 * The registrations of the tournaments, before their draw. See docs/registrations.md.
 * The calls of a person work with the signed-in account, or with the token of the links of the emails. The
 * repository keeps the token of each tournament on the device, from the link ([confirm]) or from [register].
 */
interface RegistrationRepository {
    /** The public list: places, waiting list and teams, without emails. */
    suspend fun publicList(tournamentId: String): AppResult<TournamentRegistrations>

    /** The tournament of a public link, `/inscripcion/<slug>`. */
    suspend fun tournamentOfLink(slug: String): AppResult<String>

    suspend fun register(tournamentId: String, request: RegisterRequest): AppResult<RegisterResult>

    /** The link of the email confirms a pending registration. */
    suspend fun confirm(tournamentId: String, token: String): AppResult<OwnRegistration>

    /** The registration of the person, or null when the person is not registered (or it has no account and no link). */
    suspend fun own(tournamentId: String): AppResult<OwnRegistration?>

    suspend fun updateOwn(tournamentId: String, foodRestrictions: String, consent: Boolean): AppResult<OwnRegistration>

    suspend fun cancelOwn(tournamentId: String): AppResult<Unit>

    suspend fun createTeam(tournamentId: String, name: String): AppResult<OwnRegistration>

    suspend fun renameTeam(tournamentId: String, name: String): AppResult<OwnRegistration>

    suspend fun leaveTeam(tournamentId: String): AppResult<OwnRegistration>

    suspend fun addTeamMember(tournamentId: String, emaId: String): AppResult<OwnRegistration>

    suspend fun removeTeamMember(tournamentId: String, emaId: String): AppResult<OwnRegistration>

    suspend fun requestWithoutEmaNumber(tournamentId: String, request: NoEmaNumberRequest): AppResult<Unit>

    // The organizers.

    suspend fun adminList(tournamentId: String): AppResult<AdminRegistrations>

    suspend fun adminAdd(tournamentId: String, registration: NewRegistration): AppResult<AdminRegistration>

    suspend fun adminUpdate(tournamentId: String, emaId: String, update: RegistrationUpdate): AppResult<AdminRegistration>

    suspend fun adminRemove(tournamentId: String, emaId: String, notify: Boolean): AppResult<Unit>

    suspend fun updateSettings(tournamentId: String, settings: RegistrationSettings): AppResult<RegistrationSettings>

    /** Reads the answers of the old Google Form (CSV or Excel). Without [apply] it only checks them. */
    suspend fun import(tournamentId: String, fileName: String, bytes: ByteArray, apply: Boolean): AppResult<RegistrationImport>

    /** Makes the draw with the confirmed people. The tournament is a normal one after it. */
    suspend fun makeDraw(
        tournamentId: String,
        numTries: Long,
        players: List<TournamentPlayer>,
        tables: List<TournamentTable>,
        assignments: List<DrawAssignment>,
    ): AppResult<Tournament>
}
