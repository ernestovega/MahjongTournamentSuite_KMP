package com.etologic.mahjongtournamentsuite.presentation.screenshots

import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.GlobalUserRole
import com.etologic.mahjongtournamentsuite.domain.model.PlayerAccountActionResult
import com.etologic.mahjongtournamentsuite.domain.model.PlayerEmailItem
import com.etologic.mahjongtournamentsuite.domain.model.PlayerEmailResult
import com.etologic.mahjongtournamentsuite.domain.model.PlayerExportFile
import com.etologic.mahjongtournamentsuite.domain.model.PlayerImportPreview
import com.etologic.mahjongtournamentsuite.domain.model.PlayerImportResult
import com.etologic.mahjongtournamentsuite.domain.repository.AdminRepository
import com.etologic.mahjongtournamentsuite.domain.repository.AuthRepository
import com.etologic.mahjongtournamentsuite.domain.repository.FeatureFlagsRepository
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerAccountRepository
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerImportRepository
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerRepository
import com.etologic.mahjongtournamentsuite.domain.repository.StatisticsRepository
import com.etologic.mahjongtournamentsuite.domain.repository.SupportRepository
import com.etologic.mahjongtournamentsuite.domain.repository.TournamentRepository
import com.etologic.mahjongtournamentsuite.presentation.di.presentationModule
import com.etologic.mahjongtournamentsuite.screenshots.fake.ScreenshotAdminRepository
import com.etologic.mahjongtournamentsuite.screenshots.fake.ScreenshotAuthRepository
import com.etologic.mahjongtournamentsuite.screenshots.fake.ScreenshotFeatureFlagsRepository
import com.etologic.mahjongtournamentsuite.screenshots.fake.ScreenshotPlayerRepository
import com.etologic.mahjongtournamentsuite.screenshots.fake.ScreenshotStatisticsRepository
import com.etologic.mahjongtournamentsuite.screenshots.fake.ScreenshotSupportRepository
import com.etologic.mahjongtournamentsuite.screenshots.fake.ScreenshotTournamentRepository
import org.koin.core.module.Module
import org.koin.dsl.module

private class NoPlayerAccounts : PlayerAccountRepository {
    override suspend fun send(tournamentId: String, playerIds: List<Int>): AppResult<List<PlayerAccountActionResult>> = AppResult.Failure(AppError.Unexpected("Screenshots do not write"))

    override suspend fun saveEmails(tournamentId: String, items: List<PlayerEmailItem>): AppResult<List<PlayerEmailResult>> = AppResult.Failure(AppError.Unexpected("Screenshots do not write"))
}

private class NoPlayerImport : PlayerImportRepository {
    override suspend fun export(tournamentId: String): AppResult<PlayerExportFile> = AppResult.Failure(AppError.Unexpected("unused"))
    override suspend fun preview(tournamentId: String, fileName: String, bytes: ByteArray): AppResult<PlayerImportPreview> = AppResult.Failure(AppError.Unexpected("unused"))

    override suspend fun apply(tournamentId: String, fileName: String, bytes: ByteArray): AppResult<PlayerImportResult> = AppResult.Failure(AppError.Unexpected("unused"))
}

/** The real presenters of the admin app over the fake data of the screenshots. */
internal fun adminModules(
    role: GlobalUserRole = GlobalUserRole.ADMIN,
    tournaments: TournamentRepository = ScreenshotTournamentRepository(),
    admin: AdminRepository = ScreenshotAdminRepository(role),
    statistics: StatisticsRepository = ScreenshotStatisticsRepository(),
    auth: AuthRepository = ScreenshotAuthRepository(),
): List<Module> = listOf(
    presentationModule,
    module {
        single<TournamentRepository> { tournaments }
        single<PlayerRepository> { ScreenshotPlayerRepository() }
        single<AuthRepository> { auth }
        single<AdminRepository> { admin }
        single<StatisticsRepository> { statistics }
        single<FeatureFlagsRepository> { ScreenshotFeatureFlagsRepository() }
        single<SupportRepository> { ScreenshotSupportRepository() }
        single<PlayerAccountRepository> { NoPlayerAccounts() }
        single<PlayerImportRepository> { NoPlayerImport() }
    },
)
