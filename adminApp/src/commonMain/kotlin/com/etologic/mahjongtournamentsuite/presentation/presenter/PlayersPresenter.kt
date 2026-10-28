package com.etologic.mahjongtournamentsuite.presentation.presenter

import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.Country
import com.etologic.mahjongtournamentsuite.domain.model.IdCardSheetLayout
import com.etologic.mahjongtournamentsuite.domain.model.Player
import com.etologic.mahjongtournamentsuite.domain.model.PlayerAccountActionResult
import com.etologic.mahjongtournamentsuite.domain.model.PlayerEmailItem
import com.etologic.mahjongtournamentsuite.domain.model.PlayerEmailResult
import com.etologic.mahjongtournamentsuite.domain.model.PlayerSubstitution
import com.etologic.mahjongtournamentsuite.domain.model.TournamentPlayer
import com.etologic.mahjongtournamentsuite.domain.model.TournamentTable
import com.etologic.mahjongtournamentsuite.domain.model.TournamentTeam
import com.etologic.mahjongtournamentsuite.domain.repository.TournamentRepository
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerAccountRepository
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerRepository
import com.etologic.mahjongtournamentsuite.domain.repository.RefreshMode

class PlayersPresenter(
    private val tournamentRepository: TournamentRepository,
    private val playerRepository: PlayerRepository,
    private val accountRepository: PlayerAccountRepository,
    private val logger: Logger,
) {
    suspend fun loadPlayers(tournamentId: String, force: Boolean = false): AppResult<List<TournamentPlayer>> {
        logger.i { "Loading tournament players." }
        return tournamentRepository.listTournamentPlayers(tournamentId, force.mode())
    }

    suspend fun loadBasePlayers(force: Boolean = false): AppResult<List<Player>> = playerRepository.listPlayers(force.mode())

    suspend fun loadCountries(force: Boolean = false): AppResult<List<Country>> = tournamentRepository.listCountries(force.mode())

    suspend fun loadTeams(tournamentId: String, force: Boolean = false): AppResult<List<TournamentTeam>> =
        tournamentRepository.listTournamentTeams(tournamentId, force.mode())

    suspend fun loadTables(tournamentId: String, force: Boolean = false): AppResult<List<TournamentTable>> =
        tournamentRepository.listTournamentTables(tournamentId, refreshMode = force.mode())

    suspend fun generateIdCards(tournamentId: String, sheet: IdCardSheetLayout? = null): AppResult<ByteArray> =
        tournamentRepository.generateTournamentIdCards(tournamentId, sheet)

    suspend fun generateIdList(tournamentId: String): AppResult<ByteArray> =
        tournamentRepository.generateTournamentIdList(tournamentId)

    /** Invites the players with no account and resets the password of the others. */
    /** Sets or clears the pending email of slots. */
    suspend fun saveEmails(tournamentId: String, items: List<PlayerEmailItem>): AppResult<List<PlayerEmailResult>> =
        accountRepository.saveEmails(tournamentId, items)

    suspend fun sendToPlayers(tournamentId: String, playerIds: List<Int>): AppResult<List<PlayerAccountActionResult>> =
        accountRepository.send(tournamentId, playerIds)

    suspend fun assignPlayer(
        tournamentId: String,
        tournamentPlayerId: Int,
        emaId: String?,
    ): AppResult<Unit> = tournamentRepository.assignTournamentPlayer(
        tournamentId,
        tournamentPlayerId,
        emaId,
    )

    suspend fun setSubstitution(
        tournamentId: String,
        tournamentPlayerId: Int,
        substitution: PlayerSubstitution?,
    ): AppResult<Unit> = tournamentRepository.setPlayerSubstitution(tournamentId, tournamentPlayerId, substitution)
}

private fun Boolean.mode() = if (this) RefreshMode.FORCE else RefreshMode.IF_CHANGED
