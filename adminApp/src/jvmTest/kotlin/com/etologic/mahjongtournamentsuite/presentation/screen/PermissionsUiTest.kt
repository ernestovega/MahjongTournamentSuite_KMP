package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import com.etologic.mahjongtournamentsuite.domain.model.AdminStatus
import com.etologic.mahjongtournamentsuite.domain.model.CustomRole
import com.etologic.mahjongtournamentsuite.domain.model.GlobalUserRole
import com.etologic.mahjongtournamentsuite.domain.model.LevelPermissions
import com.etologic.mahjongtournamentsuite.domain.model.ManagedUser
import com.etologic.mahjongtournamentsuite.domain.model.Permission
import com.etologic.mahjongtournamentsuite.domain.model.RolesCatalog
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Custom roles and the permissions of one person: what each viewer can change and what goes to the server. */
@OptIn(ExperimentalTestApi::class)
class PermissionsUiTest {
    private val editorDefaults = Permission.defaultsOf(GlobalUserRole.EDITOR)
    private val referee = CustomRole("r1", "Referee", GlobalUserRole.EDITOR, editorDefaults + Permission.TOURNAMENT_RESET_TABLE)
    private val registry = CustomRole("r2", "Registry", GlobalUserRole.EDITOR, editorDefaults + Permission.EMA_EDIT)
    private val catalog = RolesCatalog(
        standard = GlobalUserRole.entries.filter { it != GlobalUserRole.PLAYER }.associateWith { Permission.defaultsOf(it) },
        custom = listOf(referee, registry),
        grantable = mapOf(
            GlobalUserRole.EDITOR to editorDefaults + setOf(Permission.TOURNAMENT_RESET_TABLE, Permission.EMA_EDIT, Permission.EMA_CREATE),
            GlobalUserRole.CLUB_ADMIN to Permission.defaultsOf(GlobalUserRole.CLUB_ADMIN) + Permission.EMA_EDIT,
        ),
        required = setOf(Permission.TOURNAMENT_VIEW),
    )
    private val editor = ManagedUser("e1", "ed@example.test", "Ed", GlobalUserRole.EDITOR, false, emptyList(), canManage = true, rolePermissions = mapOf(GlobalUserRole.EDITOR to editorDefaults))
    private val superAdmin = AdminStatus("me", GlobalUserRole.SUPER_ADMIN)
    private val clubAdmin = AdminStatus("me", GlobalUserRole.CLUB_ADMIN, adminOrganizations = setOf("org-1"))

    private fun ComposeUiTest.show(content: @Composable () -> Unit) {
        setContent { MtsTheme(useDarkTheme = false) { content() } }
        waitForIdle()
    }

    @Test
    fun aSuperAdminTakesAwayOnePermissionAndTheWholeListGoesToTheServer() = runComposeUiTest {
        var saved: Map<GlobalUserRole, LevelPermissions>? = null
        show { AccountPermissionsDialog(editor, catalog, superAdmin, saving = false, onDismiss = {}, onSave = { saved = it }, onSaveAsNewRole = { _, _ -> }) }
        onNodeWithText("ID cards").performScrollTo().performClick()
        onNodeWithText("Changed for this person").assertExistsCompat()
        onNodeWithText("Save").performClick()
        val level = assertNotNull(saved?.get(GlobalUserRole.EDITOR))
        assertNull(level.customRoleId)
        assertEquals(editorDefaults - Permission.TOOLS_ID_CARDS, level.permissions)
    }

    @Test
    fun choosingACustomRoleStartsTheBoxesFromThatRole() = runComposeUiTest {
        var saved: Map<GlobalUserRole, LevelPermissions>? = null
        show { AccountPermissionsDialog(editor, catalog, superAdmin, saving = false, onDismiss = {}, onSave = { saved = it }, onSaveAsNewRole = { _, _ -> }) }
        onNodeWithText("Editor (standard)").performClick()
        onNodeWithText("Referee").performClick()
        onNodeWithText("Save").performClick()
        assertEquals(LevelPermissions("r1", referee.permissions), saved?.get(GlobalUserRole.EDITOR))
    }

    @Test
    fun aCountryAdminOnlyChoosesRolesThatDoNotGiveMoreThanItHas() = runComposeUiTest {
        var saved: Map<GlobalUserRole, LevelPermissions>? = null
        show { AccountPermissionsDialog(editor, catalog, clubAdmin, saving = false, onDismiss = {}, onSave = { saved = it }, onSaveAsNewRole = { _, _ -> }) }
        onNodeWithText("Only a super admin changes single permissions", substring = true).assertExistsCompat()
        onNodeWithText("Edit players, tables, hands and results").assertIsNotEnabled()
        onNodeWithText("Editor (standard)").performClick()
        // The registry role edits EMA players, which a club admin cannot do.
        assertTrue(onAllNodesWithText("Registry").fetchSemanticsNodes().isEmpty())
        onNodeWithText("Referee").performClick()
        onNodeWithText("Save").performClick()
        assertEquals(LevelPermissions("r1", null), saved?.get(GlobalUserRole.EDITOR))
    }

    @Test
    fun aClubAdminCannotChangeTheRoleOfAnAccountWithGrantsOutsideItsOrganizations() = runComposeUiTest {
        show { AccountPermissionsDialog(editor.copy(hasGrantsOutside = true), catalog, clubAdmin, saving = false, onDismiss = {}, onSave = {}, onSaveAsNewRole = { _, _ -> }) }
        onNodeWithText("also works outside your organizations", substring = true).assertExistsCompat()
        onNodeWithText("Save").assertIsNotEnabled()
    }

    @Test
    fun aNewRoleNeedsAFreeNameAndKeepsTheRequiredPermission() = runComposeUiTest {
        var saved: Triple<String, GlobalUserRole, Set<Permission>>? = null
        show {
            RoleEditorDialog(
                draft = RoleDraft(id = null, name = "", base = GlobalUserRole.EDITOR, permissions = emptySet()),
                catalog = catalog,
                saving = false,
                onDismiss = {},
                onSave = { name, base, permissions -> saved = Triple(name, base, permissions) },
                onDuplicate = {},
                onDelete = {},
            )
        }
        onNodeWithText("Save").assertIsNotEnabled()
        onNodeWithText("Name").performTextInput("referee")
        onNodeWithText("Another role already has this name.").assertExistsCompat()
        onNodeWithText("Save").assertIsNotEnabled()
        onNodeWithText("Name").performTextInput(" 2")
        onNodeWithText("Save").assertIsEnabled().performClick()
        assertEquals(Triple("referee 2", GlobalUserRole.EDITOR, setOf(Permission.TOURNAMENT_VIEW)), saved)
    }

    @Test
    fun aStandardRoleOnlyShowsAndOffersACopy() = runComposeUiTest {
        var duplicated = false
        show {
            RoleEditorDialog(
                draft = RoleDraft(id = null, name = "", base = GlobalUserRole.EDITOR, permissions = editorDefaults, readOnly = true),
                catalog = catalog,
                saving = false,
                onDismiss = {},
                onSave = { _, _, _ -> },
                onDuplicate = { duplicated = true },
                onDelete = {},
            )
        }
        onNodeWithText("Round control and Timer").assertIsNotEnabled()
        assertTrue(onAllNodesWithText("Save").fetchSemanticsNodes().isEmpty())
        onNodeWithText("Duplicate").performClick()
        assertTrue(duplicated)
    }

    @Test
    fun deletingAUsedRoleSaysWhereItsAccountsGo() = runComposeUiTest {
        show { DeleteRoleDialog(referee.copy(accounts = 3), saving = false, onDismiss = {}, onConfirm = {}) }
        onNodeWithText("The 3 accounts that use it go back to the standard role Editor.", substring = true).assertExistsCompat()
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertExistsCompat() = assertExists()
}
