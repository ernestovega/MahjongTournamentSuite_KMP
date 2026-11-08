package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.navigation.compose.rememberNavController
import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.ErrorGroup
import com.etologic.mahjongtournamentsuite.domain.model.ErrorGroupDetail
import com.etologic.mahjongtournamentsuite.domain.model.ErrorLogCursor
import com.etologic.mahjongtournamentsuite.domain.model.ErrorReportEntry
import com.etologic.mahjongtournamentsuite.domain.model.GlobalUserRole
import com.etologic.mahjongtournamentsuite.domain.repository.AdminRepository
import com.etologic.mahjongtournamentsuite.presentation.presenter.ErrorsPresenter
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Clock

/** An administrator reads the error log: the list, the filters and the detail of one problem. */
@OptIn(ExperimentalTestApi::class)
class ErrorsScreenUiTest {
    private val now = Clock.System.now().toEpochMilliseconds()

    private fun group(
        id: String,
        type: String,
        platform: String,
        count: Int,
        kind: String = "handled",
        env: String = "prod",
        app: String = "admin",
    ) = ErrorGroup(
        id = id, errorType = type, area = "tournaments", message = "boom $id", count = count, firstSeen = now - 5_000, lastSeen = now - 1_000,
        lastKind = kind, lastApp = app, lastPlatform = platform, lastVersion = "1.2.3", lastEnv = env,
        platforms = mapOf(platform to count), apps = mapOf(app to count),
    )

    private val webGroup = group("g1", "WebFailure", "web", 7)
    private val androidGroup = group("g2", "AndroidFailure", "android", 2)

    private fun ComposeUiTest.show(
        pageSize: Int = Int.MAX_VALUE,
        extra: List<ErrorGroup> = emptyList(),
        base: List<ErrorGroup> = listOf(webGroup, androidGroup),
        failure: AppError? = null,
        configure: FakeAdminRepository.() -> Unit = {},
        ready: String? = "WebFailure",
    ): FakeAdminRepository {
        val admin = FakeAdminRepository(GlobalUserRole.ADMIN).apply {
            errorPageSize = pageSize
            errorGroupsFailure = failure
            errorGroups = base + extra
            errorDetails = mapOf(
                "g1" to ErrorGroupDetail(
                    group = webGroup,
                    stackTrace = "at com.example.Failing.run(Failing.kt:12)",
                    nextReportCursor = null,
                    reports = listOf(
                        ErrorReportEntry(
                            reportId = "report-abc", createdAt = now - 1_000, app = "admin", platform = "web", kind = "handled", env = "prod",
                            role = "admin", appVersion = "1.2.3", reason = "server_error", httpStatus = 500, message = "boom", language = "en",
                        ),
                    ),
                ),
            )
            configure()
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
                            factory { ErrorsPresenter(get()) }
                        },
                    )
                }.koin
            }
            KoinContext(koin) {
                CompositionLocalProvider(LocalThemeController provides themeController) {
                    MtsTheme(useDarkTheme = false) { ErrorsScreen(rememberNavController()) }
                }
            }
        }
        if (ready != null) waitUntil(timeoutMillis = 5_000) { onAllNodesWithText(ready).fetchSemanticsNodes().isNotEmpty() }
        return admin
    }

    @Test
    fun theListShowsEveryProblemAndAPlatformFilterNarrowsIt() = runComposeUiTest {
        show()
        onNodeWithText("AndroidFailure").assertExistsText()
        onAllNodesWithText("Android").onFirst().performClick()
        waitForIdle()
        onAllNodesWithText("WebFailure").assertCountEquals(0)
        onNodeWithText("AndroidFailure").assertExistsText()
    }

    @Test
    fun aClickOnAProblemOpensItsStackTraceAndReports() = runComposeUiTest {
        show()
        onAllNodesWithText("WebFailure").onFirst().performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("at com.example.Failing.run(Failing.kt:12)").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Report ID: report-abc").assertExistsText()
        onNodeWithText("Copy stack trace").assertExistsText()
    }

    @Test
    fun everyPageIsLoadedSoNoProblemIsLeftOut() = runComposeUiTest {
        val extra = (3..5).map { group("g$it", "Extra$it", "web", 1) }
        val admin = show(pageSize = 2, extra = extra)
        // The list shows only the rows in view, so the summary line proves that all five problems are loaded.
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("5 of 5 problems").fetchSemanticsNodes().isNotEmpty() }
        // Five problems in pages of two: three requests, each with the cursor of the page before.
        assertEquals(3, admin.errorGroupRequests.size)
    }

    @Test
    fun thePeriodGoesToTheServer() = runComposeUiTest {
        val admin = show()
        onNodeWithText("Last 24 hours").performClick()
        waitUntil(timeoutMillis = 5_000) { admin.errorGroupRequests.size >= 2 }
        assertNotNull(admin.errorGroupRequests.last().first)
        assertNull(admin.errorGroupRequests.first().first)
    }

    @Test
    fun theSummaryCountsTheProblemsTheOccurrencesAndTheCrashes() = runComposeUiTest {
        show(base = listOf(webGroup, group("g3", "CrashFailure", "web", 4, kind = "crash")))

        onNodeWithText("Problems").assertExists()
        onNodeWithText("Occurrences").assertExists()
        onNodeWithText("11").assertExists()
        onNodeWithText("2 of 2 problems").assertExists()
        onNodeWithText("Crashes").assertExists()
    }

    @Test
    fun anEmptyLogSaysSoAndAFilterWithNoMatchSaysThatInstead() = runComposeUiTest {
        show(base = emptyList(), ready = "No errors are logged yet.")
        onNodeWithText("No errors are logged yet.").assertExists()
        onNodeWithText("0 of 0 problems").assertExists()
    }

    @Test
    fun aSearchThatMatchesNothingShowsTheNoMatchMessage() = runComposeUiTest {
        show()
        onNodeWithText("Search by type, area or message").performTextInput("zzz-nothing")
        waitForIdle()

        onNodeWithText("No problem matches these filters.").assertExists()
        onAllNodesWithText("WebFailure").assertCountEquals(0)
    }

    @Test
    fun aSearchKeepsOnlyTheMatchingProblem() = runComposeUiTest {
        show()
        onNodeWithText("Search by type, area or message").performTextInput("android")
        waitForIdle()

        onAllNodesWithText("WebFailure").assertCountEquals(0)
        onNodeWithText("AndroidFailure").assertExists()
        onNodeWithText("1 of 2 problems").assertExists()
    }

    @Test
    fun developmentBuildsAreHiddenUntilProductionOnlyIsTurnedOff() = runComposeUiTest {
        show(extra = listOf(group("g4", "DevFailure", "web", 1, env = "dev")))
        onAllNodesWithText("DevFailure").assertCountEquals(0)

        onNodeWithText("Production only").performClick()
        waitForIdle()

        onNodeWithText("DevFailure").assertExists()
    }

    @Test
    fun theAppAndTheTypeFiltersNarrowTheList() = runComposeUiTest {
        show(
            base = listOf(
                group("g1", "AdminHandled", "web", 1),
                group("g2", "PlayerCrash", "web", 1, kind = "crash", app = "player"),
            ),
            ready = "AdminHandled",
        )

        // The same words are in the filter chips and in the rows. The chips come first.
        onAllNodesWithText("Player app").onFirst().performClick()
        waitForIdle()
        onAllNodesWithText("AdminHandled").assertCountEquals(0)
        onNodeWithText("PlayerCrash").assertExists()

        onNodeWithText("All apps").performClick()
        onAllNodesWithText("Handled").onFirst().performClick()
        waitForIdle()
        onAllNodesWithText("PlayerCrash").assertCountEquals(0)
        onNodeWithText("AdminHandled").assertExists()
    }

    @Test
    fun reloadReadsTheLogAgain() = runComposeUiTest {
        val admin = show()
        assertEquals(1, admin.errorGroupRequests.size)

        onNodeWithText("Reload").performClick()
        waitUntil(timeoutMillis = 5_000) { admin.errorGroupRequests.size == 2 }

        assertEquals(2, admin.errorGroupRequests.size)
    }

    @Test
    fun aFailedLoadShowsTheErrorDialogAndDismissingItKeepsTheScreen() = runComposeUiTest {
        show(failure = AppError.Network, ready = "Error")
        onNodeWithText("Error").assertExists()

        onNodeWithText("OK").performClick()
        waitForIdle()

        onAllNodesWithText("Error").assertCountEquals(0)
        onNodeWithText("No errors are logged yet.").assertExists()
    }

    @Test
    fun theDetailShowsTheCountsPerPlatformAndAppAndCopiesWhenThereIsAStackTrace() = runComposeUiTest {
        show()
        onAllNodesWithText("WebFailure").onFirst().performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Copy stack trace").fetchSemanticsNodes().isNotEmpty() }

        onNodeWithText("By platform: Web 7", substring = true).assertExists()
        onNodeWithText("By app: Admin app 7", substring = true).assertExists()
        onNodeWithText("Copy stack trace").assertIsEnabled()
        onNodeWithText("Reason: server_error 500").assertExists()
    }

    @Test
    fun aDetailWithNoStackTraceSaysSoAndCannotCopy() = runComposeUiTest {
        show(configure = {
            errorDetails = mapOf("g1" to errorDetails.getValue("g1").copy(stackTrace = ""))
        })
        onAllNodesWithText("WebFailure").onFirst().performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("No stack trace.").fetchSemanticsNodes().isNotEmpty() }

        onNodeWithText("Copy stack trace").assertIsNotEnabled()
    }

    @Test
    fun theCloseButtonClosesTheDetail() = runComposeUiTest {
        show()
        onAllNodesWithText("WebFailure").onFirst().performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Copy stack trace").fetchSemanticsNodes().isNotEmpty() }

        onNodeWithText("Close").performClick()
        waitForIdle()

        onAllNodesWithText("Copy stack trace").assertCountEquals(0)
    }

    @Test
    fun moreReportsLoadOnRequestAndAddToTheOnesShown() = runComposeUiTest {
        val firstReport = ErrorReportEntry(
            reportId = "report-1", createdAt = now - 2_000, app = "admin", platform = "web", kind = "handled", env = "prod",
            role = null, appVersion = "1.2.3", reason = "", httpStatus = null, message = "", language = "en",
        )
        val secondReport = firstReport.copy(reportId = "report-2", createdAt = now - 3_000)
        val admin = show(configure = {
            errorDetails = mapOf(
                "g1" to ErrorGroupDetail(webGroup, "stack", listOf(firstReport), nextReportCursor = ErrorLogCursor(now - 2_000, "doc1")),
            )
            errorNextReportPages = mapOf("g1" to ErrorGroupDetail(webGroup, "stack", listOf(secondReport), nextReportCursor = null))
        })
        onAllNodesWithText("WebFailure").onFirst().performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Load more reports").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Report ID: report-1").assertExists()
        onAllNodesWithText("Report ID: report-2").assertCountEquals(0)

        onNodeWithText("Load more reports").performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Report ID: report-2").fetchSemanticsNodes().isNotEmpty() }

        onNodeWithText("Report ID: report-1").assertExists()
        onAllNodesWithText("Load more reports").assertCountEquals(0)
        assertEquals(listOf("g1" to null, "g1" to ErrorLogCursor(now - 2_000, "doc1")), admin.errorDetailRequests)
    }

    @Test
    fun aDetailThatFailsToLoadShowsTheErrorAndClosesWithIt() = runComposeUiTest {
        show(configure = { errorDetails = emptyMap() })
        onAllNodesWithText("WebFailure").onFirst().performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Error").fetchSemanticsNodes().isNotEmpty() }

        onNodeWithText("OK").performClick()
        waitForIdle()

        onAllNodesWithText("Error").assertCountEquals(0)
        onAllNodesWithText("Copy stack trace").assertCountEquals(0)
    }

    @Test
    fun theRelativeTimeOfTheLastErrorIsShown() = runComposeUiTest {
        show()

        // The two problems were seen one second ago.
        onAllNodesWithText("Just now").onFirst().assertExists()
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertExistsText() = assertExists()
}
