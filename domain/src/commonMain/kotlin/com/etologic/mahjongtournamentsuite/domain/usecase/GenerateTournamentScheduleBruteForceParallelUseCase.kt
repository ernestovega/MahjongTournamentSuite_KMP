package com.etologic.mahjongtournamentsuite.domain.usecase

import com.etologic.mahjongtournamentsuite.domain.model.TournamentPlayer
import com.etologic.mahjongtournamentsuite.domain.model.TournamentTable
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

data class TournamentScheduleCalcProgress(
    val runningTries: Int,
    val tried: Long,
    val maxConcurrency: Int,
    /** True while the second way to draw runs ([GenerateScheduleBySwapsUseCase]), after the first took too long. */
    val backup: Boolean = false,
)

data class TournamentScheduleCalcResult(
    val triesUsed: Long,
    /** Random seed used for this generation. Useful for diagnostics and audit logs. */
    val randomSeed: Long,
    val players: List<TournamentPlayer>,
    val tables: List<TournamentTable>,
    /** True when the second way to draw found it ([DrawTournamentScheduleUseCase]). */
    val usedBackup: Boolean = false,
    /**
     * With more rounds than fit without repeats, the rounds of each block ([DrawRoundsInBlocksUseCase]): nobody meets
     * anyone a second time before the second block, a third time before the third… 0 for a draw without repeats.
     */
    val perBlock: Int = 0,
)

/**
 * Brute-force schedule generation, using bounded concurrency.
 *
 * Each "try" starts from scratch and either produces a valid schedule or fails.
 */
class GenerateTournamentScheduleBruteForceParallelUseCase {
    suspend operator fun invoke(
        numPlayers: Int,
        numRounds: Int,
        isTeams: Boolean,
        maxConcurrency: Int,
        onProgress: (TournamentScheduleCalcProgress) -> Unit,
    ): TournamentScheduleCalcResult? = coroutineScope {
        if (numPlayers <= 0 || numPlayers % 4 != 0) return@coroutineScope null
        if (numRounds <= 0) return@coroutineScope null

        val safeConcurrency = maxConcurrency.coerceAtLeast(1)
        val randomSeed = Random.Default.nextLong()

        val players = generatePlayers(numPlayers)
        val teamByPlayerId = IntArray(numPlayers + 1) { 0 }.also { teams ->
            for (p in players) teams[p.id] = p.team
        }
        val numTablesPerRound = numPlayers / 4

        val progressMutex = Mutex()
        var running = 0
        var tried = 0L

        val reportInterval = 100.milliseconds
        var lastReport = TimeSource.Monotonic.markNow()

        suspend fun report(force: Boolean) {
            val shouldReport = force || lastReport.elapsedNow() >= reportInterval
            if (!shouldReport) return

            val snapshot = progressMutex.withLock {
                TournamentScheduleCalcProgress(
                    runningTries = running,
                    tried = tried,
                    maxConcurrency = safeConcurrency,
                )
            }
            lastReport = TimeSource.Monotonic.markNow()
            onProgress(snapshot)
        }

        val found = CompletableDeferred<Pair<Long, List<TablePlayer>>>()
        val tries = Channel<Long>(capacity = safeConcurrency * 2)

        found.invokeOnCompletion {
            tries.cancel()
        }

        val producer = launch {
            var i = 1L
            while (!found.isCompleted) {
                tries.send(i++)
            }
        }

        val workers = List(safeConcurrency) { workerIndex ->
            launch(Dispatchers.Default) {
                val seedBase = randomSeed + (workerIndex.toLong() shl 32)
                for (tryNumber in tries) {
                    ensureActive()
                    if (found.isCompleted) break

                    progressMutex.withLock { running++ }
                    report(force = false)

                    val schedule = attemptGenerateTournament(
                        numPlayers = numPlayers,
                        numRounds = numRounds,
                        numTablesPerRound = numTablesPerRound,
                        isTeamsChecked = isTeams,
                        teamByPlayerId = teamByPlayerId,
                        rng = Random(seedBase + tryNumber),
                    )

                    var completed = false
                    var triesUsedAtSuccess = 0L
                    progressMutex.withLock {
                        running--
                        tried++
                        if (schedule != null && !found.isCompleted) {
                            triesUsedAtSuccess = tried
                            found.complete(triesUsedAtSuccess to schedule)
                            completed = true
                        }
                    }

                    if (completed) report(force = true) else report(force = false)
                }
            }
        }

        report(force = true)

        producer.join()
        workers.joinAll()
        report(force = true)

        val (triesUsed, tablePlayers) = found.await()
        val domainPlayers = players.map { p ->
            TournamentPlayer(
                id = p.id,
                name = "Player ${p.id}",
                // A tournament without teams has no team data.
                team = if (isTeams) p.team else 0,
            )
        }
        TournamentScheduleCalcResult(
            triesUsed = triesUsed,
            randomSeed = randomSeed,
            players = domainPlayers,
            tables = generateTables(tablePlayers),
        )
    }
}

private data class Player(
    val id: Int,
    val team: Int,
)

private data class TablePlayer(
    val round: Int,
    val table: Int,
    val seat: Int,
    val playerId: Int,
)

private fun generatePlayers(numPlayers: Int): List<Player> {
    // Players 1 to 4 are team 1, 5 to 8 team 2, and so on.
    val players = ArrayList<Player>(numPlayers)
    (1..numPlayers).forEach { id -> players.add(Player(id = id, team = (id - 1) / 4 + 1)) }
    return players
}

private fun attemptGenerateTournament(
    numPlayers: Int,
    numRounds: Int,
    numTablesPerRound: Int,
    isTeamsChecked: Boolean,
    teamByPlayerId: IntArray,
    rng: Random,
): List<TablePlayer>? {
    val allPlayerIds = IntArray(numPlayers) { it + 1 }
    val tablePlayers = ArrayList<TablePlayer>(numRounds * numTablesPerRound * 4)

    // The draw has at least one round of one table, so these ranges are never empty.
    (1..numRounds).forEach { currentRound ->
        val playersNotUsedThisRound = allPlayerIds.toMutableList()

        (1..numTablesPerRound).forEach { currentTable ->
            for (currentSeat in 1..4) {
                val chosenId = choosePlayerForSeat(
                    playersNotUsedThisRound = playersNotUsedThisRound,
                    tablePlayers = tablePlayers,
                    currentRound = currentRound,
                    currentTable = currentTable,
                    numRounds = numRounds,
                    numPlayers = numPlayers,
                    isTeamsChecked = isTeamsChecked,
                    teamByPlayerId = teamByPlayerId,
                    rng = rng,
                ) ?: return null
                tablePlayers.add(
                    TablePlayer(
                        round = currentRound,
                        table = currentTable,
                        seat = currentSeat,
                        playerId = chosenId,
                    ),
                )
            }
        }
    }

    return tablePlayers
}

private fun choosePlayerForSeat(
    playersNotUsedThisRound: MutableList<Int>,
    tablePlayers: List<TablePlayer>,
    currentRound: Int,
    currentTable: Int,
    numRounds: Int,
    numPlayers: Int,
    isTeamsChecked: Boolean,
    teamByPlayerId: IntArray,
    rng: Random,
): Int? {
    val candidateIds = playersNotUsedThisRound.toIntArray()
    val available = BooleanArray(numPlayers + 1).also { flags ->
        candidateIds.forEach { flags[it] = true }
    }
    var safeGuard = 0

    while (true) {
        if (safeGuard++ > 50_000) return null
        if (candidateIds.none { available[it] }) return null

        val candidateId = candidateIds[rng.nextInt(candidateIds.size)]
        if (!available[candidateId]) continue
        available[candidateId] = false

        val rivals = getRivals(
            tablePlayers = tablePlayers,
            chosenId = candidateId,
            currentRound = currentRound,
            numRounds = numRounds,
            numPlayers = numPlayers,
        )
        val chosenTeam = teamByPlayerId[candidateId]
        val conflicts = tablePlayers.any { player ->
            player.round == currentRound &&
                player.table == currentTable &&
                (rivals[player.playerId] ||
                    (isTeamsChecked && teamByPlayerId[player.playerId] == chosenTeam))
        }

        if (!conflicts) {
            playersNotUsedThisRound.remove(candidateId)
            return candidateId
        }
    }
}

private fun getRivals(
    tablePlayers: List<TablePlayer>,
    chosenId: Int,
    currentRound: Int,
    numRounds: Int,
    numPlayers: Int,
): BooleanArray {
    if (currentRound <= 1) return BooleanArray(numPlayers + 1) { false }

    val tablesWhereChosenPlayed = ArrayList<Pair<Int, Int>>(numRounds)
    for (tp in tablePlayers) {
        if (tp.round < currentRound && tp.playerId == chosenId) {
            tablesWhereChosenPlayed.add(tp.round to tp.table)
        }
    }
    // From round 2 on, everyone played every round before, so the list is never empty.
    val rivals = BooleanArray(numPlayers + 1) { false }
    for (tp in tablePlayers) {
        if (tp.round >= currentRound) continue
        for (rt in tablesWhereChosenPlayed) {
            if (tp.round == rt.first && tp.table == rt.second && tp.playerId != chosenId) {
                rivals[tp.playerId] = true
            }
        }
    }
    return rivals
}

/** The tables of the draw, by round and table, each with its 4 players by seat. Every table has its 4 seats. */
private fun generateTables(tablePlayers: List<TablePlayer>): List<TournamentTable> = tablePlayers.groupBy { it.round to it.table }.entries
    .sortedWith(compareBy({ it.key.first }, { it.key.second }))
    .map { (key, seats) ->
        TournamentTable(
            roundId = key.first,
            tableId = key.second,
            playerIds = seats.sortedBy { it.seat }.map { it.playerId },
            isCompleted = false,
            useTotalsOnly = false,
            hasProgress = false,
        )
    }
