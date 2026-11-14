package com.etologic.mahjongtournamentsuite.player.e2e

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** A small client for the local test backend. Tests use it to play another user or to restore data. */
object E2eApi {
    data class Reply(val status: Int, val body: String) {
        fun json(): JSONObject = JSONObject(body)
    }

    fun call(method: String, path: String, token: String? = null, body: String? = null): Reply {
        val connection = URL(if (path.startsWith("http")) path else E2E_BACKEND_URL + path).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = 10_000
        connection.readTimeout = 60_000
        connection.setRequestProperty("content-type", "application/json")
        token?.let { connection.setRequestProperty("authorization", "Bearer $it") }
        if (body != null) {
            connection.doOutput = true
            connection.outputStream.use { it.write(body.toByteArray()) }
        }
        val status = connection.responseCode
        val stream = if (status < 400) connection.inputStream else connection.errorStream
        return Reply(status, stream?.bufferedReader()?.readText().orEmpty())
    }

    private fun signInReply(email: String): JSONObject {
        val reply = call("POST", "/auth/signIn", body = """{"email":"$email","password":"${E2eAccounts.PASSWORD}"}""")
        check(reply.status == 200) { "Sign-in of $email failed: ${reply.status} ${reply.body}" }
        return reply.json()
    }

    fun signIn(email: String): String = signInReply(email).getString("idToken")

    /** The text that the app saves as its session. A test writes it to start the app already signed in. */
    fun sessionFileText(email: String): String = signInReply(email).let {
        JSONObject()
            .put("uid", it.getString("uid"))
            .put("idToken", it.getString("idToken"))
            .put("refreshToken", it.getString("refreshToken"))
            .toString()
    }

    /** Writes the original data of the tournaments again. It also removes changes that other tests made. */
    fun reset(vararg tournamentIds: String) {
        val reply = call("POST", "${E2E_BACKEND_URL.removeSuffix("/api")}/__e2e/reset?only=${tournamentIds.joinToString(",")}")
        check(reply.status == 200) { "Reset failed: ${reply.status} ${reply.body}" }
    }

    /** The emails that the local backend "sent". It keeps them in memory because no mail server runs. */
    fun sentEmails(): List<JSONObject> {
        val reply = call("GET", "${E2E_BACKEND_URL.removeSuffix("/api")}/__e2e/mail")
        check(reply.status == 200) { "Reading the mail box failed: ${reply.status} ${reply.body}" }
        val list = reply.json().getJSONArray("emails")
        return (0 until list.length()).map { list.getJSONObject(it) }
    }

    fun clearEmails() {
        call("DELETE", "${E2E_BACKEND_URL.removeSuffix("/api")}/__e2e/mail")
    }

    /** Plays another editor: writes hand 16 of round 3, table 6 of test-last-table, as a finished hand. */
    fun otherEditorFinishesLastHand(winner: String = "17", loser: String = "6", score: String = "8") {
        val token = signIn(E2eAccounts.ADMIN)
        val table = call("GET", "/tournaments/test-last-table/tables/3/6", token).json().getJSONObject("table")
        val body = """{"expectedVersion":${table.getInt("version")},"tablePatch":{},"handPatches":[{"handId":16,"patch":{"playerWinnerId":"$winner","playerLooserId":"$loser","handScore":"$score","isDone":true}}]}"""
        val reply = call("PUT", "/tournaments/test-last-table/tables/3/6/state", token, body)
        check(reply.status == 200) { "The other editor could not save: ${reply.status} ${reply.body}" }
    }

    /** Deletes the tournaments that a test created. The seeded `test-*` tournaments stay. */
    fun deleteTournamentsNamed(prefix: String) {
        val token = signIn(E2eAccounts.ADMIN)
        val list = call("GET", "/tournaments", token).json().getJSONArray("tournaments")
        for (index in 0 until list.length()) {
            val item = list.getJSONObject(index)
            if (item.getString("name").startsWith(prefix)) call("DELETE", "/tournaments/${item.getString("id")}", token)
        }
    }
}
