package com.etologic.mahjongtournamentsuite.presentation.presenter

import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.TableHand
import com.etologic.mahjongtournamentsuite.domain.model.TableState
import com.etologic.mahjongtournamentsuite.domain.model.TournamentManifest
import com.etologic.mahjongtournamentsuite.domain.repository.TournamentRepository
import com.etologic.mahjongtournamentsuite.domain.repository.RefreshMode

class TableManagerPresenter(
    private val tournamentRepository: TournamentRepository,
    private val logger: Logger,
) {
    suspend fun loadTableWithHands(
        tournamentId: String,
        roundId: Int,
        tableId: Int,
        forceRefresh: Boolean = false,
    ): AppResult<Pair<TableState, List<TableHand>>> {
        logger.i { "Loading table with hands." }
        return tournamentRepository.getTableWithHands(
            tournamentId = tournamentId,
            roundId = roundId,
            tableId = tableId,
            refreshMode = if (forceRefresh) RefreshMode.FORCE else RefreshMode.IF_CHANGED,
        )
    }

    suspend fun loadRoundTablesWithHands(
        tournamentId: String,
        roundId: Int,
        forceRefresh: Boolean = false,
    ): AppResult<List<Pair<TableState, List<TableHand>>>> {
        logger.i { "Loading round tables with hands." }
        return tournamentRepository.getRoundTablesWithHands(
            tournamentId = tournamentId,
            roundId = roundId,
            refreshMode = if (forceRefresh) RefreshMode.FORCE else RefreshMode.IF_CHANGED,
        )
    }

    /** One cheap request that tells which tables changed on the server. It does not use the cache. */
    suspend fun loadManifest(tournamentId: String): AppResult<TournamentManifest> {
        logger.d { "Checking the tournament for changes." }
        return tournamentRepository.getTournamentManifest(tournamentId)
    }

    suspend fun saveTableState(
        tournamentId: String,
        roundId: Int,
        tableId: Int,
        expectedVersion: Long,
        tablePatch: Map<String, Any?>,
        handPatches: Map<Int, Map<String, Any?>>,
    ): AppResult<Pair<TableState, List<TableHand>>> {
        logger.i { "Saving table state." }
        return tournamentRepository.saveTableState(
            tournamentId = tournamentId,
            roundId = roundId,
            tableId = tableId,
            expectedVersion = expectedVersion,
            tablePatch = tablePatch,
            handPatches = handPatches,
        )
    }

    suspend fun resetTable(
        tournamentId: String,
        roundId: Int,
        tableId: Int,
    ): AppResult<Unit> {
        logger.i { "Resetting table." }
        return tournamentRepository.resetTable(
            tournamentId = tournamentId,
            roundId = roundId,
            tableId = tableId,
        )
    }
}
