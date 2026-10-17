package com.etologic.mahjongtournamentsuite.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IdCardSheetLayoutTest {
    @Test
    fun tenRealSizeCardsFitInTwoColumnsAndFiveRows() {
        val grid = IdCardSheetLayout(cardsPerSheet = 10, scalePercent = 100.0).grid()
        assertEquals(IdCardSheetGrid(columns = 2, rows = 5, scale = 1.0), grid)
    }

    @Test
    fun largerCardsReduceTheCapacity() {
        val layout = IdCardSheetLayout(cardsPerSheet = 10, scalePercent = 150.0)
        assertEquals(3, layout.capacity())
        assertEquals(IdCardSheetGrid(columns = 1, rows = 3, scale = 1.5), layout.grid())
    }

    @Test
    fun noGridWhenOneCardDoesNotFit() {
        assertNull(IdCardSheetLayout(scalePercent = 400.0).grid())
    }

    @Test
    fun landscapeSheetsHoldNineRealSizeCardsAndAllowLargerCards() {
        val layout = IdCardSheetLayout(cardsPerSheet = 20, landscape = true)
        assertEquals(IdCardSheetGrid(columns = 3, rows = 3, scale = 1.0), layout.grid())
        assertEquals(335.0, layout.maxScalePercent())
        assertEquals(233.5, IdCardSheetLayout().maxScalePercent())
    }

    /** Every orientation, double-sided choice, card size and number of cards that a user can choose. */
    private fun combinations(): Sequence<IdCardSheetLayout> = sequence {
        for (landscape in listOf(false, true)) {
            for (doubleSided in listOf(false, true)) {
                var scale = 25.0
                while (scale <= 400.0) {
                    for (cards in 1..40) {
                        yield(IdCardSheetLayout(cards, scale, cutMarks = true, landscape = landscape, doubleSided = doubleSided))
                    }
                    scale += 2.5
                }
            }
        }
    }

    @Test
    fun everyGridKeepsAllCardsInsideThePrintableAreaWithoutEmptyRows() {
        var checked = 0
        combinations().forEach { layout ->
            val grid = layout.grid() ?: return@forEach
            checked++
            val cardWidth = IdCardSheetLayout.CARD_WIDTH_PT * grid.scale
            val cardHeight = IdCardSheetLayout.CARD_HEIGHT_PT * grid.scale
            val margins = 2 * IdCardSheetLayout.SHEET_MARGIN_PT
            assertTrue(cardWidth * grid.columns <= layout.pageWidthPt - margins + 1e-6, "width $layout")
            assertTrue(cardHeight * grid.rows <= layout.pageHeightPt - margins + 1e-6, "height $layout")
            val count = minOf(layout.cardsPerSheet, layout.capacity())
            assertTrue(grid.columns * grid.rows >= count, "holds all $layout")
            assertTrue((grid.rows - 1) * grid.columns < count, "empty row $layout")
        }
        assertTrue(checked > 1000)
    }

    @Test
    fun theLargestCardSizeFitsOnceAndAnyLargerSizeFitsNever() {
        listOf(false, true).forEach { landscape ->
            val probe = IdCardSheetLayout(cardsPerSheet = 1, landscape = landscape)
            val largest = probe.maxScalePercent()
            val fits = probe.copy(scalePercent = largest).grid()
            assertEquals(IdCardSheetGrid(columns = 1, rows = 1, scale = largest / 100), fits)
            assertNull(probe.copy(scalePercent = largest + 0.2).grid())
        }
    }

    @Test
    fun cardsNeverOverlapAndEveryBackSitsBehindItsFront() {
        combinations().forEach { layout ->
            val grid = layout.grid() ?: return@forEach
            val count = minOf(layout.cardsPerSheet, grid.columns * grid.rows)
            val frontCells = mutableSetOf<IdCardSheetCell>()
            val backCells = mutableSetOf<IdCardSheetCell>()
            for (index in 0 until count) {
                val front = layout.cell(index, back = false)!!
                val back = layout.cell(index, back = true)!!
                assertTrue(front.column in 0 until grid.columns && front.row in 0 until grid.rows, "front $layout")
                assertTrue(back.column in 0 until grid.columns && back.row in 0 until grid.rows, "back $layout")
                frontCells += front
                backCells += back
                assertEquals(front.row, back.row, "row $layout")
                val expectedBackColumn = if (layout.doubleSided) grid.columns - 1 - front.column else front.column
                assertEquals(expectedBackColumn, back.column, "mirror $layout")
            }
            assertEquals(count, frontCells.size, "front overlap $layout")
            assertEquals(count, backCells.size, "back overlap $layout")
        }
    }
}
