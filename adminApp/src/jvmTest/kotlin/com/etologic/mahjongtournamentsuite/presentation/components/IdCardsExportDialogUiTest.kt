package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import com.etologic.mahjongtournamentsuite.domain.model.GlobalUserRole
import com.etologic.mahjongtournamentsuite.domain.model.IdCardSheetLayout
import com.etologic.mahjongtournamentsuite.domain.model.IdCardStaffCandidate
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The options of the PDF of the ID cards (23-nov): the back of the cards and the extra sheets of the staff. */
@OptIn(ExperimentalTestApi::class)
class IdCardsExportDialogUiTest {
    private var previous: Locale = Locale.getDefault()

    @BeforeTest
    fun english() {
        previous = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
    }

    @AfterTest
    fun restore() = Locale.setDefault(previous)

    private fun ComposeUiTest.export(): IdCardSheetLayout? {
        // The buttons ignore a second press within 500 ms (real time).
        Thread.sleep(700)
        onNodeWithText("Create PDF").performClick()
        waitForIdle()
        return exported
    }

    private var exported: IdCardSheetLayout? = null

    private fun ComposeUiTest.dialog(
        initial: IdCardSheetLayout = IdCardSheetLayout(),
        savedStaffNames: List<String> = emptyList(),
        candidates: List<IdCardStaffCandidate> = emptyList(),
        compact: Boolean = false,
    ) {
        setContent {
            CompositionLocalProvider(LocalCompactLayout provides compact) {
                MtsTheme(useDarkTheme = false) {
                    IdCardsExportDialog(
                        onExport = { exported = it },
                        onDismiss = {},
                        initialLayout = initial,
                        savedStaffNames = savedStaffNames,
                        staffCandidates = candidates,
                    )
                }
            }
        }
        waitForIdle()
    }

    private fun ComposeUiTest.chooseBack(current: String, choice: String) {
        Thread.sleep(700)
        onAllNodesWithText(current).onFirst().performClick()
        waitForIdle()
        onAllNodesWithText(choice).onFirst().performClick()
        waitForIdle()
    }

    @Test
    fun byDefaultTheCardsHaveTheTableOfRoundsOnTheBack() = runComposeUiTest {
        dialog()
        onNodeWithText("Back sheet").assertExists()

        val layout = export()!!
        assertTrue(layout.backs)
        assertFalse(layout.backSameAsFront)
    }

    @Test
    fun theBackCanRepeatTheFrontOrBeLeftOut() = runComposeUiTest {
        dialog()

        chooseBack("Table of rounds", "Same as the front")
        val same = export()!!
        assertTrue(same.backs)
        assertTrue(same.backSameAsFront)

        chooseBack("Same as the front", "No back")
        onNodeWithText("Back sheet").assertDoesNotExist()
        val none = export()!!
        assertFalse(none.backs)
        assertFalse(none.backSameAsFront, "no back and repeating the front are one choice")
    }

    @Test
    fun theSavedStaffNamesFillTheSheetsOfTheStaffAndGoWithThePdf() = runComposeUiTest {
        dialog(IdCardSheetLayout(staffSheets = 1), savedStaffNames = listOf("Lucía Gómez", "Pedro Martín"))

        assertEquals(listOf("Lucía Gómez", "Pedro Martín"), export()!!.cleanStaffNames())
    }

    @Test
    fun withoutSheetsOfTheStaffNoNameGoesWithThePdf() = runComposeUiTest {
        dialog(IdCardSheetLayout(staffSheets = 0, staffNames = listOf("Lucía Gómez")))

        val layout = export()!!
        assertEquals(0, layout.staffSheets)
        assertEquals(emptyList(), layout.staffNames)
    }

    private fun ComposeUiTest.field(label: String) = onNode(hasSetTextAction() and hasText(label))
    private fun mm(value: Double) = String.format(Locale.ENGLISH, "%.1f", value)
    /** The icon button, not the tooltip around it. */
    private fun ComposeUiTest.button(description: String, index: Int) =
        onAllNodes(hasClickAction() and hasContentDescription(description))[index].performScrollTo()

    private fun ComposeUiTest.press(node: androidx.compose.ui.test.SemanticsNodeInteraction) {
        Thread.sleep(700)
        node.performClick()
        waitForIdle()
    }

    @Test
    fun aWidthOrAHeightInMillimetersSetsTheSizeOfTheCards() = runComposeUiTest {
        dialog()
        field("Width (mm)").performTextReplacement(mm(IdCardSheetLayout(scalePercent = 60.0).cardWidthMm()))
        waitForIdle()
        assertEquals(60.0, export()!!.scalePercent, 0.5)

        field("Height (mm)").performTextReplacement(mm(IdCardSheetLayout(scalePercent = 70.0).cardHeightMm()))
        waitForIdle()
        assertEquals(70.0, export()!!.scalePercent, 0.5)
    }

    @Test
    fun aSizeOutOfRangeSaysTheRangeAndTheDialogCannotCreate() = runComposeUiTest {
        dialog()
        field("Width (mm)").performTextReplacement("1")
        field("Height (mm)").performTextReplacement("x")
        waitForIdle()
        assertEquals(2, onAllNodesWithText("Use ", substring = true).fetchSemanticsNodes().size)
        onNode(hasClickAction() and hasText("Create PDF")).assertIsNotEnabled()
    }

    @Test
    fun landscapeTurnsTheSheetAndRestoreGoesBackToTheDefaults() = runComposeUiTest {
        dialog()
        press(onNodeWithContentDescription("Landscape"))
        assertTrue(export()!!.landscape)

        press(onNodeWithContentDescription("Portrait"))
        assertFalse(export()!!.landscape)

        press(onNodeWithContentDescription("Landscape"))
        press(onNodeWithText("Restore settings"))
        assertEquals(IdCardSheetLayout(), export()!!)
    }

    @Test
    fun onAPhoneRestoreIsAnIcon() = runComposeUiTest {
        dialog(IdCardSheetLayout(landscape = true), compact = true)
        press(onNodeWithContentDescription("Restore settings"))
        assertFalse(export()!!.landscape)
    }

    @Test
    fun theStaffNamesAreWrittenAddedRemovedOrChosenFromTheUsers() = runComposeUiTest {
        dialog(candidates = listOf(IdCardStaffCandidate("u1", "Rosa Ruiz", GlobalUserRole.EDITOR, inTournament = true)))
        // The second counter is the staff one. Its first sheet opens one empty name.
        press(button("Add a sheet", 1))
        // The fields are the width, the height and then the names.
        onNodeWithText("Name and surname").assertExists()
        onAllNodes(hasSetTextAction())[2].performScrollTo().performTextInput("Ana Gil")
        waitForIdle()
        press(onNode(hasClickAction() and hasText("Add person")).performScrollTo())

        press(onAllNodesWithText("Choose a user").onFirst().performScrollTo())
        press(onAllNodesWithText("Rosa Ruiz").onFirst())
        assertEquals(listOf("Ana Gil", "Rosa Ruiz"), export()!!.cleanStaffNames())

        press(button("Remove person", 0))
        assertEquals(listOf("Rosa Ruiz"), export()!!.cleanStaffNames())

        press(button("Remove a sheet", 1))
        assertEquals(emptyList(), export()!!.staffNames)
    }
}
