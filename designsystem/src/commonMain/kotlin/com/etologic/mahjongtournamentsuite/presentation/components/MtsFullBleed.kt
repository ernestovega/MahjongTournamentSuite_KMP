package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp

/*
 * Ernesto, 2026-11-26: a list or a scrolling content that takes the whole width of a screen or a dialog reaches its
 * edges, so the fading edges take the whole width. The margins of the design go inside the scroll.
 */

/** The padding that the Material AlertDialog keeps around its title, text and buttons. */
val MtsDialogPadding: Dp = 24.dp

/**
 * Lets the content go [start] past its start, [end] past its end and [bottom] past its bottom, over the margins of its
 * container, while it keeps the place it had in the layout. The content then puts those margins back inside, as padding
 * of its scroll. The size keeps working as before: content that wraps still wraps, and content that fills still fills.
 */
fun Modifier.bleedHorizontally(start: Dp, end: Dp = start, bottom: Dp = 0.dp): Modifier = layout { measurable, constraints ->
    val startPx = start.roundToPx()
    val endPx = end.roundToPx()
    val extraWidth = if (constraints.hasBoundedWidth) startPx + endPx else 0
    val extraHeight = if (constraints.hasBoundedHeight) bottom.roundToPx() else 0
    if (extraWidth == 0 && extraHeight == 0) {
        val placeable = measurable.measure(constraints)
        return@layout layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
    val placeable = measurable.measure(
        constraints.copy(
            minWidth = constraints.minWidth + extraWidth,
            maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth + extraWidth else Constraints.Infinity,
            minHeight = constraints.minHeight + extraHeight,
            maxHeight = if (constraints.hasBoundedHeight) constraints.maxHeight + extraHeight else Constraints.Infinity,
        ),
    )
    layout(
        constraints.constrainWidth(placeable.width - extraWidth),
        constraints.constrainHeight(placeable.height - extraHeight),
    ) { placeable.place(if (extraWidth == 0) 0 else -startPx, 0) }
}

/**
 * [bleedHorizontally] over the padding of a card ([MtsTheme.layout.cardPadding][com.etologic.mahjongtournamentsuite.presentation.theme.MtsLayout.cardPadding]):
 * for the list that ends a card, which then reaches its sides and its bottom edge.
 */
fun Modifier.cardFullBleed(cardPadding: Dp): Modifier = bleedHorizontally(cardPadding, cardPadding, bottom = cardPadding)

/** [bleedHorizontally] over the side padding of an AlertDialog, for the scrolling content of its text. */
fun Modifier.dialogFullBleed(): Modifier = bleedHorizontally(MtsDialogPadding)

/**
 * The scroll of the content of an AlertDialog: it reaches both sides of the dialog, so its fading edges take the whole
 * width, the scrollbar goes over the end margin, and the content keeps the margins of the dialog inside the scroll.
 */
@Composable
fun Modifier.dialogVerticalScroll(state: ScrollState = rememberScrollState()): Modifier = this
    .dialogFullBleed()
    .scrollIndicators(state, scrollbar = MtsScrollbar.Overlay)
    .verticalScroll(state)
    .padding(horizontal = MtsDialogPadding)

/** The content padding of a lazy list with [dialogFullBleed]: the side margins of the dialog, inside the list. */
fun dialogListPadding(top: Dp = 0.dp, bottom: Dp = 0.dp): PaddingValues =
    PaddingValues(start = MtsDialogPadding, top = top, end = MtsDialogPadding, bottom = bottom)
