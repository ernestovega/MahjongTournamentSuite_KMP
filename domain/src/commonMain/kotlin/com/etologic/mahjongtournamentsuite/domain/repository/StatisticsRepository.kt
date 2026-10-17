package com.etologic.mahjongtournamentsuite.domain.repository

import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.PersonTournamentDigest
import com.etologic.mahjongtournamentsuite.domain.model.StatisticsOverview
import com.etologic.mahjongtournamentsuite.domain.usecase.Standing

/**
 * The statistics that the server keeps. A request reads a fixed number of documents,
 * or the tables of one player, and never the hands of a tournament.
 */
interface StatisticsRepository {
    /** The statistics of all tournaments together. Only an administrator can load it. */
    suspend fun loadOverview(): AppResult<StatisticsOverview>

    /**
     * The results of one player in the tournaments that the signed-in account can see.
     * A null [emaId] means the player of the signed-in account.
     */
    suspend fun loadPerson(emaId: String?): AppResult<List<PersonTournamentDigest>>

    /** The final place of one player in each of the given tournaments. At most 25 tournaments for each call. */
    suspend fun loadStandings(emaId: String?, tournamentIds: List<String>): AppResult<Map<String, Standing>>
}
