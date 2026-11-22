package com.etologic.mahjongtournamentsuite.presentation.presenter

import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.data.platform.platformCpuCount
import com.etologic.mahjongtournamentsuite.domain.model.AdminRegistration
import com.etologic.mahjongtournamentsuite.domain.model.AdminRegistrations
import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.NewRegistration
import com.etologic.mahjongtournamentsuite.domain.model.Player
import com.etologic.mahjongtournamentsuite.domain.model.REGISTRATION_TEAM_SIZE
import com.etologic.mahjongtournamentsuite.domain.model.RegistrationErrorCode
import com.etologic.mahjongtournamentsuite.domain.model.RegistrationImport
import com.etologic.mahjongtournamentsuite.domain.model.RegistrationSettings
import com.etologic.mahjongtournamentsuite.domain.model.RegistrationStatus
import com.etologic.mahjongtournamentsuite.domain.model.RegistrationUpdate
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerRepository
import com.etologic.mahjongtournamentsuite.domain.repository.RefreshMode
import com.etologic.mahjongtournamentsuite.domain.repository.RegistrationRepository
import com.etologic.mahjongtournamentsuite.domain.telemetry.NoTelemetry
import com.etologic.mahjongtournamentsuite.domain.telemetry.Telemetry
import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryAction
import com.etologic.mahjongtournamentsuite.domain.telemetry.trackAction
import com.etologic.mahjongtournamentsuite.domain.usecase.GenerateTournamentScheduleBruteForceParallelUseCase
import com.etologic.mahjongtournamentsuite.domain.usecase.TournamentScheduleCalcProgress
import com.etologic.mahjongtournamentsuite.domain.usecase.assignRegistrationsToDraw
import com.etologic.mahjongtournamentsuite.domain.validation.TournamentPlayerCountValidator

/** The registrations of a tournament before its draw, as the organizers manage them. See docs/registrations.md. */
class RegistrationsPresenter(
    private val repository: RegistrationRepository,
    private val playerRepository: PlayerRepository,
    private val generateSchedule: GenerateTournamentScheduleBruteForceParallelUseCase,
    private val logger: Logger,
    private val telemetry: Telemetry = NoTelemetry,
) {
    suspend fun load(tournamentId: String): AppResult<AdminRegistrations> = repository.loadAdminList(tournamentId)

    /** The EMA registry, to pick the person to add. */
    suspend fun loadRegistry(): AppResult<List<Player>> = playerRepository.listPlayers(RefreshMode.IF_CHANGED)

    suspend fun add(tournamentId: String, registration: NewRegistration): AppResult<AdminRegistration> = repository.adminAdd(tournamentId, registration).trackAction(telemetry, TelemetryAction.REGISTRATION_ADDED)

    suspend fun update(tournamentId: String, emaId: String, update: RegistrationUpdate): AppResult<AdminRegistration> = repository.adminUpdate(tournamentId, emaId, update).trackAction(telemetry, TelemetryAction.REGISTRATION_UPDATED)

    suspend fun remove(tournamentId: String, emaId: String, notify: Boolean): AppResult<Unit> = repository.adminRemove(tournamentId, emaId, notify).trackAction(telemetry, TelemetryAction.REGISTRATION_REMOVED)

    suspend fun saveSettings(tournamentId: String, settings: RegistrationSettings): AppResult<RegistrationSettings> = repository.updateSettings(tournamentId, settings).trackAction(telemetry, TelemetryAction.REGISTRATION_SETTINGS_UPDATED)

    suspend fun previewImport(tournamentId: String, fileName: String, bytes: ByteArray): AppResult<RegistrationImport> = repository.previewImport(tournamentId, fileName, bytes)

    /**
     * Saves the list as a CSV file that the import reads back (see [RegistrationsCsv]) with [save], which gets the
     * name and the text of the file. The app builds it from the list that it already shows, so it needs no request.
     */
    fun exportCsv(
        tournamentName: String,
        registrations: AdminRegistrations,
        registry: List<Player>,
        save: (fileName: String, content: String) -> Unit,
    ): AppResult<Unit> {
        val result = try {
            save(RegistrationsCsv.fileName(tournamentName), RegistrationsCsv.build(registrations, registry))
            AppResult.Success(Unit)
        } catch (exception: Exception) {
            logger.w(exception) { "The CSV file of the registrations was not saved." }
            AppResult.Failure(AppError.Unexpected(exception.message ?: "The file was not saved."))
        }
        return result.trackAction(telemetry, TelemetryAction.REGISTRATIONS_EXPORTED)
    }

    suspend fun applyImport(tournamentId: String, fileName: String, bytes: ByteArray): AppResult<RegistrationImport> = repository.applyImport(tournamentId, fileName, bytes).trackAction(telemetry, TelemetryAction.REGISTRATIONS_IMPORTED)

    /**
     * Makes the draw with the confirmed people, as the creation of a tournament does: the app computes the rounds and
     * the tables, the people take the slots in a random order (a registration team takes one team of the draw), and
     * the server writes everything. [onProgress] gets the tries of the computation.
     */
    suspend fun makeDraw(
        tournament: Tournament,
        registrations: AdminRegistrations,
        onProgress: (TournamentScheduleCalcProgress) -> Unit,
    ): AppResult<Tournament> {
        val confirmed = registrations.registrations.filter { it.status == RegistrationStatus.CONFIRMED }
        if (!TournamentPlayerCountValidator.isValid(confirmed.size)) {
            return AppResult.Failure(AppError.Registration(RegistrationErrorCode.BAD_PLAYER_COUNT, listOf(confirmed.size.toString())))
        }
        val incomplete = if (registrations.isTeams) {
            confirmed.filter { registration ->
                val team = registrations.teamOf(registration)
                team == null || confirmed.count { it.teamId == team.id } != REGISTRATION_TEAM_SIZE
            }
        } else {
            emptyList()
        }
        if (incomplete.isNotEmpty()) {
            return AppResult.Failure(AppError.Registration(RegistrationErrorCode.INCOMPLETE_TEAMS, incomplete.map { it.name.ifBlank { it.emaId } }))
        }
        logger.i { "Calculating the draw for ${confirmed.size} registered people." }
        val schedule = generateSchedule(
            numPlayers = confirmed.size,
            numRounds = tournament.numRounds,
            isTeams = registrations.isTeams,
            maxConcurrency = (platformCpuCount() / 2).coerceIn(1, 32),
            onProgress = onProgress,
        ) ?: return AppResult.Failure(AppError.Unexpected("Unable to generate rounds/tables locally with the current constraints."))
        val assignments = assignRegistrationsToDraw(
            slots = schedule.players,
            emaIds = confirmed.map { it.emaId },
            isTeams = registrations.isTeams,
            teamOf = { emaId -> confirmed.firstOrNull { it.emaId == emaId }?.teamId },
        ) ?: return AppResult.Failure(AppError.Registration(RegistrationErrorCode.BAD_ASSIGNMENTS))
        return repository.makeDraw(tournament.id, schedule.triesUsed, schedule.players, schedule.tables, assignments)
            .trackAction(telemetry, TelemetryAction.DRAW_MADE)
    }
}
