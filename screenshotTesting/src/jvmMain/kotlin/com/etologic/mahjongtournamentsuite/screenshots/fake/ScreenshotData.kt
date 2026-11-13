package com.etologic.mahjongtournamentsuite.screenshots.fake

import com.etologic.mahjongtournamentsuite.domain.model.Country
import com.etologic.mahjongtournamentsuite.domain.model.GlobalUserRole
import com.etologic.mahjongtournamentsuite.domain.model.ManagedUser
import com.etologic.mahjongtournamentsuite.domain.model.Player
import com.etologic.mahjongtournamentsuite.domain.model.TableHand
import com.etologic.mahjongtournamentsuite.domain.model.TablePoints
import com.etologic.mahjongtournamentsuite.domain.model.TableState
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.TournamentAssignment
import com.etologic.mahjongtournamentsuite.domain.model.TournamentPlayer
import com.etologic.mahjongtournamentsuite.domain.model.TournamentRoundSchedule
import com.etologic.mahjongtournamentsuite.domain.model.TournamentTable
import com.etologic.mahjongtournamentsuite.domain.model.TournamentTeam
import com.etologic.mahjongtournamentsuite.domain.model.TournamentVenue
import com.etologic.mahjongtournamentsuite.domain.model.UserProfile
import kotlin.random.Random

/**
 * The data of the screenshots: the same on every run (fixed seed, fixed dates, no "now"), so an image changes only when
 * a screen changes. 16 players in 4 teams play 4 rounds of 4 tables. In [ONGOING] rounds 1 and 2 are complete, round 3
 * is halfway and round 4 has not started; [COMPLETED] has every round complete.
 */
object ScreenshotData {
    const val ONGOING = "madrid-open-2026"
    const val COMPLETED = "lisbon-cup-2025"
    const val UPCOMING = "berlin-riichi-2027"
    const val INDIVIDUAL = "paris-classic-2026"

    const val HANDS_PER_TABLE = 16

    private data class Person(val first: String, val last: String, val country: String)

    private val people = listOf(
        Person("Lucía", "Martín", "ES"), Person("Hugo", "García", "ES"), Person("Claire", "Dubois", "FR"),
        Person("Jonas", "Becker", "DE"), Person("Giulia", "Rossi", "IT"), Person("Daan", "de Vries", "NL"),
        Person("Freja", "Nielsen", "DK"), Person("Mateus", "Silva", "PT"), Person("Ana", "López", "ES"),
        Person("Pierre", "Moreau", "FR"), Person("Lena", "Schmidt", "DE"), Person("Marco", "Bianchi", "IT"),
        Person("Sanne", "Jansen", "NL"), Person("Oskar", "Lindqvist", "SE"), Person("Inês", "Costa", "PT"),
        Person("Tomás", "Navarro", "ES"),
    )

    /** The player base: the 16 players of the tournaments and 4 more who never played. */
    val players: List<Player> = (
        people + listOf(
            Person("Erik", "Hansen", "NO"),
            Person("Zofia", "Nowak", "PL"),
            Person("Aoife", "Byrne", "IE"),
            Person("Mikko", "Virtanen", "FI"),
        )
        ).mapIndexed { index, person ->
        Player(emaId = "0401%04d".format(index + 1), firstName = person.first, lastName = person.last, country = person.country)
    }

    val countries: List<Country> = listOf(
        "DE" to "Germany", "DK" to "Denmark", "ES" to "Spain", "FI" to "Finland", "FR" to "France", "IE" to "Ireland",
        "IT" to "Italy", "NL" to "Netherlands", "NO" to "Norway", "PL" to "Poland", "PT" to "Portugal", "SE" to "Sweden",
    ).map { (code, name) -> Country(code, name) }

    val teamNames = listOf("Red Dragons", "East Wind", "Bamboo Club", "White Tigers")

    /** Player p (1..16) is in the 4×4 grid at row (p-1)/4, column (p-1)%4. Teams are the diagonals. */
    private fun team(playerId: Int): Int {
        val row = (playerId - 1) / 4
        val column = (playerId - 1) % 4
        return ((row - column) % 4 + 4) % 4 + 1
    }

    fun tournamentPlayers(teams: Boolean = true): List<TournamentPlayer> = (1..16).map { id ->
        val player = players[id - 1]
        TournamentPlayer(
            id = id,
            name = "Player $id",
            team = if (teams) team(id) else 0,
            country = player.country,
            assignedEmaId = player.emaId,
            hasAccount = id % 3 != 0,
            hasEmail = id % 4 != 0,
        )
    }

    fun teams(): List<TournamentTeam> = (1..4).map { teamId ->
        TournamentTeam(teamId, teamNames[teamId - 1], (1..16).filter { team(it) == teamId })
    }

    /** The four players of each table of each round: rows, columns and two kinds of diagonals of the grid. */
    fun seating(roundId: Int): List<List<Int>> = (0 until 4).map { table ->
        (0 until 16).filter { index ->
            val row = index / 4
            val column = index % 4
            when (roundId) {
                1 -> row == table
                2 -> column == table
                3 -> (row + column) % 4 == table
                else -> (row + 2 * column) % 4 == table
            }
        }.map { it + 1 }
    }

    /** Hands of a table, the same for the same table on every run. Some draws, some self-drawn, a few chicken hands. */
    fun hands(roundId: Int, tableId: Int, playerIds: List<Int>, count: Int): List<TableHand> {
        val random = Random(roundId * 100 + tableId)
        return (1..count).map { handId ->
            val kind = random.nextInt(10)
            val winner = playerIds[random.nextInt(4)]
            val loser = playerIds.filter { it != winner }[random.nextInt(3)]
            val penalty = if (roundId == 2 && tableId == 3 && handId == 5) "-10" else "0"
            when {
                kind == 0 -> TableHand(handId, "", "", "0", false, true, "0", "0", "0", "0")
                kind <= 3 -> TableHand(handId, "$winner", "", "${8 + random.nextInt(25)}", false, true, penalty, "0", "0", "0")
                else -> {
                    val chicken = kind == 9
                    val score = if (chicken) "8" else "${8 + random.nextInt(40)}"
                    TableHand(handId, "$winner", "$loser", score, chicken, true, penalty, "0", "0", "0")
                }
            }
        }
    }

    /** The totals of the four seats after [hands]: a discard pays score + 8 and the others 8, a self-draw score + 8 each. */
    fun totals(playerIds: List<Int>, hands: List<TableHand>): List<Int> {
        val totals = IntArray(4)
        for (hand in hands) {
            val score = hand.handScore.toInt()
            val winner = playerIds.indexOf(hand.playerWinnerId.toIntOrNull())
            if (winner >= 0 && score > 0) {
                val loser = playerIds.indexOf(hand.playerLooserId.toIntOrNull())
                for (seat in 0 until 4) {
                    if (seat == winner) continue
                    val pays = if (loser < 0) {
                        score + 8
                    } else if (seat == loser) {
                        score + 8
                    } else {
                        8
                    }
                    totals[seat] -= pays
                    totals[winner] += pays
                }
            }
            listOf(hand.playerEastPenalty, hand.playerSouthPenalty, hand.playerWestPenalty, hand.playerNorthPenalty)
                .forEachIndexed { seat, penalty -> totals[seat] += penalty.toIntOrNull() ?: 0 }
        }
        return totals.toList()
    }

    /** How many hands each table of a round has in [tournamentId]. */
    fun handCount(tournamentId: String, roundId: Int, tableId: Int): Int = when (tournamentId) {
        UPCOMING -> 0
        ONGOING -> when (roundId) {
            1, 2 -> HANDS_PER_TABLE
            3 -> 4 + tableId * 2
            else -> 0
        }
        else -> HANDS_PER_TABLE
    }

    fun table(tournamentId: String, roundId: Int, tableId: Int): Pair<TableState, List<TableHand>> {
        val playerIds = seating(roundId)[tableId - 1]
        val count = handCount(tournamentId, roundId, tableId)
        val hands = hands(roundId, tableId, playerIds, count)
        val totals = totals(playerIds, hands)
        val scores = if (count == 0) List(4) { "" } else totals.map(Int::toString)
        val state = TableState(
            version = count.toLong(),
            roundId = roundId,
            tableId = tableId,
            playerIds = playerIds,
            playerEastId = "${playerIds[0]}",
            playerSouthId = "${playerIds[1]}",
            playerWestId = "${playerIds[2]}",
            playerNorthId = "${playerIds[3]}",
            playerEastScore = scores[0],
            playerSouthScore = scores[1],
            playerWestScore = scores[2],
            playerNorthScore = scores[3],
            isCompleted = count == HANDS_PER_TABLE,
            useTotalsOnly = false,
        )
        return state to hands
    }

    fun tables(tournamentId: String, roundId: Int? = null): List<TournamentTable> = (1..4).filter { roundId == null || it == roundId }.flatMap { round ->
        (1..4).map { tableId ->
            val (state, hands) = table(tournamentId, round, tableId)
            TournamentTable(
                roundId = round,
                tableId = tableId,
                playerIds = state.playerIds,
                isCompleted = state.isCompleted,
                useTotalsOnly = false,
                hasProgress = hands.isNotEmpty(),
                completionStatus = if (state.isCompleted) {
                    "COMPLETED"
                } else if (hands.isNotEmpty()) {
                    "IN_PROGRESS"
                } else {
                    "EMPTY"
                },
                bestHandScores = hands.mapNotNull { it.handScore.toIntOrNull() }.sortedDescending().take(1),
                chickenHandCount = hands.count { it.isChickenHand },
                version = state.version,
            )
        }
    }

    /** Checks that the fake tables add up, so the screens never show impossible totals. */
    fun points(state: TableState): List<String>? = TablePoints.fromScores(
        listOf(state.playerEastScore, state.playerSouthScore, state.playerWestScore, state.playerNorthScore).map { it.toIntOrNull() },
    )

    private fun schedule(start: String) = (1..4).map { round ->
        TournamentRoundSchedule(round, date = if (round <= 2) start else start.dropLast(2) + "%02d".format(start.takeLast(2).toInt() + 1), startTime = if (round % 2 == 1) "10:00" else "16:00")
    }

    val tournaments: List<Tournament> = listOf(
        Tournament(
            id = ONGOING, name = "Madrid Open 2026", isTeams = true, numPlayers = 16, numRounds = 4, shortName = "MOM26",
            hostCountry = "ES", hostCity = "Madrid", eventStartDate = "2026-05-16", eventEndDate = "2026-05-17",
            venue = TournamentVenue(name = "Casa Asia", address = "Calle de Alcalá 1, Madrid", contactEmail = "open@example.test"),
            roundSchedules = schedule("2026-05-16"), timeZone = "Europe/Madrid", numTries = 1000,
        ),
        Tournament(
            id = COMPLETED, name = "Lisbon Cup 2025", isTeams = true, numPlayers = 16, numRounds = 4, shortName = "LC25",
            primaryColor = "#1565C0", hostCountry = "PT", hostCity = "Lisbon", eventStartDate = "2025-10-04",
            eventEndDate = "2025-10-05", roundSchedules = schedule("2025-10-04"), timeZone = "Europe/Lisbon", isCompleted = true,
        ),
        Tournament(
            id = INDIVIDUAL, name = "Paris Classic 2026", isTeams = false, numPlayers = 16, numRounds = 4, shortName = "PC26",
            primaryColor = "#6A1B9A", hostCountry = "FR", hostCity = "Paris", eventStartDate = "2026-03-07",
            eventEndDate = "2026-03-08", roundSchedules = schedule("2026-03-07"), timeZone = "Europe/Paris", isCompleted = true,
        ),
        Tournament(
            id = UPCOMING, name = "Berlin Spring 2027", isTeams = false, numPlayers = 16, numRounds = 4, shortName = "BS27",
            primaryColor = "#C62828", hostCountry = "DE", hostCity = "Berlin", eventStartDate = "2027-04-10",
            eventEndDate = "2027-04-11", roundSchedules = schedule("2027-04-10"), timeZone = "Europe/Berlin", isPublic = false,
        ),
    )

    fun tournament(id: String): Tournament = tournaments.first { it.id == id }

    val admin = UserProfile(uid = "admin-1", email = "ada@example.test", alias = "Ada", emaId = null, roles = setOf(GlobalUserRole.ADMIN))

    /** The player account of Lucía Martín (player 1 of every tournament). */
    val playerAccount = UserProfile(
        uid = "player-1",
        email = "lucia@example.test",
        alias = "Lucía",
        emaId = players[0].emaId,
        roles = setOf(GlobalUserRole.PLAYER),
    )

    val users: List<ManagedUser> = listOf(
        ManagedUser("admin-1", "ada@example.test", "Ada", GlobalUserRole.ADMIN, false, emptyList()),
        ManagedUser(
            "editor-1",
            "bruno@example.test",
            "Bruno",
            GlobalUserRole.EDITOR,
            false,
            listOf(TournamentAssignment(ONGOING, "Madrid Open 2026")),
        ),
        ManagedUser(
            "editor-2",
            "carla@example.test",
            "Carla",
            GlobalUserRole.EDITOR,
            true,
            listOf(TournamentAssignment(COMPLETED, "Lisbon Cup 2025")),
        ),
    ) + players.take(6).mapIndexed { index, player ->
        ManagedUser(
            uid = "player-${index + 1}",
            email = "${player.firstName.lowercase().replace("í", "i").replace("ê", "e")}@example.test",
            alias = player.firstName,
            role = GlobalUserRole.PLAYER,
            disabled = false,
            tournamentAssignments = emptyList(),
            emaId = player.emaId,
        )
    }
}
