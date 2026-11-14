package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.domain.model.ActivityEntry
import com.etologic.mahjongtournamentsuite.domain.model.ActivityFieldChange
import com.etologic.mahjongtournamentsuite.domain.model.ActivityOrigin
import com.etologic.mahjongtournamentsuite.domain.model.ActivitySource
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.GlobalUserRole
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.repository.AdminRepository
import com.etologic.mahjongtournamentsuite.presentation.presenter.ActivityPresenter
import com.etologic.mahjongtournamentsuite.presentation.store.AppMemoryStore
import com.etologic.mahjongtournamentsuite.presentation.theme.LocalThemeController
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import com.etologic.mahjongtournamentsuite.presentation.theme.ThemeController
import com.etologic.mahjongtournamentsuite.presentation.theme.ThemePreference
import org.koin.compose.KoinContext
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock

/** An administrator reads who wrote what and when: the list, the filters, the next page and one entry. */
@OptIn(ExperimentalTestApi::class)
class ActivityScreenUiTest {
    private val now = Clock.System.now().toEpochMilliseconds()

    private fun entry(
        id: String,
        action: String,
        uid: String,
        name: String,
        isTableSave: Boolean = false,
        changes: List<ActivityFieldChange> = emptyList(),
    ) = ActivityEntry(
        id = id,
        isTableSave = isTableSave,
        at = now - id.length * 1_000L,
        action = action,
        uid = uid,
        name = name,
        source = if (isTableSave) "player" else null,
        tournamentId = "t1",
        tournamentName = "Madrid Open",
        target = if (isTableSave) null else "player 5",
        details = if (isTableSave) emptyList() else listOf("teamId" to "3"),
        roundId = if (isTableSave) 2 else null,
        tableId = if (isTableSave) 4 else null,
        version = if (isTableSave) 7 else null,
        changes = changes,
    )

    private val save = entry(
        "a",
        "table.save",
        "u2",
        "Player 123",
        isTableSave = true,
        changes = listOf(ActivityFieldChange("handScore", null, "8", handId = 3)),
    )
    private val assign = entry("bb", "player.assign", "u1", "Ana Admin")

    private fun ComposeUiTest.show(
        entries: List<ActivityEntry> = listOf(save, assign),
        pageSize: Int = Int.MAX_VALUE,
        widthDp: Int? = null,
        tournaments: List<Tournament> = emptyList(),
        initialTournamentId: String? = null,
    ): FakeAdminRepository {
        val admin = FakeAdminRepository(GlobalUserRole.ADMIN).apply {
            activity = entries
            activityPageSize = pageSize
        }
        setContent {
            val themeController = ThemeController(ThemePreference.Light, false, {})
            val koin = remember {
                koinApplication {
                    modules(
                        module {
                            single { Logger.withTag("ui-test") }
                            single<AdminRepository> { admin }
                            single { AppMemoryStore() }
                            factory { ActivityPresenter(get()) { AppResult.Success(tournaments) } }
                        },
                    )
                }.koin
            }
            KoinContext(koin) {
                CompositionLocalProvider(LocalThemeController provides themeController) {
                    MtsTheme(useDarkTheme = false) {
                        if (widthDp == null) {
                            ActivityScreen(rememberNavController(), initialTournamentId = initialTournamentId)
                        } else {
                            Box(Modifier.size(widthDp.dp, 760.dp)) { ActivityScreen(rememberNavController()) }
                        }
                    }
                }
            }
        }
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Table saved").fetchSemanticsNodes().isNotEmpty() }
        return admin
    }

    @Test
    fun theListShowsWhoDidWhatAndWhere() = runComposeUiTest {
        show()
        onNodeWithText("Player 123").assertExists()
        // The origin filter chip and the row of the table save.
        onAllNodesWithText("Player app").assertCountEquals(2)
        onNodeWithText("Madrid Open · Round 2 · Table 4").assertExists()
        onNodeWithText("Player assigned").assertExists()
        onNodeWithText("Ana Admin").assertExists()
    }

    @Test
    fun aSourceFilterAsksTheServerAgain() = runComposeUiTest {
        val admin = show()
        onNodeWithText("Table saves").performClick()
        waitForIdle()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Player assigned").fetchSemanticsNodes().isEmpty() }
        assertEquals(ActivitySource.TABLES, admin.activityRequests.last().first.source)
        assertEquals(null, admin.activityRequests.last().second)
    }

    @Test
    fun anOriginFilterAsksTheServerAgain() = runComposeUiTest {
        val admin = show()
        onAllNodesWithText("Player app").onFirst().performClick()
        waitUntil(timeoutMillis = 5_000) { admin.activityRequests.last().first.origin == ActivityOrigin.PLAYER_APP }
        onNodeWithText("All origins").performClick()
        waitUntil(timeoutMillis = 5_000) { admin.activityRequests.last().first.origin == null }
    }

    @Test
    fun openedFromATournamentItStartsFilteredByIt() = runComposeUiTest {
        val admin = show(tournaments = listOf(tournament("t2", "Barcelona Cup"), tournament("t1", "Madrid Open")), initialTournamentId = "t2")
        assertEquals("t2", admin.activityRequests.first().first.tournamentId)
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Barcelona Cup").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun theTournamentMenuKeepsTheOrderOfTheTournamentList() = runComposeUiTest {
        // The list of tournaments comes newest first; the menu must not sort it by name.
        show(tournaments = listOf(tournament("t2", "Zaragoza Open"), tournament("t1", "Alicante Cup")))
        onNodeWithText("All tournaments").performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Alicante Cup").fetchSemanticsNodes().isNotEmpty() }
        val zaragoza = onNodeWithText("Zaragoza Open").fetchSemanticsNode().boundsInRoot.top
        val alicante = onNodeWithText("Alicante Cup").fetchSemanticsNode().boundsInRoot.top
        assertTrue(zaragoza < alicante, "Zaragoza Open ($zaragoza) should be above Alicante Cup ($alicante)")
    }

    @Test
    fun theSearchAsksTheServerWithTheTextOnceTheTypingStops() = runComposeUiTest {
        val admin = show()
        onNodeWithText("Search").performTextInput("José")
        waitUntil(timeoutMillis = 5_000) { admin.activityRequests.last().first.text == "José" }
        onNodeWithContentDescription("Clear search").performClick()
        waitUntil(timeoutMillis = 5_000) { admin.activityRequests.last().first.text == null }
    }

    @Test
    fun loadMoreReadsTheNextPage() = runComposeUiTest {
        val admin = show(pageSize = 1)
        onAllNodesWithText("Player assigned").assertCountEquals(0)
        onNodeWithText("Load more").performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Player assigned").fetchSemanticsNodes().isNotEmpty() }
        assertEquals("1", admin.activityRequests.last().second)
        onAllNodesWithText("Load more").assertCountEquals(0)
    }

    @Test
    fun anEntryShowsItsChangesAndCanFilterByItsPerson() = runComposeUiTest {
        val admin = show()
        onAllNodesWithText("Table saved").onFirst().performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("– → 8").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Hand 3 · handScore").assertExists()
        onNodeWithText("Show only this person").performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Person: Player 123").fetchSemanticsNodes().isNotEmpty() }
        assertEquals("u2", admin.activityRequests.last().first.uid)
    }

    private fun tournament(id: String, name: String) = Tournament(id = id, name = name, isTeams = false, numPlayers = 8, numRounds = 5)

    /** With MTS_SCREENSHOT_DIR set, saves the screen at a desktop and a phone width to check the filters by eye. */
    @Test
    fun theFiltersFitAtDesktopAndPhoneWidth() {
        for (width in listOf(1280, 400)) {
            runComposeUiTest {
                show(entries = listOf(save, assign, entry("ccc", "tournament.update", "u1", "Ana Admin")), pageSize = 2, widthDp = width)
                waitForIdle()
                System.getenv("MTS_SCREENSHOT_DIR")?.let { dir ->
                    ImageIO.write(onAllNodes(isRoot())[0].captureToImage().toAwtImage(), "png", File(dir, "activity-$width.png"))
                }
                onNodeWithText("Origin").assertExists()
            }
        }
    }
}
