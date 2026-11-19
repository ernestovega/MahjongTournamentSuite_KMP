package com.etologic.mahjongtournamentsuite.presentation.presenter

import com.etologic.mahjongtournamentsuite.domain.model.AdminStatus
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.Organization
import com.etologic.mahjongtournamentsuite.domain.model.OrganizationDraft
import com.etologic.mahjongtournamentsuite.domain.repository.AdminRepository
import com.etologic.mahjongtournamentsuite.domain.repository.RefreshMode
import com.etologic.mahjongtournamentsuite.domain.telemetry.NoTelemetry
import com.etologic.mahjongtournamentsuite.domain.telemetry.Telemetry
import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryAction
import com.etologic.mahjongtournamentsuite.domain.telemetry.trackAction

/** The clubs and federations that organize the tournaments. Only super admins create and delete them. */
class OrganizationsPresenter(
    private val adminRepository: AdminRepository,
    private val telemetry: Telemetry = NoTelemetry,
) {
    suspend fun loadAdminStatus(): AppResult<AdminStatus> = adminRepository.whoAmI(RefreshMode.IF_CHANGED)

    suspend fun loadOrganizations(): AppResult<List<Organization>> = adminRepository.listOrganizations()

    suspend fun createOrganization(draft: OrganizationDraft): AppResult<Organization> = adminRepository.createOrganization(draft)
        .trackAction(telemetry, TelemetryAction.ORGANIZATION_CREATED)

    suspend fun updateOrganization(id: String, draft: OrganizationDraft): AppResult<Organization> = adminRepository.updateOrganization(id, draft)
        .trackAction(telemetry, TelemetryAction.ORGANIZATION_UPDATED)

    suspend fun deleteOrganization(id: String): AppResult<Unit> = adminRepository.deleteOrganization(id)
        .trackAction(telemetry, TelemetryAction.ORGANIZATION_DELETED)
}
