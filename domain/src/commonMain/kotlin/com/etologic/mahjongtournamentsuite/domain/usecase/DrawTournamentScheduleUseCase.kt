package com.etologic.mahjongtournamentsuite.domain.usecase

import com.etologic.mahjongtournamentsuite.domain.model.RepeatFreeRounds
import com.etologic.mahjongtournamentsuite.domain.model.TournamentPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToLong
import kotlin.random.Random
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
 * With many rounds for the players (docs/draw.md, "More rounds than fit without repeats"):
 * - With more rounds than fit without repeats ([RepeatFreeRounds.max]), neither way can find one: the rounds go in
 *   blocks at once ([DrawRoundsInBlocksUseCase]), for at most [blocksLimit].
 * - For a size with a perfect pattern ([PerfectPattern]: 16, 28, 40, 64, 256 players), when the first way is not in
 *   time the pattern takes the place of the second: it is instant, also with teams.
 * - With more rounds than the draw finds for sure ([RepeatFreeRounds.sure]) but not more than fit, both ways try, and
 *   when neither finds one, the rounds go in blocks instead of no draw.
 *
 * The limits grow with the tables and rounds: a big tournament needs more time to draw, even when it is easy.
 */
class DrawTournamentScheduleUseCase(
    private val first: GenerateTournamentScheduleBruteForceParallelUseCase = GenerateTournamentScheduleBruteForceParallelUseCase(),
    private val second: GenerateScheduleBySwapsUseCase = GenerateScheduleBySwapsUseCase(),
    private val blocks: DrawRoundsInBlocksUseCase = DrawRoundsInBlocksUseCase(second),
    private val firstLimit: (tables: Int, rounds: Int) -> Duration = ::firstDrawLimit,
    private val secondLimit: (tables: Int, rounds: Int) -> Duration = ::secondDrawLimit,
    /** Time for the draw in blocks: the same as the second way. */
    private val blocksLimit: (tables: Int, rounds: Int) -> Duration = ::secondDrawLimit,
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
        if (numRounds > RepeatFreeRounds.max(numPlayers, isTeams)) return inBlocks(numPlayers, numRounds, isTeams, maxConcurrency, backup = false, onProgress)
        // The limits count real time, on Dispatchers.Default, whatever clock the caller runs on (a test's virtual time).
        withContext(Dispatchers.Default) {
            withTimeoutOrNull(firstLimit(tables, numRounds)) {
                first(numPlayers, numRounds, isTeams, maxConcurrency, onProgress)
            }
        }?.let { return it }
        if (PerfectPattern.of(numPlayers) != null) return inBlocks(numPlayers, numRounds, isTeams, maxConcurrency, backup = true, onProgress)

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
        } ?: return if (numRounds > RepeatFreeRounds.sure(numPlayers, isTeams)) {
            inBlocks(numPlayers, numRounds, isTeams, maxConcurrency, backup = false, onProgress)
        } else {
            null
        }
        return TournamentScheduleCalcResult(
            triesUsed = found.swaps,
            randomSeed = seed,
            players = players,
            tables = found.tables,
            usedBackup = true,
        )
    }

    /**
     * The draw on the slots of a tournament that changes its number of players ([slots]: numbers and teams kept).
     * With teams of 4, or without teams, it is the draw of the app moved onto the slots ([onSlots]). When a removal
     * left some teams incomplete (Ernesto, 24-nov: the organizer marks people, and the teams are reorganized
     * afterwards), the first way cannot draw it: the rounds are drawn by swaps on the slots themselves, with every
     * team as it is (teammates never share a table), in blocks when they do not fit without repeats.
     */
    suspend fun onSlots(
        slots: List<TournamentPlayer>,
        numRounds: Int,
        isTeams: Boolean,
        maxConcurrency: Int,
        onProgress: (TournamentScheduleCalcProgress) -> Unit,
    ): TournamentScheduleCalcResult? {
        val count = slots.size
        if (count <= 0 || count % 4 != 0 || numRounds <= 0 || slots.map { it.id }.toSet().size != count) return null
        val teamsOfFour = !isTeams || slots.groupBy { it.team }.values.all { it.size == 4 }
        if (teamsOfFour) return invoke(count, numRounds, isTeams, maxConcurrency, onProgress)?.onSlots(slots, isTeams)
        val players = slots.sortedBy { it.id }.map { TournamentPlayer(id = it.id, name = "Player ${it.id}", team = it.team) }
        val seed = Random.Default.nextLong()
        val found = withContext(Dispatchers.Default) {
            withTimeoutOrNull(blocksLimit(count / 4, numRounds)) {
                blocks(
                    playerIds = players.map { it.id },
                    roundIds = (1..numRounds).toList(),
                    teamOf = players.associate { it.id to it.team },
                    maxConcurrency = maxConcurrency,
                    seed = seed,
                    onProgress = { onProgress(it.copy(backup = true)) },
                )
            }
        } ?: return null
        onProgress(TournamentScheduleCalcProgress(runningTries = 0, tried = found.swaps, maxConcurrency = maxConcurrency, backup = true))
        return TournamentScheduleCalcResult(
            triesUsed = found.swaps.coerceAtLeast(1),
            randomSeed = seed,
            players = players,
            tables = found.tables,
            usedBackup = true,
            perBlock = if (numRounds > found.perBlock) found.perBlock else 0,
        )
    }

    private suspend fun inBlocks(
        numPlayers: Int,
        numRounds: Int,
        isTeams: Boolean,
        maxConcurrency: Int,
        /** True when it takes the place of the second way, after the first one ran out of time. */
        backup: Boolean,
        onProgress: (TournamentScheduleCalcProgress) -> Unit,
    ): TournamentScheduleCalcResult? {
        val players = (1..numPlayers).map { id -> TournamentPlayer(id = id, name = "Player $id", team = if (isTeams) (id - 1) / 4 + 1 else 0) }
        val seed = Random.Default.nextLong()
        val found = withContext(Dispatchers.Default) {
            withTimeoutOrNull(blocksLimit(numPlayers / 4, numRounds)) {
                blocks(
                    playerIds = players.map { it.id },
                    roundIds = (1..numRounds).toList(),
                    teamOf = if (isTeams) players.associate { it.id to it.team } else emptyMap(),
                    maxConcurrency = maxConcurrency,
                    seed = seed,
                    onProgress = { onProgress(it.copy(backup = backup)) },
                )
            }
        } ?: return null
        onProgress(TournamentScheduleCalcProgress(runningTries = 0, tried = found.swaps, maxConcurrency = maxConcurrency, backup = backup))
        return TournamentScheduleCalcResult(
            // A pattern is one try, with no swaps.
            triesUsed = found.swaps.coerceAtLeast(1),
            randomSeed = seed,
            players = players,
            tables = found.tables,
            // Within the rounds that fit, it is a draw without repeats that took the place of the second way.
            usedBackup = backup,
            perBlock = if (numRounds > found.perBlock) found.perBlock else 0,
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

/**
 * The same draw on other slot numbers: both ways draw slots 1..N (with teams, 1-4 in team 1, 5-8 in team 2…), and the
 * change of the number of players keeps the numbers of the people who stay (docs/player-count-change.md). Without
 * history every renaming keeps the rules, so slot i of the draw becomes the i-th of [slots] in order; with teams, the
 * k-th team of the draw becomes the k-th team of [slots], and its 4 players in order. Returns null when [slots] do not
 * fit the draw: another count, a repeated number, or with teams a team without exactly 4 slots.
 */
fun TournamentScheduleCalcResult.onSlots(slots: List<TournamentPlayer>, isTeams: Boolean): TournamentScheduleCalcResult? {
    if (slots.size != players.size || slots.map { it.id }.toSet().size != slots.size) return null
    val mapping = HashMap<Int, Int>(slots.size)
    if (!isTeams) {
        val from = players.map { it.id }.sorted()
        val to = slots.map { it.id }.sorted()
        from.zip(to).forEach { (a, b) -> mapping[a] = b }
    } else {
        val fromTeams = players.groupBy { it.team }.entries.sortedBy { it.key }.map { (_, team) -> team.map { it.id }.sorted() }
        val toTeams = slots.groupBy { it.team }.entries.sortedBy { it.key }.map { (_, team) -> team.map { it.id }.sorted() }
        if (fromTeams.size != toTeams.size || toTeams.any { it.size != 4 } || fromTeams.any { it.size != 4 }) return null
        fromTeams.zip(toTeams).forEach { (a, b) -> a.zip(b).forEach { (x, y) -> mapping[x] = y } }
    }
    return copy(
        players = slots.sortedBy { it.id }.map { TournamentPlayer(id = it.id, name = "Player ${it.id}", team = if (isTeams) it.team else 0) },
        tables = tables.map { table -> table.copy(playerIds = table.playerIds.map { mapping.getValue(it) }) },
    )
}
