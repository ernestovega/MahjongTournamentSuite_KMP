package com.etologic.mahjongtournamentsuite.presentation.presenter

import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.data.platform.platformCpuCount
import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.PlayerCountChangeRequest
import com.etologic.mahjongtournamentsuite.domain.model.PlayerCountOutcome
import com.etologic.mahjongtournamentsuite.domain.model.PlayerCountRules
import com.etologic.mahjongtournamentsuite.domain.model.RegistrationErrorCode
import com.etologic.mahjongtournamentsuite.domain.model.RegistrationStatus
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.TournamentPlayer
import com.etologic.mahjongtournamentsuite.domain.model.TournamentTeam
import com.etologic.mahjongtournamentsuite.domain.model.displayName
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerRepository
import com.etologic.mahjongtournamentsuite.domain.repository.RefreshMode
import com.etologic.mahjongtournamentsuite.domain.repository.RegistrationRepository
import com.etologic.mahjongtournamentsuite.domain.repository.TournamentRepository
import com.etologic.mahjongtournamentsuite.domain.telemetry.NoTelemetry
import com.etologic.mahjongtournamentsuite.domain.telemetry.Telemetry
import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryAction
import com.etologic.mahjongtournamentsuite.domain.telemetry.trackAction
import com.etologic.mahjongtournamentsuite.domain.usecase.DrawTournamentScheduleUseCase
import com.etologic.mahjongtournamentsuite.domain.usecase.TournamentScheduleCalcProgress

/** One slot of the tournament as the dialog lists it: its number, the name to show and its team. */
data class PlayerCountSlot(val id: Int, val name: String, val emaId: String?, val team: Int)

/** One person of the waiting list of the registrations, in the order of the list. */
data class PlayerCountWaiting(val emaId: String, val name: String, val position: Int)

/** What the dialog needs: the slots, the teams and, with registrations, the waiting list. */
data class PlayerCountData(
    val slots: List<PlayerCountSlot>,
    val teams: List<TournamentTeam>,
    val waiting: List<PlayerCountWaiting>,
)

/**
 * Changes the number of players of a tournament before its first hand (docs/player-count-change.md). The app computes
 * the tables of every round for the new number, as Create tournament does, and the server keeps the people who stay,
 * with their numbers.
 */
class PlayerCountPresenter(
    private val tournamentRepository: TournamentRepository,
    private val playerRepository: PlayerRepository,
    private val registrationRepository: RegistrationRepository,
    private val generateSchedule: DrawTournamentScheduleUseCase,
    private val logger: Logger,
    private val telemetry: Telemetry = NoTelemetry,
) {
    suspend fun load(tournament: Tournament): AppResult<PlayerCountData> {
        val slots = when (val result = tournamentRepository.listTournamentPlayers(tournament.id, RefreshMode.FORCE)) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> return result
        }
        val teams = if (tournament.isTeams) {
            when (val result = tournamentRepository.listTournamentTeams(tournament.id, RefreshMode.FORCE)) {
                is AppResult.Success -> result.value
                is AppResult.Failure -> return result
            }
        } else {
            emptyList()
        }
        // The names of the EMA players come from the registry. Without it, the slot name is enough.
        val registry = (playerRepository.listPlayers(RefreshMode.IF_CHANGED) as? AppResult.Success)?.value.orEmpty().associateBy { it.emaId }
        val waiting = if (tournament.registration != null) {
            when (val result = registrationRepository.loadAdminList(tournament.id)) {
                is AppResult.Success -> result.value.registrations
                    .filter { it.status == RegistrationStatus.WAITING }
                    .sortedBy { it.position }
                    .map { PlayerCountWaiting(it.emaId, it.name.ifBlank { it.emaId }, it.position) }
                is AppResult.Failure -> return result
            }
        } else {
            emptyList()
        }
        return AppResult.Success(
            PlayerCountData(
                slots = slots.sortedBy { it.id }.map { slot ->
                    val name = slot.displayName ?: slot.assignedEmaId?.let { registry[it]?.displayName } ?: slot.name
                    PlayerCountSlot(slot.id, name, slot.assignedEmaId, slot.team)
                },
                teams = teams.sortedBy { it.id },
                waiting = waiting,
            ),
        )
    }

    /**
     * Computes the new draw for [target] players and sends it with the choice of the organizer. The people who stay keep
     * their numbers ([slots] are the current ones), and the new places take the numbers after the highest one.
     */
    suspend fun change(
        tournament: Tournament,
        slots: List<PlayerCountSlot>,
        target: Int,
        removedSlotIds: List<Int>,
        waitingEmaIds: List<String>,
        /** With teams, the people who stay and change team to complete one (the step Teams): slot to new team. */
        teamMoves: Map<Int, Int> = emptyMap(),
        onProgress: (TournamentScheduleCalcProgress) -> Unit,
    ): AppResult<PlayerCountOutcome> {
        if (!PlayerCountRules.isValidTarget(tournament.numPlayers, target)) {
            return AppResult.Failure(AppError.Registration(RegistrationErrorCode.BAD_PLAYER_COUNT, listOf(target.toString())))
        }
        logger.i { "Calculating a new draw for $target players instead of ${tournament.numPlayers}." }
        val targets = PlayerCountRules.targetSlots(
            current = slots.map { TournamentPlayer(id = it.id, name = "", team = it.team) },
            removedSlotIds = removedSlotIds,
            target = target,
            isTeams = tournament.isTeams,
            teamMoves = teamMoves,
        )
        // With teams, the teams that lost someone are completed in the step Teams, or stay incomplete (Ernesto, 24-nov).
        val schedule = generateSchedule.onSlots(
            slots = targets,
            numRounds = tournament.numRounds,
            isTeams = tournament.isTeams,
            maxConcurrency = (platformCpuCount() / 2).coerceIn(1, 32),
            onProgress = onProgress,
        ) ?: return AppResult.Failure(AppError.Unexpected("Unable to generate rounds/tables locally with the current constraints."))
        return tournamentRepository.changePlayerCount(
            PlayerCountChangeRequest(
                tournamentId = tournament.id,
                numTries = schedule.triesUsed,
                players = schedule.players,
                tables = schedule.tables,
                removedSlotIds = removedSlotIds,
                waitingEmaIds = waitingEmaIds,
            ),
        ).trackAction(telemetry, TelemetryAction.PLAYER_COUNT_CHANGED)
    }
}
