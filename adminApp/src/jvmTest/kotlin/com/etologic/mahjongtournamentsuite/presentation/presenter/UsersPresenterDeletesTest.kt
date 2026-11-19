package com.etologic.mahjongtournamentsuite.presentation.presenter

import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.GlobalUserRole
import com.etologic.mahjongtournamentsuite.domain.model.UserDeleteFailure
import com.etologic.mahjongtournamentsuite.domain.model.UserDeleteOutcome
import com.etologic.mahjongtournamentsuite.domain.model.UserDeleteResult
import com.etologic.mahjongtournamentsuite.presentation.screen.FakeAdminRepository
import com.etologic.mahjongtournamentsuite.presentation.screen.FakePlayerRepository
import com.etologic.mahjongtournamentsuite.presentation.screen.FakeSignedInAuthRepository
import com.etologic.mahjongtournamentsuite.presentation.screen.FakeTournamentRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class UsersPresenterDeletesTest {
    private fun presenter(admin: FakeAdminRepository) = UsersPresenter(FakeTournamentRepository(), admin, FakeSignedInAuthRepository(), FakePlayerRepository())

    private fun uids(count: Int) = (1..count).map { "u$it" }

    @Test
    fun aLongListGoesInRequestsOfAtMost50AndKeepsTheOrder() = runTest {
        val admin = FakeAdminRepository(GlobalUserRole.SUPER_ADMIN)

        val results = assertIs<AppResult.Success<List<UserDeleteResult>>>(presenter(admin).deleteUsers(uids(120))).value

        assertEquals(listOf(50, 50, 20), admin.deleteRequests.map { it.size })
        assertEquals(uids(120), results.map { it.uid })
    }

    @Test
    fun aRequestThatFailsAfterOthersWentThroughMarksItsAccountsAsFailed() = runTest {
        val admin = FakeAdminRepository(GlobalUserRole.SUPER_ADMIN).apply { failDeletesFromRequest = 2 }

        val results = assertIs<AppResult.Success<List<UserDeleteResult>>>(presenter(admin).deleteUsers(uids(70))).value

        assertEquals(50, results.count { it.outcome == UserDeleteOutcome.DELETED })
        assertEquals(20, results.count { it.outcome == UserDeleteOutcome.FAILED && it.reason == UserDeleteFailure.UNKNOWN })
    }

    @Test
    fun aFirstRequestThatFailsIsAFailureOfTheWholeCall() = runTest {
        val admin = FakeAdminRepository(GlobalUserRole.SUPER_ADMIN).apply { failDeletesFromRequest = 1 }

        assertEquals(AppResult.Failure(AppError.Network), presenter(admin).deleteUsers(uids(3)))
    }
}
