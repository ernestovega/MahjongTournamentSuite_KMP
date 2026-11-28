@file:OptIn(ExperimentalTestApi::class)

package com.etologic.mahjongtournamentsuite.e2e

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.isFocused
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import androidx.compose.ui.test.waitUntilDoesNotExist
import androidx.compose.ui.test.runSkikoComposeUiTest
import kotlin.time.Duration.Companion.minutes
import com.etologic.mahjongtournamentsuite.SuiteRoot
import com.etologic.mahjongtournamentsuite.data.platform.IosPlatform
import com.etologic.mahjongtournamentsuite.data.session.storedSessionUid
import com.etologic.mahjongtournamentsuite.SuiteSection
import com.etologic.mahjongtournamentsuite.SuiteStart

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
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
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.semantics.SemanticsNode

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

/** The local test backend. The iOS simulator reaches the Mac that runs it at 127.0.0.1. */
val E2E_BACKEND_URL: String = E2ePlatform.backendUrl ?: "http://127.0.0.1:5055/api"

/** How long a test waits for a screen. The slow emulator of the CI runner gets more time with the `e2eWaitMs` argument. */
private val WAIT_MS: Long = E2ePlatform.waitMs

/**
 * The app under test: [SuiteRoot] in a Compose test of the iOS simulator, as the iOS app shows it. Unlike Android, no
 * activity starts: [launch] composes the app again, and [close] removes it.
 */
class AdminApp {
    lateinit var compose: ComposeUiTest
    private var run by mutableIntStateOf(0)
    private var shown by mutableStateOf(false)

    /** Runs [block] with a clean app pointed at the local backend. A failure adds the texts on the screen. */
    fun runTest(block: () -> Unit) {
        // Without a backend (the normal test run) the end-to-end tests do nothing.
        if (E2ePlatform.backendUrl == null) return
        E2ePlatform.resetAppState()
        // Like on Android, the admin part runs on a tablet (an iPad on its side) and the player part on a phone (an
        // iPhone). E2E_FORM=phone or E2E_FORM=tablet runs every test on the other one too.
        val screen = E2ePlatform.screen(tablet = true)
        runSkikoComposeUiTest(size = screen.size, density = screen.density, testTimeout = 30.minutes) {
            compose = this
            setContent {
                if (shown) {
                    // The back of the system, which the tests send with pressBack. On a device it is the swipe from the
                    // left edge.
                    CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides E2eBack.owner) {
                    key(run) {
                        SuiteRoot(
                            initial = SuiteStart(SuiteSection.ADMIN),
                            startRequest = null,
                            onStartHandled = {},
                            post = { action -> action() },
                            storedSessionUid = ::storedSessionUid,
                            onPartShown = {},
                        )
                    }
                    }
                }
            }
            try {
                block()
            } catch (error: Throwable) {
                val screen = runCatching {
                    // The top window, for example a dialog, comes first. A long screen is cut at the end of the report.
                    onAllNodes(isRoot(), useUnmergedTree = true).fetchSemanticsNodes().reversed().joinToString("\n") { it.toDebugString() }
                }.getOrElse { "(no screen: ${it.message})" }
                throw AssertionError("${error.message}\n--- screen ---\n$screen", error)
            } finally {
                // Let a running screen change end before the test ends. Ending during it can crash the navigation library.
                runCatching {
                    mainClock.advanceTimeBy(2_000)
                    waitForIdle()
                }
            }
        }
    }

    fun launch() {
        IosPlatform.backendUrlOverride = E2E_BACKEND_URL
        launchKeepingBackend()
    }

    /** Saves a session for the account, so the app starts already signed in. */
    fun signInThroughFile(email: String) {
        E2ePlatform.writeSession(E2eApi.sessionFileText(email))
    }

    /** Starts the app with the backend address that the test has set. */
    fun launchKeepingBackend() {
        // A build with Firebase settings asks for the analytics consent. The test accepts it before the start.
        E2ePlatform.acceptTelemetry()
        run++
        shown = true
        repeat(50) {
            if (runCatching { compose.waitForIdle() }.isSuccess) return
            E2ePlatform.sleep(200)
        }
    }

    fun close() {
        runCatching {
            compose.mainClock.advanceTimeBy(2_000)
            compose.waitForIdle()
        }
        shown = false
        runCatching { compose.waitForIdle() }
    }
}

/**
 * The click label of the profile button of the top bar: the photo, alias and role of the signed-in user. It opens the
 * profile, which holds the Logout button. It shows when the tournament list has loaded for an admin or an editor.
 */
const val PROFILE_BUTTON = "Open profile"

/** A node whose click action has this label, such as the profile button, which has no text of its own to match. */
fun hasClickLabel(label: String): SemanticsMatcher =
    SemanticsMatcher("OnClick label = '$label'") { it.config.getOrNull(SemanticsActions.OnClick)?.label == label }

/** A node that shows the text, or describes itself with it, or has it as the label of its click action. */
internal fun hasLabel(text: String, substring: Boolean = false): SemanticsMatcher =
    hasText(text, substring = substring) or hasContentDescription(text, substring = substring) or hasClickLabel(text)

@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.waitForText(text: String, substring: Boolean = false, timeoutMs: Long = WAIT_MS) {
    waitUntilAtLeastOneExists(hasLabel(text, substring), timeoutMs)
}

@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.waitForNoText(text: String, substring: Boolean = false, timeoutMs: Long = WAIT_MS) {
    waitUntilDoesNotExist(hasLabel(text, substring), timeoutMs)
}

fun SemanticsNodeInteractionsProvider.text(text: String, substring: Boolean = false): SemanticsNodeInteraction =
    onNodeWithText(text, substring = substring)

/**
 * Types into the sign-in form and presses the button. The admin part has no sign-in of its own: without a session it
 * shows the sign-in form of the player part, as on the web.
 */
fun ComposeUiTest.signIn(email: String, password: String = E2eAccounts.PASSWORD) {
    waitForText("Sign in")
    // After a sign-out the app opens on the public tournament list: its Sign in button opens the form.
    val emailField = hasSetTextAction() and hasText("Email")
    if (onAllNodes(emailField).fetchSemanticsNodes().isEmpty()) clickButton("Sign in")
    waitUntil(timeoutMillis = WAIT_MS) { onAllNodes(emailField).fetchSemanticsNodes().isNotEmpty() }
    onNodeWithText("Email").performTextInput(email)
    onNodeWithText("Password").performTextInput(password)
    clickButton("Sign in")
}

/** Presses a button by its text. A screen title can have the same text, so only nodes that click are used. */
fun ComposeUiTest.clickButton(text: String) {
    val label = hasLabel(text)
    // The screens turn their buttons off while they load. A tap on a button that is off does nothing.
    if (onAllNodes(label and hasClickAction()).fetchSemanticsNodes().isNotEmpty()) {
        waitUntil(timeoutMillis = WAIT_MS) { onAllNodes(label and hasClickAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
    }
    val enabled = onAllNodes(label and hasClickAction() and isEnabled())
    val merged = if (enabled.fetchSemanticsNodes().isNotEmpty()) enabled else onAllNodes(label and hasClickAction())
    val button = if (merged.fetchSemanticsNodes().isNotEmpty()) merged[0] else onAllNodes(label, useUnmergedTree = true)[0]
    // A button outside a scroll container is already visible.
    runCatching { button.performScrollTo() }
    button.tap()
}

/**
 * Presses an option of the "More" menu of the top bar of a tournament (Teams, Players, Stats, EMA report, Activity).
 * It opens the menu first: "More" on a wide screen, or the three-dots menu of the top bar on a phone.
 */
fun ComposeUiTest.clickMoreMenuButton(text: String) {
    openMoreMenu()
    clickButton(text)
}

/** Opens the "More" menu of the top bar of a tournament, so that its options are on screen. */
fun ComposeUiTest.openMoreMenu() {
    if (onAllNodes(hasLabel("More") and hasClickAction()).fetchSemanticsNodes().isNotEmpty()) {
        clickButton("More")
    } else {
        clickButton("Menu")
        if (onAllNodes(hasLabel("More") and hasClickAction()).fetchSemanticsNodes().isNotEmpty()) clickButton("More")
    }
    waitForIdle()
}

/**
 * A tap, or the click action of the node when the system refuses the touch ("Failed to inject touch input"): after
 * another activity (the Timer) the window can lose the focus for a while.
 */
fun SemanticsNodeInteraction.tap() {
    try {
        performClick()
    } catch (error: AssertionError) {
        if (error.message?.contains("Failed to inject touch input") != true) throw error
        performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
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

/** Writes the screen to the output of the test. Use it while writing a test. */
fun ComposeUiTest.dumpScreen(label: String) {
    val tree = onAllNodes(isRoot(), useUnmergedTree = true).fetchSemanticsNodes().joinToString("\n") { it.toDebugString() }
    println("E2E [$label]\n$tree")
}

/**
 * Opens a row of the tournament list. The table scrolls sideways on a phone, so the middle of the row can be out of
 * the screen. The tap goes to the left side of the row.
 */
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.openTournament(name: String) {
    waitForText(PROFILE_BUTTON)
    // The list is lazy. A row far down is not composed until the list scrolls to it.
    waitUntil(timeoutMillis = WAIT_MS) {
        if (onAllNodes(hasText(name)).fetchSemanticsNodes().isEmpty()) {
            onAllNodes(hasScrollToNodeAction()).fetchSemanticsNodes().indices.forEach { index ->
                runCatching { onAllNodes(hasScrollToNodeAction())[index].performScrollToNode(hasText(name)) }
            }
        }
        onAllNodes(hasText(name)).fetchSemanticsNodes().isNotEmpty()
    }
    onNode(hasClickAction() and hasAnyDescendant(hasText(name)), useUnmergedTree = true).performTouchInput { click(Offset(40f, height / 2f)) }
}

/**
 * Presses the chip of a table in the row of table chips and waits until the screen shows that table.
 * A chip stays off until its table loads, and the row scrolls, so the chip is brought into view and pressed once it is on.
 */
fun ComposeUiTest.selectTable(number: Int) {
    val chip = hasTestTag("table-chip-$number")
    // The header over the table, "Round 3 • Table 6". "Table 1" must not match "Table 12".
    val header = SemanticsMatcher("header of table $number") { node ->
        node.config.getOrNull(SemanticsProperties.Text)?.any { it.text.endsWith("• Table $number") } == true
    }
    waitUntil(timeoutMillis = WAIT_MS) {
        if (onAllNodes(header).fetchSemanticsNodes().isNotEmpty()) return@waitUntil true
        onAllNodes(hasScrollToNodeAction()).fetchSemanticsNodes().indices.forEach { index ->
            runCatching { onAllNodes(hasScrollToNodeAction())[index].performScrollToNode(chip) }
        }
        if (onAllNodes(chip and isEnabled()).fetchSemanticsNodes().isNotEmpty()) {
            runCatching { onNode(chip and isEnabled()).performClick() }
            E2ePlatform.sleep(300)
        }
        false
    }
}

/** Goes from the first round to [round] with the Next round button, one round at a time. */
fun ComposeUiTest.goToRound(round: Int) {
    for (next in 2..round) {
        clickButton("Next round")
        waitForText("Round $next")
        // The button ignores a second press within 500 ms.
        E2ePlatform.sleep(700)
    }
}

/** Lets the app run for a while. The test clock only moves when the test moves it, so never use Thread.sleep alone. */
fun ComposeUiTest.settle(frames: Int = 6) {
    repeat(frames) {
        mainClock.advanceTimeBy(300)
        waitForIdle()
        E2ePlatform.sleep(100)
    }
}

@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.assertNoText(text: String, substring: Boolean = false) {
    onAllNodes(hasLabel(text, substring)).assertCountEquals(0)
}

/** The editable field that has this label. */
fun ComposeUiTest.field(label: String): SemanticsNodeInteraction = onNode(hasSetTextAction() and hasText(label))

/** A select field. It is not a text field, so it has the role of a dropdown list and no text action. */
fun ComposeUiTest.selectField(label: String): SemanticsNodeInteraction =
    onNode(androidx.compose.ui.test.SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.Role, androidx.compose.ui.semantics.Role.DropdownList) and hasText(label))

/**
 * Picks [option] in the select field [label]. A menu with many options starts with a search field (only the first 50
 * options compose), so the helper types the option there when the menu has one.
 */
fun ComposeUiTest.chooseInSelectField(label: String, option: String) {
    val dropdown = androidx.compose.ui.test.SemanticsMatcher.expectValue(
        androidx.compose.ui.semantics.SemanticsProperties.Role,
        androidx.compose.ui.semantics.Role.DropdownList,
    )
    val search = androidx.compose.ui.test.hasTestTag("select-search")
    val item = hasText(option) and hasClickAction() and !hasSetTextAction() and !dropdown
    selectField(label).also { runCatching { it.performScrollTo() } }.performClick()
    waitUntil(timeoutMillis = 5_000) { onAllNodes(search).fetchSemanticsNodes().isNotEmpty() || onAllNodes(item).fetchSemanticsNodes().isNotEmpty() }
    if (onAllNodes(search).fetchSemanticsNodes().isNotEmpty()) onAllNodes(search).onLast().performTextInput(option)
    waitUntil(timeoutMillis = 5_000) { onAllNodes(item).fetchSemanticsNodes().isNotEmpty() }
    onAllNodes(item).onLast().performClick()
    waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText(option) and dropdown).fetchSemanticsNodes().isNotEmpty() }
}

/** Base class of the admin app tests. Each test runs its body with [e2e]. */
abstract class AdminE2eTest {
    protected val app = AdminApp()

    protected val c: ComposeUiTest get() = app.compose

    /** Runs a test: a clean app, the local backend, and the screen in the message of a failure. */
    protected fun e2e(block: () -> Unit) = app.runTest(block)

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
fun ComposeUiTest.isOnScreen(text: String): Boolean =
    onAllNodes(hasText(text) or hasContentDescription(text)).fetchSemanticsNodes().isNotEmpty()

/** Waits until one of the texts is on the screen. */
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.waitForAnyText(vararg texts: String, timeoutMs: Long = WAIT_MS) {
    waitUntil(timeoutMillis = timeoutMs) { texts.any { isOnScreen(it) } }
}

/** Scrolls the lazy list of the screen until a text or an icon description is composed. */
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.scrollListTo(text: String, substring: Boolean = false) {
    val matcher = hasText(text, substring = substring) or hasContentDescription(text, substring = substring)
    waitUntil(timeoutMillis = WAIT_MS) {
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
fun ComposeUiTest.pickSeat(wind: String, which: Int) {
    val nodes = onAllNodes(hasContentDescription(wind), useUnmergedTree = true)
    val count = nodes.fetchSemanticsNodes().size
    nodes[count - 2 + which].performClick()
}

/** Presses the last button with this text. A dialog is the last one in the tree, so this reaches its button, not the page's. */
fun ComposeUiTest.clickLastButton(text: String) {
    onAllNodes((hasText(text) or hasContentDescription(text)) and hasClickAction()).onLast().tap()
}

/** The back of the system: the swipe from the left edge on iOS, the back button on Android. */
fun ComposeUiTest.pressBack() {
    waitForIdle()
    runOnIdle { E2eBack.input.backCompleted() }
    waitForIdle()
}

/**
 * The back of the system for the app under test. The app is not in a window of UIKit, so the tests give it its own
 * dispatcher and send the back there.
 */
object E2eBack {
    private val dispatcher = NavigationEventDispatcher()
    val input = DirectNavigationEventInput().also { dispatcher.addInput(it) }
    val owner = object : NavigationEventDispatcherOwner {
        override val navigationEventDispatcher = dispatcher
    }
}

/** Today on the clock of the device, as the date fields of the app show it: 28/11/2026. */
fun todayDdMmYyyy(): String {
    val today = kotlin.time.Clock.System.now().toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()).date
    return "${today.day.toString().padStart(2, '0')}/${today.month.number.toString().padStart(2, '0')}/${today.year}"
}

/** A text field has the focus, so the keyboard of the device shows. */
fun ComposeUiTest.keyboardShown(): Boolean = onAllNodes(isFocused() and hasSetTextAction()).fetchSemanticsNodes().isNotEmpty()

/** The clock of the device in milliseconds, for the waits of the tests. */
fun currentTimeMillis(): Long = kotlin.time.Clock.System.now().toEpochMilliseconds()

/** Runs [block] only when the local backend runs. The normal test run has none, and the tests then do nothing. */
fun onlyWithBackend(block: () -> Unit) {
    if (E2ePlatform.backendUrl != null) block()
}
