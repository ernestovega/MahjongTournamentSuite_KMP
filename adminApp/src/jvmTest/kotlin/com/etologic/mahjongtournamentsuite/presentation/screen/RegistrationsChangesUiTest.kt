package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.domain.model.AdminRegistration
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.NewRegistration
import com.etologic.mahjongtournamentsuite.domain.model.RegistrationUpdate
import com.etologic.mahjongtournamentsuite.presentation.components.LocalPressTimeSource
import com.etologic.mahjongtournamentsuite.presentation.screenshots.adminModules
import com.etologic.mahjongtournamentsuite.presentation.theme.LocalThemeController
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import com.etologic.mahjongtournamentsuite.presentation.theme.ThemeController
import com.etologic.mahjongtournamentsuite.presentation.theme.ThemePreference
import com.etologic.mahjongtournamentsuite.screenshots.fake.ScreenshotData
import com.etologic.mahjongtournamentsuite.screenshots.fake.ScreenshotRegistrationRepository
import org.koin.compose.KoinContext
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

/** The registrations of the screenshots, where the changes of the organizer work and are written down. */
private class ChangingRegistrations : ScreenshotRegistrationRepository() {
    val calls = mutableListOf<String>()

    private suspend fun first(): AdminRegistration = (loadAdminList(ScreenshotData.UPCOMING) as AppResult.Success).value.registrations.first()

    override suspend fun adminAdd(tournamentId: String, registration: NewRegistration): AppResult<AdminRegistration> {
        calls += "add:${registration.emaId}"
        return AppResult.Success(first())
    }

    override suspend fun adminUpdate(tournamentId: String, emaId: String, update: RegistrationUpdate): AppResult<AdminRegistration> {
        calls += "update:$emaId:${update.paid}:${update.status}"
        return AppResult.Success(first())
    }

    override suspend fun adminRemove(tournamentId: String, emaId: String, notify: Boolean): AppResult<Unit> {
        calls += "remove:$emaId:$notify"
        return AppResult.Success(Unit)
    }
}

/** What an organizer changes in the list of registrations, and what the server gets. */
@OptIn(ExperimentalTestApi::class)
class RegistrationsChangesUiTest {
    private val people = ScreenshotData.players
    private val original = Locale.getDefault()

    // The buttons ignore a second press within 500 ms of real time: the test moves this clock before each press.
    private val clock = TestTimeSource()

    @BeforeTest
    fun english() = Locale.setDefault(Locale.ENGLISH)

    @AfterTest
    fun restore() = Locale.setDefault(original)

    private fun ComposeUiTest.show(registrations: ScreenshotRegistrationRepository) {
        setContent {
            val koin = koinApplication {
                modules(module { single { Logger.withTag("ui-test") } })
                modules(adminModules())
                modules(module { single<com.etologic.mahjongtournamentsuite.domain.repository.RegistrationRepository> { registrations } })
                allowOverride(true)
            }.koin
            KoinContext(koin) {
                CompositionLocalProvider(LocalThemeController provides ThemeController(ThemePreference.Light, false, {}), LocalPressTimeSource provides clock) {
                    MtsTheme(useDarkTheme = false) {
                        val navController = rememberNavController()
                        NavHost(navController, startDestination = "screen") {
                            composable("screen") { RegistrationsScreen(navController, ScreenshotData.UPCOMING) }
                        }
                    }
                }
            }
        }
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("With problems", substring = true).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun ComposeUiTest.menuOf(index: Int) {
        clock += 1.seconds
        val person = people[index]
        onNodeWithContentDescription("More actions for ${person.firstName} ${person.lastName}".trim()).performScrollTo().performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.click(text: String, last: Boolean = false) {
        clock += 1.seconds
        val nodes = onAllNodes(hasClickAction() and hasText(text) and !hasSetTextAction())
        val node = if (last) nodes.onLast() else nodes[0]
        runCatching { node.performScrollTo() }
        node.performClick()
        waitForIdle()
    }

    @Test
    fun theOrganizerMarksAPaymentMovesChangesAndRemovesPeople() = runComposeUiTest {
        val registrations = ChangingRegistrations()
        show(registrations)
        val first = people[0].emaId
        onNodeWithTag("registration-paid-$first").performScrollTo().performClick()
        waitUntil(timeoutMillis = 5_000) { registrations.calls.size == 1 }
        menuOf(0)
        click("Move to the waiting list", last = true)
        waitUntil(timeoutMillis = 5_000) { registrations.calls.size == 2 }
        menuOf(0)
        click("Change", last = true)
        click("Save", last = true)
        waitUntil(timeoutMillis = 5_000) { registrations.calls.size == 3 }
        menuOf(0)
        click("Remove", last = true)
        click("Remove", last = true)
        waitUntil(timeoutMillis = 5_000) { registrations.calls.size == 4 }
        assertEquals("update:$first:false:null", registrations.calls[0])
        assertEquals("update:$first:null:WAITING", registrations.calls[1])
        assertEquals(true, registrations.calls[2].startsWith("update:$first:"))
        assertEquals(true, registrations.calls[3].startsWith("remove:$first:"))
    }

    @Test
    fun aChangeThatFailsShowsTheErrorAndTheAddDialogCloses() = runComposeUiTest {
        show(ScreenshotRegistrationRepository())
        onNodeWithTag("registration-paid-${people[0].emaId}").performScrollTo().performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText("OK")).fetchSemanticsNodes().isNotEmpty() }
        click("OK")
        click("Add a person")
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText("Cancel")).fetchSemanticsNodes().isNotEmpty() }
        click("Cancel", last = true)
    }
}
