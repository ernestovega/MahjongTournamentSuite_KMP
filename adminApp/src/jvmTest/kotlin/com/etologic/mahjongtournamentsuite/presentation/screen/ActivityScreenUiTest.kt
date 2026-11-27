package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.domain.model.ActivityEntry
import com.etologic.mahjongtournamentsuite.domain.model.ActivityFieldChange
import com.etologic.mahjongtournamentsuite.domain.model.ActivityOrigin
import com.etologic.mahjongtournamentsuite.domain.model.ActivitySource
import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.GlobalUserRole
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.repository.AdminRepository
import com.etologic.mahjongtournamentsuite.presentation.components.LocalAppLogo
import com.etologic.mahjongtournamentsuite.presentation.components.LocalCompactLayout
import com.etologic.mahjongtournamentsuite.presentation.components.LocalPressTimeSource
import com.etologic.mahjongtournamentsuite.presentation.presenter.ActivityPresenter
import com.etologic.mahjongtournamentsuite.presentation.store.AppMemoryStore
import com.etologic.mahjongtournamentsuite.presentation.theme.LocalThemeController
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import com.etologic.mahjongtournamentsuite.presentation.theme.ThemeController
import com.etologic.mahjongtournamentsuite.presentation.theme.ThemePreference
import kotlinx.coroutines.CompletableDeferred
import org.koin.compose.KoinContext
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

/** An administrator reads who wrote what and when: the list, the filters, the next page and one entry. */
@OptIn(ExperimentalTestApi::class)
class ActivityScreenUiTest {
    private val clock = TestTimeSource()
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
        players = if (isTableSave) listOf("Lucía Martín", "Hugo García", "Claire Dubois", "Jonas Becker") else emptyList(),
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
        failure: AppError? = null,
        tournaments: List<Tournament> = emptyList(),
        initialTournamentId: String? = null,
        compact: Boolean = false,
    ): FakeAdminRepository {
        val admin = FakeAdminRepository(GlobalUserRole.SUPER_ADMIN).apply {
            activity = entries
            activityPageSize = pageSize
            activityError = failure
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
                CompositionLocalProvider(
                    LocalThemeController provides themeController,
                    LocalPressTimeSource provides clock,
                    LocalCompactLayout provides compact,
                    LocalAppLogo provides ColorPainter(Color.Green),
                ) {
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
        if (failure == null) waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Table saved").fetchSemanticsNodes().isNotEmpty() }
        return admin
    }

    @Test
    fun theListShowsWhoDidWhatAndWhere() = runComposeUiTest {
        show()
        onNodeWithText("Player 123").assertExists()
        // The origin filter chip and the row of the table save.
        onAllNodesWithText("Player app").assertCountEquals(2)
        onNodeWithText("Madrid Open · Round 2 · Table 4").assertExists()
        onNodeWithText("Lucía Martín · Hugo García · Claire Dubois · Jonas Becker").assertExists()
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
        val admin = show(tournaments = listOf(tournament("t2", "Barcelona Cup"), tournament("t1", "Valencia Open")), initialTournamentId = "t1")
        assertEquals("t1", admin.activityRequests.first().first.tournamentId)
        // The menu shows the tournament, not "All tournaments".
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Valencia Open").fetchSemanticsNodes().isNotEmpty() }
        onAllNodesWithText("All tournaments").assertCountEquals(0)
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
    fun aSearchKeepsTheFiltersOnScreenAndShowsTheLogoOnlyInTheList() = runComposeUiTest {
        val admin = show()
        val gate = CompletableDeferred<Unit>()
        admin.activityGate = gate
        onNodeWithText("Search").performTextInput("José")
        waitUntil(timeoutMillis = 5_000) { admin.activityRequests.last().first.text == "José" }
        // The filters stay, and the logo is the small one of the list, not the large one that covers the screen.
        onNodeWithText("Origin").assertIsDisplayed()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithContentDescription(LOGO).fetchSemanticsNodes().isNotEmpty() }
        onAllNodesWithContentDescription(LOGO).assertCountEquals(1)[0].assertWidthIsEqualTo(72.dp)
        gate.complete(Unit)
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithContentDescription(LOGO).fetchSemanticsNodes().isEmpty() }
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

    private fun ComposeUiTest.tap(text: String) {
        clock += 1.seconds
        onAllNodes(hasText(text, substring = true) and hasClickAction()).onLast().performSemanticsAction(SemanticsActions.OnClick)
        waitForIdle()
    }

    @Test
    fun aPeriodIsPickedWithADateAndATimeAndCleared() = runComposeUiTest {
        val admin = show()

        tap("From")
        tap("OK")
        tap("OK")
        waitUntil(timeoutMillis = 5_000) { admin.activityRequests.last().first.fromMillis != null }

        tap("Until")
        tap("OK")
        tap("OK")
        waitUntil(timeoutMillis = 5_000) { admin.activityRequests.last().first.toMillis != null }
        val period = admin.activityRequests.last().first
        assertEquals(60_000L, period.toMillis!! - period.fromMillis!!, "both ends at this minute, the end one minute later")

        tap("Until")
        tap("Cancel")
        clock += 1.seconds
        onNode(hasContentDescription("Clear dates") and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        waitUntil(timeoutMillis = 5_000) { admin.activityRequests.last().first.let { it.fromMillis == null && it.toMillis == null } }
    }

    @Test
    fun anEntryWithoutChangesShowsItsDetailsAndThePersonFilterIsCleared() = runComposeUiTest {
        val admin = show()

        onNodeWithText("Player assigned").performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("teamId: 3").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Details").assertExists()
        onNodeWithText("Show only this person").performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Person: Ana Admin").fetchSemanticsNodes().isNotEmpty() }
        assertEquals("u1", admin.activityRequests.last().first.uid)

        tap("Person: Ana Admin")
        waitUntil(timeoutMillis = 5_000) { admin.activityRequests.last().first.uid == null }
    }

    @Test
    fun aFailedReadShowsTheError() = runComposeUiTest {
        show(failure = AppError.Network)

        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("OK").fetchSemanticsNodes().isNotEmpty() }
        tap("OK")
    }

    private companion object {
        const val LOGO = "Mahjong Tournament Suite logo"
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

    @Test
    fun aPhoneShowsEachEntryOnOneCompactRow() = runComposeUiTest {
        val conflict = entry("ccc", "table.conflict", "u3", "Eva Editor", isTableSave = true)
        show(entries = listOf(save, assign, conflict), compact = true)

        onAllNodesWithText("Madrid Open · Round 2 · Table 4").onFirst().assertExists()
        onAllNodesWithText("Player 123", substring = true).onFirst().assertExists()
        onAllNodesWithText("1 value changed", substring = true).onFirst().assertExists()
    }

    @Test
    fun aTableSaveWithoutChangesSaysSo() = runComposeUiTest {
        show(entries = listOf(entry("a", "table.save", "u2", "Player 123", isTableSave = true)))
        onAllNodesWithText("Table saved").onFirst().performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("No values changed", substring = true).fetchSemanticsNodes().isNotEmpty() }
    }
}
