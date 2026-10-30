package com.etologic.mahjongtournamentsuite.e2e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.etologic.mahjongtournamentsuite.MainActivity
import com.etologic.mahjongtournamentsuite.data.network.BackendUrlOverride
import android.os.ParcelFileDescriptor
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.isRoot
import org.junit.rules.ExternalResource
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runners.model.Statement
import androidx.compose.ui.semantics.SemanticsNode
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import java.io.File

/** The accounts of the local test backend. See firebase/functions/src/scripts/startE2eBackend.ts. */
object E2eAccounts {
    const val PASSWORD = "Passw0rd!-e2e"
    const val ADMIN = "admin@e2e.test"
    const val EDITOR = "editor@e2e.test"
    const val OUTSIDER = "outsider@e2e.test"
    const val SEATED = "seated@e2e.test"
    const val WAITING = "waiting@e2e.test"
    const val DONE = "done@e2e.test"
    const val STRANGER = "stranger@e2e.test"
    const val PLAYER = "player@e2e.test"
    const val DISABLED = "disabled@e2e.test"
    const val EXTRA_TIME = "extra@e2e.test"
    const val TIME_OVER = "over@e2e.test"
}

/**
 * The Android emulator reaches the computer that runs the backend at 10.0.2.2. A device in Firebase Test Lab gets a
 * public address of the backend in the `e2eBackendUrl` instrumentation argument.
 */
val E2E_BACKEND_URL: String =
    InstrumentationRegistry.getArguments().getString("e2eBackendUrl") ?: "http://10.0.2.2:5055/api"

private const val WAIT_MS = 20_000L

/** Starts every test with no saved session, and points the app to the local backend. */
class FreshAppState : ExternalResource() {
    override fun before() {
        // The Google password manager shows a "Save password?" window over the app after a sign-in.
        shell("settings put secure autofill_service null")
        BackendUrlOverride.functionsBaseUrl = E2E_BACKEND_URL
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.filesDir, "auth_session.json").delete()
        context.getSharedPreferences("mahjong_tournament_suite_preferences", 0).edit().clear().commit()
    }
}

/** A test rule that clears the state, then lets the test start the activity itself. */
class AdminAppRule {
    val compose: ComposeTestRule = createEmptyComposeRule()
    private var scenario: ActivityScenario<MainActivity>? = null

    // The activity is not closed when a test ends. The test orchestrator ends the process. Closing the activity while a
    // screen is open can crash the navigation library in the test harness. A test can still call [close] to restart.
    val chain: TestRule = RuleChain.outerRule(FreshAppState()).around(compose).around(FailureReport(compose))

    fun launch() {
        BackendUrlOverride.functionsBaseUrl = E2E_BACKEND_URL
        launchKeepingBackend()
    }

    /** Saves a session for the account, so the app starts already signed in. */
    fun signInThroughFile(email: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.filesDir, "auth_session.json").writeText(E2eApi.sessionFileText(email))
    }

    /** Starts the app with the backend address that the test has set. */
    fun launchKeepingBackend() {
        // A build with Firebase settings asks for the analytics consent. The test accepts it before the start.
        InstrumentationRegistry.getInstrumentation().targetContext
            .getSharedPreferences("mts_telemetry", android.content.Context.MODE_PRIVATE)
            .edit().putBoolean("consent", true).commit()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        // The first frame can come a little later than the launch. Wait until the app has a Compose screen.
        repeat(50) {
            if (runCatching { compose.waitForIdle() }.isSuccess) return
            Thread.sleep(200)
        }
    }

    fun close() {
        // Let a running screen change end. Closing during it can crash the navigation library.
        runCatching {
            compose.mainClock.advanceTimeBy(2_000)
            compose.waitForIdle()
        }
        scenario?.close()
        scenario = null
    }
}

@OptIn(ExperimentalTestApi::class)
fun ComposeTestRule.waitForText(text: String, substring: Boolean = false, timeoutMs: Long = WAIT_MS) {
    waitUntilAtLeastOneExists(hasText(text, substring = substring) or hasContentDescription(text, substring = substring), timeoutMs)
}

@OptIn(ExperimentalTestApi::class)
fun ComposeTestRule.waitForNoText(text: String, substring: Boolean = false, timeoutMs: Long = WAIT_MS) {
    waitUntilDoesNotExist(hasText(text, substring = substring) or hasContentDescription(text, substring = substring), timeoutMs)
}

fun SemanticsNodeInteractionsProvider.text(text: String, substring: Boolean = false): SemanticsNodeInteraction =
    onNodeWithText(text, substring = substring)

/** Types into the sign-in form and presses the button. */
fun ComposeTestRule.signIn(email: String, password: String = E2eAccounts.PASSWORD) {
    waitForText("Sign in")
    onNodeWithText("Email").performTextInput(email)
    onNodeWithText("Password").performTextInput(password)
    clickButton("Sign in")
}

/** Presses a button by its text. A screen title can have the same text, so only nodes that click are used. */
fun ComposeTestRule.clickButton(text: String) {
    // On a phone the keyboard can cover the button.
    runCatching { androidx.test.espresso.Espresso.closeSoftKeyboard() }
    val label = hasText(text) or hasContentDescription(text)
    val merged = onAllNodes(label and hasClickAction())
    val button = if (merged.fetchSemanticsNodes().isNotEmpty()) merged[0] else onAllNodes(label, useUnmergedTree = true)[0]
    // A button outside a scroll container is already visible.
    runCatching { button.performScrollTo() }
    button.performClick()
}

/**
 * When a test fails, this rule adds the texts on the screen to the error message and saves a screenshot with the shell.
 * Read the screenshots with: adb pull /sdcard/Download/e2e
 */
class FailureReport(private val compose: ComposeTestRule) : TestWatcher() {
    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            try {
                base.evaluate()
            } catch (error: Throwable) {
                val name = "${description.className.substringAfterLast('.')}.${description.methodName}"
                runCatching {
                    shell("mkdir -p /sdcard/Download/e2e")
                    shell("screencap -p /sdcard/Download/e2e/$name.png")
                }
                val screen = runCatching {
                    compose.onAllNodes(isRoot(), useUnmergedTree = true).fetchSemanticsNodes().joinToString("\n") { it.toDebugString() }
                }.getOrElse { "(no screen: ${it.message})" }
                throw AssertionError("${error.message}\n--- screen ---\n$screen", error)
            }
        }
    }
}

/** The texts, descriptions and clickable nodes on the screen, as an indented list. */
fun SemanticsNode.toDebugString(depth: Int = 0): String {
    val texts = config.getOrNull(SemanticsProperties.Text)?.joinToString("|") { it.text }.orEmpty()
    val editable = config.getOrNull(SemanticsProperties.EditableText)?.text.orEmpty()
    val description = config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString("|").orEmpty()
    val parts = listOf(texts, editable, description).filter { it.isNotEmpty() }
    val line = "  ".repeat(depth) + parts.joinToString(" / ") + if (config.contains(SemanticsActions.OnClick)) " [click]" else ""
    val own = if (parts.all { it == "-" } && !config.contains(SemanticsActions.OnClick)) "" else line
    return (listOf(own) + children.map { it.toDebugString(depth + 1) }).filter { it.isNotBlank() }.joinToString("\n")
}

/** Writes the screen to the log. Use it while writing a test: adb logcat -s E2E */
fun ComposeTestRule.dumpScreen(label: String) {
    val tree = onAllNodes(isRoot(), useUnmergedTree = true).fetchSemanticsNodes().joinToString("\n") { it.toDebugString() }
    tree.chunked(3500).forEachIndexed { index, part -> android.util.Log.i("E2E", "[$label $index]\n$part") }
}

/** Runs a shell command as the shell user and waits until it ends. */
fun shell(command: String): String {
    val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
    return ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes().decodeToString() }
}

/**
 * Opens a row of the tournament list. The table scrolls sideways on a phone, so the middle of the row can be out of
 * the screen. The tap goes to the left side of the row.
 */
@OptIn(ExperimentalTestApi::class)
fun ComposeTestRule.openTournament(name: String) {
    waitForText("Logout")
    // The list is lazy. A row far down is not composed until the list scrolls to it.
    waitUntil(WAIT_MS) {
        if (onAllNodes(hasText(name)).fetchSemanticsNodes().isEmpty()) {
            onAllNodes(hasScrollToNodeAction()).fetchSemanticsNodes().indices.forEach { index ->
                runCatching { onAllNodes(hasScrollToNodeAction())[index].performScrollToNode(hasText(name)) }
            }
        }
        onAllNodes(hasText(name)).fetchSemanticsNodes().isNotEmpty()
    }
    onNode(hasClickAction() and hasAnyDescendant(hasText(name)), useUnmergedTree = true).performTouchInput { click(Offset(40f, height / 2f)) }
}

/** Presses the chip of a table in the row of table chips. */
fun ComposeTestRule.selectTable(number: Int) {
    onAllNodes(hasClickAction() and hasAnyDescendant(hasText(number.toString())), useUnmergedTree = true)[0].performClick()
}

/** Lets the app run for a while. The test clock only moves when the test moves it, so never use Thread.sleep alone. */
fun ComposeTestRule.settle(frames: Int = 6) {
    repeat(frames) {
        mainClock.advanceTimeBy(300)
        waitForIdle()
        Thread.sleep(100)
    }
}

@OptIn(ExperimentalTestApi::class)
fun ComposeTestRule.assertNoText(text: String, substring: Boolean = false) {
    onAllNodes(hasText(text, substring = substring) or hasContentDescription(text, substring = substring)).assertCountEquals(0)
}

/** The editable field that has this label. */
fun ComposeTestRule.field(label: String): SemanticsNodeInteraction = onNode(hasSetTextAction() and hasText(label))

/** A select field. It is not a text field, so it has the role of a dropdown list and no text action. */
fun ComposeTestRule.selectField(label: String): SemanticsNodeInteraction =
    onNode(androidx.compose.ui.test.SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.Role, androidx.compose.ui.semantics.Role.DropdownList) and hasText(label))

/** Base class of the admin app tests. */
abstract class AdminE2eTest {
    protected val app = AdminAppRule()

    @get:org.junit.Rule
    val rules: TestRule = app.chain

    protected val c get() = app.compose

    /** Starts the app with a saved session of the account. [ready] is a text that shows when the first screen has loaded. */
    protected fun start(email: String, ready: String = "Test - Live ongoing tournament") {
        app.signInThroughFile(email)
        app.launch()
        c.waitForText(ready)
    }
}

fun SemanticsNodeInteraction.performTextInputText(text: String) = performTextInput(text)

fun SemanticsNodeInteraction.performTextReplacementText(text: String) = performTextReplacement(text)

/** True when a text is on the screen now. */
fun ComposeTestRule.isOnScreen(text: String): Boolean =
    onAllNodes(hasText(text) or hasContentDescription(text)).fetchSemanticsNodes().isNotEmpty()

/** Waits until one of the texts is on the screen. */
@OptIn(ExperimentalTestApi::class)
fun ComposeTestRule.waitForAnyText(vararg texts: String, timeoutMs: Long = WAIT_MS) {
    waitUntil(timeoutMs) { texts.any { isOnScreen(it) } }
}

/** Scrolls the lazy list of the screen until a text is composed. */
@OptIn(ExperimentalTestApi::class)
fun ComposeTestRule.scrollListTo(text: String, substring: Boolean = false) {
    val matcher = hasText(text, substring = substring)
    waitUntil(WAIT_MS) {
        if (onAllNodes(matcher).fetchSemanticsNodes().isEmpty()) {
            onAllNodes(hasScrollToNodeAction()).fetchSemanticsNodes().indices.forEach { index ->
                runCatching { onAllNodes(hasScrollToNodeAction())[index].performScrollToNode(matcher) }
            }
        }
        onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
    }
}

/**
 * Chooses a seat in a hand dialog. The dialog lists the seats of the winner first and then the seats of the loser.
 * The wind icons carry the wind name as description. The page behind the dialog has icons too, so the dialog entries
 * are the last ones in the tree.
 * [which] is 0 for the winner list and 1 for the loser list.
 */
fun ComposeTestRule.pickSeat(wind: String, which: Int) {
    val nodes = onAllNodes(hasContentDescription(wind), useUnmergedTree = true)
    val count = nodes.fetchSemanticsNodes().size
    nodes[count - 2 + which].performClick()
}

/** Presses the last button with this text. A dialog is the last one in the tree, so this reaches its button, not the page's. */
fun ComposeTestRule.clickLastButton(text: String) {
    onAllNodes((hasText(text) or hasContentDescription(text)) and hasClickAction()).onLast().performClick()
}
