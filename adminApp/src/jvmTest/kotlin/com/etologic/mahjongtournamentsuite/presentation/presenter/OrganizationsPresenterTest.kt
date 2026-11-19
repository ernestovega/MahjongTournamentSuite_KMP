package com.etologic.mahjongtournamentsuite.presentation.presenter

import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.GlobalUserRole
import com.etologic.mahjongtournamentsuite.domain.model.ResolvedVenueLink
import com.etologic.mahjongtournamentsuite.presentation.screen.FakeAdminRepository
import com.etologic.mahjongtournamentsuite.presentation.screen.FakeTournamentRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** The premises of a club use the same reading of a Google Maps link as the venue of a tournament. */
class OrganizationsPresenterTest {
    @Test
    fun readsThePlaceOfAClubWithTheVenueLinkReader() = runTest {
        val presenter = OrganizationsPresenter(FakeAdminRepository(GlobalUserRole.CLUB_ADMIN), tournamentRepository = FakeTournamentRepository())

        val result = assertIs<AppResult.Success<ResolvedVenueLink>>(presenter.resolvePlaceLink("https://maps.app.goo.gl/abc"))

        assertEquals("https://maps.app.goo.gl/abc", result.value.url)
    }

    @Test
    fun withoutAReaderTheLinkFailsAndTheFormReadsTheLinkText() = runTest {
        val presenter = OrganizationsPresenter(FakeAdminRepository(GlobalUserRole.CLUB_ADMIN))

        assertIs<AppResult.Failure>(presenter.resolvePlaceLink("https://maps.app.goo.gl/abc"))
    }
}
