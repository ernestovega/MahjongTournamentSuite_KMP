package com.etologic.mahjongtournamentsuite.e2e

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
}
