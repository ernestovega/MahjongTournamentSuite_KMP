package com.etologic.mahjongtournamentsuite.presentation.presenter

import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.ErrorGroupDetail
import com.etologic.mahjongtournamentsuite.domain.model.ErrorGroupsPage
import com.etologic.mahjongtournamentsuite.domain.model.ErrorLogCursor
import com.etologic.mahjongtournamentsuite.domain.repository.AdminRepository

/** Reads the error log of the apps, one page at a time. Only administrators can call it. */
class ErrorsPresenter(
    private val adminRepository: AdminRepository,
) {
    /** The problems seen at [sinceMillis] or later (all when null). Pass the cursor of the last page to read the next one. */
    suspend fun loadGroups(sinceMillis: Long?, cursor: ErrorLogCursor?): AppResult<ErrorGroupsPage> = adminRepository.loadErrorGroups(sinceMillis, cursor)

    suspend fun loadGroup(id: String, reportCursor: ErrorLogCursor? = null): AppResult<ErrorGroupDetail> = adminRepository.loadErrorGroup(id, reportCursor)
}
