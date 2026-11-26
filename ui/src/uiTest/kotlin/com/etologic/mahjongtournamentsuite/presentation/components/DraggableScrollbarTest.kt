package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.lazy.LazyListItemInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DraggableScrollbarTest {
    private class Item(override val index: Int, override val offset: Int, override val size: Int = 100) : LazyListItemInfo {
        override val key: Any = index
        override val contentType: Any? = null
    }

    /** A viewport of 300 px that shows the items from [first], each 100 px tall, scrolled [scrolled] px into the first. */
    private fun metrics(total: Int, first: Int, scrolled: Int = 0) = scrollbarMetricsOf(
        totalItems = total,
        visible = (0..3).map { Item(first + it, it * 100 - scrolled) }.filter { it.index < total },
        viewportStart = 0,
        viewportEnd = 300,
        firstIndex = first,
        firstScrollOffset = scrolled,
        canScroll = true,
    )

    @Test
    fun topAndEndOfTheList() {
        assertEquals(0f, metrics(total = 100, first = 0)!!.position)
        assertEquals(1f, metrics(total = 100, first = 97)!!.position)
        assertEquals(0.03f, metrics(total = 100, first = 0)!!.visibleFraction, 0.001f)
    }

    @Test
    fun theMiddleIsHalfWay() {
        assertEquals(0.5f, metrics(total = 103, first = 50)!!.position, 0.001f)
    }

    @Test
    fun aListThatFitsHasNoScrollbar() {
        assertNull(scrollbarMetricsOf(3, listOf(Item(0, 0)), 0, 300, 0, 0, canScroll = false))
    }

    @Test
    fun aDragGoesToTheMatchingItem() {
        assertEquals(0, scrollbarTargetIndex(0f, totalItems = 100, visibleItems = 3f))
        assertEquals(99, scrollbarTargetIndex(1f, totalItems = 100, visibleItems = 3f))
        assertEquals(96, scrollbarTargetIndex(0.99f, totalItems = 100, visibleItems = 3f))
        assertEquals(50, scrollbarTargetIndex(0.5f, totalItems = 103, visibleItems = 3f))
    }
}
