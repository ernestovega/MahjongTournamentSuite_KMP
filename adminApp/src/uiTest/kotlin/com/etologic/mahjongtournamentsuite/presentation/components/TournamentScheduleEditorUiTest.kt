package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import com.etologic.mahjongtournamentsuite.presentation.util.UiText
import mahjongtournamentsuite.adminapp.generated.resources.Res
import mahjongtournamentsuite.adminapp.generated.resources.schedule_agenda_invalid_date
import mahjongtournamentsuite.adminapp.generated.resources.schedule_agenda_invalid_time
import mahjongtournamentsuite.adminapp.generated.resources.schedule_round_invalid_date
import mahjongtournamentsuite.adminapp.generated.resources.schedule_round_invalid_time
import mahjongtournamentsuite.adminapp.generated.resources.schedule_round_missing_date
import mahjongtournamentsuite.adminapp.generated.resources.schedule_round_missing_time
import mahjongtournamentsuite.adminapp.generated.resources.schedule_round_outside_range
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The schedule of a tournament: the date and start of each round, the agenda, and the pickers of both. */
@OptIn(ExperimentalTestApi::class)
class TournamentScheduleEditorUiTest {
    private val original = Locale.getDefault()

    @BeforeTest
    fun english() = Locale.setDefault(Locale.ENGLISH)

    @AfterTest
    fun restore() = Locale.setDefault(original)

    private var rounds by mutableStateOf(listOf(RoundScheduleEditorRow(1, "06/02/2027", "10:00"), RoundScheduleEditorRow(2)))
    private var agenda by mutableStateOf(listOf(AgendaItemEditorRow("Lunch", "06/02/2027", "14:00")))

    private fun ComposeUiTest.show() = setContent {
        MtsTheme(useDarkTheme = false) {
            TournamentScheduleEditor(
                roundRows = rounds,
                onRoundRowsChange = { rounds = it },
                agendaRows = agenda,
                onAgendaRowsChange = { agenda = it },
                enabled = true,
                minimumDisplayDate = "06/02/2027",
                maximumDisplayDate = "07/02/2027",
                modifier = Modifier.verticalScroll(rememberScrollState()),
            )
        }
    }

    private fun ComposeUiTest.press(description: String) {
        onNode(hasContentDescription(description) and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        waitForIdle()
    }

    private fun ComposeUiTest.button(text: String) {
        onAllNodes(hasText(text) and hasClickAction()).onLast().performSemanticsAction(SemanticsActions.OnClick)
        waitForIdle()
    }

    @Test
    fun thePickersAndTheFieldsChangeTheRoundsAndTheAgenda() = runComposeUiTest {
        show()
        press("Choose round 1 date")
        button("OK")
        press("Choose round 2 date")
        button("Cancel")
        press("Choose round 1 start time")
        button("OK")
        press("Choose round 2 start time")
        button("Cancel")
        press("Choose activity 1 date")
        button("OK")
        press("Choose activity 1 start time")
        button("OK")
        waitForIdle()
        assertEquals("10:00", rounds[0].startTime)
        assertEquals("06/02/2027", agenda[0].date)

        onAllNodes(hasSetTextAction() and hasText("Lunch")).onFirst().performTextReplacement("Awards")
        onAllNodes(hasSetTextAction() and hasText("06/02/2027")).onLast().performTextReplacement("07022027")
        onAllNodes(hasSetTextAction() and hasText("14:00")).onLast().performTextReplacement("1530")
        waitForIdle()
        assertEquals("Awards", agenda[0].title)
        button("Add activity")
        assertEquals(2, agenda.size)
        button("Remove")
        assertEquals(1, agenda.size)
    }

    @Test
    fun withoutRoundsTheEditorAsksForThemFirst() = runComposeUiTest {
        rounds = emptyList()
        agenda = emptyList()
        show()
        waitForIdle()
        onAllNodesWithText("Enter a valid number of rounds", substring = true).onFirst().assertExists()
    }

    @Test
    fun eachWrongValueOfTheScheduleHasItsMessage() {
        val start = "2027-02-06"
        val end = "2027-02-07"
        fun error(rounds: List<RoundScheduleEditorRow>, agenda: List<AgendaItemEditorRow> = emptyList()) =
            tournamentScheduleEditorError(rounds, agenda, start, end)
        val good = RoundScheduleEditorRow(1, "06/02/2027", "10:00")
        assertEquals(UiText.of(Res.string.schedule_round_missing_date, 1), error(listOf(RoundScheduleEditorRow(1))))
        assertEquals(UiText.of(Res.string.schedule_round_invalid_date, 1), error(listOf(RoundScheduleEditorRow(1, "31/31/2027"))))
        assertEquals(UiText.of(Res.string.schedule_round_outside_range, 1), error(listOf(RoundScheduleEditorRow(1, "08/02/2027", "10:00"))))
        assertEquals(UiText.of(Res.string.schedule_round_missing_time, 1), error(listOf(good.copy(startTime = " "))))
        assertEquals(UiText.of(Res.string.schedule_round_invalid_time, 1), error(listOf(good.copy(startTime = "25:00"))))
        assertEquals(UiText.of(Res.string.schedule_agenda_invalid_date, 1), error(listOf(good), listOf(AgendaItemEditorRow("Lunch", "99/99/2027"))))
        assertEquals(UiText.of(Res.string.schedule_agenda_invalid_time, 2), error(listOf(good), listOf(AgendaItemEditorRow(""), AgendaItemEditorRow("Lunch", "", "9h"))))
        assertNull(error(listOf(good), listOf(AgendaItemEditorRow(""), AgendaItemEditorRow("Lunch", "", ""))))
    }
}
