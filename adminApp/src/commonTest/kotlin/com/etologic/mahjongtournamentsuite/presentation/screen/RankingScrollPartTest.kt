package com.etologic.mahjongtournamentsuite.presentation.screen

import kotlin.test.Test
import kotlin.test.assertEquals

class RankingScrollPartTest {
    @Test
    fun aListThatFitsIsOnePart() {
        assertEquals(1 to 1, rankingScrollPart(rows = 16, perScroll = 20, firstScreenRows = 18, screenRows = 20, firstItem = 0))
    }

    @Test
    fun aLongListCountsItsScreensAsThePagingGoes() {
        // First screen: rows 1 to 16. Then from row 21 (item 21), which shows rows 21 to 38, then from row 41.
        assertEquals(1 to 3, rankingScrollPart(rows = 45, perScroll = 20, firstScreenRows = 16, screenRows = 18, firstItem = 0))
        assertEquals(2 to 3, rankingScrollPart(rows = 45, perScroll = 20, firstScreenRows = 16, screenRows = 18, firstItem = 21))
        assertEquals(3 to 3, rankingScrollPart(rows = 45, perScroll = 20, firstScreenRows = 16, screenRows = 18, firstItem = 41))
    }

    @Test
    fun theLastRowsShowOnTheLastPart() {
        // Rows 21 to 40 fit on the second screen: no third one.
        assertEquals(2 to 2, rankingScrollPart(rows = 40, perScroll = 20, firstScreenRows = 16, screenRows = 20, firstItem = 21))
        // A step of less than one row still moves on.
        assertEquals(1 to 2, rankingScrollPart(rows = 3, perScroll = 0, firstScreenRows = 2, screenRows = 3, firstItem = 0))
    }
}
