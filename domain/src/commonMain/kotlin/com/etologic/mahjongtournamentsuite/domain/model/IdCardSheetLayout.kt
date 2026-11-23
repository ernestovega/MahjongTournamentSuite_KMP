package com.etologic.mahjongtournamentsuite.domain.model

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min

/** Describes how ID cards are placed on A4 sheets. The backend uses the same rules. */
data class IdCardSheetLayout(
    val cardsPerSheet: Int = 10,
    /** The card size in percent of the real card size. It can have decimals. */
    val scalePercent: Double = 100.0,
    val cutMarks: Boolean = true,
    val landscape: Boolean = false,
    /** Mirrors the back sheet so each back is behind its front when printing on both sides. */
    val doubleSided: Boolean = true,
    /** Adds a back sheet after each front sheet. Without it the PDF only has the fronts. */
    val backs: Boolean = true,
    /** Full sheets of blank cards for substitutes: no name and no number, both written by hand. */
    val substituteSheets: Int = 0,
    /** Full sheets of staff cards: STAFF instead of the number. */
    val staffSheets: Int = 0,
    /** The names of the staff cards, in order. The other staff cards have no name. */
    val staffNames: List<String> = emptyList(),
) {
    /** The staff names without the empty lines. */
    fun cleanStaffNames(): List<String> = staffNames.map { it.trim() }.filter { it.isNotEmpty() }

    /** The staff sheets that are printed: more than the chosen ones when the names do not fit on them. */
    fun printedStaffSheets(cardsOnOneSheet: Int): Int {
        val perSheet = cardsOnOneSheet.coerceAtLeast(1)
        return maxOf(staffSheets, (cleanStaffNames().size + perSheet - 1) / perSheet)
    }

    val pageWidthPt: Double get() = if (landscape) A4_HEIGHT_PT else A4_WIDTH_PT
    val pageHeightPt: Double get() = if (landscape) A4_WIDTH_PT else A4_HEIGHT_PT

    /** The number of cards that fit in one row and in one column at this card size. */
    private fun maxColumns(): Int = floor((pageWidthPt - 2 * SHEET_MARGIN_PT) / (CARD_WIDTH_PT * scalePercent / 100)).toInt()
    private fun maxRows(): Int = floor((pageHeightPt - 2 * SHEET_MARGIN_PT) / (CARD_HEIGHT_PT * scalePercent / 100)).toInt()

    /** The grid of this layout, or null when not even one card fits on the sheet. */
    fun grid(): IdCardSheetGrid? {
        val maxColumns = maxColumns()
        val maxRows = maxRows()
        if (maxColumns < 1 || maxRows < 1) return null
        val count = cardsPerSheet.coerceIn(1, maxColumns * maxRows)
        val columns = min(maxColumns, count)
        val rows = ceil(count.toDouble() / columns).toInt()
        return IdCardSheetGrid(columns = columns, rows = rows, scale = scalePercent / 100)
    }

    /**
     * The grid cell of the card with this index on the front or the back sheet, or null when no card fits.
     * The sheet turns like a book page, so the columns of the back are mirrored when printing on both sides.
     */
    fun cell(index: Int, back: Boolean): IdCardSheetCell? {
        val grid = grid() ?: return null
        val column = index % grid.columns
        val row = index / grid.columns
        return IdCardSheetCell(
            column = if (back && doubleSided) grid.columns - 1 - column else column,
            row = row,
        )
    }

    /** The largest number of cards that fit on one sheet at this card size. */
    fun capacity(): Int = (maxColumns() * maxRows()).coerceAtLeast(0)

    /** The largest card size, in percent, that still fits once on the sheet. */
    fun maxScalePercent(): Double = floor(
        min(
            (pageWidthPt - 2 * SHEET_MARGIN_PT) / CARD_WIDTH_PT,
            (pageHeightPt - 2 * SHEET_MARGIN_PT) / CARD_HEIGHT_PT,
        ) * 1000,
    ) / 10

    /** The width of one card in millimeters. */
    fun cardWidthMm(): Double = CARD_WIDTH_PT * scalePercent / 100 * MM_PER_PT

    /** The height of one card in millimeters. */
    fun cardHeightMm(): Double = CARD_HEIGHT_PT * scalePercent / 100 * MM_PER_PT

    companion object {
        const val A4_WIDTH_PT = 595.28
        const val A4_HEIGHT_PT = 841.89
        const val CARD_WIDTH_PT = 242.88
        const val CARD_HEIGHT_PT = 153.0
        const val SHEET_MARGIN_PT = 14.0
        const val MIN_SCALE_PERCENT = 50.0
        const val MM_PER_PT = 25.4 / 72
        const val MAX_EXTRA_SHEETS = 20
        const val MAX_STAFF_NAMES = 200
        const val MAX_STAFF_NAME_LENGTH = 60
    }
}

data class IdCardSheetCell(val column: Int, val row: Int)

data class IdCardSheetGrid(val columns: Int, val rows: Int, val scale: Double)
