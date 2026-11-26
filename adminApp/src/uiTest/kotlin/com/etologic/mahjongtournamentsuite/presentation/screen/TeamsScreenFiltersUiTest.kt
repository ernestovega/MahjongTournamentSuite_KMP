package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.navigation.compose.rememberNavController
import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.domain.model.GlobalUserRole
import com.etologic.mahjongtournamentsuite.domain.model.Player
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.TournamentPlayer
import com.etologic.mahjongtournamentsuite.domain.model.TournamentTeam
import com.etologic.mahjongtournamentsuite.domain.repository.AdminRepository
import com.etologic.mahjongtournamentsuite.domain.repository.AuthRepository
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerAccountRepository
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerImportRepository
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerRepository
import com.etologic.mahjongtournamentsuite.domain.repository.TournamentRepository
import com.etologic.mahjongtournamentsuite.presentation.presenter.PlayerImportPresenter
import com.etologic.mahjongtournamentsuite.presentation.presenter.PlayersPresenter
import com.etologic.mahjongtournamentsuite.presentation.presenter.TeamsPresenter
import com.etologic.mahjongtournamentsuite.presentation.presenter.TournamentsPresenter
import com.etologic.mahjongtournamentsuite.presentation.components.LocalPressTimeSource
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
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

/** The search and the country filter of the teams, and picking a member that plays in another team. */
@OptIn(ExperimentalTestApi::class)
class TeamsScreenFiltersUiTest {
    private val clock = TestTimeSource()
    private val names = listOf("Ana", "Bo", "Cai", "Dan", "Eve", "Fay", "Gus", "Hal")
    private val registry = names.mapIndexed { index, name ->
        Player(emaId = "1${index + 1}", firstName = name, lastName = "Lee", country = if (index < 4) "ES" else "FR")
    }

    private fun world() = FakeTournamentRepository().apply {
        tournaments = listOf(Tournament(id = "t1", name = "Open", isTeams = true, numPlayers = 8, numRounds = 1))
        players = (1..8).map { TournamentPlayer(id = it, name = "P$it", team = if (it <= 4) 1 else 2, assignedEmaId = "1$it") }
        teams = listOf(TournamentTeam(id = 1, name = "Aces", playerIds = listOf(1, 2, 3, 4)), TournamentTeam(id = 2, name = "Dragons", playerIds = listOf(5, 6, 7, 8)))
        writesSucceed = true
    }

    private fun ComposeUiTest.show(server: FakeTournamentRepository) {
        setContent {
            val themeController = ThemeController(ThemePreference.Light, false, {})
            val koin = remember {
                koinApplication {
                    modules(
                        module {
                            single { Logger.withTag("ui-test") }
                            single<TournamentRepository> { server }
                            single<AdminRepository> { FakeAdminRepository(GlobalUserRole.SUPER_ADMIN) }
                            single<AuthRepository> { FakeSignedInAuthRepository() }
                            single<PlayerRepository> { FakePlayerRepository(registry) }
                            single<PlayerAccountRepository> { FakePlayerAccountRepository() }
                            single { AppMemoryStore() }
                            factory { TournamentsPresenter(get(), get(), get()) }
                            factory { PlayersPresenter(get(), get(), get(), get()) }
                            factory { TeamsPresenter(get(), get(), get()) }
                            single<PlayerImportRepository> { NoImportRepository() }
                            factory { PlayerImportPresenter(get(), get()) }
                        },
                    )
                }.koin
            }
            KoinContext(koin) {
                CompositionLocalProvider(LocalThemeController provides themeController, LocalPressTimeSource provides clock) {
                    MtsTheme(useDarkTheme = false) { TeamsScreen(rememberNavController(), "t1") }
                }
            }
        }
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Aces").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun ComposeUiTest.shown(text: String) = onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    private fun ComposeUiTest.search(text: String) {
        onAllNodesWithText("Search")[0].performTextClearance()
        onAllNodesWithText("Search")[0].performTextInput(text)
        waitForIdle()
    }

    /** A country chip holds a flag with its own tooltip, so the click goes to the first ancestor of the flag that has one. */
    private fun ComposeUiTest.pressChip(description: String) {
        clock += 1.seconds
        var node = onAllNodes(hasContentDescription(description), useUnmergedTree = true).onFirst()
        while (SemanticsActions.OnClick !in node.fetchSemanticsNode().config) node = node.onParent()
        node.performSemanticsAction(SemanticsActions.OnClick)
    }

    @Test
    fun theSearchFindsTeamsByNameNumberAndPlayer() = runComposeUiTest {
        show(world())

        search("drag")
        waitUntil(timeoutMillis = 5_000) { !shown("Aces") }
        assertTrue(shown("Dragons"))

        search("eve")
        waitUntil(timeoutMillis = 5_000) { !shown("Aces") }

        search("1")
        waitUntil(timeoutMillis = 5_000) { shown("Aces") }

        search("nobody here")
        waitUntil(timeoutMillis = 5_000) { !shown("Aces") && !shown("Dragons") }

        onAllNodesWithContentDescription("Clear search")[0].performClick()
        waitUntil(timeoutMillis = 5_000) { shown("Aces") && shown("Dragons") }
    }

    @Test
    fun theCountryFilterKeepsTheTeamsWithAPlayerOfThatCountry() = runComposeUiTest {
        show(world())
        // The teams come before the registry that gives the countries.
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasContentDescription("Filter by FR")).fetchSemanticsNodes().isNotEmpty() }

        pressChip("Filter by FR")
        waitUntil(timeoutMillis = 5_000) { !shown("Aces") }
        assertTrue(shown("Dragons"))

        pressChip("Remove FR country filter")
        waitUntil(timeoutMillis = 5_000) { shown("Aces") }
    }

    @Test
    fun aMemberFromAnotherTeamShowsAWarningAndIsSaved() = runComposeUiTest {
        val server = world()
        show(server)

        val icon = onAllNodesWithContentDescription("Edit").fetchSemanticsNodes()
        if (icon.isNotEmpty()) onAllNodesWithContentDescription("Edit")[0].performClick() else onAllNodesWithText("Edit")[0].performClick()
        waitUntil(timeoutMillis = 5_000) { shown("Save team") }
        onNodeWithText("1 - ", substring = true).performClick()
        waitUntil(timeoutMillis = 5_000) { shown("Select player 1") }

        onAllNodesWithText("Search").onLast().performTextInput("zzz")
        waitUntil(timeoutMillis = 5_000) { shown("No players match the search.") }
        onAllNodesWithText("Search").onLast().performTextClearance()
        onAllNodesWithText("Search").onLast().performTextInput("eve")
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("EMA 15", substring = true).fetchSemanticsNodes().isNotEmpty() }
        onAllNodesWithText("EMA 15", substring = true).onFirst().performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("is assigned to Dragons as player 5", substring = true).fetchSemanticsNodes().isNotEmpty() }

        onNodeWithText("Save team").performClick()
        waitUntil(timeoutMillis = 5_000) { server.writes.isNotEmpty() }
        assertEquals("team 1 Aces [15, 12, 13, 14]", server.writes.first())
    }
}
