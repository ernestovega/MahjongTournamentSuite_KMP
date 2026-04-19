package com.etologic.mahjongtournamentsuite.presentation.presenter

import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.TableHand
import com.etologic.mahjongtournamentsuite.domain.model.TableState
import com.etologic.mahjongtournamentsuite.domain.repository.TournamentRepository

class TableManagerPresenter(
    private val tournamentRepository: TournamentRepository,
    private val logger: Logger,
) {
    suspend fun loadTableWithHands(
        tournamentId: String,
        roundId: Int,
        tableId: Int,
    ): AppResult<Pair<TableState, List<TableHand>>> {
        logger.i { "Loading table with hands." }
        return tournamentRepository.getTableWithHands(
            tournamentId = tournamentId,
            roundId = roundId,
            tableId = tableId,
        )
    }

    suspend fun patchTable(
        tournamentId: String,
        roundId: Int,
        tableId: Int,
        patch: Map<String, Any?>,
    ): AppResult<Unit> {
        logger.i { "Patching table." }
        return tournamentRepository.patchTable(
            tournamentId = tournamentId,
            roundId = roundId,
            tableId = tableId,
            patch = patch,
        )
    }

    suspend fun patchHand(
        tournamentId: String,
        roundId: Int,
        tableId: Int,
        handId: Int,
        patch: Map<String, Any?>,
    ): AppResult<Unit> {
        logger.i { "Patching hand." }
        return tournamentRepository.patchHand(
            tournamentId = tournamentId,
            roundId = roundId,
            tableId = tableId,
            handId = handId,
            patch = patch,
        )
    }
}

