package com.etologic.mahjongtournamentsuite.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.etologic.mahjongtournamentsuite.domain.model.TableHand
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import kotlinx.coroutines.delay

/** Whether the list of hands shows the hand: it has a result or a penalty. */
internal fun TableHand.isListed(): Boolean = isDone || penaltyRows().isNotEmpty()

/**
 * What changed in the list of hands of a table from one load to the next. [added] and [edited] are hand numbers.
 * [removed] keeps the hands as they were, so the list can fold them away. [serial] grows with every change.
 */
internal data class HandListChange(
    val added: Set<Int>,
    val edited: Set<Int>,
    val removed: List<TableHand>,
    val serial: Int,
)

/** The listed hands that are new, changed or gone. Null when the list shows the same hands. */
internal fun handListChange(before: List<TableHand>, after: List<TableHand>, serial: Int): HandListChange? {
    val old = before.filter { it.isListed() }.associateBy { it.handId }
    val new = after.filter { it.isListed() }.associateBy { it.handId }
    val added = new.keys - old.keys
    val edited = new.filter { (id, hand) -> old[id]?.let { it != hand } == true }.keys
    val removed = old.filterKeys { it !in new }.values.toList()
    if (added.isEmpty() && edited.isEmpty() && removed.isEmpty()) return null
    return HandListChange(added, edited, removed, serial)
}

/**
 * Follows the hands of the table screen between loads and says which change to animate. The first load shows at once.
 * A later change animates, also on the phone that saved it, unless the device asks for less motion.
 */
internal class TableChangeTracker {
    private var last: List<TableHand>? = null
    private var serial = 0

    /** The change to animate for these hands, or null when nothing should move. */
    fun update(hands: List<TableHand>, animate: Boolean): HandListChange? {
        val before = last
        last = hands
        if (before == null) return null
        val change = handListChange(before, hands, serial + 1) ?: return null
        serial = change.serial
        return change.takeIf { animate }
    }
}

/** How long a new hand takes to come in, and a gone hand to fold away. */
internal const val HAND_ENTER_MILLIS = 1_350
internal const val HAND_EXIT_MILLIS = 1_200

/**
 * The glow of a hand, from 0 (none) to 1. A new hand starts lit and fades out after it came in. A corrected hand
 * glows twice softly.
 */
@Composable
internal fun rememberHandGlow(handId: Int, change: HandListChange?): Float {
    val glow = remember { Animatable(0f) }
    LaunchedEffect(change?.serial) {
        when {
            change == null -> Unit
            handId in change.added -> {
                glow.snapTo(1f)
                delay(HAND_ENTER_MILLIS.toLong())
                glow.animateTo(0f, tween(3_000))
            }
            handId in change.edited -> repeat(2) {
                glow.animateTo(1f, tween(750))
                glow.animateTo(0f, tween(1_050))
            }
        }
    }
    return glow.value
}

/** Green for a new hand, the primary color for a corrected one. */
@Composable
internal fun handGlowColor(handId: Int, change: HandListChange?): Color = if (change != null && handId in change.edited) MaterialTheme.colorScheme.primary else MtsTheme.colors.gain
