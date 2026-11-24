package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.domain.model.GlobalUserRole
import com.etologic.mahjongtournamentsuite.domain.repository.TournamentRepository
import com.etologic.mahjongtournamentsuite.domain.usecase.DrawTournamentScheduleUseCase
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
import kotlin.test.assertTrue

/** The form marks the required fields and the wrong fields. */
@OptIn(ExperimentalTestApi::class)
class CreateTournamentDialogUiTest {
    private val originalLocale = java.util.Locale.getDefault()

    @kotlin.test.AfterTest
    fun restoreLocale() = java.util.Locale.setDefault(originalLocale)

    private fun ComposeUiTest.awaitText(text: String) = waitUntil(timeoutMillis = 5_000) { onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    private fun ComposeUiTest.showForm() {
        setContent {
            val themeController = ThemeController(ThemePreference.Light, false, {})
            val koin = remember {
                koinApplication {
                    modules(
                        module {
                            single { Logger.withTag("ui-test") }
                            single<TournamentRepository> { FakeTournamentRepository() }
                            single { AppMemoryStore() }
                            factory { DrawTournamentScheduleUseCase() }
                            factory { CreateTournamentPresenter(get(), get(), get(), adminRepository = FakeAdminRepository(GlobalUserRole.SUPER_ADMIN)) }
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

        onNodeWithText("Organizers").performClick()
        waitForIdle()
        onAllNodesWithText(" *").assertCountEquals(OrganizersRequiredFieldCount)

        onNodeWithText("Venue").performClick()
        waitForIdle()
        onAllNodesWithText(" *").assertCountEquals(VenueRequiredFieldCount)

        onNodeWithText("Schedule").performClick()
        waitForIdle()
        onAllNodesWithText(" *").assertCountEquals(ScheduleRequiredFieldCount)
    }

    /** With registrations open there is no draw now, so "How does the draw work?" is not shown until the switch is off again. */
    @Test
    fun theDrawButtonHidesWhenRegistrationsAreOpen() = runComposeUiTest {
        showForm()
        val registrationSwitch = onNode(isToggleable() and hasText("Open registrations"))

        onNodeWithTag("drawExplanationButton").assertExists()

        registrationSwitch.performClick()
        waitForIdle()
        onNodeWithTag("drawExplanationButton").assertDoesNotExist()

        registrationSwitch.performClick()
        waitForIdle()
        onNodeWithTag("drawExplanationButton").assertExists()
    }

    /** The explanation shows the first way and, after all of it, the second way with its own diagram. */
    @Test
    fun theExplanationShowsBothWaysToDrawInOrder() = runComposeUiTest {
        showForm()
        onNodeWithTag("drawExplanationButton").performClick()
        waitForIdle()
        onNodeWithText("First way: seat by seat").assertExists()
        onNodeWithContentDescription("Flowchart of the draw").assertExists()
        onNodeWithTag("drawBackupTitle").performScrollTo().assertIsDisplayed().assertTextEquals("Second way: by swaps")
        onNodeWithContentDescription("Flowchart of the second way to draw").performScrollTo().assertIsDisplayed()
        onNodeWithText("How long each way waits").performScrollTo().assertIsDisplayed()
        val first = onNodeWithText("First way: seat by seat").fetchSemanticsNode().positionInRoot.y
        val second = onNodeWithTag("drawBackupTitle").fetchSemanticsNode().positionInRoot.y
        assertTrue(second > first, "The second way comes after the first")
    }

    /** After the second way, the third case: more rounds than fit, in blocks, with its own diagram. */
    @Test
    fun theExplanationEndsWithTheBlocks() = runComposeUiTest {
        showForm()
        onNodeWithTag("drawExplanationButton").performClick()
        waitForIdle()
        onNodeWithTag("drawBlocksTitle").performScrollTo().assertIsDisplayed()
            .assertTextEquals("Third case: more rounds than fit without repeats")
        onNodeWithContentDescription("Flowchart of the draw in blocks").performScrollTo().assertIsDisplayed()
        val second = onNodeWithTag("drawBackupTitle").fetchSemanticsNode().positionInRoot.y
        val third = onNodeWithTag("drawBlocksTitle").fetchSemanticsNode().positionInRoot.y
        assertTrue(third > second, "The blocks come after the second way")
    }

    /** Under the players and rounds: nothing while the rounds fit, a notice with how many fit when they do not. */
    @Test
    fun moreRoundsThanFitShowANotice() = runComposeUiTest {
        showForm()
        val players = onNode(hasSetTextAction() and hasText("Players", substring = true))
        val rounds = onNode(hasSetTextAction() and hasText("Rounds", substring = true))
        // Teams are on by default: 16 players in teams fit 4 rounds.
        players.performTextReplacement("16")
        rounds.performTextReplacement("4")
        waitForIdle()
        onNodeWithTag("createRoundsNotice").assertDoesNotExist()

        rounds.performTextReplacement("5")
        waitForIdle()
        onNodeWithTag("createRoundsNotice").assertTextEquals(
            "With 16 players, 4 rounds fit without anyone meeting the same rival twice. With 5 rounds the draw goes in " +
                "blocks, and some players will meet a rival again, but nobody more than 2 times.",
        )

        // Without teams they fit 5 rounds; 8 are a block of 5 and one of 3.
        onNode(isToggleable() and hasText("Teams")).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag("createRoundsNotice").assertDoesNotExist()
        rounds.performTextReplacement("8")
        waitForIdle()
        onNodeWithTag("createRoundsNotice").assertTextEquals(
            "With 16 players, 5 rounds fit without anyone meeting the same rival twice. With 8 rounds the draw goes in " +
                "blocks, and some players will meet a rival again, but nobody more than 2 times.",
        )

        // 32 players fit 10 rounds, but the draw is sure of 7.
        players.performTextReplacement("32")
        waitForIdle()
        onNodeWithTag("createRoundsNotice").assertTextEquals(
            "With 32 players, the draw is sure to find 7 rounds without anyone meeting the same rival twice. With 8 it " +
                "will try, and if it does not make it in time, some players will meet a rival again, but nobody more than 2 times.",
        )

        // With registrations open there is no draw now.
        onNode(isToggleable() and hasText("Open registrations")).performClick()
        waitForIdle()
        onNodeWithTag("createRoundsNotice").assertDoesNotExist()
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
        onNodeWithText(com.etologic.mahjongtournamentsuite.presentation.components.timeZoneLabel(com.etologic.mahjongtournamentsuite.presentation.components.deviceTimeZoneId())).assertExists()
    }

    private companion object {
        /** Name, short name, players, rounds, match duration. */
        const val GeneralRequiredFieldCount = 5

        /** Map link, country, city, contact email, time zone. */
        const val VenueRequiredFieldCount = 5

        /** The main organizer. */
        const val OrganizersRequiredFieldCount = 1

        /** The 7 rounds of a new tournament. */
        const val DefaultRoundCount = 7

        /** From, to, and the date and the start time of each round. */
        const val ScheduleRequiredFieldCount = 2 + 2 * DefaultRoundCount
    }

    @Test
    fun theFormIsInSpanishWhenTheLanguageIsSpanish() = runComposeUiTest {
        java.util.Locale.setDefault(java.util.Locale.forLanguageTag("es"))
        showForm()

        awaitText("Los campos marcados con * son obligatorios.")
        onNodeWithText("Crea las rondas, las mesas y los jugadores automáticamente.").assertExists()
        onNodeWithText("General").assertExists()
        onNodeWithText("Sede").assertExists()
        onNodeWithText("Organizadores").assertExists()
        onNodeWithText("Horario").assertExists()
        onNodeWithText("Crear").assertExists()
    }

    @Test
    fun theValidationMessagesAreInSpanish() = runComposeUiTest {
        java.util.Locale.setDefault(java.util.Locale.forLanguageTag("es"))
        showForm()
        awaitText("Crear")

        onNodeWithText("Crear").performClick()
        awaitText("El nombre es obligatorio.")
        onNodeWithText("Usa de 1 a 10 caracteres.").assertExists()

        onNodeWithText("Sede").performClick()
        awaitText("Elige un país anfitrión.")
        onNodeWithText("La ciudad es obligatoria.").assertExists()
        onNodeWithText("Añade un enlace de Google Maps, o escribe el nombre de la sede y la dirección.").assertExists()
        onNodeWithText("El correo electrónico de contacto es obligatorio.").assertExists()
    }
}
