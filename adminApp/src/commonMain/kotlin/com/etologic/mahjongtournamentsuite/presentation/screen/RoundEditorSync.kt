package com.etologic.mahjongtournamentsuite.presentation.screen

import com.etologic.mahjongtournamentsuite.presentation.util.UiText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.TableHand
import com.etologic.mahjongtournamentsuite.domain.model.TableKey
import com.etologic.mahjongtournamentsuite.domain.model.TableState
import com.etologic.mahjongtournamentsuite.domain.model.TournamentManifest
import com.etologic.mahjongtournamentsuite.domain.model.TournamentTable
import com.etologic.mahjongtournamentsuite.domain.model.displayName
import com.etologic.mahjongtournamentsuite.presentation.presenter.TableManagerPresenter
import com.etologic.mahjongtournamentsuite.presentation.presenter.TablesPresenter
import com.etologic.mahjongtournamentsuite.presentation.sync.TableChangeDetector
import com.etologic.mahjongtournamentsuite.presentation.util.toUiText
import kotlin.time.Clock
import kotlin.time.Instant
import mahjongtournamentsuite.adminapp.generated.resources.Res
import org.jetbrains.compose.resources.stringResource

/**
 * The tables of one tournament that a user edits, and the rules that keep them in step with the server.
 *
 * - The server always wins. Newer server data replaces an editor, and the user gets a notice.
 * - A save sends the version the editor holds. The server refuses a save from an old version.
 * - Nothing replaces an editor while a save or a load runs ([isLoading]). The next check tries again.
 */
internal class RoundEditorSync(
    private val tournamentId: String,
    private val tablePresenter: TableManagerPresenter,
    private val tablesPresenter: TablesPresenter,
    private val selectedRoundId: () -> Int?,
    private val selectedTableId: () -> Int?,
    /** Reloads the tournament list when it changed on the server. Returns true if it worked. */
    private val reloadTournaments: suspend () -> Boolean = { true },
    private val now: () -> Instant = { Clock.System.now() },
) {
    var isLoading by mutableStateOf(true)
    /** True while the tables of the round load in the background. */
    var isLoadingRest by mutableStateOf(false)
    var errorMessage by mutableStateOf<UiText?>(null)
    var allTables by mutableStateOf<List<TournamentTable>>(emptyList())
    var tableEditors by mutableStateOf<Map<Int, TableManagerEditorState>>(emptyMap())
    var playerNamesById by mutableStateOf<Map<Int, String>>(emptyMap())

    /** When the server last answered a check. The screen shows it next to the data revision. */
    var lastCheckedAt by mutableStateOf<Instant?>(null)
    private var knownPlayersRevision: Long? = null
    private var knownEmaPlayersRevision: Long? = null
    private var knownTournamentsRevision: Long? = null

    /** Server data replaced these tables. The user must accept the notice. */
    var updateNotices by mutableStateOf<List<TableUpdateNotice>>(emptyList())

    /** Changes when server data replaced the editor that has the focus, so the screen can return the focus. */
    var focusToken by mutableStateOf(0)

    val hasUnsavedChanges: Boolean get() = tableEditors.values.any { it.hasUnsavedChanges }

    /** Loads the names of the tournament players. Returns an error message, or null. */
    suspend fun loadPlayerNames(force: Boolean): UiText? {
        val basePlayers = when (val result = tablesPresenter.loadBasePlayers(force)) {
            is AppResult.Success -> result.value.associateBy { it.emaId }
            is AppResult.Failure -> emptyMap()
        }
        return when (val result = tablesPresenter.loadPlayers(tournamentId, force)) {
            is AppResult.Success -> {
                // A slot without a person has no entry. The screens show the number of the player then.
                playerNamesById = result.value.mapNotNull { slot ->
                    val name = slot.assignedEmaId?.let(basePlayers::get)?.displayName ?: slot.nonMember?.displayName
                    name?.let { slot.id to it }
                }.toMap()
                null
            }

            is AppResult.Failure -> result.error.toUiText()
        }
    }

    /** Puts server data into the editor and the table row. Returns the notice, or null if the data is not newer. */
    fun replaceWithServer(
        table: TableState,
        hands: List<TableHand>,
        saveRejected: Boolean = false,
    ): TableUpdateNotice? {
        val update = TableReconciler.reconcile(tableEditors[table.tableId], table, hands, saveRejected) ?: return null
        tableEditors = tableEditors + (table.tableId to update.editor)
        allTables = allTables.map { summary ->
            if (summary.roundId == table.roundId && summary.tableId == table.tableId) update.editor.applyTo(summary) else summary
        }
        if (table.tableId == selectedTableId()) focusToken++
        return update.notice
    }

    /** The server rejected a save. The server data wins. The user sees what was lost. */
    private suspend fun handleRejectedSave(editor: TableManagerEditorState, conflict: AppError.Conflict): TableUpdateNotice? {
        val serverTable = conflict.currentTable
        if (serverTable != null) return replaceWithServer(serverTable, conflict.currentHands, saveRejected = true)
        // The answer had no table data. Load the table again.
        return when (val result = tablePresenter.loadTableWithHands(tournamentId, editor.roundId, editor.tableId, true)) {
            is AppResult.Success -> replaceWithServer(result.value.first, result.value.second, saveRejected = true)
            is AppResult.Failure -> {
                errorMessage = result.error.toUiText()
                null
            }
        }
    }

    /**
     * Saves the tables one by one. Tables are independent, so a rejected table does not stop the others.
     * Any other error stops the run. Returns true only if every table was saved.
     * The caller then runs the action that waited for the save.
     */
    suspend fun saveTables(tableIds: Collection<Int>): Boolean {
        val current = tableEditors.filterKeys { it in tableIds }.toList()
        val notices = mutableListOf<TableUpdateNotice>()
        var allSaved = true
        try {
            for ((tableId, editor) in current) {
                val tablePatch = editor.buildTablePatch()
                val handPatches = editor.buildHandPatches().toMap()
                if (tablePatch.isEmpty() && handPatches.isEmpty()) continue
                isLoading = true
                when (val result = tablePresenter.saveTableState(
                    tournamentId = tournamentId,
                    roundId = editor.roundId,
                    tableId = tableId,
                    expectedVersion = editor.version,
                    tablePatch = tablePatch,
                    handPatches = handPatches,
                )) {
                    is AppResult.Success -> {
                        val saved = TableManagerEditorState.from(result.value.first, result.value.second)
                        tableEditors = tableEditors + (tableId to saved)
                        allTables = allTables.map { summary ->
                            if (summary.roundId == saved.roundId && summary.tableId == tableId) saved.applyTo(summary) else summary
                        }
                    }

                    is AppResult.Failure -> {
                        allSaved = false
                        val conflict = result.error as? AppError.Conflict
                        if (conflict == null) {
                            errorMessage = result.error.toUiText()
                            break
                        }
                        handleRejectedSave(editor, conflict)?.let(notices::add)
                    }
                }
            }
        } finally {
            isLoading = false
            if (notices.isNotEmpty()) updateNotices = updateNotices + notices
        }
        return allSaved
    }

    /** Loads the tables that someone else saved and replaces them. Other rounds only refresh their rows. */
    suspend fun applyServerChanges(changed: Set<TableKey>) {
        val roundId = selectedRoundId()
        val (loaded, summaryOnly) = changed.partition { it.roundId == roundId && it.tableId in tableEditors }
        val notices = mutableListOf<TableUpdateNotice>()
        for (key in loaded) {
            val result = tablePresenter.loadTableWithHands(tournamentId, key.roundId, key.tableId, true)
            // The user may have started a save or changed the round while the request ran. The next check tries again.
            if (result !is AppResult.Success || isLoading || selectedRoundId() != roundId) continue
            replaceWithServer(result.value.first, result.value.second)?.let(notices::add)
        }
        if (summaryOnly.isNotEmpty()) {
            // The ranking holds the hands of every table and never asks the server again. Loading the changed
            // tables here keeps that copy current. If one load fails, the rows stay old and the next check retries.
            val reads = summaryOnly.map { key -> tablePresenter.loadTableWithHands(tournamentId, key.roundId, key.tableId, true) }
            if (reads.any { it !is AppResult.Success }) {
                if (notices.isNotEmpty()) updateNotices = updateNotices + notices
                return
            }
            val result = tablesPresenter.loadTables(tournamentId, null, true)
            if (result is AppResult.Success && !isLoading) {
                val openEditors = tableEditors.values.associateBy { TableKey(it.roundId, it.tableId) }
                allTables = result.value
                    .sortedWith(compareBy(TournamentTable::roundId, TournamentTable::tableId))
                    .map { row -> openEditors[TableKey(row.roundId, row.tableId)]?.applyTo(row) ?: row }
            }
        }
        if (notices.isNotEmpty()) updateNotices = updateNotices + notices
    }

    /** One poll tick. It asks the server for the table versions and loads only the tables that changed. */
    suspend fun checkForChanges() {
        // Do not touch the editors while something else changes them.
        if (isLoading || isLoadingRest) return
        val manifest = (tablePresenter.loadManifest(tournamentId) as? AppResult.Success)?.value ?: return
        lastCheckedAt = now()
        refreshWhatIsOld(manifest)
        val known = TableReconciler.knownVersions(allTables, tableEditors.values)
        val changed = TableChangeDetector.changedTables(known, manifest)
        if (changed.isNotEmpty()) applyServerChanges(changed)
    }

    /**
     * Reloads what the screen shows from other lists when polling finds that it is old:
     * the player names (assignments or EMA player data changed) and the tournament settings.
     * A revision counts as seen only after the reload worked, so a failed reload is tried again at the next check.
     */
    private suspend fun refreshWhatIsOld(manifest: TournamentManifest) {
        val knownPlayers = knownPlayersRevision
        val knownEma = knownEmaPlayersRevision
        val namesOld = (knownPlayers != null && manifest.playersRevision != knownPlayers) ||
            (knownEma != null && manifest.emaPlayersRevision != knownEma)
        if (!namesOld || loadPlayerNames(force = true) == null) {
            knownPlayersRevision = manifest.playersRevision
            knownEmaPlayersRevision = manifest.emaPlayersRevision
        }
        val knownTournaments = knownTournamentsRevision
        if (knownTournaments == null || manifest.tournamentsRevision == knownTournaments || reloadTournaments()) {
            knownTournamentsRevision = manifest.tournamentsRevision
        }
    }

    fun dismissNotices() {
        updateNotices = emptyList()
        focusToken++
    }
}
