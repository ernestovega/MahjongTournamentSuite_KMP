package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * A gong hanging from its frame: the button that starts a round in the Timer. Material has no gong, so it is drawn
 * here in the 24 x 24 grid of the Material icons. `Icon` tints it like any other icon.
 */
val GongIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Gong",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        val solid = SolidColor(Color.Black)
        // The frame: a beam on two posts with feet.
        path(fill = solid) {
            moveTo(1.5f, 2f)
            lineTo(22.5f, 2f)
            lineTo(22.5f, 3.6f)
            lineTo(1.5f, 3.6f)
            close()
            moveTo(3f, 3.6f)
            lineTo(4.4f, 3.6f)
            lineTo(4.4f, 21f)
            lineTo(3f, 21f)
            close()
            moveTo(19.6f, 3.6f)
            lineTo(21f, 3.6f)
            lineTo(21f, 21f)
            lineTo(19.6f, 21f)
            close()
            moveTo(1.5f, 21f)
            lineTo(6f, 21f)
            lineTo(6f, 22.4f)
            lineTo(1.5f, 22.4f)
            close()
            moveTo(18f, 21f)
            lineTo(22.5f, 21f)
            lineTo(22.5f, 22.4f)
            lineTo(18f, 22.4f)
            close()
        }
        // The cords that hold the gong.
        path(stroke = solid, strokeLineWidth = 0.9f, strokeLineCap = StrokeCap.Round) {
            moveTo(8.6f, 3.6f)
            lineTo(10f, 7.4f)
            moveTo(15.4f, 3.6f)
            lineTo(14f, 7.4f)
        }
        // The disc with its rim, and the boss in the middle.
        path(fill = solid, pathFillType = PathFillType.EvenOdd) {
            disc(cx = 12f, cy = 13.2f, r = 6.4f)
            disc(cx = 12f, cy = 13.2f, r = 5f)
            disc(cx = 12f, cy = 13.2f, r = 4.4f)
            disc(cx = 12f, cy = 13.2f, r = 1.9f)
            disc(cx = 12f, cy = 13.2f, r = 1.1f)
        }
    }.build()
}

/** A full circle, as two arcs. */
private fun androidx.compose.ui.graphics.vector.PathBuilder.disc(cx: Float, cy: Float, r: Float) {
    moveTo(cx - r, cy)
    arcTo(r, r, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = cx + r, y1 = cy)
    arcTo(r, r, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = cx - r, y1 = cy)
    close()
}
