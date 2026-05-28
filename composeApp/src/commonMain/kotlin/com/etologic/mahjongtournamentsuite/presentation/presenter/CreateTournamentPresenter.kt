package com.etologic.mahjongtournamentsuite.presentation.presenter

import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.data.platform.platformCpuCount
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.CreateTournamentRequest
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.repository.TournamentRepository
import com.etologic.mahjongtournamentsuite.domain.usecase.GenerateTournamentScheduleBruteForceParallelUseCase
import com.etologic.mahjongtournamentsuite.domain.usecase.TournamentScheduleCalcProgress
import kotlinx.coroutines.ensureActive
import kotlin.math.roundToInt
import kotlin.coroutines.coroutineContext

class CreateTournamentPresenter(
    private val tournamentRepository: TournamentRepository,
    private val logger: Logger,
    private val generateSchedule: GenerateTournamentScheduleBruteForceParallelUseCase,
) {
    enum class ComputeMode {
        LIGHT,
        HEAVY,
    }

    data class Progress(
        val phase: Phase,
        val runningTries: Int,
        val tried: Long,
        val maxConcurrency: Int,
    )

    enum class Phase {
        CALCULATING,
        CREATING,
    }

    suspend fun createTournament(
        name: String,
        isTeams: Boolean,
        numPlayers: Int,
        numRounds: Int,
        computeMode: ComputeMode,
        onProgress: (Progress) -> Unit,
    ): AppResult<Tournament> {
        logger.i { "Calculating tournament schedule." }

        val cpuCount = platformCpuCount().coerceAtLeast(1)
        val maxConcurrency = calculateMaxConcurrency(
            cpuCount = cpuCount,
            computeMode = computeMode,
            numPlayers = numPlayers,
            numRounds = numRounds,
        )

        fun emit(progress: TournamentScheduleCalcProgress) {
            onProgress(
                Progress(
                    phase = Phase.CALCULATING,
                    runningTries = progress.runningTries,
                    tried = progress.tried,
                    maxConcurrency = progress.maxConcurrency,
                ),
            )
        }

        val schedule = generateSchedule(
            numPlayers = numPlayers,
            numRounds = numRounds,
            isTeams = isTeams,
            maxConcurrency = maxConcurrency,
            onProgress = ::emit,
        ) ?: return AppResult.Failure(
            AppError.Unexpected(
                "Unable to generate rounds/tables locally with the current constraints. Try fewer rounds/players and retry.",
            ),
        )

        coroutineContext.ensureActive()

        onProgress(
            Progress(
                phase = Phase.CREATING,
                runningTries = 0,
                tried = schedule.triesUsed,
                maxConcurrency = 1,
            ),
        )

        val shouldAttemptRecovery = (numPlayers.toLong() * numRounds.toLong()) >= 1_000L
        val tournamentsBeforeIds: Set<String>? = if (shouldAttemptRecovery) {
            when (val before = tournamentRepository.listTournaments()) {
                is AppResult.Success -> before.value.mapTo(mutableSetOf()) { it.id }
                is AppResult.Failure -> null
            }
        } else {
            null
        }

        logger.i { "Creating tournament on backend." }
        val result = tournamentRepository.createTournament(
            CreateTournamentRequest(
                name = name,
                isTeams = isTeams,
                numPlayers = numPlayers,
                numRounds = numRounds,
                numTries = schedule.triesUsed,
                players = schedule.players,
                tables = schedule.tables,
            ),
        )

        if (result is AppResult.Failure && tournamentsBeforeIds != null && result.error.isConnectionDropHint()) {
            when (val after = tournamentRepository.listTournaments()) {
                is AppResult.Success -> {
                    val candidates = after.value.filter { t ->
                        t.id !in tournamentsBeforeIds &&
                            t.name == name &&
                            t.isTeams == isTeams &&
                            t.numPlayers == numPlayers &&
                            t.numRounds == numRounds
                    }
                    if (candidates.size == 1) {
                        logger.w { "Create tournament failed with connection drop, but tournament is visible after retry. Treating as success." }
                        return AppResult.Success(candidates.single())
                    }
                }

                is AppResult.Failure -> Unit
            }
        }

        return result
    }

    private fun calculateMaxConcurrency(
        cpuCount: Int,
        computeMode: ComputeMode,
        numPlayers: Int,
        numRounds: Int,
    ): Int {
        if (cpuCount <= 1) return 1

        // Per-try cost grows strongly with the number of players (more tables, more constraints, more allocations).
        // Scale concurrency down for larger player counts to reduce memory/GC pressure.
        val workUnits = numPlayers.toLong().coerceAtLeast(1).toDouble()
        val baseline = 60.0 // default UI value

        val heavyBase = (cpuCount * 20).toDouble()
        val scale = (baseline / workUnits).coerceIn(0.15, 1.0)
        val heavyScaled = (heavyBase * scale).roundToInt()

        return when (computeMode) {
            ComputeMode.LIGHT -> (cpuCount / 2).coerceAtLeast(1).coerceAtMost(32)
            ComputeMode.HEAVY -> heavyScaled.coerceAtLeast(cpuCount).coerceAtMost(2000)
        }
    }
}

private fun AppError.isConnectionDropHint(): Boolean {
    val msg = (this as? AppError.Unexpected)?.message?.trim().orEmpty()
    return msg.contains("connection dropped", ignoreCase = true) ||
        msg.contains("prematurely closed", ignoreCase = true) ||
        msg.contains("failed to parse http response", ignoreCase = true)
}
