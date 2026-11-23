package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.etologic.mahjongtournamentsuite.domain.model.IdCardSheetLayout
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

    private fun ComposeUiTest.dialog(initial: IdCardSheetLayout = IdCardSheetLayout(), savedStaffNames: List<String> = emptyList()) {
        setContent {
            MtsTheme(useDarkTheme = false) {
                IdCardsExportDialog(onExport = { exported = it }, onDismiss = {}, initialLayout = initial, savedStaffNames = savedStaffNames)
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
}
