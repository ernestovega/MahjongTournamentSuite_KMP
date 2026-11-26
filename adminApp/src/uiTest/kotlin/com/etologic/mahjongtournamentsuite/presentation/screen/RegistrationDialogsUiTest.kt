package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import com.etologic.mahjongtournamentsuite.domain.model.AdminRegistration
import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.MAX_FOOD_RESTRICTIONS_LENGTH
import com.etologic.mahjongtournamentsuite.domain.model.MAX_REGISTRATION_TEAM_NAME_LENGTH
import com.etologic.mahjongtournamentsuite.domain.model.Player
import com.etologic.mahjongtournamentsuite.domain.model.RegistrationImport
import com.etologic.mahjongtournamentsuite.domain.model.RegistrationImportRow
import com.etologic.mahjongtournamentsuite.domain.model.RegistrationStatus
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import kotlinx.coroutines.CompletableDeferred
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The dialog that adds or changes a registration, and the dialog that imports the answers of a Google Form. */
@OptIn(ExperimentalTestApi::class)
class RegistrationDialogsUiTest {
    private val ana = Player(emaId = "01000001", firstName = "Ana", lastName = "Lopez", country = "ES")
    private val ben = Player(emaId = "02000002", firstName = "Ben", lastName = "Smith", country = "GB")

    private data class Saved(val emaId: String, val email: String, val team: String, val food: String, val paid: Boolean, val notify: Boolean)

    private class EditHost {
        var saved: Saved? = null
        var dismissed = 0
    }

    private fun ComposeUiTest.showEdit(
        registration: AdminRegistration? = null,
        isTeams: Boolean = false,
        teamName: String = "",
    ): EditHost {
        val host = EditHost()
        setContent {
            MtsTheme(useDarkTheme = false) {
                RegistrationEditDialog(
                    title = if (registration == null) "Add a person" else "Change the registration",
                    registration = registration,
                    registry = listOf(ana, ben),
                    isTeams = isTeams,
                    teamName = teamName,
                    onSave = { emaId, email, team, food, paid, notify -> host.saved = Saved(emaId, email, team, food, paid, notify) },
                    onDismiss = { host.dismissed++ },
                )
            }
        }
        waitForIdle()
        return host
    }

    private fun ComposeUiTest.button(text: String) = onNode(hasClickAction() and hasText(text))
    private fun ComposeUiTest.field(label: String) = onNode(hasSetTextAction() and hasText(label))
    private fun ComposeUiTest.count(text: String) = onAllNodesWithText(text).fetchSemanticsNodes().size

    private fun ComposeUiTest.pickInTheRegistry(player: Player) {
        onNodeWithText("Select EMA player").assertExists()
        onNode(hasClickAction() and hasText(player.emaId, substring = true)).performClick()
        waitForIdle()
        onNodeWithText("Select EMA player").assertDoesNotExist()
    }

    private fun registration(problems: List<String> = emptyList(), fileEmaId: String? = null) = AdminRegistration(
        emaId = if (problems.isEmpty()) "09000009" else "row-3",
        name = "ZOE REG",
        country = "FR",
        status = RegistrationStatus.CONFIRMED,
        position = 1,
        email = "zoe@example.test",
        paid = true,
        foodRestrictions = "Vegan",
        problems = problems,
        fileEmaId = fileEmaId,
    )

    @Test
    fun toAddAPersonTheEditorPicksTheEmaPlayerFirst() = runComposeUiTest {
        showEdit()
        button("Save").assertIsNotEnabled()
        button("Choose the EMA player").performClick()
        waitForIdle()
        pickInTheRegistry(ana)
        button("ANA LOPEZ · 01000001").assertExists()
        button("Save").assertIsEnabled()
    }

    @Test
    fun savingANewPersonGivesEveryFieldOfTheForm() = runComposeUiTest {
        val host = showEdit(isTeams = true, teamName = "Dragons")
        button("Choose the EMA player").performClick()
        waitForIdle()
        pickInTheRegistry(ben)
        field("Email (optional)").performTextInput(" ben@example.test ")
        field("Team (optional)").performTextReplacement("Tigers")
        field("Food restrictions").performTextInput("No nuts")
        button("Paid").performClick()
        button("Tell the person by email").performClick()
        waitForIdle()
        button("Save").performClick()
        waitForIdle()
        assertEquals(Saved("02000002", "ben@example.test", "Tigers", "No nuts", paid = true, notify = false), host.saved)
    }

    @Test
    fun theTeamAndTheFoodFieldsKeepTheirLimits() = runComposeUiTest {
        val host = showEdit(isTeams = true)
        button("Choose the EMA player").performClick()
        waitForIdle()
        pickInTheRegistry(ana)
        field("Team (optional)").performTextInput("T".repeat(MAX_REGISTRATION_TEAM_NAME_LENGTH + 5))
        field("Food restrictions").performTextInput("F".repeat(MAX_FOOD_RESTRICTIONS_LENGTH + 5))
        button("Save").performClick()
        waitForIdle()
        assertEquals(MAX_REGISTRATION_TEAM_NAME_LENGTH, host.saved?.team?.length)
        assertEquals(MAX_FOOD_RESTRICTIONS_LENGTH, host.saved?.food?.length)
        assertEquals(true, host.saved?.notify)
    }

    @Test
    fun withoutTeamsTheFormHasNoTeamField() = runComposeUiTest {
        showEdit(isTeams = false)
        assertEquals(0, count("Team (optional)"))
    }

    @Test
    fun changingARegistrationKeepsItsPersonAndHasNoEmailToSend() = runComposeUiTest {
        val host = showEdit(registration = registration(), teamName = "Dragons")
        onNodeWithText("ZOE REG · 09000009").assertExists()
        assertEquals(0, count("Choose the EMA player"))
        assertEquals(0, count("Tell the person by email"))
        assertEquals(0, count("Choose the right person"))
        field("Email").performTextReplacement("zoe@other.test")
        button("Save").assertIsEnabled().performClick()
        waitForIdle()
        assertEquals(Saved("09000009", "zoe@other.test", "Dragons", "Vegan", paid = true, notify = true), host.saved)
    }

    @Test
    fun aRegistrationWithAnEmaProblemShowsItAndLetsTheEditorChooseTheRightPerson() = runComposeUiTest {
        val host = showEdit(registration = registration(problems = listOf("EMA_NOT_FOUND", "INVALID_EMAIL"), fileEmaId = "1234"))
        onNodeWithText("ZOE REG · 1234").assertExists()
        onNodeWithText("The EMA number is not in the EMA registry.").assertExists()
        // Only the problems of the EMA number show here: the email has its own field.
        assertEquals(0, count("The email is not valid."))
        onNodeWithText("In the file: 1234").assertExists()

        onNodeWithTag("registration-choose-ema").performClick()
        waitForIdle()
        pickInTheRegistry(ana)
        onNodeWithText("ANA LOPEZ · 01000001").assertExists()
        assertEquals(0, count("The EMA number is not in the EMA registry."))
        assertEquals(0, count("In the file: 1234"))

        button("Save").performClick()
        waitForIdle()
        assertEquals("01000001", host.saved?.emaId)
    }

    @Test
    fun anEmaProblemWithoutTheTextOfTheFileShowsOnlyTheProblem() = runComposeUiTest {
        showEdit(registration = registration(problems = listOf("DUPLICATE_EMA")))
        onNodeWithText("ZOE REG · row-3").assertExists()
        onNodeWithText("The EMA number is twice in the file.").assertExists()
        assertEquals(0, count("In the file:", substring = true))
    }

    private fun ComposeUiTest.count(text: String, substring: Boolean) = onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().size

    @Test
    fun cancelClosesTheFormWithoutSaving() = runComposeUiTest {
        val host = showEdit(registration = registration())
        button("Cancel").performClick()
        waitForIdle()
        assertEquals(1, host.dismissed)
        assertNull(host.saved)
    }

    private fun row(
        line: Int,
        emaId: String,
        registryName: String? = null,
        team: String? = null,
        problems: List<String> = emptyList(),
        sharedEmail: Boolean = false,
    ) = RegistrationImportRow(
        line = line,
        emaId = emaId,
        firstName = "First$line",
        lastName = "Last$line",
        email = "p$line@example.test",
        country = "ES",
        team = team,
        foodRestrictions = "",
        registryName = registryName,
        registryCountry = null,
        sharedEmail = sharedEmail,
        problems = problems,
    )

    private class ImportHost {
        val done = mutableListOf<Int?>()
        var previews = 0
        var applies = 0
    }

    private fun ComposeUiTest.showImport(
        preview: suspend () -> AppResult<RegistrationImport>,
        apply: suspend () -> AppResult<RegistrationImport> = { AppResult.Failure(AppError.Network) },
    ): ImportHost {
        val host = ImportHost()
        val file = "form.csv" to byteArrayOf(1, 2)
        setContent {
            MtsTheme(useDarkTheme = false) {
                RegistrationImportDialog(
                    file = file,
                    preview = { name, bytes ->
                        assertEquals("form.csv", name)
                        assertEquals(2, bytes.size)
                        host.previews++
                        preview()
                    },
                    apply = { _, _ ->
                        host.applies++
                        apply()
                    },
                    onDone = { host.done += it },
                )
            }
        }
        waitForIdle()
        return host
    }

    private val twoRows = RegistrationImport(
        rows = listOf(
            row(2, "01000001", registryName = "ANA LOPEZ", team = "Dragons"),
            row(3, "row-3", problems = listOf("INVALID_EMA", "SOMETHING_NEW"), sharedEmail = true),
        ),
        imported = 0,
        replaced = 5,
    )

    @Test
    fun thePreviewListsTheRowsTheirProblemsAndWhatTheImportReplaces() = runComposeUiTest {
        val host = showImport(preview = { AppResult.Success(twoRows) })
        onNodeWithText("Import the Google Form").assertExists()
        onNodeWithTag("registrations-import-replaces").assertExists()
        onNodeWithText("The file replaces the current list: the 5 people registered now are removed, without an email.").assertExists()
        onNodeWithText("2 rows will be imported.").assertExists()
        onNodeWithText("The rows with problems are imported too, marked in the list, so you can fix them there.").assertExists()
        onNodeWithText("2. ANA LOPEZ · 01000001 · Dragons").assertExists()
        onNodeWithText("3. First3 Last3 · row-3").assertExists()
        onNodeWithText("The EMA number is not valid.").assertExists()
        // A code the app does not know yet shows as it came.
        onNodeWithText("SOMETHING_NEW").assertExists()
        onNodeWithText("Another person uses this email. One account cannot share it.").assertExists()
        assertEquals(1, host.previews)
    }

    @Test
    fun aCleanFileForAnEmptyListSaysNothingOfReplacingOrProblems() = runComposeUiTest {
        showImport(preview = { AppResult.Success(RegistrationImport(rows = listOf(row(2, "01000001", registryName = "ANA LOPEZ")), imported = 0)) })
        onNodeWithText("1 row will be imported.").assertExists()
        onNodeWithTag("registrations-import-replaces").assertDoesNotExist()
        assertEquals(0, count("The rows with problems are imported too, marked in the list, so you can fix them there."))
        button("Import 1").assertIsEnabled()
    }

    @Test
    fun importingAppliesTheFileAndGivesTheCount() = runComposeUiTest {
        val host = showImport(
            preview = { AppResult.Success(twoRows) },
            apply = { AppResult.Success(twoRows.copy(imported = 2)) },
        )
        button("Import 2").performClick()
        waitForIdle()
        assertEquals(1, host.applies)
        assertEquals(listOf<Int?>(2), host.done)
    }

    @Test
    fun aFailedImportShowsTheErrorAndKeepsThePreview() = runComposeUiTest {
        val host = showImport(preview = { AppResult.Success(twoRows) })
        button("Import 2").performClick()
        waitForIdle()
        onNodeWithText("Network request failed.").assertExists()
        button("OK").performClick()
        waitForIdle()
        onNodeWithText("Network request failed.").assertDoesNotExist()
        onNodeWithText("2 rows will be imported.").assertExists()
        assertEquals(emptyList(), host.done)
    }

    @Test
    fun anUnreadableFileShowsTheErrorAndClosesTheImport() = runComposeUiTest {
        val host = showImport(preview = { AppResult.Failure(AppError.Network) })
        onNodeWithText("Network request failed.").assertExists()
        assertEquals(0, count("Import the Google Form"))
        button("OK").performClick()
        waitForIdle()
        assertEquals(listOf<Int?>(null), host.done)
    }

    @Test
    fun aFileWithoutRowsHasNothingToImport() = runComposeUiTest {
        showImport(preview = { AppResult.Success(RegistrationImport(rows = emptyList(), imported = 0)) })
        onNodeWithText("0 rows will be imported.").assertExists()
        assertEquals(0, count("Import 0"))
    }

    @Test
    fun whileTheFileIsReadCancelIsOffAndAfterwardsItClosesTheImport() = runComposeUiTest {
        val gate = CompletableDeferred<AppResult<RegistrationImport>>()
        val host = showImport(preview = { gate.await() })
        button("Cancel").assertIsNotEnabled()
        gate.complete(AppResult.Success(twoRows))
        waitForIdle()
        button("Cancel").assertIsEnabled().performClick()
        waitForIdle()
        assertEquals(listOf<Int?>(null), host.done)
        assertEquals(0, host.applies)
    }
}
