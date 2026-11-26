package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import com.etologic.mahjongtournamentsuite.domain.model.AdminStatus
import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.GlobalUserRole
import com.etologic.mahjongtournamentsuite.domain.model.ManagedUser
import com.etologic.mahjongtournamentsuite.domain.model.Organization
import com.etologic.mahjongtournamentsuite.domain.model.Player
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.TournamentAssignment
import com.etologic.mahjongtournamentsuite.presentation.components.LocalPressTimeSource
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

/** The dialog that adds or changes an account, its tournament picker, and the dialogs that delete or disable an account. */
@OptIn(ExperimentalTestApi::class)
class ManageUserDialogUiTest {
    private val superAdmin = AdminStatus("me", GlobalUserRole.SUPER_ADMIN)
    private val clubAdmin = AdminStatus("me", GlobalUserRole.CLUB_ADMIN, adminOrganizations = setOf("org-a"))
    private val editor = AdminStatus("me", GlobalUserRole.EDITOR)

    private val clubA = Organization(id = "org-a", name = "Club A")
    private val clubB = Organization(id = "org-b", name = "Club B")

    private val open = Tournament(id = "t1", name = "Open Madrid", isTeams = false, numPlayers = 8, numRounds = 1, organizerIds = listOf("org-a"))
    private val cup = Tournament(id = "t2", name = "Cup Uppsala", isTeams = false, numPlayers = 8, numRounds = 1, organizerIds = listOf("org-b"))
    private val league = Tournament(id = "t3", name = "League Lyon", isTeams = false, numPlayers = 8, numRounds = 1, organizerIds = listOf("org-b"))

    private val ana = Player(emaId = "01000001", firstName = "Ana", lastName = "Lopez", country = "ES")
    private val ben = Player(emaId = "02000002", firstName = "Ben", lastName = "Smith", country = "GB")

    private val other = ManagedUser("u-other", "taken@example.test", "Other", GlobalUserRole.PLAYER, false, emptyList(), emaId = ben.emaId)
    private val ed = ManagedUser(
        uid = "u-ed",
        email = "ed@example.test",
        alias = "Ed",
        role = GlobalUserRole.EDITOR,
        disabled = false,
        tournamentAssignments = listOf(TournamentAssignment(open.id, open.name, open.organizerIds)),
        emaId = "05000005",
        canManage = true,
    )

    /** Buttons ignore a second press within 500 ms: the tests move this clock instead of waiting. */
    private val pressClock = TestTimeSource()

    private class Host {
        val saved = mutableListOf<AccountEdit>()
        val calls = mutableListOf<String>()
        var dismissed = 0
    }

    private fun ComposeUiTest.show(
        original: ManagedUser? = null,
        access: AdminStatus = superAdmin,
        isSelf: Boolean = false,
        saving: Boolean = false,
        tournaments: List<Tournament> = listOf(open, cup, league),
        removePhoto: suspend (ManagedUser) -> AppResult<ManagedUser> = { AppResult.Failure(AppError.Network) },
    ): Host {
        val host = Host()
        setContent {
            CompositionLocalProvider(LocalPressTimeSource provides pressClock) {
                MtsTheme(useDarkTheme = false) {
                    ManageUserDialog(
                        organizations = listOf(clubA, clubB),
                        original = original,
                        tournaments = tournaments,
                        accounts = listOfNotNull(other, original),
                        emaPlayers = listOf(ana, ben),
                        isSelf = isSelf,
                        access = access,
                        saving = saving,
                        onDismiss = { host.dismissed++ },
                        onUploadPhoto = { _, _, _ -> AppResult.Failure(AppError.Network) },
                        onRemovePhoto = {
                            host.calls += "removePhoto ${it.uid}"
                            removePhoto(it)
                        },
                        onSave = { host.saved += it },
                        onResetPassword = { host.calls += "reset $it" },
                        onChangeDisabled = { host.calls += "disabled ${it.uid}" },
                        onDelete = { host.calls += "delete ${it.uid}" },
                        onRemoveFromMyOrganizations = { host.calls += "remove ${it.uid}" },
                        onEditPermissions = { host.calls += "permissions ${it.uid}" },
                    )
                }
            }
        }
        waitForIdle()
        return host
    }

    private fun ComposeUiTest.button(text: String) = onNode(hasClickAction() and hasText(text))
    private fun ComposeUiTest.field(label: String) = onNode(hasSetTextAction() and hasText(label))
    private fun ComposeUiTest.count(text: String, substring: Boolean = false) = onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().size

    private fun ComposeUiTest.chooseRole(label: String) {
        onNodeWithText("Global role").performClick()
        waitForIdle()
        onAllNodesWithText(label).onLast().performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.typeEmails(email: String, confirm: String = email) {
        field("Email").performTextInput(email)
        field("Confirm email").performTextInput(confirm)
        waitForIdle()
    }

    private fun ComposeUiTest.openTournamentPicker(summary: String) {
        button(summary).performScrollTo().performClick()
        waitForIdle()
        onNodeWithText("Assign tournaments").assertExists()
    }

    private fun ComposeUiTest.closeTournamentPicker() {
        button("Close").performClick()
        waitForIdle()
        onNodeWithText("Assign tournaments").assertDoesNotExist()
    }

    @Test
    fun aNewEditorNeedsMatchingEmailsAndATournament() = runComposeUiTest {
        val host = show()
        onNodeWithText("Add account").assertExists()
        onNodeWithText("Tournament assignments").assertExists()
        onNodeWithText("An editor needs at least one tournament.").assertExists()

        typeEmails(" new@example.test ", "other@example.test")
        onNodeWithText("The email addresses do not match.").assertExists()
        field("Confirm email").performTextReplacement("NEW@example.test")
        waitForIdle()
        assertEquals(0, count("The email addresses do not match."))
        button("Save and Send password-reset email").assertIsNotEnabled()

        openTournamentPicker("No tournaments assigned")
        onNodeWithText("0 selected").assertExists()
        onNodeWithText("Cup Uppsala").performClick()
        waitForIdle()
        onNodeWithText("1 selected").assertExists()
        closeTournamentPicker()
        assertEquals(0, count("An editor needs at least one tournament."))

        field("Alias").performTextInput("  Newbie ")
        button("Save and Send password-reset email").assertIsEnabled().performClick()
        waitForIdle()
        assertEquals(
            listOf(
                AccountEdit(
                    email = "new@example.test",
                    alias = "Newbie",
                    role = GlobalUserRole.EDITOR,
                    assignments = listOf(TournamentAssignment(cup.id, cup.name, cup.organizerIds)),
                    emaId = null,
                    adminOrganizations = emptySet(),
                ),
            ),
            host.saved,
        )
    }

    @Test
    fun theTournamentPickerSearchesFiltersSelectsAndClears() = runComposeUiTest {
        show()
        openTournamentPicker("No tournaments assigned")
        onNodeWithText("Selected only").performClick()
        waitForIdle()
        onNodeWithText("No tournaments are selected.").assertExists()
        button("Clear all").assertIsNotEnabled()
        onNodeWithText("Selected only").performClick()
        waitForIdle()

        field("Search").performTextInput("zzz")
        waitForIdle()
        onNodeWithText("No tournaments match the search.").assertExists()
        button("Select results").assertIsNotEnabled()
        field("Search").performTextReplacement("")
        waitForIdle()

        button("Select results").performClick()
        waitForIdle()
        onNodeWithText("3 selected").assertExists()
        closeTournamentPicker()
        onNodeWithText("3 tournaments selected").assertExists()
        onNodeWithText("Open Madrid, Cup Uppsala · +1 more").assertExists()

        openTournamentPicker("3 tournaments selected")
        onNodeWithText("Cup Uppsala").performClick()
        waitForIdle()
        closeTournamentPicker()
        onNodeWithText("Open Madrid, League Lyon").assertExists()

        openTournamentPicker("2 tournaments selected")
        button("Clear all").performClick()
        waitForIdle()
        onNodeWithText("0 selected").assertExists()
        closeTournamentPicker()
        onNodeWithText("No tournaments assigned").assertExists()
    }

    @Test
    fun anEditorWithoutTournamentsToChooseFromSaysSo() = runComposeUiTest {
        show(tournaments = emptyList())
        onNodeWithText("No tournaments are available.").assertExists()
    }

    @Test
    fun aPlayerAccountNeedsAFreeEmaIdAndAFreeEmail() = runComposeUiTest {
        val host = show()
        chooseRole("Player")
        onNodeWithText("A player account needs an EMA ID.").assertExists()
        assertEquals(0, count("Tournament assignments"))

        typeEmails("taken@example.test")
        onNodeWithText("Another account already uses this email.").assertExists()
        field("Email").performTextReplacement("free@example.test")
        field("Confirm email").performTextReplacement("free@example.test")

        // Only letters and digits stay in the EMA ID.
        field("EMA ID").performTextInput("02-000 002")
        waitForIdle()
        onNodeWithText("Another User already uses this EMA ID: taken@example.test.").assertExists()
        button("Save and Send password-reset email").assertIsNotEnabled()

        field("EMA ID").performTextReplacement("01000001")
        waitForIdle()
        onNodeWithText("EMA Player: ANA LOPEZ").assertExists()
        button("Save and Send password-reset email").assertIsEnabled().performClick()
        waitForIdle()
        assertEquals(GlobalUserRole.PLAYER, host.saved.single().role)
        assertEquals("01000001", host.saved.single().emaId)
        assertEquals(emptyList(), host.saved.single().assignments)
    }

    @Test
    fun theEmaSearchFillsTheEmaId() = runComposeUiTest {
        show()
        onNode(hasClickAction() and hasContentDescription("Search EMA players")).performClick()
        waitForIdle()
        onNodeWithText("Select EMA player").assertExists()
        onNode(hasClickAction() and hasText(ana.emaId, substring = true)).performClick()
        waitForIdle()
        onNodeWithText("Select EMA player").assertDoesNotExist()
        onNodeWithText("EMA Player: ANA LOPEZ").assertExists()
    }

    @Test
    fun aClubAdminNeedsAnOrganizationAndIsEditorOnlyInTheTournamentsOfOthers() = runComposeUiTest {
        val host = show()
        typeEmails("club@example.test")
        chooseRole("Club Admin")
        onNodeWithText("Organizations").assertExists()
        button("Save and Send password-reset email").assertIsNotEnabled()

        onNodeWithText("Add an organization").performClick()
        waitForIdle()
        onAllNodesWithText("Club A").onLast().performClick()
        waitForIdle()
        onNode(hasContentDescription("Remove Club A")).assertExists()

        // Open Madrid belongs to Club A, so only the tournaments of Club B need an assignment.
        onNodeWithText("Editor in tournaments of other organizations").assertExists()
        openTournamentPicker("No tournaments assigned")
        assertEquals(0, count("Open Madrid"))
        onNodeWithText("League Lyon").performClick()
        waitForIdle()
        closeTournamentPicker()

        button("Save and Send password-reset email").assertIsEnabled().performClick()
        waitForIdle()
        val edit = host.saved.single()
        assertEquals(GlobalUserRole.CLUB_ADMIN, edit.role)
        assertEquals(setOf("org-a"), edit.adminOrganizations)
        assertEquals(listOf(league.id), edit.assignments.map { it.tournamentId })
    }

    @Test
    fun anExistingEditorKeepsItsDataAndHasTheAccountActions() = runComposeUiTest {
        val host = show(original = ed)
        onNodeWithText("Edit account").assertExists()
        assertEquals(0, count("Confirm email"))
        onNodeWithText("Open Madrid").assertExists()

        button("Send password-reset email").performClick()
        button("Permissions").performClick()
        button("Disable account").performClick()
        button("Delete account").performClick()
        waitForIdle()
        assertEquals(listOf("reset ed@example.test", "permissions u-ed", "disabled u-ed", "delete u-ed"), host.calls)

        button("Save").performClick()
        waitForIdle()
        assertEquals(AccountEdit("ed@example.test", "Ed", GlobalUserRole.EDITOR, ed.tournamentAssignments, "05000005", emptySet()), host.saved.single())
    }

    @Test
    fun aDisabledAccountCanBeEnabledAgain() = runComposeUiTest {
        val host = show(original = ed.copy(disabled = true))
        button("Enable account").performClick()
        waitForIdle()
        assertEquals(listOf("disabled u-ed"), host.calls)
    }

    @Test
    fun theOwnAccountHasNoAccountActionsAndNoRoleChange() = runComposeUiTest {
        show(original = ed, isSelf = true)
        listOf("Send password-reset email", "Permissions", "Disable account", "Delete account", "Remove from my organizations").forEach {
            assertEquals(0, count(it), it)
        }
        onNodeWithText("Global role").assertIsNotEnabled()
    }

    @Test
    fun aClubAdminRemovesAnAccountFromItsOrganizationsAndSeesWhatItDoesNotManage() = runComposeUiTest {
        val host = show(original = ed.copy(hasGrantsOutside = true), access = clubAdmin)
        onNodeWithText("This account also has organizations or tournaments that you do not manage.", substring = true).assertExists()
        assertEquals(0, count("Disable account"))
        assertEquals(0, count("Delete account"))
        button("Remove from my organizations").performClick()
        waitForIdle()
        assertEquals(listOf("remove u-ed"), host.calls)
        // Tournaments outside count for the editor, so it saves without one of the viewer.
        button("Save").assertIsEnabled()
    }

    @Test
    fun aHiddenEmailStaysHiddenAndSavesEmpty() = runComposeUiTest {
        val player = ManagedUser("u-p", "secret@example.test", "Pat", GlobalUserRole.PLAYER, false, emptyList(), emaId = "07000007", emailHidden = true)
        val host = show(original = player, access = editor)
        onNodeWithText("Hidden").assertExists()
        onNodeWithText("You see the email of a player only when the player plays in one of your tournaments.").assertExists()
        assertEquals(0, count("Send password-reset email"))
        // Only a super admin changes the EMA ID of an account: here it is shown, without the search.
        onNodeWithText("07000007").assertExists()
        assertEquals(0, onAllNodes(hasContentDescription("Search EMA players")).fetchSemanticsNodes().size)
        button("Save").performClick()
        waitForIdle()
        assertEquals("", host.saved.single().email)
    }

    @Test
    fun withoutAnEmaIdAndWithoutTheRightToSetItTheFieldIsNotShown() = runComposeUiTest {
        show(original = ed.copy(emaId = null), access = editor)
        assertEquals(0, count("EMA ID"))
    }

    @Test
    fun anAccountDisabledWithoutTournamentsSaysWhy() = runComposeUiTest {
        show(original = ed.copy(disabled = true, disabledWithoutRole = true, role = GlobalUserRole.PLAYER, roles = emptySet()), access = clubAdmin)
        onNodeWithText("This account was disabled when it lost its last tournament.", substring = true).assertExists()
    }

    @Test
    fun removingTheOwnPhotoShowsTheNewPhotoOrTheError() = runComposeUiTest {
        val withPhoto = ed.copy(photoUrl = "https://example.test/ed.jpg", hasOwnPhoto = true)
        var answer: AppResult<ManagedUser> = AppResult.Failure(AppError.Network)
        val host = show(original = withPhoto, removePhoto = { answer })
        button("Remove photo").performClick()
        waitForIdle()
        onNodeWithText("Network request failed.").assertExists()

        answer = AppResult.Success(withPhoto.copy(photoUrl = null, hasOwnPhoto = false))
        pressClock += 1.seconds
        button("Remove photo").performClick()
        waitForIdle()
        assertEquals(0, count("Remove photo"))
        assertEquals(listOf("removePhoto u-ed", "removePhoto u-ed"), host.calls)
    }

    @Test
    fun whileSavingNothingCanBeChanged() = runComposeUiTest {
        val host = show(original = ed, saving = true)
        button("Save").assertIsNotEnabled()
        button("Cancel").assertIsNotEnabled()
        assertEquals(0, host.dismissed)
    }

    @Test
    fun cancelClosesTheDialogWithoutSaving() = runComposeUiTest {
        val host = show(original = ed)
        button("Cancel").performClick()
        waitForIdle()
        assertEquals(1, host.dismissed)
        assertEquals(emptyList(), host.saved)
    }

    @Test
    fun theRoleOptionsFollowTheRightsOfTheViewer() {
        assertEquals(
            listOf(GlobalUserRole.EDITOR, GlobalUserRole.CLUB_ADMIN, GlobalUserRole.SUPER_ADMIN, GlobalUserRole.PLAYER),
            roleOptions(superAdmin, null),
        )
        assertEquals(listOf(GlobalUserRole.PLAYER), roleOptions(editor, null))
        assertEquals(listOf(GlobalUserRole.EDITOR, GlobalUserRole.CLUB_ADMIN, GlobalUserRole.PLAYER), roleOptions(clubAdmin, null))
        // An admin of another club keeps its role there: the club admin can neither make it an editor nor a player.
        val adminOfBoth = ed.copy(role = GlobalUserRole.CLUB_ADMIN, adminOrganizations = setOf("org-a", "org-b"), hasGrantsOutside = true)
        assertEquals(listOf(GlobalUserRole.CLUB_ADMIN), roleOptions(clubAdmin, adminOfBoth))
    }

    private class ConfirmHost {
        var confirmed = 0
        var dismissed = 0
    }

    private fun ComposeUiTest.showConfirm(delete: Boolean, saving: Boolean = false): ConfirmHost {
        val host = ConfirmHost()
        setContent {
            MtsTheme(useDarkTheme = false) {
                if (delete) {
                    DeleteUserDialog(user = ed, saving = saving, onConfirm = { host.confirmed++ }, onDismiss = { host.dismissed++ })
                } else {
                    DisableUserDialog(user = ed, saving = saving, onConfirm = { host.confirmed++ }, onDismiss = { host.dismissed++ })
                }
            }
        }
        waitForIdle()
        return host
    }

    @Test
    fun theDeleteDialogNamesTheAccountAndDeletesOnConfirm() = runComposeUiTest {
        val host = showConfirm(delete = true)
        onNodeWithText("Delete account?").assertExists()
        onNodeWithText("ed@example.test will be deleted for good", substring = true).assertExists()
        button("Delete").performClick()
        waitForIdle()
        button("Cancel").performClick()
        waitForIdle()
        assertEquals(1, host.confirmed)
        assertEquals(1, host.dismissed)
    }

    @Test
    fun theDisableDialogNamesTheAccountAndDisablesOnConfirm() = runComposeUiTest {
        val host = showConfirm(delete = false)
        onNodeWithText("Disable account?").assertExists()
        onNodeWithText("ed@example.test will not be able to sign in. Its profile and history will remain.").assertExists()
        button("Disable").performClick()
        waitForIdle()
        button("Cancel").performClick()
        waitForIdle()
        assertEquals(1, host.confirmed)
        assertEquals(1, host.dismissed)
    }

    @Test
    fun whileSavingTheConfirmDialogsDoNothing() = runComposeUiTest {
        val host = showConfirm(delete = true, saving = true)
        button("Delete").assertIsNotEnabled()
        button("Cancel").assertIsNotEnabled()
        button("Delete").performClick()
        waitForIdle()
        assertEquals(0, host.confirmed)
    }

    @Test
    fun whileSavingTheDisableDialogDoesNothing() = runComposeUiTest {
        showConfirm(delete = false, saving = true)
        button("Disable").assertIsNotEnabled()
        button("Cancel").assertIsNotEnabled()
    }
}
