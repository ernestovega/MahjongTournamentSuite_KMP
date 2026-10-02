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
)

data class TournamentScheduleCalcResult(
    val triesUsed: Long,
    /** Random seed used for this generation. Useful for diagnostics and audit logs. */
    val randomSeed: Long,
    val players: List<TournamentPlayer>,
    val tables: List<TournamentTable>,
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
                team = p.team,
            )
        }
        val domainTables = generateTables(
            tablePlayers = tablePlayers,
            numRounds = numRounds,
            numTablesPerRound = numTablesPerRound,
        )
        if (domainTables.isEmpty()) return@coroutineScope null

        TournamentScheduleCalcResult(
            triesUsed = triesUsed,
            randomSeed = randomSeed,
            players = domainPlayers,
            tables = domainTables,
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
    val players = ArrayList<Player>(numPlayers)
    for (i in 1..(numPlayers / 4)) {
        val teamId = i
        for (j in 1..4) {
            val playerId = (4 * i) - (4 - j)
            players.add(Player(id = playerId, team = teamId))
        }
    }
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

    for (currentRound in 1..numRounds) {
        val playersNotUsedThisRound = allPlayerIds.toMutableList()

        for (currentTable in 1..numTablesPerRound) {
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
    if (tablesWhereChosenPlayed.isEmpty()) return BooleanArray(numPlayers + 1) { false }

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

private fun generateTables(
    tablePlayers: List<TablePlayer>,
    numRounds: Int,
    numTablesPerRound: Int,
): List<TournamentTable> {
    val tables = ArrayList<TournamentTable>(
        numRounds * numTablesPerRound,
    )
    val playersByTable = tablePlayers.groupBy { it.round to it.table }

    for (currentRound in 1..numRounds) {
        for (currentTable in 1..numTablesPerRound) {
            val players = playersByTable[currentRound to currentTable]
                ?.sortedBy { it.seat }
                ?.map { it.playerId }

            if (players == null || players.size != 4) return emptyList()

            tables.add(
                TournamentTable(
                    roundId = currentRound,
                    tableId = currentTable,
                    playerIds = players,
                    isCompleted = false,
                    useTotalsOnly = true,
                    usePointsCalculation = true,
                    hasProgress = false,
                ),
            )
        }
    }

    return tables
}
