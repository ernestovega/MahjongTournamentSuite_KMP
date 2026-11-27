package com.etologic.mahjongtournamentsuite.player

import com.etologic.mahjongtournamentsuite.presentation.util.UiText
import mahjongtournamentsuite.playerapp.generated.resources.Res
import mahjongtournamentsuite.playerapp.generated.resources.age_hours
import mahjongtournamentsuite.playerapp.generated.resources.age_minutes
import mahjongtournamentsuite.playerapp.generated.resources.age_seconds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** How old the data of the tournaments is, in the unit that fits. */
class AgeTextTest {
    @Test
    fun secondsMinutesAndHoursEachHaveTheirUnit() {
        assertEquals(UiText.Plural(Res.plurals.age_seconds, 45, listOf(45L)), 45.seconds.toAgeText())
        assertEquals(UiText.Plural(Res.plurals.age_minutes, 2, listOf(2L)), 150.seconds.toAgeText())
        assertEquals(UiText.Plural(Res.plurals.age_hours, 3, listOf(3L)), (3.hours + 59.minutes).toAgeText())
    }
}
