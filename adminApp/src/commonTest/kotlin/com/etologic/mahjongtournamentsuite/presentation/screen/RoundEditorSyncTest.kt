package com.etologic.mahjongtournamentsuite.presentation.screen

import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.domain.model.TableKey
import com.etologic.mahjongtournamentsuite.domain.model.TournamentPlayer
import com.etologic.mahjongtournamentsuite.presentation.presenter.TableManagerPresenter
import com.etologic.mahjongtournamentsuite.presentation.presenter.TablesPresenter
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** One user with an open round editor on a shared fake server. */
private class TestUser(val server: FakeTournamentRepository, val name: String = "user") {
    var tournamentReloads = 0
    var tournamentReloadWorks = true
    var clock = kotlin.time.Instant.parse("2026-10-11T15:30:00Z")
    var round: Int? = 1
    var table: Int? = 1
    val sync = RoundEditorSync(
        tournamentId = "t1",
        tablePresenter = TableManagerPresenter(server, Logger.withTag(name)),
        tablesPresenter = TablesPresenter(server, NoPlayersRepository(), Logger.withTag(name)),
        selectedRoundId = { round },
        selectedTableId = { table },
        reloadTournaments = { tournamentReloads++; tournamentReloadWorks },
        now = { clock },
    )

    /** Opens the editors for the given tables of round 1, like the screen after its first load. */
    fun open(vararg tableIds: Int, roundId: Int = 1) {
        sync.allTables = server.summaries()
        sync.tableEditors = tableIds.associateWith { id ->
            val (state, hands) = server.tables.getValue(TableKey(roundId, id))
            TableManagerEditorState.from(state, hands)
        }
        sync.isLoading = false
    }

    fun editor(tableId: Int = 1) = sync.tableEditors.getValue(tableId)

    fun typeHandScore(score: String, tableId: Int = 1, handId: Int = 1) {
        editor(tableId).hands.first { it.handId == handId }.handScore = score
    }

    fun typeWinner(winner: String, tableId: Int = 1, handId: Int = 1) {
        editor(tableId).hands.first { it.handId == handId }.playerWinnerId = winner
    }

    suspend fun save(vararg tableIds: Int) = sync.saveTables(tableIds.toList().ifEmpty { listOf(1) })
}

private fun FakeTournamentRepository.withTables(vararg ids: Int, roundId: Int = 1): FakeTournamentRepository {
    ids.forEach { seed(syncTable(version = 1, roundId = roundId, tableId = it)) }
    return this
}

class RoundEditorSyncTest {
    // ------------------------------------------------------------------ polling

    @Test
    fun checkWithNoChangeAsksOnlyForTheManifest() = runTest {
        val server = FakeTournamentRepository().withTables(1, 2)
        val user = TestUser(server).also { it.open(1, 2) }

        user.sync.checkForChanges()

        assertEquals(listOf("manifest"), server.calls)
        assertTrue(user.sync.updateNotices.isEmpty())
    }

    @Test
    fun anotherUsersSaveReplacesTheOpenTableAndTellsTheUser() = runTest {
        val server = FakeTournamentRepository().withTables(1)
        val user = TestUser(server).also { it.open(1) }
        server.otherUserSaves(1, 1, handScore = "24")

        user.sync.checkForChanges()

        assertEquals(2L, user.editor().version)
        assertEquals("24", user.editor().hands.first { it.handId == 1 }.handScore)
        val notice = user.sync.updateNotices.single()
        assertEquals(TableKey(1, 1), notice.key)
        assertTrue(notice.lostChanges.isEmpty())
        assertEquals(2L, user.sync.allTables.single().version)
    }

    @Test
    fun unsavedTypingIsReplacedAndListedAsLost() = runTest {
        val server = FakeTournamentRepository().withTables(1)
        val user = TestUser(server).also { it.open(1) }
        user.typeHandScore("16")
        user.typeWinner("2")
        server.otherUserSaves(1, 1, east = "1", handScore = "8")

        user.sync.checkForChanges()

        assertFalse(user.sync.hasUnsavedChanges)
        assertEquals("8", user.editor().hands.first().handScore)
        val lost = user.sync.updateNotices.single().lostChanges.associate { it.label to it.value }
        assertEquals("16", lost["Hand 1 · Hand score"])
        assertEquals("2", lost["Hand 1 · Player winner id"])
    }

    @Test
    fun theFocusReturnsOnlyWhenTheSelectedTableWasReplaced() = runTest {
        val server = FakeTournamentRepository().withTables(1, 2)
        val user = TestUser(server).also { it.open(1, 2) }
        val before = user.sync.focusToken

        server.otherUserSaves(1, 2, handScore = "8")
        user.sync.checkForChanges()
        assertEquals(before, user.sync.focusToken)

        server.otherUserSaves(1, 1, handScore = "8")
        user.sync.checkForChanges()
        assertEquals(before + 1, user.sync.focusToken)
    }

    @Test
    fun aChangeInAnotherRoundOnlyRefreshesTheRowsWithoutOpeningAnything() = runTest {
        val server = FakeTournamentRepository().withTables(1).also { it.seed(syncTable(version = 1, roundId = 2, tableId = 1)) }
        val user = TestUser(server).also { it.open(1) }
        server.otherUserSaves(2, 1, isCompleted = true)

        user.sync.checkForChanges()

        // The table is loaded too, so the ranking copy stays current. Then the rows refresh.
        assertEquals(listOf("manifest", "get 2/1 FORCE", "list all FORCE"), server.calls)
        assertTrue(user.sync.updateNotices.isEmpty())
        assertEquals(setOf(1), user.sync.tableEditors.keys)
        val row = user.sync.allTables.first { it.roundId == 2 }
        assertEquals(2L, row.version)
        assertTrue(row.isCompleted)
    }

    @Test
    fun aFailedLoadOfAnotherRoundsTableKeepsTheRowsOldSoTheNextCheckRetries() = runTest {
        val server = FakeTournamentRepository().withTables(1).also { it.seed(syncTable(version = 1, roundId = 2, tableId = 1)) }
        val user = TestUser(server).also { it.open(1) }
        server.otherUserSaves(2, 1, isCompleted = true)
        server.failTableReads = 1

        user.sync.checkForChanges()
        assertEquals(1L, user.sync.allTables.first { it.roundId == 2 }.version)

        user.sync.checkForChanges()
        assertEquals(2L, user.sync.allTables.first { it.roundId == 2 }.version)
    }

    @Test
    fun refreshingRowsKeepsTheStateOfOpenEditors() = runTest {
        val server = FakeTournamentRepository().withTables(1).also { it.seed(syncTable(version = 1, roundId = 2, tableId = 1)) }
        val user = TestUser(server).also { it.open(1) }
        user.typeHandScore("16")
        server.otherUserSaves(2, 1, isCompleted = true)

        user.sync.checkForChanges()

        assertTrue(user.sync.hasUnsavedChanges, "typing in the open table stays")
        assertTrue(user.sync.allTables.first { it.roundId == 1 }.hasProgress || user.editor().hasUnsavedChanges)
    }

    @Test
    fun severalChangedTablesGiveOneNoticeEach() = runTest {
        val server = FakeTournamentRepository().withTables(1, 2, 3)
        val user = TestUser(server).also { it.open(1, 2, 3) }
        server.otherUserSaves(1, 1, handScore = "8")
        server.otherUserSaves(1, 3, handScore = "9")

        user.sync.checkForChanges()

        assertEquals(setOf(TableKey(1, 1), TableKey(1, 3)), user.sync.updateNotices.map { it.key }.toSet())
        assertEquals(1L, user.editor(2).version)
    }

    @Test
    fun noticesAccumulateUntilTheUserAcceptsThem() = runTest {
        val server = FakeTournamentRepository().withTables(1)
        val user = TestUser(server).also { it.open(1) }
        server.otherUserSaves(1, 1, handScore = "8")
        user.sync.checkForChanges()
        server.otherUserSaves(1, 1, handScore = "9")
        user.sync.checkForChanges()

        assertEquals(2, user.sync.updateNotices.size)

        val token = user.sync.focusToken
        user.sync.dismissNotices()

        assertTrue(user.sync.updateNotices.isEmpty())
        assertEquals(token + 1, user.sync.focusToken)
    }

    @Test
    fun theSameChangeIsNotAppliedTwice() = runTest {
        val server = FakeTournamentRepository().withTables(1)
        val user = TestUser(server).also { it.open(1) }
        server.otherUserSaves(1, 1, handScore = "8")

        user.sync.checkForChanges()
        server.calls.clear()
        user.sync.checkForChanges()

        assertEquals(listOf("manifest"), server.calls)
        assertEquals(1, user.sync.updateNotices.size)
    }

    @Test
    fun checkDoesNothingWhileASaveOrLoadRuns() = runTest {
        val server = FakeTournamentRepository().withTables(1)
        val user = TestUser(server).also { it.open(1) }
        server.otherUserSaves(1, 1, handScore = "8")

        user.sync.isLoading = true
        user.sync.checkForChanges()
        user.sync.isLoading = false
        user.sync.isLoadingRest = true
        user.sync.checkForChanges()

        assertTrue(server.calls.isEmpty())
        assertEquals(1L, user.editor().version)
    }

    @Test
    fun aFailedManifestIsIgnoredAndTheNextCheckSucceeds() = runTest {
        val server = FakeTournamentRepository().withTables(1)
        val user = TestUser(server).also { it.open(1) }
        server.otherUserSaves(1, 1, handScore = "8")
        server.manifestFails = true

        user.sync.checkForChanges()
        assertEquals(1L, user.editor().version)
        assertNull(user.sync.errorMessage, "a background check never shows an error")

        server.manifestFails = false
        user.sync.checkForChanges()
        assertEquals(2L, user.editor().version)
    }

    @Test
    fun aFailedTableReadKeepsTheEditorAndTheNextCheckRetries() = runTest {
        val server = FakeTournamentRepository().withTables(1)
        val user = TestUser(server).also { it.open(1) }
        user.typeHandScore("16")
        server.otherUserSaves(1, 1, handScore = "8")
        server.failTableReads = 1

        user.sync.checkForChanges()
        assertTrue(user.sync.hasUnsavedChanges, "typing is not lost when the read fails")
        assertTrue(user.sync.updateNotices.isEmpty())

        user.sync.checkForChanges()
        assertFalse(user.sync.hasUnsavedChanges)
        assertEquals(1, user.sync.updateNotices.size)
    }

    @Test
    fun aSaveThatStartsWhileTheTableLoadsWinsOverTheLoadedData() = runTest {
        val server = FakeTournamentRepository().withTables(1)
        val user = TestUser(server).also { it.open(1) }
        user.typeHandScore("16")
        server.otherUserSaves(1, 1, handScore = "8")
        server.beforeTableRead = { user.sync.isLoading = true } // the user pressed Save during the request

        user.sync.checkForChanges()

        assertTrue(user.sync.hasUnsavedChanges, "the editor stays for the save")
        assertTrue(user.sync.updateNotices.isEmpty())
    }

    @Test
    fun aRoundChangeWhileTheTableLoadsDropsTheLoadedData() = runTest {
        val server = FakeTournamentRepository().withTables(1)
        val user = TestUser(server).also { it.open(1) }
        server.otherUserSaves(1, 1, handScore = "8")
        server.beforeTableRead = { user.round = 2 }

        user.sync.checkForChanges()

        assertEquals(1L, user.editor().version)
        assertTrue(user.sync.updateNotices.isEmpty())
    }

    @Test
    fun anOlderPollAnswerAfterTheUsersOwnSaveIsIgnored() = runTest {
        val server = FakeTournamentRepository().withTables(1)
        val user = TestUser(server).also { it.open(1) }
        server.otherUserSaves(1, 1, handScore = "8") // the manifest will say version 2
        server.beforeTableRead = {
            // The user's own save finished at version 3 while the poll read version 2.
            user.sync.tableEditors = mapOf(1 to TableManagerEditorState.from(syncTable(version = 3), syncHands()))
        }

        user.sync.checkForChanges()

        assertEquals(3L, user.editor().version)
        assertTrue(user.sync.updateNotices.isEmpty())
    }

    // ------------------------------------------------------------------ player names

    @Test
    fun namesReloadWhenAnotherUserChangesThePlayers() = runTest {
        val server = FakeTournamentRepository().withTables(1)
        server.players = listOf(TournamentPlayer(id = 1, name = "P1", team = 0))
        val user = TestUser(server).also { it.open(1) }
        user.sync.checkForChanges() // records the revision
        assertEquals(0, server.calls.count { it.startsWith("players") })

        server.playersRevision = 2
        user.sync.checkForChanges()

        assertEquals(listOf("players FORCE"), server.calls.filter { it.startsWith("players") })
        assertEquals("Player 1", user.sync.playerNamesById[1])
    }

    @Test
    fun namesStayWhenThePlayersDidNotChange() = runTest {
        val server = FakeTournamentRepository().withTables(1)
        val user = TestUser(server).also { it.open(1) }

        repeat(3) { user.sync.checkForChanges() }

        assertEquals(0, server.calls.count { it.startsWith("players") })
    }

    @Test
    fun aFailedNameReloadIsTriedAgainAtTheNextCheck() = runTest {
        val server = FakeTournamentRepository().withTables(1)
        server.players = listOf(TournamentPlayer(id = 1, name = "P1", team = 0))
        val user = TestUser(server).also { it.open(1) }
        user.sync.checkForChanges()
        server.playersRevision = 2
        server.failPlayers = true

        user.sync.checkForChanges()
        assertTrue(user.sync.playerNamesById.isEmpty())

        server.failPlayers = false
        user.sync.checkForChanges()
        assertEquals("Player 1", user.sync.playerNamesById[1])
    }

    @Test
    fun theNamesAreNotReloadedWhileASaveRuns() = runTest {
        val server = FakeTournamentRepository().withTables(1)
        val user = TestUser(server).also { it.open(1) }
        user.sync.checkForChanges()
        server.playersRevision = 2

        user.sync.isLoading = true
        user.sync.checkForChanges()

        assertEquals(0, server.calls.count { it.startsWith("players") })
    }

    @Test
    fun theTimeOfTheLastSuccessfulCheckIsKept() = runTest {
        val server = FakeTournamentRepository().withTables(1)
        val user = TestUser(server).also { it.open(1) }
        assertNull(user.sync.lastCheckedAt)

        user.sync.checkForChanges()
        assertEquals(kotlin.time.Instant.parse("2026-10-11T15:30:00Z"), user.sync.lastCheckedAt)

        user.clock = kotlin.time.Instant.parse("2026-10-11T15:30:10Z")
        user.sync.checkForChanges()
        assertEquals(kotlin.time.Instant.parse("2026-10-11T15:30:10Z"), user.sync.lastCheckedAt)
    }

    @Test
    fun aFailedOrSkippedCheckDoesNotMoveTheTime() = runTest {
        val server = FakeTournamentRepository().withTables(1)
        val user = TestUser(server).also { it.open(1) }
        user.sync.checkForChanges()
        val first = user.sync.lastCheckedAt

        user.clock = kotlin.time.Instant.parse("2026-10-11T15:31:00Z")
        server.manifestFails = true
        user.sync.checkForChanges()
        server.manifestFails = false
        user.sync.isLoading = true
        user.sync.checkForChanges()

        assertEquals(first, user.sync.lastCheckedAt)
    }

    @Test
    fun namesReloadWhenTheEmaPlayersChanged() = runTest {
        val server = FakeTournamentRepository().withTables(1)
        val user = TestUser(server).also { it.open(1) }
        user.sync.checkForChanges()

        server.emaPlayersRevision = 2
        user.sync.checkForChanges()

        assertEquals(listOf("players FORCE"), server.calls.filter { it.startsWith("players") })
    }

    @Test
    fun theTournamentSettingsReloadWhenTheyChanged() = runTest {
        val server = FakeTournamentRepository().withTables(1)
        val user = TestUser(server).also { it.open(1) }
        user.sync.checkForChanges()
        assertEquals(0, user.tournamentReloads, "the first check only records")

        server.tournamentsRevision = 2
        user.sync.checkForChanges()
        user.sync.checkForChanges()

        assertEquals(1, user.tournamentReloads, "one reload for one change")
    }

    @Test
    fun aFailedSettingsReloadIsTriedAgain() = runTest {
        val server = FakeTournamentRepository().withTables(1)
        val user = TestUser(server).also { it.open(1) }
        user.sync.checkForChanges()
        server.tournamentsRevision = 2
        user.tournamentReloadWorks = false

        user.sync.checkForChanges()
        user.sync.checkForChanges()
        assertEquals(2, user.tournamentReloads)

        user.tournamentReloadWorks = true
        user.sync.checkForChanges()
        user.sync.checkForChanges()
        assertEquals(3, user.tournamentReloads, "no more reloads after it worked")
    }

    // ------------------------------------------------------------------ saving

    @Test
    fun savingWithoutChangesMakesNoRequest() = runTest {
        val server = FakeTournamentRepository().withTables(1)
        val user = TestUser(server).also { it.open(1) }

        assertTrue(user.save())

        assertTrue(server.calls.isEmpty())
    }

    @Test
    fun aSuccessfulSaveStoresTheServerResultAndUpdatesTheRow() = runTest {
        val server = FakeTournamentRepository().withTables(1)
        val user = TestUser(server).also { it.open(1) }
        user.typeHandScore("16")

        assertTrue(user.save())

        assertEquals(2L, user.editor().version)
        assertFalse(user.sync.hasUnsavedChanges)
        assertEquals(2L, user.sync.allTables.single().version)
        assertEquals("16", server.tables.getValue(TableKey(1, 1)).second.first().handScore)
        assertFalse(user.sync.isLoading)
        assertTrue(user.sync.updateNotices.isEmpty())
    }

    @Test
    fun aSavedVersionIsNotMistakenForAChangeByThePoll() = runTest {
        val server = FakeTournamentRepository().withTables(1)
        val user = TestUser(server).also { it.open(1) }
        user.typeHandScore("16")
        user.save()
        server.calls.clear()

        user.sync.checkForChanges()

        assertEquals(listOf("manifest"), server.calls)
        assertTrue(user.sync.updateNotices.isEmpty())
    }

    @Test
    fun aRejectedSaveGivesTheServerDataAndListsTheLostValues() = runTest {
        val server = FakeTournamentRepository().withTables(1)
        val user = TestUser(server).also { it.open(1) }
        user.typeHandScore("16")
        server.otherUserSaves(1, 1, handScore = "8")

        assertFalse(user.save())

        assertEquals("8", user.editor().hands.first().handScore)
        assertEquals(2L, user.editor().version)
        assertFalse(user.sync.hasUnsavedChanges)
        val notice = user.sync.updateNotices.single()
        assertTrue(notice.saveRejected)
        assertEquals(listOf("Hand 1 · Hand score" to "16"), notice.lostChanges.map { it.label to it.value })
        assertFalse(user.sync.isLoading)
        assertNull(user.sync.errorMessage)
    }

    @Test
    fun afterARejectedSaveTheUserCanTypeAgainAndSave() = runTest {
        val server = FakeTournamentRepository().withTables(1)
        val user = TestUser(server).also { it.open(1) }
        user.typeHandScore("16")
        server.otherUserSaves(1, 1, east = "1", handScore = "8")
        user.save()
        user.sync.dismissNotices()

        user.typeHandScore("24")
        assertTrue(user.save())

        assertEquals("24", server.tables.getValue(TableKey(1, 1)).second.first().handScore)
        assertEquals(3L, user.editor().version)
    }

    @Test
    fun aConflictWithoutTableDataLoadsTheTableAgain() = runTest {
        val server = FakeTournamentRepository().withTables(1)
        val user = TestUser(server).also { it.open(1) }
        user.typeHandScore("16")
        server.otherUserSaves(1, 1, handScore = "8")
        server.conflictWithoutTable = true

        assertFalse(user.save())

        assertEquals("8", user.editor().hands.first().handScore)
        assertTrue(user.sync.updateNotices.single().saveRejected)
        assertTrue(server.calls.any { it == "get 1/1 FORCE" })
    }

    @Test
    fun aConflictWithoutTableDataAndAFailedReloadShowsAnError() = runTest {
        val server = FakeTournamentRepository().withTables(1)
        val user = TestUser(server).also { it.open(1) }
        user.typeHandScore("16")
        server.otherUserSaves(1, 1, handScore = "8")
        server.conflictWithoutTable = true
        server.failTableReads = 1

        assertFalse(user.save())

        assertNotNull(user.sync.errorMessage)
        assertTrue(user.sync.updateNotices.isEmpty())
        assertTrue(user.sync.hasUnsavedChanges, "the typed values stay when nothing replaced them")
        assertFalse(user.sync.isLoading)
    }

    @Test
    fun aVersionFromTheFutureStillGivesTheServerData() = runTest {
        val server = FakeTournamentRepository().withTables(1)
        val user = TestUser(server).also { it.open(1) }
        user.typeHandScore("16")
        // The server restored an older copy: its version is lower than the editor's.
        server.tables[TableKey(1, 1)] = syncTable(version = 0) to syncHands("8")

        assertFalse(user.save())

        assertEquals(0L, user.editor().version)
        assertEquals("8", user.editor().hands.first().handScore)
        assertTrue(user.sync.updateNotices.single().saveRejected)
    }

    @Test
    fun anotherErrorStopsTheRunKeepsTheTypingAndShowsAMessage() = runTest {
        val server = FakeTournamentRepository().withTables(1, 2)
        val user = TestUser(server).also { it.open(1, 2) }
        user.typeHandScore("16", tableId = 1)
        user.typeHandScore("24", tableId = 2)
        server.saveError = com.etologic.mahjongtournamentsuite.domain.model.AppError.Network

        assertFalse(user.save(1, 2))

        assertNotNull(user.sync.errorMessage)
        assertEquals(listOf("save 1/1 v1"), server.calls, "the second table is not tried")
        assertTrue(user.editor(1).hasUnsavedChanges)
        assertTrue(user.editor(2).hasUnsavedChanges)
        assertFalse(user.sync.isLoading)
    }

    @Test
    fun aRejectedTableDoesNotStopTheOtherTables() = runTest {
        val server = FakeTournamentRepository().withTables(1, 2)
        val user = TestUser(server).also { it.open(1, 2) }
        user.typeHandScore("16", tableId = 1)
        user.typeHandScore("24", tableId = 2)
        server.otherUserSaves(1, 1, handScore = "8")

        assertFalse(user.save(1, 2), "the caller must not run the waiting action")

        assertEquals("24", server.tables.getValue(TableKey(1, 2)).second.first().handScore)
        assertEquals(2L, user.editor(2).version)
        assertEquals("8", user.editor(1).hands.first().handScore)
        assertEquals(listOf(TableKey(1, 1)), user.sync.updateNotices.map { it.key })
    }

    @Test
    fun savingOnlyTheGivenTablesLeavesTheOthersAlone() = runTest {
        val server = FakeTournamentRepository().withTables(1, 2)
        val user = TestUser(server).also { it.open(1, 2) }
        user.typeHandScore("16", tableId = 1)
        user.typeHandScore("24", tableId = 2)

        user.save(1)

        assertTrue(user.editor(2).hasUnsavedChanges)
        assertEquals(listOf("save 1/1 v1"), server.calls)
    }

    @Test
    fun anUnexpectedExceptionStillEndsTheLoadingState() = runTest {
        val server = FakeTournamentRepository().withTables(1)
        val user = TestUser(server).also { it.open(1) }
        user.typeHandScore("16")
        server.beforeSave = { error("crash") }

        assertFailsWith<IllegalStateException> { user.save() }

        assertFalse(user.sync.isLoading, "the screen must not stay blocked")
        assertTrue(user.sync.hasUnsavedChanges)
    }

    // ------------------------------------------------------------------ two users

    @Test
    fun twoUsersOnDifferentTablesBothSave() = runTest {
        val server = FakeTournamentRepository().withTables(1, 2)
        val ana = TestUser(server, "ana").also { it.open(1, 2) }
        val ben = TestUser(server, "ben").also { it.open(1, 2); it.table = 2 }
        ana.typeHandScore("16", tableId = 1)
        ben.typeHandScore("24", tableId = 2)

        assertTrue(ana.save(1))
        assertTrue(ben.save(2))

        assertEquals("16", server.tables.getValue(TableKey(1, 1)).second.first().handScore)
        assertEquals("24", server.tables.getValue(TableKey(1, 2)).second.first().handScore)
    }

    @Test
    fun theSecondOfTwoUsersOnTheSameTableIsRejectedAndSeesTheFirstUsersData() = runTest {
        val server = FakeTournamentRepository().withTables(1)
        val ana = TestUser(server, "ana").also { it.open(1) }
        val ben = TestUser(server, "ben").also { it.open(1) }
        ana.typeHandScore("16")
        ben.typeHandScore("24")

        assertTrue(ana.save())
        assertFalse(ben.save())

        assertEquals("16", server.tables.getValue(TableKey(1, 1)).second.first().handScore)
        assertEquals("16", ben.editor().hands.first().handScore)
        assertEquals(listOf("24"), ben.sync.updateNotices.single().lostChanges.map { it.value })
    }

    @Test
    fun differentHandsOfOneTableStillRejectTheSecondUser() = runTest {
        val server = FakeTournamentRepository().withTables(1)
        val ana = TestUser(server, "ana").also { it.open(1) }
        val ben = TestUser(server, "ben").also { it.open(1) }
        ana.typeHandScore("16", handId = 1)
        ben.typeHandScore("24", handId = 2)

        ana.save()

        assertFalse(ben.save())
        assertEquals("16", ben.editor().hands.first { it.handId == 1 }.handScore)
        assertEquals("", ben.editor().hands.first { it.handId == 2 }.handScore)
        // Ben types hand 2 again and saves on top of Ana's data.
        ben.typeHandScore("24", handId = 2)
        assertTrue(ben.save())
        val hands = server.tables.getValue(TableKey(1, 1)).second
        assertEquals(listOf("16", "24"), hands.take(2).map { it.handScore })
    }

    @Test
    fun theTwoUsersSavingAtTheSameMomentLeaveExactlyOneWinner() = runTest {
        val server = FakeTournamentRepository().withTables(1)
        val ana = TestUser(server, "ana").also { it.open(1) }
        val ben = TestUser(server, "ben").also { it.open(1) }
        ana.typeHandScore("16")
        ben.typeHandScore("24")
        // Ben's save reaches the server while Ana's save is on its way. Both used version 1.
        server.beforeSave = {
            if (server.calls.count { it.startsWith("save") } == 1) {
                server.beforeSave = {}
                assertTrue(ben.save())
            }
        }

        val anaSaved = ana.save()

        // Ben's nested save ran first and won. Ana, who used the same version, was rejected.
        assertFalse(anaSaved)
        assertEquals("24", server.tables.getValue(TableKey(1, 1)).second.first().handScore)
        assertEquals(2L, server.tables.getValue(TableKey(1, 1)).first.version)
    }
}
