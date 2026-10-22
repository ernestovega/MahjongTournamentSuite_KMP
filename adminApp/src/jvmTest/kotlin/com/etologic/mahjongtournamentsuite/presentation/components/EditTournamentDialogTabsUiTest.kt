package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.runSkikoComposeUiTest
import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.TournamentAgendaItem
import com.etologic.mahjongtournamentsuite.domain.model.TournamentRoundSchedule
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
class EditTournamentDialogTabsUiTest {
    @Test
    fun everyTabOpens() = runSkikoComposeUiTest(size = Size(380f, 700f)) {
        val tournament = Tournament(id = "t1", name = "Open", isTeams = false, numPlayers = 8, numRounds = 8,
            eventStartDate = "2026-10-22", eventEndDate = "2026-10-24",
            roundSchedules = listOf(
                TournamentRoundSchedule(1, "2026-10-22", "10:00"),
                TournamentRoundSchedule(2, "2026-10-22", "14:00"),
                TournamentRoundSchedule(3),
                TournamentRoundSchedule(4, "2026-10-23", "10:00"),
                TournamentRoundSchedule(5, "2026-10-23", "14:00"),
                TournamentRoundSchedule(6),
                TournamentRoundSchedule(7),
                TournamentRoundSchedule(8),
            ),
            agendaItems = listOf(TournamentAgendaItem("Lunch", "2026-10-22", "13:00"), TournamentAgendaItem("Awards"), TournamentAgendaItem("A", "2026-10-23", "09:00"), TournamentAgendaItem("B"), TournamentAgendaItem("C")),
        )
        val server = FakeTournamentRepository().apply { tournaments = listOf(tournament) }
        val store = AppMemoryStore()
        setContent {
            val themeController = ThemeController(ThemePreference.Light, false) {}
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
        listOf("Branding", "Match", "Schedule", "Data", "General").forEach {
            onNodeWithText(it).performClick()
            waitForIdle()
        }
    }
}
