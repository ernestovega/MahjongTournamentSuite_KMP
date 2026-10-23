package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.domain.repository.TournamentRepository
import com.etologic.mahjongtournamentsuite.domain.usecase.GenerateTournamentScheduleBruteForceParallelUseCase
import com.etologic.mahjongtournamentsuite.presentation.presenter.CreateTournamentPresenter
import com.etologic.mahjongtournamentsuite.presentation.presenter.IdCardProofPresenter
import com.etologic.mahjongtournamentsuite.presentation.store.AppMemoryStore
import com.etologic.mahjongtournamentsuite.presentation.theme.LocalThemeController
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import com.etologic.mahjongtournamentsuite.presentation.theme.ThemeController
import com.etologic.mahjongtournamentsuite.presentation.theme.ThemePreference
import org.koin.compose.KoinContext
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test

/** The form marks the required fields and the wrong fields. */
@OptIn(ExperimentalTestApi::class)
class CreateTournamentDialogUiTest {
    private fun ComposeUiTest.showForm() {
        setContent {
            val themeController = ThemeController(ThemePreference.Light, false) {}
            val koin = remember {
                koinApplication {
                    modules(
                        module {
                            single { Logger.withTag("ui-test") }
                            single<TournamentRepository> { FakeTournamentRepository() }
                            single { AppMemoryStore() }
                            factory { GenerateTournamentScheduleBruteForceParallelUseCase() }
                            factory { CreateTournamentPresenter(get(), get(), get()) }
                            factory { IdCardProofPresenter(get()) }
                        },
                    )
                }.koin
            }
            KoinContext(koin) {
                CompositionLocalProvider(LocalThemeController provides themeController) {
                    MtsTheme(useDarkTheme = false) { CreateTournamentDialog(onDismiss = {}, onCreated = {}) }
                }
            }
        }
        waitForIdle()
    }

    @Test
    fun requiredFieldsHaveAStar() = runComposeUiTest {
        showForm()

        // Each required label has a separate star text. The fields without a star are optional.
        onAllNodesWithText(" *").assertCountEquals(GeneralRequiredFieldCount)
        onNodeWithText("Fields marked with * are required.").assertExists()

        onNodeWithText("Branding").performClick()
        waitForIdle()
        onAllNodesWithText(" *").assertCountEquals(BrandingRequiredFieldCount)

        onNodeWithText("Venue").performClick()
        waitForIdle()
        onAllNodesWithText(" *").assertCountEquals(VenueRequiredFieldCount)

        onNodeWithText("Schedule").performClick()
        waitForIdle()
        onAllNodesWithText(" *").assertCountEquals(ScheduleRequiredFieldCount)
    }

    @Test
    fun emptyRequiredFieldsAreMarkedWrongWithAShortText() = runComposeUiTest {
        showForm()

        onNodeWithText("Create").performClick()
        waitForIdle()

        onNodeWithText("Name is required.").assertExists()
        onNodeWithText("Use 1 to 10 characters.").assertExists()

        onNodeWithText("Venue").performClick()
        waitForIdle()
        onNodeWithText("Select a host country.").assertExists()
        onNodeWithText("City is required.").assertExists()
        onNodeWithText("Add a Google Maps link, or type the venue name and the address.").assertExists()
        onNodeWithText("Contact email is required.").assertExists()
    }

    @Test
    fun aWrongFieldOnAnotherTabOpensThatTab() = runComposeUiTest {
        showForm()
        onNodeWithText("Schedule").performClick()
        waitForIdle()

        onNodeWithText("Create").performClick()
        waitForIdle()

        // The first wrong field is on the General tab, so the dialog goes back to it.
        onNodeWithText("Name is required.").assertExists()
    }

    @Test
    fun anInvalidContactEmailIsMarkedWrong() = runComposeUiTest {
        showForm()
        onNodeWithText("Venue").performClick()
        waitForIdle()
        onNodeWithText("Contact email").performTextInput("not an email")

        onNodeWithText("Create").performClick()
        waitForIdle()
        onNodeWithText("Venue").performClick()
        waitForIdle()

        onNodeWithText("Use a valid email address.").assertExists()
    }

    @Test
    fun aWrongFieldIsClearedWhenTheUserTypesInIt() = runComposeUiTest {
        showForm()
        onNodeWithText("Create").performClick()
        waitForIdle()
        onNodeWithText("Name is required.").assertExists()

        onNodeWithText("Name").performTextInput("Open")
        waitForIdle()

        onAllNodesWithText("Name is required.").assertCountEquals(0)
        onNodeWithText("Use 1 to 10 characters.").assertExists()
    }

    @Test
    fun theTimeZoneIsAFieldWithAMenuAndStartsWithTheZoneOfTheDevice() = runComposeUiTest {
        showForm()
        onNodeWithText("Venue").performClick()
        waitForIdle()

        onNodeWithText("Time zone").assertExists()
        onNodeWithText(kotlinx.datetime.TimeZone.currentSystemDefault().id).assertExists()
    }

    private companion object {
        /** Name, short name, players, rounds, match duration. */
        const val GeneralRequiredFieldCount = 5

        /** Map link, country, city, contact email, time zone. */
        const val VenueRequiredFieldCount = 5

        /** Color. */
        const val BrandingRequiredFieldCount = 1

        /** From, to. */
        const val ScheduleRequiredFieldCount = 2
    }
}
