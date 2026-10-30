package com.etologic.mahjongtournamentsuite.presentation.presenter

import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.GlobalUserRole
import com.etologic.mahjongtournamentsuite.domain.model.IdCardProofRequest
import com.etologic.mahjongtournamentsuite.domain.model.ManagedUser
import com.etologic.mahjongtournamentsuite.domain.model.Player
import com.etologic.mahjongtournamentsuite.domain.model.PlayerSubstitution
import com.etologic.mahjongtournamentsuite.domain.model.SubstitutionReason
import com.etologic.mahjongtournamentsuite.domain.model.TournamentVenue
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerRepository
import com.etologic.mahjongtournamentsuite.domain.repository.RefreshMode
import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryAction
import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryEvent
import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryOutcome
import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryParam
import com.etologic.mahjongtournamentsuite.domain.usecase.CalculateTournamentRankingsUseCase
import com.etologic.mahjongtournamentsuite.presentation.screen.FakeAdminRepository
import com.etologic.mahjongtournamentsuite.presentation.screen.FakePlayerAccountRepository
import com.etologic.mahjongtournamentsuite.presentation.screen.FakeSignedInAuthRepository
import com.etologic.mahjongtournamentsuite.presentation.screen.FakeTournamentRepository
import com.etologic.mahjongtournamentsuite.presentation.telemetry.RecordingTelemetry
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Every write of the admin app counts one `admin_action` for each try. The outcome tells if the request worked.
 * These tests run each write twice, once with a repository that works and once with one that fails.
 */
class AdminActionTrackingTest {
    private class World(works: Boolean) {
        val telemetry = RecordingTelemetry()
        val tournaments = FakeTournamentRepository().apply { writesSucceed = works }
        val admin = FakeAdminRepository(GlobalUserRole.ADMIN, users = listOf(USER)).apply { failWrites = !works }
        val accounts = FakePlayerAccountRepository().apply { fails = !works }
        val players = SharedPlayers(works)

        val tournamentsPresenter = TournamentsPresenter(tournaments, admin, FakeSignedInAuthRepository(), telemetry)
        val playersPresenter = PlayersPresenter(tournaments, players, accounts, Logger.withTag("test"), telemetry)
        val teamsPresenter = TeamsPresenter(tournaments, players, Logger.withTag("test"), telemetry)
        val playerBasePresenter = PlayerBasePresenter(players, tournaments, telemetry)
        val proofPresenter = IdCardProofPresenter(tournaments, telemetry)
        val usersPresenter = UsersPresenter(tournaments, admin, FakeSignedInAuthRepository(), players, telemetry)
        val rankingPresenter = RankingPresenter(tournaments, players, CalculateTournamentRankingsUseCase(), Logger.withTag("test"), telemetry)
    }

    /** The shared EMA players. The writes work or fail as a whole. */
    private class SharedPlayers(private val works: Boolean) : PlayerRepository {
        private fun <T> result(value: T): AppResult<T> = if (works) AppResult.Success(value) else AppResult.Failure(AppError.Network)
        override suspend fun listPlayers(refreshMode: RefreshMode) = AppResult.Success(emptyList<Player>())
        override suspend fun createPlayer(player: Player) = result(player)
        override suspend fun updatePlayer(previousEmaId: String, player: Player) = result(Unit)
        override suspend fun updatePlayerPhoto(emaId: String, contentType: String, bytes: ByteArray) = result(PLAYER)
    }

    private class Case(val action: String, val run: suspend (World) -> Unit)

    private val cases = listOf(
        Case(TelemetryAction.TOURNAMENT_RENAMED) { it.tournamentsPresenter.renameTournament("t1", "New name") },
        Case(TelemetryAction.TOURNAMENT_SETTINGS_UPDATED) {
            it.tournamentsPresenter.updateTournamentSettings(
                tournamentId = "t1", name = "Open", shortName = "O", primaryColor = "#fff", eventStartDate = "2026-01-01",
                eventEndDate = "2026-01-02", hostCountry = "ES", hostCity = "Madrid", venue = TournamentVenue(),
                associationLogoContentType = null, associationLogoBytes = null, associationLogoSourceTournamentId = null,
                removeAssociationLogo = false, roundSchedules = emptyList(), agendaItems = emptyList(),
            )
        },
        Case(TelemetryAction.PLAYER_ASSIGNED) { it.playersPresenter.assignPlayer("t1", 1, "1234") },
        Case(TelemetryAction.PLAYER_UNASSIGNED) { it.playersPresenter.assignPlayer("t1", 1, null) },
        Case(TelemetryAction.PLAYER_SUBSTITUTED) {
            it.playersPresenter.setSubstitution("t1", 1, PlayerSubstitution(SubstitutionReason.ILLNESS, fromRound = 2))
        },
        Case(TelemetryAction.SUBSTITUTION_CLEARED) { it.playersPresenter.setSubstitution("t1", 1, null) },
        Case(TelemetryAction.TEAM_UPDATED) { it.teamsPresenter.updateTeam("t1", 1, "Team", listOf("1", "2")) },
        Case(TelemetryAction.EMAILS_SAVED) { it.playersPresenter.saveEmails("t1", emptyList()) },
        Case(TelemetryAction.ID_CARDS_EXPORTED) { it.playersPresenter.generateIdCards("t1") },
        Case(TelemetryAction.ID_LIST_EXPORTED) { it.playersPresenter.generateIdList("t1") },
        Case(TelemetryAction.ID_CARD_PROOF_CREATED) { it.proofPresenter.generate(IdCardProofRequest("O", "2026", "#fff")) },
        Case(TelemetryAction.EMA_REPORT_EXPORTED) { it.rankingPresenter.generateEmaReport("t1", emptyList()) },
        Case(TelemetryAction.ACCOUNT_CREATED) {
            it.usersPresenter.createUser("new@example.test", "New", GlobalUserRole.EDITOR, emptyList())
        },
        Case(TelemetryAction.ACCOUNT_UPDATED) { it.usersPresenter.updateUser(USER) },
        Case(TelemetryAction.ACCOUNT_DISABLED) { it.usersPresenter.setUserDisabled(USER.uid, true) },
        Case(TelemetryAction.ACCOUNT_ENABLED) { it.usersPresenter.setUserDisabled(USER.uid, false) },
        Case(TelemetryAction.ACCOUNT_PHOTO_UPDATED) { it.usersPresenter.updateUserPhoto(USER.uid, "image/png", ByteArray(1)) },
        Case(TelemetryAction.ACCOUNT_PHOTO_REMOVED) { it.usersPresenter.removeUserPhoto(USER.uid) },
        Case(TelemetryAction.REGISTRY_PLAYER_CREATED) { it.playerBasePresenter.createPlayer(PLAYER) },
        Case(TelemetryAction.REGISTRY_PLAYER_UPDATED) { it.playerBasePresenter.updatePlayer("1234", PLAYER) },
        Case(TelemetryAction.REGISTRY_PHOTO_UPDATED) { it.playerBasePresenter.updatePlayerPhoto("1234", "image/png", ByteArray(1)) },
    )

    private fun expected(action: String, outcome: String) = TelemetryEvent.ADMIN_ACTION to mapOf<String, Any>(TelemetryParam.ACTION to action, TelemetryParam.OUTCOME to outcome)

    @Test
    fun everyWriteCountsOneActionWithOutcomeOkWhenItWorks() = runTest {
        cases.forEach { case ->
            val world = World(works = true)
            case.run(world)
            assertEquals(listOf(expected(case.action, TelemetryOutcome.OK)), world.telemetry.events, case.action)
        }
    }

    @Test
    fun everyWriteCountsOneActionWithOutcomeFailedWhenItFails() = runTest {
        cases.forEach { case ->
            val world = World(works = false)
            case.run(world)
            assertEquals(listOf(expected(case.action, TelemetryOutcome.FAILED)), world.telemetry.events, case.action)
        }
    }

    @Test
    fun theCasesCoverEveryActionWord() {
        // A new word in TelemetryAction needs a case here, so its write is tracked and tested.
        val covered = cases.map { it.action }.toSet()
        assertEquals(TELEMETRY_ACTION_WORDS, covered)
        assertEquals(cases.size, covered.size)
    }

    @Test
    fun theWordsAreLowercaseWordsWithUnderscores() {
        TELEMETRY_ACTION_WORDS.forEach { assertEquals(true, Regex("[a-z]+(_[a-z]+)*").matches(it), it) }
    }

    @Test
    fun sendingToPlayersKeepsItsOwnEventAndNoActionEvent() = runTest {
        val world = World(works = true)
        world.playersPresenter.sendToPlayers("t1", listOf(1, 2, 3))
        assertEquals(
            listOf(TelemetryEvent.ACCOUNTS_SENT to mapOf<String, Any>(TelemetryParam.PLAYERS to 3)),
            world.telemetry.events,
        )
        val failing = World(works = false)
        failing.playersPresenter.sendToPlayers("t1", listOf(1))
        assertEquals(emptyList(), failing.telemetry.events)
    }

    private companion object {
        val USER = ManagedUser("u1", "u1@example.test", "User", GlobalUserRole.EDITOR, false, emptyList())
        val PLAYER = Player(emaId = "1234", firstName = "Ann", lastName = "Lee")

        /** Every word of [TelemetryAction]. A test of the domain module checks that the list is complete. */
        val TELEMETRY_ACTION_WORDS = TelemetryAction.all.toSet()
    }
}
