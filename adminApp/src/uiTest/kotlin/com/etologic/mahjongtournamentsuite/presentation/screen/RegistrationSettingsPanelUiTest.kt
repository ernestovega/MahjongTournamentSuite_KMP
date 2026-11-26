package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import com.etologic.mahjongtournamentsuite.domain.model.AdminRegistrations
import com.etologic.mahjongtournamentsuite.domain.model.RegistrationPhase
import com.etologic.mahjongtournamentsuite.domain.model.RegistrationSettings
import com.etologic.mahjongtournamentsuite.presentation.components.LocalPressTimeSource
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

/** Each setting of the registrations turns into its field, and Save gives the new settings. */
@OptIn(ExperimentalTestApi::class)
class RegistrationSettingsPanelUiTest {
    private val clock = TestTimeSource()
    private val saved = mutableListOf<RegistrationSettings>()
    private var draws = 0

    private fun ComposeUiTest.show(settings: RegistrationSettings) {
        setContent {
            var editing by mutableStateOf<RegistrationSettingField?>(null)
            CompositionLocalProvider(LocalPressTimeSource provides clock) {
                MtsTheme(useDarkTheme = false) {
                    RegistrationSettingsPanel(
                        data = AdminRegistrations(settings, isTeams = false, drawPending = false, publicLink = "https://mts.test/open", registrations = emptyList(), teams = emptyList()),
                        editing = editing,
                        busy = false,
                        canConfigure = true,
                        onEdit = { editing = it },
                        onCancel = { editing = null },
                        onSave = {
                            saved += it
                            editing = null
                        },
                        onCopyLink = {},
                        onDraw = { draws += 1 },
                    )
                }
            }
        }
        waitForIdle()
    }

    private fun ComposeUiTest.tapTag(tag: String) {
        clock += 1.seconds
        onNode(hasTestTag(tag)).performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.tap(text: String) {
        clock += 1.seconds
        onAllNodes(hasText(text) and hasClickAction()).onLast().performSemanticsAction(SemanticsActions.OnClick)
        waitForIdle()
    }

    @Test
    fun placesAndTheLinkAreChangedAndACancelKeepsThem() = runComposeUiTest {
        show(RegistrationSettings(maxPlayers = null, slug = "open", confirmed = 3))

        tapTag("registrations-edit-places")
        onNode(hasTestTag("registrations-places-field") and hasSetTextAction()).performTextReplacement("4a8")
        tap("Save")
        assertEquals(48, saved.last().maxPlayers)

        tapTag("registrations-edit-link")
        onNode(hasTestTag("registrations-slug-field") and hasSetTextAction()).performTextReplacement("Big Open 2027!")
        tap("Save")
        assertEquals("bigopen2027", saved.last().slug)

        tapTag("registrations-edit-closes")
        tap("Cancel")
        assertEquals(2, saved.size)
    }

    @Test
    fun theLastDayIsSavedAndClosedRegistrationsOpenOrDraw() = runComposeUiTest {
        show(RegistrationSettings(status = RegistrationPhase.CLOSED, closesOn = "2027-02-06"))

        tapTag("registrations-edit-closes")
        tap("Save")
        assertEquals("2027-02-06", saved.last().closesOn)

        tap("Make the draw")
        assertEquals(1, draws)
        tapTag("registrations-toggle-status")
        assertEquals(RegistrationPhase.OPEN, saved.last().status)
    }
}
