package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.navigation.compose.rememberNavController
import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.GlobalUserRole
import com.etologic.mahjongtournamentsuite.domain.model.Organization
import com.etologic.mahjongtournamentsuite.domain.model.OrganizationDraft
import com.etologic.mahjongtournamentsuite.domain.model.OrganizationType
import com.etologic.mahjongtournamentsuite.domain.repository.AdminRepository
import com.etologic.mahjongtournamentsuite.domain.repository.RefreshMode
import com.etologic.mahjongtournamentsuite.presentation.components.LocalPressTimeSource
import com.etologic.mahjongtournamentsuite.presentation.platform.LocalPickedImageForTests
import com.etologic.mahjongtournamentsuite.presentation.platform.SelectedImage
import com.etologic.mahjongtournamentsuite.presentation.presenter.OrganizationsPresenter
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

/** A super admin edits, deletes and searches the organizations, and a failed read or write says so. */
@OptIn(ExperimentalTestApi::class)
class OrganizationsScreenUiTest {
    private val clock = TestTimeSource()
    private var nextImage = SelectedImage("logo.bmp", "image/bmp", ByteArray(16))

    private class Server(
        val failStatus: Boolean = false,
        val failWrites: Boolean = false,
        val failReload: Boolean = false,
    ) : AdminRepository by FakeAdminRepository(GlobalUserRole.SUPER_ADMIN) {
        val calls = mutableListOf<String>()
        private val base = FakeAdminRepository(GlobalUserRole.SUPER_ADMIN)
        private var reads = 0
        private val failure = AppResult.Failure(AppError.Network)

        override suspend fun whoAmI(refreshMode: RefreshMode) = if (failStatus) failure else base.whoAmI(refreshMode)

        override suspend fun listOrganizations(): AppResult<List<Organization>> {
            reads += 1
            if (failReload && reads > 1) return failure
            return AppResult.Success(
                listOf(
                    Organization("fed", "Spanish Federation", OrganizationType.FEDERATION, "ES", logoUrl = "https://logo.test/fed.png", canEdit = true, canDelete = true),
                    Organization("club", "Paris Club", OrganizationType.CLUB, "FR", canEdit = true),
                ),
            )
        }

        override suspend fun updateOrganization(id: String, draft: OrganizationDraft): AppResult<Organization> {
            calls += "update $id ${draft.name} logo removed ${draft.removeLogo}"
            return if (failWrites) failure else AppResult.Success(Organization(id, draft.name))
        }

        override suspend fun deleteOrganization(id: String): AppResult<Unit> {
            calls += "delete $id"
            return if (failWrites) failure else AppResult.Success(Unit)
        }
    }

    private fun ComposeUiTest.show(server: Server) {
        setContent {
            val koin = remember {
                koinApplication {
                    modules(
                        module {
                            single { Logger.withTag("ui-test") }
                            single<AdminRepository> { server }
                            single { AppMemoryStore() }
                            factory { OrganizationsPresenter(get()) }
                        },
                    )
                }.koin
            }
            KoinContext(koin) {
                CompositionLocalProvider(LocalThemeController provides ThemeController(ThemePreference.Light, false, {}), LocalPressTimeSource provides clock, LocalPickedImageForTests provides { nextImage }) {
                    MtsTheme(useDarkTheme = false) { OrganizationsScreen(rememberNavController()) }
                }
            }
        }
        waitForIdle()
    }

    private fun ComposeUiTest.shown(text: String) = onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    private fun ComposeUiTest.tap(text: String, last: Boolean = true) {
        clock += 1.seconds
        val nodes = onAllNodes(hasText(text) and hasClickAction())
        (if (last) nodes.onLast() else nodes.onFirst()).performSemanticsAction(SemanticsActions.OnClick)
        waitForIdle()
    }

    @Test
    fun anOrganizationIsSavedWithoutItsLogoAndThenDeleted() = runComposeUiTest {
        val server = Server()
        show(server)
        waitUntil(timeoutMillis = 5_000) { shown("Spanish Federation") }

        tap("Spanish Federation", last = false)
        waitUntil(timeoutMillis = 5_000) { shown("Edit organization") }
        tap("Remove")
        tap("Save")
        waitUntil(timeoutMillis = 5_000) { server.calls.isNotEmpty() }
        assertEquals("update fed Spanish Federation logo removed true", server.calls.single())

        waitUntil(timeoutMillis = 5_000) { !shown("Edit organization") }
        tap("Spanish Federation", last = false)
        waitUntil(timeoutMillis = 5_000) { shown("Edit organization") }
        tap("Delete")
        waitUntil(timeoutMillis = 5_000) { shown("Delete Spanish Federation?") }
        tap("Delete")
        waitUntil(timeoutMillis = 5_000) { !shown("Spanish Federation") }
        assertEquals("delete fed", server.calls.last())
    }

    @Test
    fun theSearchAndTheCountryFilterNarrowTheList() = runComposeUiTest {
        show(Server())
        waitUntil(timeoutMillis = 5_000) { shown("Paris Club") }

        onAllNodes(hasText("Search") and hasSetTextAction()).onFirst().performTextInput("zzz")
        waitUntil(timeoutMillis = 5_000) { !shown("Paris Club") }
        clock += 1.seconds
        onAllNodesWithContentDescription("Clear search").onFirst().performSemanticsAction(SemanticsActions.OnClick)
        waitUntil(timeoutMillis = 5_000) { shown("Paris Club") }

        clock += 1.seconds
        var chip = onAllNodes(hasContentDescription("Filter by FR"), useUnmergedTree = true).onFirst()
        while (SemanticsActions.OnClick !in chip.fetchSemanticsNode().config) chip = chip.onParent()
        chip.performSemanticsAction(SemanticsActions.OnClick)
        waitUntil(timeoutMillis = 5_000) { !shown("Spanish Federation") }
    }

    @Test
    fun aNewOrganizationNeedsANameAndACountry() = runComposeUiTest {
        show(Server())
        waitUntil(timeoutMillis = 5_000) { shown("Paris Club") }

        tap("New organization")
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText("Save") and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }
        tap("Save")
        tap("Cancel")
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText("Save") and hasClickAction()).fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun failedReadsAndWritesShowTheError() = runComposeUiTest {
        val server = Server(failStatus = true, failWrites = true)
        show(server)
        waitUntil(timeoutMillis = 5_000) { shown("OK") }
        tap("OK")

        tap("Spanish Federation", last = false)
        waitUntil(timeoutMillis = 5_000) { shown("Edit organization") }
        tap("Save")
        waitUntil(timeoutMillis = 5_000) { shown("OK") }
        tap("OK")
        tap("Delete")
        tap("Delete")
        waitUntil(timeoutMillis = 5_000) { shown("OK") }
        assertTrue(server.calls.any { it.startsWith("delete") })
    }

    @Test
    fun aFailedReloadAfterASaveShowsTheError() = runComposeUiTest {
        val server = Server(failReload = true)
        show(server)
        waitUntil(timeoutMillis = 5_000) { shown("Spanish Federation") }

        tap("Spanish Federation", last = false)
        waitUntil(timeoutMillis = 5_000) { shown("Edit organization") }
        tap("Save")
        waitUntil(timeoutMillis = 5_000) { shown("OK") }
    }

    @Test
    fun aLogoMustBeASmallJpegOrPngAndAGoodOneOpensTheCrop() = runComposeUiTest {
        show(Server())
        waitUntil(timeoutMillis = 5_000) { shown("Paris Club") }
        tap("Paris Club", last = false)
        waitUntil(timeoutMillis = 5_000) { shown("Edit organization") }

        tap("Choose logo")
        waitUntil(timeoutMillis = 5_000) { shown("Logo must be a JPEG or PNG image.") }
        tap("OK")

        nextImage = SelectedImage("logo.png", "image/png", ByteArray(2 * 1024 * 1024 + 1))
        tap("Choose logo")
        waitUntil(timeoutMillis = 5_000) { shown("Logo must be 2 MB or smaller.") }
        tap("OK")

        nextImage = SelectedImage("logo.png", "image/png", ByteArray(16))
        tap("Choose logo")
        waitUntil(timeoutMillis = 5_000) { shown("Crop the logo") }
    }

    @Test
    fun theColorIsChosenInItsDialog() = runComposeUiTest {
        show(Server())
        waitUntil(timeoutMillis = 5_000) { shown("Paris Club") }
        tap("Paris Club", last = false)
        waitUntil(timeoutMillis = 5_000) { shown("Edit organization") }

        clock += 1.seconds
        onAllNodes(hasContentDescription("Choose the color") and hasClickAction()).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText("OK") and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }
        tap("OK")
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText("OK") and hasClickAction()).fetchSemanticsNodes().isEmpty() }
    }
}
