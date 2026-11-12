package com.etologic.mahjongtournamentsuite.presentation.presenter

import com.etologic.mahjongtournamentsuite.domain.model.ActivityFilter
import com.etologic.mahjongtournamentsuite.domain.model.ActivityPage
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.repository.AdminRepository

/** Reads who wrote what and when, one page at a time. Only administrators can call it. */
class ActivityPresenter(
    private val adminRepository: AdminRepository,
    /** The tournaments of the filter. A function, so a test does not need a whole tournament repository. */
    private val tournaments: suspend () -> AppResult<List<Tournament>>,
) {
    /** Pass the cursor of the last page to read the next one. */
    suspend fun loadPage(filter: ActivityFilter, cursor: String?): AppResult<ActivityPage> = adminRepository.loadActivity(filter, cursor)

    suspend fun loadTournaments(): AppResult<List<Tournament>> = tournaments()
}
