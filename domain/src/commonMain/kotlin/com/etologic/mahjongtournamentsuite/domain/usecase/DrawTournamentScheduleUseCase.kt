package com.etologic.mahjongtournamentsuite.domain.usecase

import com.etologic.mahjongtournamentsuite.domain.model.TournamentPlayer
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * The draw of a tournament, as the app makes it (docs/draw.md):
 *
 * 1. The first way, [GenerateTournamentScheduleBruteForceParallelUseCase], exactly as it always was, for at most
 *    [firstLimit] of the size.
 * 2. When it has not found a draw by then, the second way, [GenerateScheduleBySwapsUseCase], with the same rules,
 *    for at most [secondLimit].
 * 3. When neither finds one, null: the app says that it could not draw and the organizer tries fewer rounds.
 *
 * The limits grow with the tables and rounds: a big tournament needs more time to draw, even when it is easy.
 */
class DrawTournamentScheduleUseCase(
    private val first: GenerateTournamentScheduleBruteForceParallelUseCase = GenerateTournamentScheduleBruteForceParallelUseCase(),
    private val second: GenerateScheduleBySwapsUseCase = GenerateScheduleBySwapsUseCase(),
    private val firstLimit: (tables: Int, rounds: Int) -> Duration = ::firstDrawLimit,
    private val secondLimit: (tables: Int, rounds: Int) -> Duration = ::secondDrawLimit,
) {
    suspend operator fun invoke(
        numPlayers: Int,
        numRounds: Int,
        isTeams: Boolean,
        maxConcurrency: Int,
        onProgress: (TournamentScheduleCalcProgress) -> Unit,
    ): TournamentScheduleCalcResult? {
        if (numPlayers <= 0 || numPlayers % 4 != 0 || numRounds <= 0) return null
        val tables = numPlayers / 4
        // The limits count real time, on Dispatchers.Default, whatever clock the caller runs on (a test's virtual time).
        withContext(Dispatchers.Default) {
            withTimeoutOrNull(firstLimit(tables, numRounds)) {
                first(numPlayers, numRounds, isTeams, maxConcurrency, onProgress)
            }
        }?.let { return it }

        // The same slots that the first way makes: 1..N, named by number, and teams of 4 by number with teams.
        val players = (1..numPlayers).map { id -> TournamentPlayer(id = id, name = "Player $id", team = if (isTeams) (id - 1) / 4 + 1 else 0) }
        val seed = Random.Default.nextLong()
        val found = withContext(Dispatchers.Default) {
            withTimeoutOrNull(secondLimit(tables, numRounds)) {
                second(
                    playerIds = players.map { it.id },
                    roundIds = (1..numRounds).toList(),
                    teamOf = if (isTeams) players.associate { it.id to it.team } else emptyMap(),
                    maxConcurrency = maxConcurrency,
                    seed = seed,
                    onProgress = onProgress,
                )
            }
        } ?: return null
        return TournamentScheduleCalcResult(
            triesUsed = found.swaps,
            randomSeed = seed,
            players = players,
            tables = found.tables,
            usedBackup = true,
        )
    }
}

/**
 * Time for the first way: 15 s, plus 0.1 s for each table of each round, up to 1 min. The first way finds the easy
 * tournaments in a few seconds, and the second finds the hard ones in seconds too (measured 22-nov, docs/draw.md), so
 * a hard one waits little before the second way starts.
 */
fun firstDrawLimit(tables: Int, rounds: Int): Duration =
    (15.seconds + (100.0 * tables * rounds).roundToLong().milliseconds).coerceAtMost(60.seconds)

/** Time for the second way: 30 s, plus 0.5 s for each table of each round, up to 5 min. */
fun secondDrawLimit(tables: Int, rounds: Int): Duration =
    (30.seconds + (500.0 * tables * rounds).roundToLong().milliseconds).coerceAtMost(300.seconds)
