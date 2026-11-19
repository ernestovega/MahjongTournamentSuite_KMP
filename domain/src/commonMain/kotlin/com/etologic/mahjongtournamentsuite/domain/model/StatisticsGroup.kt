package com.etologic.mahjongtournamentsuite.domain.model

/**
 * Which tournaments and players a statistics group keeps. A null field keeps everything. The filters combine:
 * for example the Spanish players in the tournaments played in Sweden in 2026.
 */
data class StatisticsGroupFilter(
    val tournamentIds: Set<String>? = null,
    /** Two-letter ISO code of the country where the tournaments were played. */
    val tournamentCountry: String? = null,
    /** EMA code of the country of the players. An empty code keeps the players without a country. */
    val playerCountry: String? = null,
    /** The first and last year of the tournaments, by the first day of each one. */
    val fromYear: Int? = null,
    val toYear: Int? = null,
)

/** The sums of the tables of the tournaments of a group, whoever played them. */
data class GroupTables(
    val tablesCompleted: Int = 0,
    val tablesWithHands: Int = 0,
    val handsPlayed: Int = 0,
    val drawnHands: Int = 0,
    val winningHands: Int = 0,
    val selfDraws: Int = 0,
    val winningScoreSum: Int = 0,
    val chickenHands: Int = 0,
    val penaltyCount: Int = 0,
    val highestHand: Int = 0,
)

/** The sums and the best values of one person in the tournaments of a group. A run counts inside one tournament. */
data class GroupPerson(
    val emaId: String,
    val tournaments: Int = 0,
    val tables: Int = 0,
    val points: Double = 0.0,
    val score: Int = 0,
    val places: List<Int> = listOf(0, 0, 0, 0),
    val tablesWithHands: Int = 0,
    val handsPlayed: Int = 0,
    val handsWon: Int = 0,
    val selfDraws: Int = 0,
    val dealIns: Int = 0,
    val drawnHands: Int = 0,
    val winningScoreSum: Int = 0,
    val chickenHands: Int = 0,
    val dealInScoreSum: Int = 0,
    val lostToSelfDraws: Int = 0,
    val otherWins: Int = 0,
    val penaltyCount: Int = 0,
    val penaltyPoints: Int = 0,
    val bestHand: Int = 0,
    val bestTableScore: Int? = null,
    val mostHandsWonAtATable: Int? = null,
    val maxPenalty: Int = 0,
    val tableWinRun: Int = 0,
    val handWinRun: Int? = null,
    val noDealInRun: Int? = null,
)

/** The statistics of a group of tournaments and players. */
data class StatisticsGroup(
    /** The tournaments that the filter keeps and that have a completed table. */
    val tournamentIds: List<String>,
    val tables: GroupTables,
    /** The people that the filter keeps, with their numbers in those tournaments. */
    val persons: List<GroupPerson>,
)

/** The best values of the people of a group. A run counts inside one tournament. Null when no table has hands. */
data class GroupRecords(
    val highestTableScore: Int?,
    val mostHandsWonAtATable: Int?,
    val tableWinRun: Int,
    val handWinRun: Int?,
    val noDealInRun: Int?,
)

fun StatisticsGroup.records(): GroupRecords = GroupRecords(
    highestTableScore = persons.mapNotNull { it.bestTableScore }.maxOrNull(),
    mostHandsWonAtATable = persons.mapNotNull { it.mostHandsWonAtATable }.maxOrNull(),
    tableWinRun = persons.maxOfOrNull { it.tableWinRun } ?: 0,
    handWinRun = persons.mapNotNull { it.handWinRun }.maxOrNull(),
    noDealInRun = persons.mapNotNull { it.noDealInRun }.maxOrNull(),
)

/**
 * The person as statistics with one result that holds all the sums. The rates, the averages and the skills are right.
 * The values of one table and the runs are not: they come from [GroupRecords].
 */
fun GroupPerson.toPlayerStatistics(playerId: Int): PlayerStatistics = PlayerStatistics(
    playerId,
    listOf(
        TableResult(
            roundId = 0, tableId = 0, seat = 0, position = 1,
            score = score, points = points,
            hasHands = tablesWithHands > 0,
            handsPlayed = handsPlayed, handsWon = handsWon, selfDraws = selfDraws, dealIns = dealIns,
            drawnHands = drawnHands, winningScoreSum = winningScoreSum, bestHand = bestHand, chickenHands = chickenHands,
            dealInScoreSum = dealInScoreSum, lostToSelfDraws = lostToSelfDraws, otherWins = otherWins,
            penaltyCount = penaltyCount, penaltyPoints = penaltyPoints, maxPenalty = maxPenalty,
        ),
    ),
)

/** The numbers of one person from the results of each tournament, in play order. The same rules as the server. */
fun groupPersonOf(emaId: String, tournaments: List<List<TableResult>>): GroupPerson {
    val all = tournaments.flatten()
    val withHands = all.filter { it.hasHands }
    val perTournament = tournaments.filter { it.isNotEmpty() }.map { PlayerStatistics(0, it) }
    return GroupPerson(
        emaId = emaId,
        tournaments = perTournament.size,
        tables = all.size,
        points = all.sumOf { it.points },
        score = all.sumOf { it.score },
        places = (1..4).map { place -> all.count { it.position == place } },
        tablesWithHands = withHands.size,
        handsPlayed = withHands.sumOf { it.handsPlayed },
        handsWon = withHands.sumOf { it.handsWon },
        selfDraws = withHands.sumOf { it.selfDraws },
        dealIns = withHands.sumOf { it.dealIns },
        drawnHands = withHands.sumOf { it.drawnHands },
        winningScoreSum = withHands.sumOf { it.winningScoreSum },
        chickenHands = withHands.sumOf { it.chickenHands },
        dealInScoreSum = withHands.sumOf { it.dealInScoreSum },
        lostToSelfDraws = withHands.sumOf { it.lostToSelfDraws },
        otherWins = withHands.sumOf { it.otherWins },
        penaltyCount = withHands.sumOf { it.penaltyCount },
        penaltyPoints = withHands.sumOf { it.penaltyPoints },
        bestHand = withHands.maxOfOrNull { it.bestHand } ?: 0,
        bestTableScore = all.maxOfOrNull { it.score },
        mostHandsWonAtATable = withHands.maxOfOrNull { it.handsWon },
        maxPenalty = withHands.maxOfOrNull { it.maxPenalty } ?: 0,
        tableWinRun = perTournament.maxOfOrNull { it.streaks.tableWins } ?: 0,
        handWinRun = perTournament.mapNotNull { it.streaks.handsWon }.maxOrNull(),
        noDealInRun = perTournament.mapNotNull { it.streaks.handsWithoutDealIn }.maxOrNull(),
    )
}
