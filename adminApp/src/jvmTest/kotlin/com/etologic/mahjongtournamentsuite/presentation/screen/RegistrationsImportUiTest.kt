package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.RegistrationImport
import com.etologic.mahjongtournamentsuite.domain.model.RegistrationImportRow
import com.etologic.mahjongtournamentsuite.domain.repository.RegistrationRepository
import com.etologic.mahjongtournamentsuite.presentation.components.LocalPressTimeSource
import com.etologic.mahjongtournamentsuite.presentation.platform.LocalPickedSpreadsheetForTests
import com.etologic.mahjongtournamentsuite.presentation.platform.SelectedSpreadsheet
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

/** The registrations of the screenshots, with an import of the Google Form that reads two rows. */
private class ImportingRegistrations : ScreenshotRegistrationRepository() {
    val calls = mutableListOf<String>()

    private val read = RegistrationImport(
        rows = listOf(
            RegistrationImportRow(2, "10990001", "Ada", "Lovelace", "ada@example.test", "GB", null, "", null, null, false, emptyList()),
            RegistrationImportRow(3, "10990002", "Alan", "Turing", "alan@example.test", "GB", null, "", null, null, false, emptyList()),
        ),
        imported = 0,
    )

    override suspend fun previewImport(tournamentId: String, fileName: String, bytes: ByteArray): AppResult<RegistrationImport> {
        calls += "preview:$fileName"
        return AppResult.Success(read)
    }

    override suspend fun applyImport(tournamentId: String, fileName: String, bytes: ByteArray): AppResult<RegistrationImport> {
        calls += "apply:$fileName"
        return AppResult.Success(read.copy(imported = 2))
    }
}

/** The import of the Google Form from the registrations screen: the chosen file, its review, and the result. */
@OptIn(ExperimentalTestApi::class)
class RegistrationsImportUiTest {
    private val original = Locale.getDefault()
    private val clock = TestTimeSource()

    @BeforeTest
    fun english() = Locale.setDefault(Locale.ENGLISH)

    @AfterTest
    fun restore() = Locale.setDefault(original)

    private fun ComposeUiTest.show(registrations: ScreenshotRegistrationRepository, picked: () -> SelectedSpreadsheet?) {
        setContent {
            val koin = koinApplication {
                modules(module { single { Logger.withTag("ui-test") } })
                modules(adminModules())
                modules(module { single<RegistrationRepository> { registrations } })
                allowOverride(true)
            }.koin
            KoinContext(koin) {
                CompositionLocalProvider(
                    LocalThemeController provides ThemeController(ThemePreference.Light, false, {}),
                    LocalPressTimeSource provides clock,
                    LocalPickedSpreadsheetForTests provides picked,
                ) {
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

    private fun ComposeUiTest.click(text: String) {
        clock += 1.seconds
        onAllNodes(hasClickAction() and hasText(text))[0].performClick()
        waitForIdle()
    }

    @Test
    fun theChosenFileIsReviewedImportedAndCounted() = runComposeUiTest {
        val registrations = ImportingRegistrations()
        show(registrations) { SelectedSpreadsheet("form.csv", "a,b".encodeToByteArray()) }

        click("Import")
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("2 rows will be imported.").fetchSemanticsNodes().isNotEmpty() }
        click("Import 2")
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("2 people were imported.").fetchSemanticsNodes().isNotEmpty() }
        click("OK")

        assertEquals(listOf("preview:form.csv", "apply:form.csv"), registrations.calls)
        onNodeWithText("2 people were imported.").assertDoesNotExist()
    }

    @Test
    fun aChooserClosedWithoutAFileImportsNothing() = runComposeUiTest {
        val registrations = ImportingRegistrations()
        show(registrations) { null }

        click("Import")

        assertEquals(emptyList<String>(), registrations.calls)
    }
}
