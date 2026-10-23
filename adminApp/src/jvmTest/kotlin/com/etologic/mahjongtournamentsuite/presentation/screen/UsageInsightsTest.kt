package com.etologic.mahjongtournamentsuite.presentation.screen

import com.etologic.mahjongtournamentsuite.domain.model.FeatureUsage
import com.etologic.mahjongtournamentsuite.domain.model.UsageAccounts
import com.etologic.mahjongtournamentsuite.domain.model.UsagePeriod
import com.etologic.mahjongtournamentsuite.domain.model.UsageReport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UsageInsightsTest {
    private fun period(saves: Int = 0, playerSaves: Int = 0, conflicts: Int = 0) = UsagePeriod(
        activeUsers = 0, saves = saves, handChanges = 0, playerSaves = playerSaves, editorSaves = saves - playerSaves,
        actions = 0, activeTournaments = 0, tournamentsCreated = 0, newAccounts = 0, conflicts = conflicts, resets = 0,
    )

    private fun report(
        current: UsagePeriod = period(),
        previous: UsagePeriod = period(),
        accounts: UsageAccounts = UsageAccounts(0, 0, 0, 0, 0, 0, 0, 0, 0),
        features: List<FeatureUsage> = emptyList(),
        heatmap: List<List<Int>> = emptyList(),
    ) = UsageReport(
        generatedAt = "", periodDays = 30, current = current, previous = previous, accounts = accounts, daily = emptyList(),
        heatmap = heatmap, users = emptyList(), features = features, funnel = emptyList(), countries = emptyList(),
        tournaments = emptyList(),
    )

    @Test
    fun aFallOfTheSavesIsAWarning() {
        val insights = usageInsights(report(current = period(saves = 10), previous = period(saves = 20)))
        assertTrue(insights.any { it.level == InsightLevel.WARNING && "fell by 50%" in it.text })
    }

    @Test
    fun accountsThatNeverSignedInAreListed() {
        val insights = usageInsights(report(accounts = UsageAccounts(10, 1, 2, 7, 0, 3, 1, 1, 5)))
        assertTrue(insights.any { it.text == "5 of 10 accounts never signed in." })
    }

    @Test
    fun unusedFeaturesAreNamed() {
        val feature = FeatureUsage("substitution", "Player substitution", "Players", true, 0, 0, null, null)
        val insights = usageInsights(report(features = listOf(feature)))
        assertTrue(insights.any { "Player substitution" in it.text })
    }

    @Test
    fun theBusiestTimeUsesTheHeatmap() {
        val heatmap = List(7) { day -> List(24) { hour -> if (day == 5 && hour == 11) 9 else 1 } }
        val insights = usageInsights(report(heatmap = heatmap))
        assertTrue(insights.any { "Saturday at 11:00" in it.text })
    }

    @Test
    fun aHighConflictRateIsAWarning() {
        val insights = usageInsights(report(current = period(saves = 8, conflicts = 4)))
        assertTrue(insights.any { it.level == InsightLevel.WARNING && "refused" in it.text })
    }

    @Test
    fun changeTextHandlesAnEmptyBase() {
        assertEquals("New against the previous 7 days.", changeText(3, 0, 7))
        assertEquals("Up 50% against the previous 7 days.", changeText(3, 2, 7))
        assertEquals("Down 50% against the previous 30 days.", changeText(1, 2, 30))
    }

    @Test
    fun agoTextIsShort() {
        assertEquals("never", agoText(null))
        assertEquals("yesterday", agoText(1))
        assertEquals("12 days ago", agoText(12))
    }
}
