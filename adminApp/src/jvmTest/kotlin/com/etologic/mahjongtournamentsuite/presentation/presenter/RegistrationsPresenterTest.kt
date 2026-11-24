package com.etologic.mahjongtournamentsuite.presentation.presenter

import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.domain.model.AdminRegistration
import com.etologic.mahjongtournamentsuite.domain.model.AdminRegistrations
import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.RegistrationErrorCode
import com.etologic.mahjongtournamentsuite.domain.model.RegistrationSettings
import com.etologic.mahjongtournamentsuite.domain.model.RegistrationStatus
import com.etologic.mahjongtournamentsuite.domain.model.RegistrationTeam
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerRepository
import com.etologic.mahjongtournamentsuite.domain.repository.RegistrationRepository
import com.etologic.mahjongtournamentsuite.domain.usecase.DrawTournamentScheduleUseCase
import kotlinx.coroutines.test.runTest
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The draw of a tournament with registrations (docs/registrations.md): it refuses before computing anything while the
 * list is not ready, and says who is wrong by name.
 */
class RegistrationsPresenterTest {
    private val tournament = Tournament(id = "t1", name = "Open", isTeams = false, numPlayers = 16, numRounds = 1)

    private fun person(n: Int, status: RegistrationStatus = RegistrationStatus.CONFIRMED, problems: List<String> = emptyList(), teamId: String? = null) = AdminRegistration(
        emaId = "1000${n.toString().padStart(4, '0')}",
        name = "Person $n",
        country = "ES",
        status = status,
        position = n,
        teamId = teamId,
        problems = problems,
    )

    private fun list(people: List<AdminRegistration>, isTeams: Boolean = false, teams: List<RegistrationTeam> = emptyList()) = AdminRegistrations(
        settings = RegistrationSettings(),
        isTeams = isTeams,
        drawPending = true,
        registrations = people,
        teams = teams,
    )

    private fun presenter(calls: MutableList<String>) = RegistrationsPresenter(
        repository = Proxy.newProxyInstance(RegistrationRepository::class.java.classLoader, arrayOf(RegistrationRepository::class.java)) { _, method, _ ->
            calls += method.name
            AppResult.Failure(AppError.Network)
        } as RegistrationRepository,
        playerRepository = Proxy.newProxyInstance(PlayerRepository::class.java.classLoader, arrayOf(PlayerRepository::class.java)) { _, _, _ ->
            AppResult.Failure(AppError.Unexpected("unused"))
        } as PlayerRepository,
        generateSchedule = DrawTournamentScheduleUseCase(),
        logger = Logger.withTag("test"),
    )

    private suspend fun draw(registrations: AdminRegistrations, calls: MutableList<String> = mutableListOf()) =
        presenter(calls).makeDraw(tournament.copy(isTeams = registrations.isTeams), registrations) {}

    @Test
    fun aPersonWithAPlaceAndAnEmaProblemOfTheImportStopsTheDrawByName() = runTest {
        val calls = mutableListOf<String>()
        val people = (1..16).map { person(it, problems = if (it == 3) listOf("EMA_NOT_FOUND") else emptyList()) }

        val failure = assertIs<AppResult.Failure>(draw(list(people), calls))

        val error = assertIs<AppError.Registration>(failure.error)
        assertEquals(RegistrationErrorCode.IMPORT_PROBLEMS, error.code)
        assertEquals(listOf("Person 3"), error.args)
        assertFalse("makeDraw" in calls, "The server got a draw: $calls")
    }

    @Test
    fun aPersonWithoutANameIsNamedByTheEmaNumberOfTheFile() = runTest {
        val people = (1..15).map { person(it) } +
            person(16, problems = listOf("INVALID_EMA")).copy(name = "", emaId = "row-9", fileEmaId = "1234")

        val error = assertIs<AppError.Registration>(assertIs<AppResult.Failure>(draw(list(people))).error)

        assertEquals(RegistrationErrorCode.IMPORT_PROBLEMS, error.code)
        assertEquals(listOf("1234"), error.args)
    }

    @Test
    fun anEmailProblemOrAProblemOnTheWaitingListDoesNotStopTheDraw() = runTest {
        val calls = mutableListOf<String>()
        val people = (1..16).map { person(it, problems = if (it == 1) listOf("INVALID_EMAIL") else emptyList()) } +
            person(17, status = RegistrationStatus.WAITING, problems = listOf("DUPLICATE_EMA"))

        // The fake server refuses; what matters is that the draw got there.
        assertIs<AppResult.Failure>(draw(list(people), calls))

        assertTrue("makeDraw" in calls, "The draw never reached the server: $calls")
    }

    @Test
    fun aNumberOfPeopleThatIsNotAMultipleOfFourComesFirst() = runTest {
        val people = (1..15).map { person(it, problems = listOf("EMA_NOT_FOUND")) }

        val error = assertIs<AppError.Registration>(assertIs<AppResult.Failure>(draw(list(people))).error)

        assertEquals(RegistrationErrorCode.BAD_PLAYER_COUNT, error.code)
        assertEquals(listOf("15"), error.args)
    }

    @Test
    fun aTeamTournamentRefusesIncompleteTeamsByTheNamesOfTheirPeople() = runTest {
        val teams = listOf(
            RegistrationTeam(id = "a", name = "A", captainEmaId = "", members = emptyList()),
            RegistrationTeam(id = "b", name = "B", captainEmaId = "", members = emptyList()),
            RegistrationTeam(id = "c", name = "C", captainEmaId = "", members = emptyList()),
            RegistrationTeam(id = "d", name = "D", captainEmaId = "", members = emptyList()),
        )
        // Team D has three people and person 16 has no team.
        val people = (1..15).map { person(it, teamId = listOf("a", "b", "c", "d")[(it - 1) / 4]) } + person(16)

        val error = assertIs<AppError.Registration>(assertIs<AppResult.Failure>(draw(list(people, isTeams = true, teams = teams))).error)

        assertEquals(RegistrationErrorCode.INCOMPLETE_TEAMS, error.code)
        assertEquals(listOf("Person 13", "Person 14", "Person 15", "Person 16"), error.args)
    }

    @Test
    fun theEmaProblemsAreTheOnesThatLeaveAPersonWithoutANumberToPlayWith() {
        assertTrue(person(1, problems = listOf("INVALID_EMA")).hasEmaProblem)
        assertTrue(person(1, problems = listOf("DUPLICATE_EMA")).hasEmaProblem)
        assertTrue(person(1, problems = listOf("EMA_NOT_FOUND")).hasEmaProblem)
        assertFalse(person(1, problems = listOf("INVALID_EMAIL")).hasEmaProblem)
        assertEquals("10000001", person(1).shownEmaId)
        assertEquals("12x", person(1).copy(emaId = "row-4", fileEmaId = "12x").shownEmaId)
    }
}
