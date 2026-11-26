package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.navigation.compose.rememberNavController
import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.domain.model.AdminStatus
import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.EmaPlayerDeleteResult
import com.etologic.mahjongtournamentsuite.domain.model.EmaPlayerTournaments
import com.etologic.mahjongtournamentsuite.domain.model.GlobalUserRole
import com.etologic.mahjongtournamentsuite.domain.model.Player
import com.etologic.mahjongtournamentsuite.domain.repository.AdminRepository
import com.etologic.mahjongtournamentsuite.domain.repository.AuthRepository
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerAccountRepository
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerRepository
import com.etologic.mahjongtournamentsuite.domain.repository.RefreshMode
import com.etologic.mahjongtournamentsuite.domain.repository.TournamentRepository
import com.etologic.mahjongtournamentsuite.presentation.presenter.PlayerBasePresenter
import com.etologic.mahjongtournamentsuite.presentation.presenter.TournamentsPresenter
import com.etologic.mahjongtournamentsuite.presentation.presenter.UsersPresenter
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

/** An EMA registry whose writes fail with a lost connection, after the first [loadsBeforeFailing] reads of the list. */
private class FailingRegistry(private val players: List<Player>, private val loadsBeforeFailing: Int = Int.MAX_VALUE) : PlayerRepository {
    val calls = mutableListOf<String>()
    private var loads = 0
    override suspend fun listPlayers(refreshMode: RefreshMode): AppResult<List<Player>> =
        if (loads++ < loadsBeforeFailing) AppResult.Success(players) else AppResult.Failure(AppError.Network)
    override suspend fun createPlayer(player: Player): AppResult<Player> {
        calls += "create:${player.emaId}"
        return AppResult.Failure(AppError.Network)
    }
    override suspend fun updatePlayer(previousEmaId: String, player: Player): AppResult<Unit> {
        calls += "update:${player.emaId}"
        return AppResult.Failure(AppError.Network)
    }
    override suspend fun updatePlayerPhoto(emaId: String, contentType: String, bytes: ByteArray): AppResult<Player> = AppResult.Failure(AppError.Network)
    override suspend fun tournamentsOfEmaPlayers(emaIds: List<String>): AppResult<List<EmaPlayerTournaments>> = AppResult.Failure(AppError.Network)
    override suspend fun deleteEmaPlayers(emaIds: List<String>): AppResult<List<EmaPlayerDeleteResult>> = AppResult.Failure(AppError.Network)
}

/** The EMA registry screen tells what is wrong: a missing field, a wrong or taken EMA number, and a failed save. */
@OptIn(ExperimentalTestApi::class)
class PlayerBaseErrorsUiTest {
    private val ada = Player(emaId = "00010001", firstName = "Ada", lastName = "Lovelace", country = "GBR")
    private val babbage = Player(emaId = "00010002", firstName = "Charles", lastName = "Babbage", country = "GBR")

    private fun ComposeUiTest.show(registry: PlayerRepository) {
        setContent {
            val store = remember { AppMemoryStore().also { it.adminStatus.value = AdminStatus("me", GlobalUserRole.SUPER_ADMIN) } }
            val koin = remember {
                koinApplication {
                    modules(
                        module {
                            single { Logger.withTag("ui-test") }
                            single<TournamentRepository> { FakeTournamentRepository() }
                            single<AdminRepository> { FakeAdminRepository(GlobalUserRole.SUPER_ADMIN, emptyList()) }
                            single<AuthRepository> { FakeSignedInAuthRepository() }
                            single<PlayerRepository> { registry }
                            single<PlayerAccountRepository> { FakePlayerAccountRepository() }
                            single { store }
                            factory { TournamentsPresenter(get(), get(), get()) }
                            factory { UsersPresenter(get(), get(), get(), get()) }
                            factory { PlayerBasePresenter(get(), get()) }
                        },
                    )
                }.koin
            }
            KoinContext(koin) {
                CompositionLocalProvider(LocalThemeController provides ThemeController(ThemePreference.Light, false, {})) {
                    MtsTheme(useDarkTheme = false) { PlayerBaseScreen(rememberNavController()) }
                }
            }
        }
        waitForIdle()
    }

    private fun ComposeUiTest.awaitText(text: String) = waitUntil(timeoutMillis = 5_000) { onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }

    private fun ComposeUiTest.selectAda() {
        awaitText("10001")
        onAllNodes(hasText("10001", substring = true) and !hasSetTextAction()).onFirst().performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText("Ada") and hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun ComposeUiTest.field(label: String) = onAllNodes(hasText(label) and hasSetTextAction()).onFirst()

    private fun ComposeUiTest.dialogField(label: String) = onNode(hasText(label) and hasSetTextAction() and hasAnyAncestor(isDialog()))

    private fun ComposeUiTest.saveAndExpect(message: String) {
        // The Save of the details can be under the bottom of the test window: the click goes to its action.
        onAllNodes(hasText("Save") and hasClickAction()).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText("Save player changes", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        onAllNodes(hasText("Save") and hasClickAction()).onLast().performClick()
        awaitText(message)
        onAllNodes(hasText("OK") and hasClickAction()).onLast().performClick()
        waitForIdle()
    }

    @Test
    fun theChangesOfAPlayerAreCheckedBeforeTheSaveAndAFailedSaveSaysSo() = runComposeUiTest {
        val registry = FailingRegistry(listOf(ada, babbage))
        show(registry)
        selectAda()
        field("First name").performTextReplacement(" ")
        saveAndExpect("First name, last name, and a three-letter EMA country code are required.")
        field("First name").performTextReplacement("Ada")
        // The field keeps only digits, so an empty number is the one that is not valid.
        field("EMA number").performTextReplacement("")
        saveAndExpect("EMA number must contain 1 to 8 digits.")
        field("EMA number").performTextReplacement("10002")
        saveAndExpect("This EMA number already exists.")
        field("EMA number").performTextReplacement("10001")
        saveAndExpect("OK")
        assertEquals(listOf("update:00010001"), registry.calls)
    }

    @Test
    fun aNewPlayerNeedsEveryFieldAndAFailedCreateSaysSo() = runComposeUiTest {
        val registry = FailingRegistry(listOf(ada, babbage))
        show(registry)
        awaitText("10001")
        onNodeWithTextClick("New player")
        awaitText("Add player")
        fun add(expected: String) {
            val button = onNode(hasText("Add player") and hasClickAction() and hasAnyAncestor(isDialog()))
            button.performSemanticsAction(SemanticsActions.OnClick)
            awaitText(expected)
            onAllNodes(hasText("OK") and hasClickAction()).onLast().performClick()
            waitForIdle()
        }
        add("EMA number is required.")
        dialogField("EMA number").performTextReplacement("10001")
        add("This EMA number already exists.")
        dialogField("EMA number").performTextReplacement("99999")
        add("First name is required.")
        dialogField("First name").performTextReplacement("Grace")
        add("Last name is required.")
        dialogField("Last name").performTextReplacement("Hopper")
        add("Choose a country")
        onNode(hasText("Country") and hasAnyAncestor(isDialog())).performClick()
        awaitText("No country")
        onAllNodesWithText("No country").onFirst().performClick()
        waitForIdle()
        add("OK")
        assertEquals(listOf("create:00099999"), registry.calls)
    }

    @Test
    fun aListThatDoesNotLoadShowsTheError() = runComposeUiTest {
        show(FailingRegistry(listOf(ada), loadsBeforeFailing = 0))
        awaitText("OK")
    }

    private fun ComposeUiTest.onNodeWithTextClick(text: String) {
        onAllNodes(hasText(text) and hasClickAction()).onFirst().performClick()
        waitForIdle()
    }
}
