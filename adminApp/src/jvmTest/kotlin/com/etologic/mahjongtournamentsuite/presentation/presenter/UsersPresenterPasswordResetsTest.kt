package com.etologic.mahjongtournamentsuite.presentation.presenter

import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.GlobalUserRole
import com.etologic.mahjongtournamentsuite.domain.model.PasswordResetFailure
import com.etologic.mahjongtournamentsuite.domain.model.PasswordResetOutcome
import com.etologic.mahjongtournamentsuite.presentation.screen.FakeAdminRepository
import com.etologic.mahjongtournamentsuite.presentation.screen.FakePlayerRepository
import com.etologic.mahjongtournamentsuite.presentation.screen.FakeSignedInAuthRepository
import com.etologic.mahjongtournamentsuite.presentation.screen.FakeTournamentRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest

class UsersPresenterPasswordResetsTest {
    private fun presenter(admin: FakeAdminRepository) =
        UsersPresenter(FakeTournamentRepository(), admin, FakeSignedInAuthRepository(), FakePlayerRepository())

    private fun uids(count: Int) = (1..count).map { "u$it" }

    @Test
    fun aShortListGoesInOneRequest() = runTest {
        val admin = FakeAdminRepository(GlobalUserRole.ADMIN)

        val results = assertIs<AppResult.Success<List<com.etologic.mahjongtournamentsuite.domain.model.PasswordResetResult>>>(
            presenter(admin).sendPasswordResets(uids(3)),
        ).value

        assertEquals(listOf(uids(3)), admin.resetRequests)
        assertEquals(uids(3), results.map { it.uid })
    }

    @Test
    fun aLongListGoesInRequestsOfAtMost200AndKeepsTheOrder() = runTest {
        val admin = FakeAdminRepository(GlobalUserRole.ADMIN)

        val results = assertIs<AppResult.Success<List<com.etologic.mahjongtournamentsuite.domain.model.PasswordResetResult>>>(
            presenter(admin).sendPasswordResets(uids(450)),
        ).value

        assertEquals(listOf(200, 200, 50), admin.resetRequests.map { it.size })
        assertEquals(uids(450), results.map { it.uid })
    }

    @Test
    fun aRequestThatFailsAfterOthersWentOutMarksItsAccountsAsFailed() = runTest {
        val admin = FakeAdminRepository(GlobalUserRole.ADMIN).apply { failResetsFromRequest = 2 }

        val results = assertIs<AppResult.Success<List<com.etologic.mahjongtournamentsuite.domain.model.PasswordResetResult>>>(
            presenter(admin).sendPasswordResets(uids(250)),
        ).value

        assertEquals(250, results.size)
        assertEquals(200, results.count { it.outcome == PasswordResetOutcome.SENT })
        assertEquals(50, results.count { it.outcome == PasswordResetOutcome.FAILED && it.reason == PasswordResetFailure.UNKNOWN })
    }

    @Test
    fun aFirstRequestThatFailsIsAFailureOfTheWholeCall() = runTest {
        val admin = FakeAdminRepository(GlobalUserRole.ADMIN).apply { failResetsFromRequest = 1 }

        val result = presenter(admin).sendPasswordResets(uids(3))

        assertEquals(AppResult.Failure(AppError.Network), result)
    }

    @Test
    fun anEmptyListSendsNothing() = runTest {
        val admin = FakeAdminRepository(GlobalUserRole.ADMIN)

        assertEquals(AppResult.Success(emptyList()), presenter(admin).sendPasswordResets(emptyList()))
        assertEquals(emptyList(), admin.resetRequests)
    }
}
