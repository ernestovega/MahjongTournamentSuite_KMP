package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.domain.model.displayName
import com.etologic.mahjongtournamentsuite.presentation.screenshots.adminModules
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import com.etologic.mahjongtournamentsuite.screenshots.fake.ScreenshotData
import org.koin.compose.KoinContext
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test

/**
 * The rows of an import with problems (docs/registrations.md, "Import"): they are in the list, marked with what is
 * wrong, and the "With problems" filter shows only them. The fake list has an invalid email in the second row and an
 * EMA number that is not in the registry in the fourth.
 */
@OptIn(ExperimentalTestApi::class)
class RegistrationsProblemsUiTest {
    private val people = ScreenshotData.players

    private fun ComposeUiTest.show() {
        setContent {
            val koin = koinApplication {
                modules(module { single { Logger.withTag("ui-test") } })
                modules(adminModules())
            }.koin
            KoinContext(koin) {
                MtsTheme(useDarkTheme = false) {
                    val navController = rememberNavController()
                    NavHost(navController, startDestination = "screen") {
                        composable("screen") { RegistrationsScreen(navController, ScreenshotData.UPCOMING) }
                    }
                }
            }
        }
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("With problems", substring = true).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun ComposeUiTest.shown(emaId: String) = onAllNodesWithText(emaId, substring = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun theRowsWithProblemsSayWhatIsWrong() = runComposeUiTest {
        show()

        onNodeWithText("With problems (2)").assertExists()
        // The filter brings both rows to the top of the list.
        onNodeWithTag("registrations-filter-problems").performClick()
        waitForIdle()
        onNodeWithText("The email is not valid.").assertExists()
        onNodeWithText("The EMA number is not in the EMA registry.").assertExists()
    }

    @Test
    fun theFilterShowsOnlyTheRowsWithProblemsAndAllShowsEveryoneAgain() = runComposeUiTest {
        show()
        check(shown(people[0].emaId)) { "The first person is not on the screen before the filter." }

        onNodeWithTag("registrations-filter-problems").performClick()
        waitForIdle()

        onNodeWithTag("registrations-filter-problems").assertIsSelected()
        check(!shown(people[0].emaId)) { "A person without problems is still in the list." }
        check(shown(people[1].emaId) && shown(people[3].emaId)) { "A person with problems left the list." }
        onAllNodesWithText("The email is not valid.").assertCountEquals(1)

        onNodeWithText("All", substring = true).performClick()
        waitForIdle()

        check(shown(people[0].emaId)) { "All did not bring back the people without problems." }
    }

    @Test
    fun removingAPersonWithAPlaceSaysWhoOfTheWaitingListGetsIt() = runComposeUiTest {
        show()
        // The fake list has 14 people with a place of 16 and two on the waiting list, the 15th person first.
        val leaving = people[0]
        onNodeWithContentDescription("More actions for ${leaving.firstName} ${leaving.lastName}".trim()).performClick()
        waitForIdle()
        onNodeWithText("Remove").performClick()
        waitForIdle()
        onNodeWithTag("registration-remove-next")
            .assertTextContains(people[14].displayName, substring = true)
            .assertTextContains("the first of the waiting list, who gets an email", substring = true)
    }
}
