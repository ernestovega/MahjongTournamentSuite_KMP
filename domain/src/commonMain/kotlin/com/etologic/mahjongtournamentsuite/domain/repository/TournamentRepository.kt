package com.etologic.mahjongtournamentsuite.domain.repository

import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.RoundClockAction
import com.etologic.mahjongtournamentsuite.domain.model.RoundClocks
import com.etologic.mahjongtournamentsuite.domain.model.RoundControlChange
import com.etologic.mahjongtournamentsuite.domain.model.TableKey
import com.etologic.mahjongtournamentsuite.domain.model.ResolvedVenueLink
import com.etologic.mahjongtournamentsuite.domain.model.Country
import com.etologic.mahjongtournamentsuite.domain.model.CreateTournamentRequest
import com.etologic.mahjongtournamentsuite.domain.model.PlayerCountChangeRequest
import com.etologic.mahjongtournamentsuite.domain.model.PlayerCountOutcome
import com.etologic.mahjongtournamentsuite.domain.model.TableHand
import com.etologic.mahjongtournamentsuite.domain.model.TableState
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.TournamentPlayer
import com.etologic.mahjongtournamentsuite.domain.model.TournamentRound
import com.etologic.mahjongtournamentsuite.domain.model.TournamentTable
import com.etologic.mahjongtournamentsuite.domain.model.TournamentMember
import com.etologic.mahjongtournamentsuite.domain.model.TournamentTeam
import com.etologic.mahjongtournamentsuite.domain.model.PlayerSubstitution
import com.etologic.mahjongtournamentsuite.domain.model.PlayerRanking
import com.etologic.mahjongtournamentsuite.domain.model.IdCardProofRequest
import com.etologic.mahjongtournamentsuite.domain.model.IdCardSheetLayout
import com.etologic.mahjongtournamentsuite.domain.model.IdCardStaffCandidate
import com.etologic.mahjongtournamentsuite.domain.model.MatchEndMode
import com.etologic.mahjongtournamentsuite.domain.model.MyTournamentSlot
import com.etologic.mahjongtournamentsuite.domain.model.MyTournaments
import com.etologic.mahjongtournamentsuite.domain.model.TournamentAgendaItem
import com.etologic.mahjongtournamentsuite.domain.model.TournamentManifest
import com.etologic.mahjongtournamentsuite.domain.model.TournamentRoundSchedule
import com.etologic.mahjongtournamentsuite.domain.model.TournamentVenue

interface TournamentRepository {
    suspend fun listTournaments(refreshMode: RefreshMode = RefreshMode.IF_CHANGED): AppResult<List<Tournament>>

    suspend fun createTournament(request: CreateTournamentRequest): AppResult<Tournament>

    suspend fun renameTournament(
        tournamentId: String,
        name: String,
    ): AppResult<Unit>

    suspend fun updateTournamentSettings(
        tournamentId: String,
        name: String,
        shortName: String,
        /** The new organizers. Null keeps them. A super admin or a club admin of the main organizer changes them. */
        organizerIds: List<String>? = null,
        eventStartDate: String,
        eventEndDate: String,
        hostCountry: String,
        hostCity: String,
        venue: TournamentVenue = TournamentVenue(),
        roundSchedules: List<TournamentRoundSchedule> = emptyList(),
        agendaItems: List<TournamentAgendaItem> = emptyList(),
        /** Null keeps the stored value. */
        countBestHands: Boolean? = null,
        countChickenHands: Boolean? = null,
        matchDurationMinutes: Int? = null,
        matchEndMode: MatchEndMode? = null,
        matchExtraMinutes: Int? = null,
        timeZone: String? = null,
        /** Null keeps the stored value. */
        isPublic: Boolean? = null,
        /** Null keeps the stored value. */
        showTableDiffs: Boolean? = null,
        /** The first round with hidden results (see [Tournament.hideResultsFromRound]). 0 turns it off. Null keeps it. */
        hideResultsFromRound: Int? = null,
    ): AppResult<Tournament>

    /**
     * Reveals the hidden results of the tournament at the prize giving, or hides them again after a reveal by mistake.
     */
    suspend fun setResultsRevealed(tournamentId: String, revealed: Boolean): AppResult<Unit> =
        AppResult.Failure(AppError.Unexpected("Revealing results is not supported"))

    /**
     * While the results are hidden, the tables of the hidden rounds where the signed-in player plays, with their
     * results. The public tables of those rounds have none. Empty when nothing is hidden.
     */
    suspend fun getOwnHiddenTables(tournamentId: String): AppResult<List<Pair<TableState, List<TableHand>>>> =
        AppResult.Success(emptyList())

    /** Creates the ID cards PDF. Without a sheet layout, the PDF has one card side on each page. */
    suspend fun generateTournamentIdCards(
        tournamentId: String,
        sheet: IdCardSheetLayout? = null,
    ): AppResult<ByteArray>

    suspend fun generateIdCardProof(request: IdCardProofRequest): AppResult<ByteArray>

    /** The names of the staff ID cards that were saved for the tournament, in order. */
    suspend fun loadIdCardStaffNames(tournamentId: String): AppResult<List<String>>

    /** The accounts of the app, by name, to choose the staff of the ID cards. */
    suspend fun listIdCardStaffCandidates(tournamentId: String): AppResult<List<IdCardStaffCandidate>>

    /** Keeps the names of the staff ID cards, so the next ID cards of the tournament start with them. */
    suspend fun saveIdCardStaffNames(tournamentId: String, names: List<String>): AppResult<List<String>>

    suspend fun generateTournamentIdList(tournamentId: String): AppResult<ByteArray>

    /** A PDF with a card for each physical table (its number and the QR of the tournament), [perSheet] cards per A4. See docs/table-qr.md. */
    suspend fun generateTableQrCodes(tournamentId: String, perSheet: Int): AppResult<ByteArray> =
        AppResult.Failure(AppError.Unexpected("The QR codes of the tables are not available"))

    suspend fun generateEmaReport(
        tournamentId: String,
        rankings: List<PlayerRanking>,
    ): AppResult<ByteArray>

    suspend fun deleteTournament(tournamentId: String): AppResult<Unit>

    suspend fun listTournamentMembers(
        tournamentId: String,
        refreshMode: RefreshMode = RefreshMode.IF_CHANGED,
    ): AppResult<List<TournamentMember>>

    suspend fun upsertTournamentMember(
        tournamentId: String,
        uid: String,
    ): AppResult<Unit>

    suspend fun removeTournamentMember(
        tournamentId: String,
        uid: String,
    ): AppResult<Unit>

    suspend fun listTournamentPlayers(
        tournamentId: String,
        refreshMode: RefreshMode = RefreshMode.IF_CHANGED,
    ): AppResult<List<TournamentPlayer>>

    /**
     * Changes the number of players of a tournament before its first hand, with the new draw of the request.
     * See docs/player-count-change.md.
     */
    suspend fun changePlayerCount(request: PlayerCountChangeRequest): AppResult<PlayerCountOutcome> =
        AppResult.Failure(AppError.Unexpected("Changing the number of players is not available"))

    suspend fun listTournamentTeams(
        tournamentId: String,
        refreshMode: RefreshMode = RefreshMode.IF_CHANGED,
    ): AppResult<List<TournamentTeam>>

    /**
     * Renames a team and assigns EMA players to its fixed schedule slots.
     * The list order matches [TournamentTeam.playerIds]. Null leaves a slot empty.
     */
    suspend fun updateTournamentTeam(
        tournamentId: String,
        teamId: Int,
        name: String,
        emaIds: List<String?>,
    ): AppResult<Unit>

    suspend fun listCountries(refreshMode: RefreshMode = RefreshMode.IF_CHANGED): AppResult<List<Country>>

    /**
     * Turns a short Google Maps link into the full link. The answer also holds the place from Google Places.
     * The place is null when Places is off or finds nothing.
     */
    suspend fun resolveVenueLink(url: String): AppResult<ResolvedVenueLink>

    /** Assigns an EMA player to a slot. Pass null to clear the slot. */
    suspend fun assignTournamentPlayer(
        tournamentId: String,
        tournamentPlayerId: Int,
        emaId: String?,
    ): AppResult<Unit>

    /**
     * Sets the substitution of a player who played some rounds, or removes it when [substitution] is null.
     * A substitute plays for the player from a round on. The substitute is never recorded.
     */
    suspend fun setPlayerSubstitution(
        tournamentId: String,
        tournamentPlayerId: Int,
        substitution: PlayerSubstitution?,
    ): AppResult<Unit>

    suspend fun listTournamentRounds(
        tournamentId: String,
        refreshMode: RefreshMode = RefreshMode.IF_CHANGED,
    ): AppResult<List<TournamentRound>>

    suspend fun listTournamentTables(
        tournamentId: String,
        roundId: Int? = null,
        refreshMode: RefreshMode = RefreshMode.IF_CHANGED,
    ): AppResult<List<TournamentTable>>

    suspend fun getTableWithHands(
        tournamentId: String,
        roundId: Int,
        tableId: Int,
        refreshMode: RefreshMode = RefreshMode.IF_CHANGED,
    ): AppResult<Pair<TableState, List<TableHand>>>

    /** Loads every table of a round with its hands in one request, sorted by table id. */
    suspend fun getRoundTablesWithHands(
        tournamentId: String,
        roundId: Int,
        refreshMode: RefreshMode = RefreshMode.IF_CHANGED,
    ): AppResult<List<Pair<TableState, List<TableHand>>>>

    /**
     * Loads every table of the tournament with its hands, sorted by round and table. The rankings need all of them.
     * The first call makes one request. With [RefreshMode.CACHED] later calls make none. With [RefreshMode.IF_CHANGED]
     * they read the tournament manifest and load all tables again only if someone saved a table. [RefreshMode.FORCE]
     * always loads.
     * The table editor keeps the held tables current with its saves, loads and polling.
     */
    suspend fun getAllTablesWithHands(
        tournamentId: String,
        refreshMode: RefreshMode = RefreshMode.IF_CHANGED,
    ): AppResult<List<Pair<TableState, List<TableHand>>>>

    /** Saves all table and hand changes in one remote transaction. */
    suspend fun saveTableState(
        tournamentId: String,
        roundId: Int,
        tableId: Int,
        expectedVersion: Long,
        tablePatch: Map<String, Any?>,
        handPatches: Map<Int, Map<String, Any?>>,
    ): AppResult<Pair<TableState, List<TableHand>>>

    /**
     * Empties a table on the server: the seat assignments, the scores and every hand. Only admins can do it, and the
     * server records it in the change log. It returns the empty table as the server holds it now.
     */
    suspend fun resetTable(
        tournamentId: String,
        roundId: Int,
        tableId: Int,
    ): AppResult<Pair<TableState, List<TableHand>>> = AppResult.Failure(AppError.Unexpected("Reset is not supported"))

    /**
     * Asks the backend for the current table versions. It always makes a request, and it costs one
     * remote read. Other cached requests of the tournament reuse the answer for a short time.
     */
    suspend fun getTournamentManifest(tournamentId: String): AppResult<TournamentManifest>

    /**
     * Asks the backend once for all the revisions that the reads of this tournament check: its own, its table
     * versions and the global ones. The reads that follow within a short time reuse that answer, so a refresh of the
     * tournament costs one request when nothing changed. A failure is ignored: the reads then ask themselves.
     */
    suspend fun prefetchRevisions(tournamentId: String) {}

    /** The clocks of the rounds, read fresh from the server. The tournament list can be a minute old. */
    suspend fun getRoundClocks(tournamentId: String): AppResult<RoundClocks> = AppResult.Failure(AppError.Unexpected("The round clocks are not available"))

    /** Play, Pause, Resume, Reset or "Set time" of the Timer. A reset of a round with hands fails with [AppError.Conflict]. */
    suspend fun updateRoundClock(tournamentId: String, roundId: Int, action: RoundClockAction): AppResult<RoundClocks> = AppResult.Failure(AppError.Unexpected("The round clocks are not available"))

    /** The plan B of the Timer: an admin opens or closes the players' hand entry or seats, or opens closed tables. */
    suspend fun updateRoundControl(tournamentId: String, roundId: Int, change: RoundControlChange): AppResult<RoundClocks> = AppResult.Failure(AppError.Unexpected("The round controls are not available"))

    /**
     * Marks many tables of several rounds as completed at once: the open tables of the finished rounds, from the
     * round control. Tables that are completed already are skipped. Answers how many tables closed.
     */
    suspend fun completeTables(tournamentId: String, tables: List<TableKey>): AppResult<Int> = AppResult.Failure(AppError.Unexpected("Closing tables is not available"))

    /** The tournaments where the signed-in account plays, with its slot and team. Empty without an account. */
    suspend fun listMyTournamentSlots(): AppResult<List<MyTournamentSlot>> = AppResult.Success(emptyList())

    /** The slots of [listMyTournamentSlots] and the registrations of the account in tournaments before their draw. */
    suspend fun listMyTournaments(): AppResult<MyTournaments> = when (val slots = listMyTournamentSlots()) {
        is AppResult.Success -> AppResult.Success(MyTournaments(slots = slots.value))
        is AppResult.Failure -> slots
    }
}
