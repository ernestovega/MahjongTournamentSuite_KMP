package com.etologic.mahjongtournamentsuite.player.e2e

import org.json.JSONArray
import org.json.JSONObject

/**
 * Plays the other people of a whole tournament through the local test backend: the organizers on their computers
 * (create, assign, Timer) and the players on their own phones (seats, hands). The journey tests use it next to the
 * app under test. The same helper lives in the admin app tests.
 */
object JourneyApi {
    data class Player(val email: String, val emaId: String, val firstName: String, val lastName: String)
    data class DrawTable(val roundId: Int, val tableId: Int, val playerIds: List<Int>)

    private val root get() = E2E_BACKEND_URL.removeSuffix("/api")
    private val tokens = mutableMapOf<String, String>()

    private fun token(email: String): String = tokens.getOrPut(email) { E2eApi.signIn(email) }

    private fun ok(reply: E2eApi.Reply, what: String): E2eApi.Reply {
        check(reply.status == 200) { "$what failed: ${reply.status} ${reply.body}" }
        return reply
    }

    /** EMA players 10000901 and up, each with a player account `journeyNN@e2e.test`. */
    fun createPlayers(count: Int = 16): List<Player> {
        val list = ok(E2eApi.call("POST", "$root/__e2e/journey-players?count=$count"), "Creating the journey players").json().getJSONArray("players")
        return (0 until list.length()).map { list.getJSONObject(it) }.map {
            Player(it.getString("email"), it.getString("emaId"), it.getString("firstName"), it.getString("lastName"))
        }
    }

    /** Removes the journey accounts and EMA players, so the counts of the other tests stay the same. */
    fun removePlayers() {
        tokens.clear()
        ok(E2eApi.call("DELETE", "$root/__e2e/journey-players"), "Removing the journey players")
    }

    /**
     * The seating of 16 players in 2 rounds, without a repeated pair: the rows of a 4x4 grid in round 1 and its columns
     * in round 2. Slot 1 sits at table 1 in both rounds.
     */
    val SIXTEEN_IN_TWO_ROUNDS: List<DrawTable> =
        (0 until 4).map { row -> DrawTable(1, row + 1, (1..4).map { row * 4 + it }) } +
            (0 until 4).map { column -> DrawTable(2, column + 1, (0 until 4).map { it * 4 + column + 1 }) }

    /** Creates a tournament as the admin app does. The rounds play today, with a planned start a few minutes from now. */
    fun createTournament(name: String, tables: List<DrawTable> = SIXTEEN_IN_TWO_ROUNDS): String {
        val players = tables.flatMap { it.playerIds }.distinct().sorted()
        val rounds = tables.maxOf { it.roundId }
        val zone = java.util.TimeZone.getDefault().id.let {
            if (it == "UTC" || (it.contains('/') && !it.startsWith("Etc/") && !it.startsWith("SystemV/"))) it else "UTC"
        }
        val now = java.time.ZonedDateTime.now(java.time.ZoneId.of(zone)).plusMinutes(5)
        val body = JSONObject()
            .put("name", name)
            .put("shortName", "JOURNEY")
            .put("primaryColor", "#02B16B")
            .put("eventStartDate", now.toLocalDate().toString())
            .put("eventEndDate", now.toLocalDate().toString())
            .put("hostCountry", "ES")
            .put("hostCity", "Madrid")
            .put("venueName", "E2E club")
            .put("venueAddress", "Calle Mayor 1, Madrid")
            .put("contactEmail", "e2e@example.com")
            .put("isTeams", false)
            .put("numPlayers", players.size)
            .put("numRounds", rounds)
            .put("numTries", 1)
            .put("timeZone", zone)
            .put("matchDurationMinutes", 120)
            .put("matchEndMode", "STOP")
            .put("players", JSONArray(players.map { JSONObject().put("id", it).put("team", 0) }))
            .put("tables", JSONArray(tables.map { JSONObject().put("roundId", it.roundId).put("tableId", it.tableId).put("playerIds", JSONArray(it.playerIds)) }))
            .put("roundSchedules", JSONArray((1..rounds).map {
                JSONObject().put("roundId", it).put("date", now.toLocalDate().toString()).put("startTime", "%02d:%02d".format(now.hour, now.minute))
            }))
        ok(E2eApi.call("POST", "/tournaments", token(E2eAccounts.ADMIN), body.toString()), "Creating the tournament")
        return tournamentId(name)
    }

    fun tournamentId(name: String): String {
        val list = ok(E2eApi.call("GET", "/tournaments", token(E2eAccounts.ADMIN)), "Listing the tournaments").json().getJSONArray("tournaments")
        return (0 until list.length()).map { list.getJSONObject(it) }.first { it.getString("name") == name }.getString("id")
    }

    fun tournament(id: String): JSONObject {
        val list = ok(E2eApi.call("GET", "/tournaments", token(E2eAccounts.ADMIN)), "Listing the tournaments").json().getJSONArray("tournaments")
        return (0 until list.length()).map { list.getJSONObject(it) }.first { it.getString("id") == id }
    }

    /** The draw of the tournament, as the app stored it. */
    fun tables(tournamentId: String): List<DrawTable> {
        val list = ok(E2eApi.call("GET", "/tournaments/$tournamentId/tables", token(E2eAccounts.ADMIN)), "Reading the tables").json().getJSONArray("tables")
        return (0 until list.length()).map { list.getJSONObject(it) }.map { item ->
            val ids = item.getJSONArray("playerIds")
            DrawTable(item.getInt("roundId"), item.getInt("tableId"), (0 until ids.length()).map { ids.getInt(it) })
        }
    }

    fun table(tournamentId: String, table: DrawTable): JSONObject =
        ok(E2eApi.call("GET", "/tournaments/$tournamentId/tables/${table.roundId}/${table.tableId}", token(E2eAccounts.ADMIN)), "Reading a table")
            .json().getJSONObject("table")

    /** The organizer puts the EMA player in the slot. */
    fun assign(tournamentId: String, slot: Int, emaId: String) {
        ok(E2eApi.call("PUT", "/tournaments/$tournamentId/players/$slot", token(E2eAccounts.ADMIN), """{"emaId":"$emaId"}"""), "Assigning slot $slot")
    }

    /** Play on the Timer of an organizer. */
    fun play(tournamentId: String, round: Int) {
        ok(E2eApi.call("POST", "/tournaments/$tournamentId/rounds/$round/clock", token(E2eAccounts.ADMIN), """{"action":"start"}"""), "Play of round $round")
    }

    fun hasClock(tournamentId: String, round: Int): Boolean {
        val clocks = ok(E2eApi.call("GET", "/public/tournaments/$tournamentId/round-clocks"), "Reading the clocks").json().getJSONArray("roundClocks")
        return (0 until clocks.length()).any { clocks.getJSONObject(it).getInt("roundId") == round }
    }

    private fun version(tournamentId: String, table: DrawTable): Int = table(tournamentId, table).optInt("version", 0)

    private fun seatsAreSet(tournamentId: String, table: DrawTable): Boolean =
        table(tournamentId, table).optString("playerEastId", "").let { it.isNotBlank() && it != "null" }

    /** A player of the table sets the seats from the phone, in the order of the draw, unless someone did already. */
    fun setSeats(tournamentId: String, table: DrawTable, by: Player) {
        if (seatsAreSet(tournamentId, table)) return
        val (east, south, west, north) = table.playerIds
        val body = """{"playerEastId":"$east","playerSouthId":"$south","playerWestId":"$west","playerNorthId":"$north","expectedVersion":${version(tournamentId, table)}}"""
        ok(E2eApi.call("POST", "/player/tournaments/$tournamentId/tables/${table.roundId}/${table.tableId}/seats", token(by.email), body), "Seats of table ${table.tableId}")
    }

    /** One player writes the next hand from the phone. [winner] null is a draw. A null [loser] is a self draw. */
    fun writeHand(tournamentId: String, table: DrawTable, by: Player, winner: Int?, loser: Int? = null, score: Int = 8) {
        val hand = if (winner == null) """"isDraw":true""" else """"playerWinnerId":"$winner","playerLooserId":"${loser ?: ""}","handScore":"$score""""
        val body = """{$hand,"expectedVersion":${version(tournamentId, table)}}"""
        ok(E2eApi.call("POST", "/player/tournaments/$tournamentId/tables/${table.roundId}/${table.tableId}/hands", token(by.email), body), "Hand at table ${table.tableId}")
    }

    /**
     * The phones of a table write [hands] hands. The four players take turns. When [winner] sits at the table, that
     * slot wins every hand by self draw. Otherwise every hand is a draw.
     */
    fun playTable(tournamentId: String, table: DrawTable, playersBySlot: Map<Int, Player>, hands: Int = 16, winner: Int? = null) {
        setSeats(tournamentId, table, playersBySlot.getValue(table.playerIds[0]))
        repeat(hands) { index ->
            val writer = playersBySlot.getValue(table.playerIds[index % 4])
            writeHand(tournamentId, table, writer, winner?.takeIf { it in table.playerIds })
        }
    }

    /** The place of the EMA player in the final ranking, from the public statistics. */
    fun finalRank(tournamentId: String, emaId: String): Int {
        val standings = ok(E2eApi.call("GET", "/public/statistics/persons/$emaId/standings?tournaments=$tournamentId"), "Reading the standings")
            .json().getJSONArray("standings")
        check(standings.length() == 1) { "No standing for $emaId: $standings" }
        return standings.getJSONObject(0).getInt("rank")
    }
}
