package com.etologic.mahjongtournamentsuite.presentation.screen

import com.etologic.mahjongtournamentsuite.domain.model.ErrorGroup
import kotlin.test.Test
import kotlin.test.assertEquals

class ErrorLogFiltersTest {
    private val now = 1_000_000_000L
    private val hour = 3_600_000L

    private fun group(
        id: String,
        lastSeen: Long = now,
        count: Int = 1,
        kind: String = "handled",
        env: String = "prod",
        type: String = "IllegalStateException",
        message: String = "",
        platforms: Map<String, Int> = mapOf("android" to 1),
        apps: Map<String, Int> = mapOf("admin" to 1),
    ) = ErrorGroup(
        id = id, errorType = type, area = "", message = message, count = count, firstSeen = lastSeen, lastSeen = lastSeen,
        lastKind = kind, lastApp = apps.keys.first(), lastPlatform = platforms.keys.first(), lastVersion = "1.0", lastEnv = env,
        platforms = platforms, apps = apps,
    )

    private fun ids(groups: List<ErrorGroup>, filters: ErrorFilters = ErrorFilters()) = groups.applyFilters(filters, now).map { it.id }

    @Test
    fun theDefaultViewHidesDevelopmentBuildsAndKeepsUnknownOnes() {
        val groups = listOf(group("prod"), group("dev", env = "dev"), group("debug", env = "debug"), group("none", env = ""))
        assertEquals(listOf("prod", "none"), ids(groups))
        assertEquals(4, ids(groups, ErrorFilters(productionOnly = false)).size)
    }

    @Test
    fun theLatestSortPutsTheNewestFirstAndTheFrequentSortPutsTheBiggestCountFirst() {
        val groups = listOf(group("old", lastSeen = now - 5 * hour, count = 50), group("new", count = 2), group("mid", lastSeen = now - hour, count = 9))
        assertEquals(listOf("new", "mid", "old"), ids(groups))
        assertEquals(listOf("old", "mid", "new"), ids(groups, ErrorFilters(sort = ErrorSort.FREQUENT)))
    }

    @Test
    fun thePeriodKeepsOnlyTheRecentProblems() {
        val groups = listOf(group("now"), group("day", lastSeen = now - 30 * hour), group("week", lastSeen = now - 100 * hour))
        assertEquals(listOf("now"), ids(groups, ErrorFilters(periodHours = 24)))
        assertEquals(listOf("now", "day", "week"), ids(groups, ErrorFilters(periodHours = 24 * 7)))
    }

    @Test
    fun appPlatformAndKindFiltersCombine() {
        val groups = listOf(
            group("a", platforms = mapOf("web" to 3), apps = mapOf("player" to 3), kind = "crash"),
            group("b", platforms = mapOf("android" to 1, "web" to 1), apps = mapOf("admin" to 1, "player" to 1)),
            group("c", platforms = mapOf("desktop" to 1)),
        )
        assertEquals(listOf("a", "b"), ids(groups, ErrorFilters(platform = "web")).sorted())
        assertEquals(listOf("a", "b"), ids(groups, ErrorFilters(app = "player")).sorted())
        assertEquals(listOf("a"), ids(groups, ErrorFilters(app = "player", kind = "crash")))
        assertEquals(listOf("c"), ids(groups, ErrorFilters(platform = "desktop", app = "admin")))
    }

    @Test
    fun theSearchLooksInTypeAndMessageWithoutCaseOrAccents() {
        val groups = listOf(group("a", type = "SerializationException", message = "Campo inválido"), group("b", type = "TimeoutException"))
        assertEquals(listOf("a"), ids(groups, ErrorFilters(query = "serialization")))
        assertEquals(listOf("a"), ids(groups, ErrorFilters(query = "invalido")))
        assertEquals(listOf("b"), ids(groups, ErrorFilters(query = "TIMEOUT")))
    }
}
