package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.runSkikoComposeUiTest
import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import androidx.compose.ui.test.SkikoComposeUiTest
import com.etologic.mahjongtournamentsuite.domain.repository.AdminRepository
import com.etologic.mahjongtournamentsuite.domain.repository.AuthRepository
import com.etologic.mahjongtournamentsuite.presentation.screen.FakeSignedInAuthRepository
import com.etologic.mahjongtournamentsuite.domain.repository.TournamentRepository
import com.etologic.mahjongtournamentsuite.domain.model.GlobalUserRole
import com.etologic.mahjongtournamentsuite.presentation.presenter.IdCardProofPresenter
import com.etologic.mahjongtournamentsuite.presentation.presenter.TournamentsPresenter
import com.etologic.mahjongtournamentsuite.presentation.screen.FakeAdminRepository
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

@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
class EditTournamentDialogMatchDurationUiTest {
    private fun SkikoComposeUiTest.openDialog(tournament: Tournament) {
        val server = FakeTournamentRepository().apply { tournaments = listOf(tournament) }
        val store = AppMemoryStore()
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
                            single { store }
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

    /** The switches of the General tab, from the top: finish the hand, best hands, chicken hands. */
    private companion object {
        const val FINISH_HAND_SWITCH = 0
    }

    private fun tournament(durationMinutes: Int) = Tournament(
        id = "t1", name = "Open", isTeams = false, numPlayers = 8, numRounds = 8,
        eventStartDate = "2026-10-22", eventEndDate = "2026-10-24", matchDurationMinutes = durationMinutes,
    )

    @Test
    fun finishingTheHandMovesTheDefaultDurationTo115() = runSkikoComposeUiTest(size = Size(380f, 900f)) {
        openDialog(tournament(120))
        onAllNodes(isToggleable())[FINISH_HAND_SWITCH].performScrollTo().performClick()
        waitForIdle()
        onNode(hasText("Match duration", substring = true) and hasSetTextAction()).assertTextContains("115", substring = true)
    }

    @Test
    fun finishingTheHandKeepsADurationThatTheUserChanged() = runSkikoComposeUiTest(size = Size(380f, 900f)) {
        openDialog(tournament(90))
        onAllNodes(isToggleable())[FINISH_HAND_SWITCH].performScrollTo().performClick()
        waitForIdle()
        onNode(hasText("Match duration", substring = true) and hasSetTextAction()).assertTextContains("90", substring = true)
    }
}
