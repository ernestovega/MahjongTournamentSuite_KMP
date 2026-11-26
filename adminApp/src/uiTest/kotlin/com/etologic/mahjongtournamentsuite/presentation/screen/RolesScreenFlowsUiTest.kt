package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.etologic.mahjongtournamentsuite.domain.model.AdminStatus
import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.CustomRole
import com.etologic.mahjongtournamentsuite.domain.model.GlobalUserRole
import com.etologic.mahjongtournamentsuite.domain.model.ManagedUser
import com.etologic.mahjongtournamentsuite.domain.model.Permission
import com.etologic.mahjongtournamentsuite.domain.model.RolesCatalog
import com.etologic.mahjongtournamentsuite.domain.repository.AdminRepository
import com.etologic.mahjongtournamentsuite.domain.repository.AuthRepository
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerRepository
import com.etologic.mahjongtournamentsuite.domain.repository.TournamentRepository
import com.etologic.mahjongtournamentsuite.presentation.components.LocalPressTimeSource
import com.etologic.mahjongtournamentsuite.presentation.presenter.UsersPresenter
import com.etologic.mahjongtournamentsuite.presentation.theme.LocalThemeController
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import com.etologic.mahjongtournamentsuite.presentation.theme.ThemeController
import com.etologic.mahjongtournamentsuite.presentation.theme.ThemePreference
import org.koin.compose.KoinApplication
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.lang.reflect.Proxy
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

private inline fun <reified T> unused(): T = Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ ->
    error("Unexpected call ${method.name}")
} as T

/** The roles of the server in memory. [failing] answers every change with a lost connection. */
private class RolesServer(private val failing: Boolean = false, private val loads: Boolean = true) {
    private val editorDefaults = Permission.defaultsOf(GlobalUserRole.EDITOR)
    var custom = listOf(CustomRole("r1", "Referee", GlobalUserRole.EDITOR, editorDefaults + Permission.TOURNAMENT_RESET_TABLE, accounts = 0))
    val calls = mutableListOf<String>()

    fun catalog() = RolesCatalog(
        standard = GlobalUserRole.entries.filter { it != GlobalUserRole.PLAYER }.associateWith { Permission.defaultsOf(it) },
        custom = custom,
        grantable = mapOf(GlobalUserRole.EDITOR to editorDefaults + Permission.TOURNAMENT_RESET_TABLE),
        required = setOf(Permission.TOURNAMENT_VIEW),
    )

    private fun <T> answer(call: String, value: () -> T): AppResult<T> {
        calls += call
        return if (failing) AppResult.Failure(AppError.Network) else AppResult.Success(value())
    }

    val repository: AdminRepository = Proxy.newProxyInstance(AdminRepository::class.java.classLoader, arrayOf(AdminRepository::class.java)) { _, method, args ->
        when (method.name) {
            "listRoles" -> if (loads) AppResult.Success(catalog()) else AppResult.Failure(AppError.Network)
            "createRole" -> answer("create:${args[0]}:${args[1]}") {
                CustomRole("r${custom.size + 1}", args[0] as String, args[1] as GlobalUserRole, @Suppress("UNCHECKED_CAST") (args[2] as Set<Permission>)).also { custom = custom + it }
            }
            "updateRole" -> answer("update:${args[0]}:${args[1]}") { custom.first { it.id == args[0] }.copy(name = args[1] as String) }
            "deleteRole" -> answer("delete:${args[0]}") { 0 }
            "setUserPermissions" -> answer("permissions:${args[0]}") { user }
            else -> error("Unexpected call ${method.name}")
        }
    } as AdminRepository

    companion object {
        val user = ManagedUser(
            "e1", "ed@example.test", "", GlobalUserRole.EDITOR, false, emptyList(), canManage = true,
            rolePermissions = mapOf(GlobalUserRole.EDITOR to Permission.defaultsOf(GlobalUserRole.EDITOR)),
        )
    }
}

/** The Roles screen and the permissions of one account, with what each one sends to the server. */
@OptIn(ExperimentalTestApi::class)
class RolesScreenFlowsUiTest {
    private val original = Locale.getDefault()

    // The buttons ignore a second press within 500 ms of real time: the test moves this clock before each press.
    private val clock = TestTimeSource()

    @BeforeTest
    fun english() {
        Locale.setDefault(Locale.ENGLISH)
        stopKoin()
    }

    @AfterTest
    fun restore() {
        Locale.setDefault(original)
        stopKoin()
    }

    private fun presenter(server: RolesServer) = UsersPresenter(unused<TournamentRepository>(), server.repository, unused<AuthRepository>(), unused<PlayerRepository>())

    private fun ComposeUiTest.frame(content: @androidx.compose.runtime.Composable () -> Unit) = setContent {
        CompositionLocalProvider(LocalThemeController provides ThemeController(ThemePreference.Light, false, {}), LocalPressTimeSource provides clock) {
            MtsTheme(useDarkTheme = false) { content() }
        }
    }

    private fun ComposeUiTest.showRoles(server: RolesServer) = frame {
        KoinApplication(application = { modules(module { single { presenter(server) } }) }) {
            val nav = rememberNavController()
            NavHost(nav, startDestination = "roles") { composable("roles") { RolesScreen(nav) } }
        }
    }

    private fun ComposeUiTest.click(text: String, last: Boolean = false) {
        clock += 1.seconds
        val nodes = onAllNodes(hasClickAction() and hasText(text) and !hasSetTextAction())
        val node = if (last) nodes.onLast() else nodes[0]
        // A button low in a dialog that scrolls: a click out of the dialog would close it.
        runCatching { node.performScrollTo() }
        node.performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.waitFor(text: String) = waitUntil(timeoutMillis = 5_000) { onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun aStandardRoleIsCopiedIntoANewOne() = runComposeUiTest {
        val server = RolesServer()
        showRoles(server)
        waitFor("Referee")
        click("Editor")
        click("Duplicate")
        waitFor("Editor (copy)")
        click("Save")
        waitUntil(timeoutMillis = 5_000) { server.calls.isNotEmpty() }
        assertEquals(listOf("create:Editor (copy):EDITOR"), server.calls)
        waitFor("Editor (copy)")
    }

    @Test
    fun aCustomRoleIsRenamedAndDeleted() = runComposeUiTest {
        val server = RolesServer()
        showRoles(server)
        waitFor("Referee")
        click("Referee")
        onNode(hasSetTextAction() and hasText("Name")).performTextReplacement("Judge")
        click("Save")
        waitUntil(timeoutMillis = 5_000) { server.calls.size == 1 }
        click("Referee")
        click("Delete role")
        click("Delete role", last = true)
        waitUntil(timeoutMillis = 5_000) { server.calls.size == 2 }
        assertEquals(listOf("update:r1:Judge", "delete:r1"), server.calls)
    }

    @Test
    fun aNewRoleFromTheButtonAndAChangeThatFailsShowTheError() = runComposeUiTest {
        val server = RolesServer(failing = true)
        showRoles(server)
        waitFor("Referee")
        click("New role")
        onNode(hasSetTextAction() and hasText("Name")).performTextReplacement("Scorer")
        click("Save")
        waitFor("OK")
        click("OK")
        // The dialog stays open after the error.
        click("Cancel")
        click("Referee")
        click("Delete role")
        click("Delete role", last = true)
        waitFor("OK")
        assertEquals(listOf("create:Scorer:EDITOR", "delete:r1"), server.calls)
    }

    @Test
    fun rolesThatDoNotLoadShowTheError() = runComposeUiTest {
        showRoles(RolesServer(loads = false))
        waitFor("OK")
        click("OK")
    }

    @Test
    fun thePermissionsOfAnAccountSaveAndBecomeANewRole() = runComposeUiTest {
        val server = RolesServer()
        val saved = mutableListOf<ManagedUser>()
        frame { AccountPermissionsFlow(RolesServer.user, AdminStatus("me", GlobalUserRole.SUPER_ADMIN), presenter(server), onDismiss = {}, onSaved = { saved += it }) }
        waitFor("ID cards")
        click("ID cards")
        click("Save as a new role")
        waitFor("ed")
        click("Save", last = true)
        waitUntil(timeoutMillis = 5_000) { saved.isNotEmpty() }
        assertEquals(listOf("create:ed:EDITOR", "permissions:e1"), server.calls)
    }

    @Test
    fun anAccountWhoseRolesDoNotLoadClosesAfterTheError() = runComposeUiTest {
        var dismissed = 0
        frame { AccountPermissionsFlow(RolesServer.user, AdminStatus("me", GlobalUserRole.SUPER_ADMIN), presenter(RolesServer(loads = false)), onDismiss = { dismissed++ }, onSaved = {}) }
        waitFor("OK")
        click("OK")
        assertEquals(1, dismissed)
    }

    @Test
    fun aSaveThatFailsShowsTheError() = runComposeUiTest {
        val server = RolesServer(failing = true)
        frame { AccountPermissionsFlow(RolesServer.user, AdminStatus("me", GlobalUserRole.SUPER_ADMIN), presenter(server), onDismiss = {}, onSaved = {}) }
        waitFor("ID cards")
        click("ID cards")
        click("Save as a new role")
        click("Save", last = true)
        waitFor("OK")
        click("OK")
        click("Cancel", last = true)
        click("Save")
        waitFor("OK")
        assertEquals(listOf("create:ed:EDITOR", "permissions:e1"), server.calls)
    }

    @Test
    fun everyPermissionHasAName() = runComposeUiTest {
        frame {
            Column {
                Permission.entries.forEach { Text(it.label()) }
                PermissionsSavedNotice(RolesServer.user) {}
            }
        }
        assertTrue(onAllNodesWithText("The permissions of ed@example.test are saved.").fetchSemanticsNodes().isNotEmpty())
    }
}
