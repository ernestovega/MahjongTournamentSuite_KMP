package com.etologic.mahjongtournamentsuite.presentation.presenter

import com.etologic.mahjongtournamentsuite.domain.model.AdminStatus
import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.Organization
import com.etologic.mahjongtournamentsuite.domain.model.OrganizationDraft
import com.etologic.mahjongtournamentsuite.domain.model.ResolvedVenueLink
import com.etologic.mahjongtournamentsuite.domain.repository.AdminRepository
import com.etologic.mahjongtournamentsuite.domain.repository.RefreshMode
import com.etologic.mahjongtournamentsuite.domain.repository.TournamentRepository
import com.etologic.mahjongtournamentsuite.domain.telemetry.NoTelemetry
import com.etologic.mahjongtournamentsuite.domain.telemetry.Telemetry
import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryAction
import com.etologic.mahjongtournamentsuite.domain.telemetry.trackAction

/** The clubs and federations that organize the tournaments. Only super admins create and delete them. */
class OrganizationsPresenter(
    private val adminRepository: AdminRepository,
    private val telemetry: Telemetry = NoTelemetry,
    /** Reads the place of a club from a Google Maps link, like the venue of a tournament. */
    private val tournamentRepository: TournamentRepository? = null,
) {
    suspend fun resolvePlaceLink(url: String): AppResult<ResolvedVenueLink> = tournamentRepository?.resolveVenueLink(url) ?: AppResult.Failure(AppError.Unexpected("No place reader"))

    suspend fun loadAdminStatus(): AppResult<AdminStatus> = adminRepository.whoAmI(RefreshMode.IF_CHANGED)

    suspend fun loadOrganizations(): AppResult<List<Organization>> = adminRepository.listOrganizations()

    suspend fun createOrganization(draft: OrganizationDraft): AppResult<Organization> = adminRepository.createOrganization(draft)
        .trackAction(telemetry, TelemetryAction.ORGANIZATION_CREATED)

    suspend fun updateOrganization(id: String, draft: OrganizationDraft): AppResult<Organization> = adminRepository.updateOrganization(id, draft)
        .trackAction(telemetry, TelemetryAction.ORGANIZATION_UPDATED)

    suspend fun deleteOrganization(id: String): AppResult<Unit> = adminRepository.deleteOrganization(id)
        .trackAction(telemetry, TelemetryAction.ORGANIZATION_DELETED)
}
