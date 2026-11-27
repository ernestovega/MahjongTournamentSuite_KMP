package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
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
import com.etologic.mahjongtournamentsuite.presentation.components.LocalPressTimeSource
import com.etologic.mahjongtournamentsuite.presentation.platform.LocalPickedImageForTests
import com.etologic.mahjongtournamentsuite.presentation.platform.SelectedImage
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
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

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

/** A registry where the players save and their photos do not. */
private class PhotoFailingRegistry(private val players: List<Player>) : PlayerRepository by FailingRegistry(players) {
    val calls = mutableListOf<String>()
    override suspend fun createPlayer(player: Player): AppResult<Player> {
        calls += "create:${player.emaId}"
        return AppResult.Success(player)
    }
    override suspend fun updatePlayer(previousEmaId: String, player: Player): AppResult<Unit> {
        calls += "update:${player.emaId}"
        return AppResult.Success(Unit)
    }
    override suspend fun updatePlayerPhoto(emaId: String, contentType: String, bytes: ByteArray): AppResult<Player> {
        calls += "photo:$emaId"
        return AppResult.Failure(AppError.Network)
    }
}

/** The EMA registry screen tells what is wrong: a missing field, a wrong or taken EMA number, and a failed save. */
@OptIn(ExperimentalTestApi::class)
class PlayerBaseErrorsUiTest {
    // A real clock would drop a second press of the same button within half a second.
    private val clock = TestTimeSource()

    private fun SemanticsNodeInteraction.press() {
        clock += 1.seconds
        performClick()
    }

    private fun SemanticsNodeInteraction.pressAction() {
        clock += 1.seconds
        performSemanticsAction(SemanticsActions.OnClick)
    }

    private val ada = Player(emaId = "00010001", firstName = "Ada", lastName = "Lovelace", country = "GBR")
    private val babbage = Player(emaId = "00010002", firstName = "Charles", lastName = "Babbage", country = "GBR")

    /** What the photo picker of the screen returns next, in place of the file chooser. */
    private var nextImage = SelectedImage("ada.png", "image/png", ByteArray(16))

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
                CompositionLocalProvider(LocalThemeController provides ThemeController(ThemePreference.Light, false, {}), LocalPressTimeSource provides clock, LocalPickedImageForTests provides { nextImage }) {
                    MtsTheme(useDarkTheme = false) { PlayerBaseScreen(rememberNavController()) }
                }
            }
        }
        waitForIdle()
    }

    private fun ComposeUiTest.awaitText(text: String) = waitUntil(timeoutMillis = 5_000) { onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }

    private fun ComposeUiTest.selectAda() {
        awaitText("10001")
        onAllNodes(hasText("10001", substring = true) and !hasSetTextAction()).onFirst().press()
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText("Ada") and hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun ComposeUiTest.field(label: String) = onAllNodes(hasText(label) and hasSetTextAction()).onFirst()

    private fun ComposeUiTest.dialogField(label: String) = onNode(hasText(label) and hasSetTextAction() and hasAnyAncestor(isDialog()))

    private fun ComposeUiTest.saveAndExpect(message: String) {
        // The Save of the details can be under the bottom of the test window: the click goes to its action.
        onAllNodes(hasText("Save") and hasClickAction()).onFirst().pressAction()
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText("Save player changes", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        onAllNodes(hasText("Save") and hasClickAction()).onLast().press()
        awaitText(message)
        onAllNodes(hasText("OK") and hasClickAction()).onLast().press()
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
            button.pressAction()
            awaitText(expected)
            onAllNodes(hasText("OK") and hasClickAction()).onLast().press()
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
        onNode(hasText("Country") and hasAnyAncestor(isDialog())).press()
        awaitText("No country")
        onAllNodesWithText("No country").onFirst().press()
        waitForIdle()
        add("OK")
        assertEquals(listOf("create:00099999"), registry.calls)
    }

    @Test
    fun aListThatDoesNotLoadShowsTheError() = runComposeUiTest {
        show(FailingRegistry(listOf(ada), loadsBeforeFailing = 0))
        awaitText("OK")
    }

    private fun ComposeUiTest.pressChip(description: String) {
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasContentDescription(description), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        var chip = onAllNodes(hasContentDescription(description), useUnmergedTree = true).onFirst()
        while (SemanticsActions.OnClick !in chip.fetchSemanticsNode().config) chip = chip.onParent()
        chip.pressAction()
        waitForIdle()
    }

    @Test
    fun theSearchAndTheCountryFilterNarrowTheRegistryAndACancelledSaveKeepsTheChanges() = runComposeUiTest {
        val curie = Player(emaId = "00020001", firstName = "Marie", lastName = "Curie", country = "FRA", photoUrl = "https://photos.test/curie.png")
        val registry = FailingRegistry(listOf(ada, babbage, curie))
        show(registry)
        awaitText("10001")

        field("Search").performTextReplacement("zzz")
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("10001", substring = true).fetchSemanticsNodes().isEmpty() }
        onAllNodesWithContentDescription("Clear search").onFirst().pressAction()
        awaitText("10001")

        pressChip("Filter by France")
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("10001", substring = true).fetchSemanticsNodes().isEmpty() }
        pressChip("Remove France country filter")
        awaitText("10001")

        selectAda()
        field("First name").performTextReplacement("Augusta")
        onAllNodes(hasText("Save") and hasClickAction()).onFirst().pressAction()
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText("Save player changes", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        onAllNodes(hasText("Cancel") and hasClickAction()).onLast().press()
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText("Save player changes", substring = true)).fetchSemanticsNodes().isEmpty() }
        assertEquals(emptyList(), registry.calls)
    }

    private fun ComposeUiTest.onNodeWithTextClick(text: String) {
        onAllNodes(hasText(text) and hasClickAction()).onFirst().press()
        waitForIdle()
    }

    @Test
    fun aPhotoOfTheWrongTypeIsRefusedAndAPhotoThatDoesNotUploadSaysSo() = runComposeUiTest {
        val registry = PhotoFailingRegistry(listOf(ada, babbage))
        show(registry)
        selectAda()

        nextImage = SelectedImage("ada.bmp", "image/bmp", ByteArray(16))
        pressChip("Choose new photo")
        awaitText("Choose a JPEG, PNG, WebP, or GIF image.")
        onAllNodes(hasText("OK") and hasClickAction()).onLast().press()
        waitForIdle()

        nextImage = SelectedImage("ada.png", "image/png", ByteArray(16))
        pressChip("Choose new photo")
        awaitText("ada.png")
        saveAndExpect("Network request failed.")
        assertEquals(listOf("update:00010001", "photo:00010001"), registry.calls)
        // The photo stays to try again.
        awaitText("ada.png")
    }

    @Test
    fun aNewPlayerKeepsTheDataWhenItsPhotoDoesNotUpload() = runComposeUiTest {
        val registry = PhotoFailingRegistry(listOf(ada, babbage))
        show(registry)
        awaitText("10001")
        onNodeWithTextClick("New player")
        awaitText("Add player")

        nextImage = SelectedImage("grace.bmp", "image/bmp", ByteArray(16))
        onNode(hasText("Choose photo") and hasClickAction() and hasAnyAncestor(isDialog())).press()
        awaitText("Choose a JPEG, PNG, WebP, or GIF image.")
        if (onAllNodes(hasText("OK") and hasClickAction()).fetchSemanticsNodes().isNotEmpty()) {
            onAllNodes(hasText("OK") and hasClickAction()).onLast().press()
            waitForIdle()
        }
        nextImage = SelectedImage("grace.png", "image/png", ByteArray(16))
        onNode(hasText("Choose photo") and hasClickAction() and hasAnyAncestor(isDialog())).press()
        awaitText("grace.png")

        dialogField("EMA number").performTextReplacement("99999")
        dialogField("First name").performTextReplacement("Grace")
        dialogField("Last name").performTextReplacement("Hopper")
        onNode(hasText("Country") and hasAnyAncestor(isDialog())).press()
        awaitText("No country")
        onAllNodesWithText("No country").onFirst().press()
        waitForIdle()
        onNode(hasText("Add player") and hasClickAction() and hasAnyAncestor(isDialog())).pressAction()
        awaitText("Network request failed.")
        assertEquals(listOf("create:00099999", "photo:00099999"), registry.calls)
    }
}
