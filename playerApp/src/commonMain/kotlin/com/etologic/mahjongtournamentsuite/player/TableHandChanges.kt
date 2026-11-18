package com.etologic.mahjongtournamentsuite.player

import com.etologic.mahjongtournamentsuite.domain.model.TableHand

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
