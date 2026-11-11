package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runSkikoComposeUiTest
import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.domain.model.GlobalUserRole
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.TournamentRoundSchedule
import com.etologic.mahjongtournamentsuite.domain.model.TournamentVenue
import com.etologic.mahjongtournamentsuite.domain.repository.AdminRepository
import com.etologic.mahjongtournamentsuite.domain.repository.AuthRepository
import com.etologic.mahjongtournamentsuite.domain.repository.TournamentRepository
import com.etologic.mahjongtournamentsuite.presentation.presenter.IdCardProofPresenter
import com.etologic.mahjongtournamentsuite.presentation.presenter.TournamentsPresenter
import com.etologic.mahjongtournamentsuite.presentation.screen.FakeAdminRepository
import com.etologic.mahjongtournamentsuite.presentation.screen.FakeSignedInAuthRepository
import com.etologic.mahjongtournamentsuite.presentation.screen.FakeTournamentRepository
import com.etologic.mahjongtournamentsuite.presentation.store.AppMemoryStore
import com.etologic.mahjongtournamentsuite.presentation.theme.LocalThemeController
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import com.etologic.mahjongtournamentsuite.presentation.theme.ThemeController
import com.etologic.mahjongtournamentsuite.presentation.theme.ThemePreference
import org.koin.compose.KoinContext
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals

/** The "Public tournament" switch of the settings hides a test tournament from the public pages. */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
class EditTournamentDialogPublicUiTest {
    private val server = FakeTournamentRepository().apply { writesSucceed = true }

    private fun SkikoComposeUiTest.openGeneralTab(tournament: Tournament) {
        server.tournaments = listOf(tournament)
        setContent {
            val themeController = ThemeController(ThemePreference.Light, false, {})
            val koin = remember {
                koinApplication {
                    modules(
                        module {
                            single { Logger.withTag("ui-test") }
                            single<TournamentRepository> { server }
                            single<AdminRepository> { FakeAdminRepository(GlobalUserRole.ADMIN) }
                            single<AuthRepository> { FakeSignedInAuthRepository() }
                            single { AppMemoryStore() }
                            factory { TournamentsPresenter(get(), get(), get()) }
                            factory { IdCardProofPresenter(get()) }
                        },
                    )
                }.koin
            }
            KoinContext(koin) {
                CompositionLocalProvider(LocalThemeController provides themeController) {
                    MtsTheme(useDarkTheme = false) { EditTournamentDialog(tournament = tournament, onDismiss = {}) }
                }
            }
        }
        waitForIdle()
        onNodeWithText("General").performClick()
        waitForIdle()
    }

    private fun tournament(isPublic: Boolean) = Tournament(
        id = "t1", name = "Open", isTeams = false, numPlayers = 8, numRounds = 1,
        eventStartDate = "2026-10-22", eventEndDate = "2026-10-24", isPublic = isPublic,
        shortName = "OPEN", primaryColor = "#02B16B", hostCountry = "ESP", hostCity = "Madrid", timeZone = "Europe/Madrid",
        venue = TournamentVenue(name = "Club", address = "Calle 1", contactEmail = "club@example.test"),
        roundSchedules = listOf(TournamentRoundSchedule(roundId = 1, date = "2026-10-22", startTime = "10:00")),
    )

    /** The switches of the General tab, from the top: finish the hand, best hands, chicken hands, public. */
    private val publicSwitch = 3

    @Test
    fun aPublicTournamentShowsTheSwitchOnAndSavesItOff() = runSkikoComposeUiTest(size = Size(380f, 900f)) {
        openGeneralTab(tournament(isPublic = true))
        onNodeWithText("Public tournament").performScrollTo()
        val switch = onAllNodes(isToggleable())[publicSwitch]
        switch.assertIsOn()
        switch.performClick()
        waitForIdle()
        switch.assertIsOff()
        onNodeWithText("Save").performClick()
        waitForIdle()
        assertEquals(false, server.savedIsPublic)
    }

    @Test
    fun aHiddenTournamentShowsTheSwitchOff() = runSkikoComposeUiTest(size = Size(380f, 900f)) {
        openGeneralTab(tournament(isPublic = false))
        onNodeWithText("Public tournament").performScrollTo()
        onAllNodes(isToggleable())[publicSwitch].assertIsOff()
    }
}
