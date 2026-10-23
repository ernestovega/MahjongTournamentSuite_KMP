package com.etologic.mahjongtournamentsuite.presentation.screen

import com.etologic.mahjongtournamentsuite.domain.model.*
import com.etologic.mahjongtournamentsuite.domain.repository.*

/**
 * A tournament server in memory. Several clients can share one instance, so tests can run races
 * between users. The hooks let a test run code at the exact moment of a request.
 */
internal class FakeTournamentRepository : TournamentRepository {
    val tables = linkedMapOf<TableKey, Pair<TableState, List<TableHand>>>()
    val calls = mutableListOf<String>()

    var manifestFails = false
    var playersRevision = 1L
    var emaPlayersRevision = 1L
    var tournamentsRevision = 1L
    var failPlayers = false
    /** Fails the next [getTableWithHands] calls for this many requests. */
    var failTableReads = 0
    /** Makes every save fail with this error. */
    var saveError: AppError? = null
    /** A conflict answer without table data, like an unreadable server body. */
    var conflictWithoutTable = false

    var beforeManifest: suspend () -> Unit = {}
    var beforeTableRead: suspend (TableKey) -> Unit = {}
    var beforeSave: suspend (TableKey) -> Unit = {}

    fun seed(table: TableState, hands: List<TableHand> = syncHands()) {
        tables[TableKey(table.roundId, table.tableId)] = table to hands
    }

    fun summaries(): List<TournamentTable> = tables.values.map { (table, hands) ->
        TournamentTable(
            roundId = table.roundId,
            tableId = table.tableId,
            playerIds = table.playerIds,
            isCompleted = table.isCompleted,
            useTotalsOnly = table.useTotalsOnly,
            hasProgress = hands.any { it.handScore.isNotBlank() },
            version = table.version,
        )
    }

    /** Another user's save: the table changes and its version goes up. */
    fun otherUserSaves(roundId: Int, tableId: Int, east: String? = null, handScore: String? = null, isCompleted: Boolean? = null) {
        val key = TableKey(roundId, tableId)
        val (table, hands) = tables.getValue(key)
        tables[key] = table.copy(
            version = table.version + 1,
            playerEastId = east ?: table.playerEastId,
            isCompleted = isCompleted ?: table.isCompleted,
        ) to hands.map { if (it.handId == 1 && handScore != null) it.copy(handScore = handScore) else it }
    }

    override suspend fun listTournamentTables(
        tournamentId: String,
        roundId: Int?,
        refreshMode: RefreshMode,
    ): AppResult<List<TournamentTable>> {
        calls += "list ${roundId ?: "all"} $refreshMode"
        return AppResult.Success(summaries().filter { roundId == null || it.roundId == roundId })
    }

    override suspend fun getTableWithHands(
        tournamentId: String,
        roundId: Int,
        tableId: Int,
        refreshMode: RefreshMode,
    ): AppResult<Pair<TableState, List<TableHand>>> {
        val key = TableKey(roundId, tableId)
        calls += "get $roundId/$tableId $refreshMode"
        beforeTableRead(key)
        if (failTableReads > 0) {
            failTableReads--
            return AppResult.Failure(AppError.Network)
        }
        return tables[key]?.let { AppResult.Success(it) } ?: AppResult.Failure(AppError.Unexpected("Table not found"))
    }

    override suspend fun getAllTablesWithHands(
        tournamentId: String,
        refreshMode: RefreshMode,
    ): AppResult<List<Pair<TableState, List<TableHand>>>> {
        calls += "all $refreshMode"
        return AppResult.Success(tables.entries.sortedWith(compareBy({ it.key.roundId }, { it.key.tableId })).map { it.value })
    }

    override suspend fun saveTableState(
        tournamentId: String,
        roundId: Int,
        tableId: Int,
        expectedVersion: Long,
        tablePatch: Map<String, Any?>,
        handPatches: Map<Int, Map<String, Any?>>,
    ): AppResult<Pair<TableState, List<TableHand>>> {
        val key = TableKey(roundId, tableId)
        calls += "save $roundId/$tableId v$expectedVersion"
        beforeSave(key)
        saveError?.let { return AppResult.Failure(it) }
        val (table, hands) = tables[key] ?: return AppResult.Failure(AppError.Unexpected("Table not found"))
        if (table.version != expectedVersion) {
            return AppResult.Failure(
                AppError.Conflict(
                    message = "This table changed on the server.",
                    expectedVersion = expectedVersion,
                    currentVersion = table.version,
                    currentTable = table.takeUnless { conflictWithoutTable },
                    currentHands = hands.takeUnless { conflictWithoutTable }.orEmpty(),
                ),
            )
        }
        val savedTable = table.copy(
            version = table.version + 1,
            playerEastId = tablePatch["playerEastId"] as? String ?: table.playerEastId,
            isCompleted = tablePatch["isCompleted"] as? Boolean ?: table.isCompleted,
        )
        val savedHands = hands.map { hand ->
            val patch = handPatches[hand.handId] ?: return@map hand
            hand.copy(
                handScore = patch["handScore"] as? String ?: hand.handScore,
                playerWinnerId = patch["playerWinnerId"] as? String ?: hand.playerWinnerId,
                isDone = patch["isDone"] as? Boolean ?: hand.isDone,
            )
        }
        tables[key] = savedTable to savedHands
        return AppResult.Success(savedTable to savedHands)
    }

    override suspend fun getTournamentManifest(tournamentId: String): AppResult<TournamentManifest> {
        calls += "manifest"
        beforeManifest()
        if (manifestFails) return AppResult.Failure(AppError.Network)
        return AppResult.Success(
            TournamentManifest(
                tablesRevision = tables.values.sumOf { it.first.version },
                tableVersions = tables.mapValues { it.value.first.version },
                playersRevision = playersRevision,
                emaPlayersRevision = emaPlayersRevision,
                tournamentsRevision = tournamentsRevision,
            ),
        )
    }

    var tournaments: List<Tournament> = emptyList()
    var players: List<TournamentPlayer> = emptyList()
    var rounds: List<TournamentRound> = listOf(TournamentRound(1))
    var countries: List<Country> = emptyList()

    override suspend fun listTournaments(refreshMode: RefreshMode): AppResult<List<Tournament>> {
        calls += "tournaments $refreshMode"
        return AppResult.Success(tournaments)
    }

    override suspend fun createTournament(request: CreateTournamentRequest) = unusedFakeResult<Tournament>()

    override suspend fun renameTournament(tournamentId: String, name: String) = unusedFakeResult<Unit>()

    override suspend fun updateTournamentSettings(
        tournamentId: String,
        name: String,
        shortName: String,
        primaryColor: String,
        eventStartDate: String,
        eventEndDate: String,
        hostCountry: String,
        hostCity: String,
        venue: TournamentVenue,
        associationLogoContentType: String?,
        associationLogoBytes: ByteArray?,
        associationLogoSourceTournamentId: String?,
        removeAssociationLogo: Boolean,
        roundSchedules: List<TournamentRoundSchedule>,
        agendaItems: List<TournamentAgendaItem>,
        countBestHands: Boolean?,
        countChickenHands: Boolean?,
        matchDurationMinutes: Int?,
        matchEndMode: MatchEndMode?,
        matchExtraMinutes: Int?,
        timeZone: String?,
    ) = unusedFakeResult<Tournament>()

    override suspend fun generateTournamentIdCards(tournamentId: String, sheet: IdCardSheetLayout?) = unusedFakeResult<ByteArray>()
    override suspend fun generateIdCardProof(request: IdCardProofRequest) = unusedFakeResult<ByteArray>()
    override suspend fun generateTournamentIdList(tournamentId: String) = unusedFakeResult<ByteArray>()
    override suspend fun generateEmaReport(tournamentId: String, rankings: List<PlayerRanking>) = unusedFakeResult<ByteArray>()
    override suspend fun deleteTournament(tournamentId: String) = unusedFakeResult<Unit>()

    override suspend fun listTournamentMembers(tournamentId: String, refreshMode: RefreshMode) = unusedFakeResult<List<TournamentMember>>()
    override suspend fun upsertTournamentMember(tournamentId: String, uid: String) = unusedFakeResult<Unit>()
    override suspend fun removeTournamentMember(tournamentId: String, uid: String) = unusedFakeResult<Unit>()

    override suspend fun listTournamentPlayers(tournamentId: String, refreshMode: RefreshMode): AppResult<List<TournamentPlayer>> {
        calls += "players $refreshMode"
        if (failPlayers) return AppResult.Failure(AppError.Network)
        return AppResult.Success(players)
    }
    override suspend fun listTournamentTeams(tournamentId: String, refreshMode: RefreshMode) = AppResult.Success(emptyList<TournamentTeam>())
    override suspend fun updateTournamentTeam(tournamentId: String, teamId: Int, name: String, emaIds: List<String?>) = unusedFakeResult<Unit>()
    override suspend fun listCountries(refreshMode: RefreshMode): AppResult<List<Country>> = AppResult.Success(countries)
    override suspend fun resolveVenueLink(url: String): AppResult<ResolvedVenueLink> = AppResult.Success(ResolvedVenueLink(url, null))

    override suspend fun assignTournamentPlayer(
        tournamentId: String,
        tournamentPlayerId: Int,
        emaId: String?,
        nonMember: NonMemberPlayer?,
    ) = unusedFakeResult<Unit>()

    override suspend fun setPlayerSubstitution(tournamentId: String, tournamentPlayerId: Int, substitution: PlayerSubstitution?) = unusedFakeResult<Unit>()

    override suspend fun listTournamentRounds(tournamentId: String, refreshMode: RefreshMode): AppResult<List<TournamentRound>> =
        AppResult.Success(rounds)



    override suspend fun getRoundTablesWithHands(
        tournamentId: String,
        roundId: Int,
        refreshMode: RefreshMode,
    ): AppResult<List<Pair<TableState, List<TableHand>>>> {
        calls += "round $roundId $refreshMode"
        return AppResult.Success(
            tables.entries.filter { it.key.roundId == roundId }.sortedBy { it.key.tableId }.map { it.value },
        )
    }

}

internal class NoPlayersRepository : PlayerRepository {
    override suspend fun listPlayers(refreshMode: RefreshMode) = AppResult.Success(emptyList<Player>())
    override suspend fun createPlayer(player: Player): AppResult<Player> = error("unused")
    override suspend fun updatePlayer(previousEmaId: String, player: Player): AppResult<Unit> = error("unused")
    override suspend fun updatePlayerPhoto(emaId: String, contentType: String, bytes: ByteArray): AppResult<Player> = error("unused")
}

private fun <T> unusedFakeResult(): AppResult<T> =
    AppResult.Failure(AppError.Unexpected("Unused test repository method"))
