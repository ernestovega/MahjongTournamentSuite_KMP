package com.etologic.mahjongtournamentsuite.data.repository

import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.data.backend.REGISTRATION_TOKEN_HEADER
import com.etologic.mahjongtournamentsuite.data.backend.jsonResponse
import com.etologic.mahjongtournamentsuite.data.backend.mockBackendApi
import com.etologic.mahjongtournamentsuite.data.cache.PersistentCacheStore
import com.etologic.mahjongtournamentsuite.data.cache.RepositoryCache
import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.AuthSession
import com.etologic.mahjongtournamentsuite.domain.model.DrawAssignment
import com.etologic.mahjongtournamentsuite.domain.model.NewRegistration
import com.etologic.mahjongtournamentsuite.domain.model.RegisterRequest
import com.etologic.mahjongtournamentsuite.domain.model.RegistrationErrorCode
import com.etologic.mahjongtournamentsuite.domain.model.RegistrationPhase
import com.etologic.mahjongtournamentsuite.domain.model.RegistrationSettings
import com.etologic.mahjongtournamentsuite.domain.model.RegistrationSource
import com.etologic.mahjongtournamentsuite.domain.model.RegistrationStatus
import com.etologic.mahjongtournamentsuite.domain.model.RegistrationUpdate
import com.etologic.mahjongtournamentsuite.domain.repository.AuthRepository
import com.etologic.mahjongtournamentsuite.domain.repository.RefreshMode
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The registrations as the apps read and send them (docs/registrations.md): the account or the token of the email
 * links, the token kept per tournament, the mapping of the answers and the reasons of a refusal.
 */
class DefaultRegistrationRepositoryTest {
    private class MemoryStore : PersistentCacheStore {
        val values = mutableMapOf<String, String>()
        override suspend fun read(name: String): String? = values[name]
        override suspend fun write(name: String, value: String) {
            values[name] = value
        }
        override suspend fun clear() = values.clear()
    }

    /** No account signed in: a person who registered from the web without an account. */
    private class NoSession(private val signedIn: AuthRepository = FakeAuthRepository()) : AuthRepository by signedIn {
        override suspend fun currentSession(): AuthSession? = null
    }

    /** Records which cached data a call marks old. */
    private class StaleRecorder : RepositoryCache {
        val stale = mutableListOf<String>()
        override suspend fun <T : Any> getOrLoad(ownerUid: String, key: String, refreshMode: RefreshMode, revision: suspend () -> Long, load: suspend () -> T): T = load()
        override suspend fun <T : Any> shared(key: String, maxAge: kotlin.time.Duration, fetch: suspend () -> T): T = fetch()
        override suspend fun markStale(vararg keyPrefixes: String) {
            stale += keyPrefixes
        }
        override suspend fun clear() = Unit
    }

    private val requests = mutableListOf<HttpRequestData>()
    private val tokens = MemoryStore()
    private val cache = StaleRecorder()

    private fun repository(
        auth: AuthRepository = FakeAuthRepository(),
        withCache: Boolean = true,
        answer: (HttpRequestData) -> Pair<HttpStatusCode, String>,
    ) = DefaultRegistrationRepository(
        backendApi = mockBackendApi { request ->
            requests += request
            val (status, body) = answer(request)
            if (status == HttpStatusCode.OK) {
                jsonResponse(body)
            } else {
                respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        },
        authRepository = auth,
        logger = Logger.withTag("DefaultRegistrationRepositoryTest"),
        tokens = tokens,
        cache = cache.takeIf { withCache },
    )

    private fun ok(body: String) = HttpStatusCode.OK to body

    private val HttpRequestData.json: JsonObject get() = Json.parseToJsonElement((body as TextContent).text).jsonObject

    private val own = """{"emaId":"10990001","name":"Ana García","country":"ES","status":"WAITING","position":3,
        "email":"a***@example.test","foodRestrictions":"Vegan","manageToken":"token-new"}"""

    @Test
    fun thePublicListReadsSettingsPeopleAndTeams() = runTest {
        val list = assertIs<AppResult.Success<*>>(
            repository {
                ok(
                    """{"settings":{"status":"CLOSED","maxPlayers":0,"closesOn":"","slug":"6thmmc","confirmed":2,"waiting":1},
                    "isTeams":true,
                    "registrations":[{"emaId":"1","name":"A","country":"ES","status":"CONFIRMED","position":1,"teamId":"t1"},
                                     {"emaId":"2","name":"B","country":"FR","status":"WAITING","position":1},
                                     {"emaId":"3","name":"C","country":"IT","status":"SOMETHING_NEW","position":2}],
                    "teams":[{"id":"t1","name":"Dragons","captainEmaId":"1","members":["1"]}]}""",
                )
            }.loadPublicList("tournament 1"),
        ).value as com.etologic.mahjongtournamentsuite.domain.model.TournamentRegistrations

        assertEquals("/functions/public/tournaments/tournament%201/registrations", requests.single().url.encodedPath)
        assertEquals(RegistrationPhase.CLOSED, list.settings.status)
        assertNull(list.settings.maxPlayers, "0 places is no limit")
        assertNull(list.settings.closesOn, "an empty date is no date")
        assertEquals("6thmmc", list.settings.slug)
        assertTrue(list.isTeams)
        assertEquals(listOf("1", "3"), list.confirmed.map { it.emaId }, "an unknown status counts as confirmed")
        assertEquals(listOf("2"), list.waiting.map { it.emaId })
        // A status that this version of the app does not know is shown as confirmed.
        assertEquals(RegistrationStatus.CONFIRMED, list.registrations[2].status)
        assertEquals("Dragons", list.teams.single().name)
    }

    @Test
    fun theShortLinkIsReadInLowerCaseWithoutSpaces() = runTest {
        val id = repository { ok("""{"tournamentId":"abc123"}""") }.resolveLink("  6thMMC ")

        assertEquals(AppResult.Success("abc123"), id)
        assertEquals("/functions/public/registration-links/6thmmc", requests.single().url.encodedPath)
    }

    @Test
    fun aPersonWithoutAccountRegistersAndTheTokenOfTheLinksIsKeptForThatTournament() = runTest {
        val repository = repository(auth = NoSession()) { ok("""{"status":"PENDING","emailHint":"a***@example.test","manageToken":"token-1"}""") }

        val result = assertIs<AppResult.Success<*>>(
            repository.register("t-1", RegisterRequest(" 10990001 ", " ana@example.test ", " Vegan ", consent = true)),
        ).value as com.etologic.mahjongtournamentsuite.domain.model.RegisterResult

        val request = requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/functions/registrations/t-1", request.url.encodedPath)
        assertNull(request.headers[HttpHeaders.Authorization], "no account, no bearer")
        assertEquals("10990001", request.json["emaId"]?.jsonPrimitive?.content)
        assertEquals("ana@example.test", request.json["email"]?.jsonPrimitive?.content)
        assertEquals("Vegan", request.json["foodRestrictions"]?.jsonPrimitive?.content)
        assertEquals(JsonPrimitive(true), request.json["consent"])
        assertEquals(RegistrationStatus.PENDING, result.status)
        assertEquals("token-1", tokens.values["registration-token.t-1"])

        // The next call of the person sends the token of that tournament.
        requests.clear()
        repository.loadOwn("t-1")
        assertEquals("token-1", requests.single().headers[REGISTRATION_TOKEN_HEADER])
    }

    @Test
    fun aBlankTokenIsNotKeptAndTheNameOfTheTokenHasOnlySafeCharacters() = runTest {
        val repository = repository(auth = NoSession(), withCache = false) { ok("""{"status":"PENDING","emailHint":"a***@example.test","manageToken":" "}""") }

        assertIs<AppResult.Success<*>>(repository.register("t/1 x", RegisterRequest("10990001", "ana@example.test", "", consent = true)))
        assertNull(tokens.values["registration-token.t1x"])

        tokens.values["registration-token.t1x"] = " "
        assertEquals(AppResult.Success(null), repository.loadOwn("t/1 x"), "a blank token is no token")
        assertEquals(1, requests.size)
        assertTrue(cache.stale.isEmpty(), "without a cache nothing is marked old")
    }

    @Test
    fun withoutAccountAndWithoutTokenThereIsNoOwnRegistrationToAskFor() = runTest {
        val result = repository(auth = NoSession()) { error("no call expected") }.loadOwn("t-1")

        assertEquals(AppResult.Success(null), result)
        assertTrue(requests.isEmpty())
    }

    @Test
    fun aSignedInPersonSendsTheAccountAndTheSavedToken() = runTest {
        tokens.values["registration-token.t-1"] = "token-old"
        val result = assertIs<AppResult.Success<*>>(repository { ok(own) }.loadOwn("t-1")).value
            as com.etologic.mahjongtournamentsuite.domain.model.OwnRegistration

        val request = requests.single()
        assertEquals("Bearer id-token", request.headers[HttpHeaders.Authorization])
        assertEquals("token-old", request.headers[REGISTRATION_TOKEN_HEADER])
        assertEquals("/functions/registrations/t-1/me", request.url.encodedPath)
        assertEquals(RegistrationStatus.WAITING, result.status)
        assertEquals(3, result.position)
    }

    @Test
    fun aRegistrationThatIsGoneForgetsTheTokenAndShowsNone() = runTest {
        tokens.values["registration-token.t-1"] = "token-old"
        val result = repository(auth = NoSession()) { HttpStatusCode.NotFound to """{"error":"Not found"}""" }.loadOwn("t-1")

        assertEquals(AppResult.Success(null), result)
        assertEquals("", tokens.values["registration-token.t-1"], "the token of a removed registration is worth nothing")
    }

    @Test
    fun confirmingKeepsTheNewTokenAndCancellingForgetsIt() = runTest {
        val repository = repository(auth = NoSession()) { request ->
            if (request.method == HttpMethod.Delete) ok("""{"ok":true}""") else ok(own)
        }

        repository.confirm("t-1", " token-email ")
        assertEquals("token-email", requests.last().json["token"]?.jsonPrimitive?.content)
        assertEquals("token-new", tokens.values["registration-token.t-1"])

        assertIs<AppResult.Success<Unit>>(repository.cancelOwn("t-1"))
        assertEquals(HttpMethod.Delete, requests.last().method)
        assertEquals("token-new", requests.last().headers[REGISTRATION_TOKEN_HEADER])
        assertEquals("", tokens.values["registration-token.t-1"])
    }

    @Test
    fun theTokensOfTwoTournamentsDoNotMix() = runTest {
        tokens.values["registration-token.t-1"] = "token-1"
        tokens.values["registration-token.t-2"] = "token-2"
        val repository = repository(auth = NoSession()) { ok(own) }

        repository.loadOwn("t-2")

        assertEquals("token-2", requests.single().headers[REGISTRATION_TOKEN_HEADER])
    }

    @Test
    fun theTeamCallsGoToTheTeamOfThePerson() = runTest {
        val repository = repository { ok(own) }

        repository.createTeam("t-1", " Dragons ")
        repository.renameTeam("t-1", " Tigers ")
        repository.addTeamMember("t-1", " 10990002 ")
        repository.removeTeamMember("t-1", "10990002")
        repository.leaveTeam("t-1")

        assertEquals(
            listOf(
                "POST /functions/registrations/t-1/me/team",
                "PUT /functions/registrations/t-1/me/team",
                "POST /functions/registrations/t-1/me/team/members",
                "DELETE /functions/registrations/t-1/me/team/members/10990002",
                "DELETE /functions/registrations/t-1/me/team",
            ),
            requests.map { "${it.method.value} ${it.url.encodedPath}" },
        )
        assertEquals("Dragons", requests[0].json["name"]?.jsonPrimitive?.content)
        assertEquals("Tigers", requests[1].json["name"]?.jsonPrimitive?.content)
        assertEquals("10990002", requests[2].json["emaId"]?.jsonPrimitive?.content)
    }

    @Test
    fun theOrganizerReadsTheFullListWithEmailsSourceAndPublicLink() = runTest {
        val list = assertIs<AppResult.Success<*>>(
            repository {
                ok(
                    """{"settings":{"status":"OPEN","maxPlayers":64,"slug":"6thmmc","confirmed":1},"isTeams":false,"drawPending":true,
                    "publicLink":"https://mts.example/6thmmc",
                    "registrations":[{"emaId":"1","name":"A","status":"CONFIRMED","position":1,"email":"a@example.test","paid":true,
                        "source":"IMPORT","registeredAt":"2026-11-20T10:00:00Z","hasAccount":true,"sharedEmail":true},
                        {"emaId":"2","name":"B","status":"PENDING","position":0,"source":"UNKNOWN"}]}""",
                )
            }.loadAdminList("t-1"),
        ).value as com.etologic.mahjongtournamentsuite.domain.model.AdminRegistrations

        assertEquals("/functions/tournaments/t-1/registrations", requests.single().url.encodedPath)
        assertEquals("Bearer id-token", requests.single().headers[HttpHeaders.Authorization])
        assertEquals(64, list.settings.maxPlayers)
        assertTrue(list.drawPending)
        assertEquals("https://mts.example/6thmmc", list.publicLink)
        with(list.registrations[0]) {
            assertEquals("a@example.test", email)
            assertTrue(paid)
            assertEquals(RegistrationSource.IMPORT, source)
            assertTrue(hasAccount)
            assertTrue(sharedEmail)
        }
        assertEquals(RegistrationSource.SELF, list.registrations[1].source, "an unknown source is a self registration")
        assertEquals(RegistrationStatus.PENDING, list.registrations[1].status)
    }

    @Test
    fun theOrganizerAddsChangesAndRemovesRegistrations() = runTest {
        val repository = repository { request ->
            if (request.method == HttpMethod.Delete) ok("""{"ok":true}""") else ok("""{"emaId":"10990001","status":"CONFIRMED"}""")
        }

        repository.adminAdd("t-1", NewRegistration(emaId = " 10990001 ", email = " a@example.test ", status = RegistrationStatus.WAITING, teamName = " Dragons ", notify = false))
        repository.adminUpdate("t-1", "10990001", RegistrationUpdate(paid = true, foodRestrictions = " Vegan "))
        repository.adminRemove("t-1", "10990001", notify = true)

        val (add, update, remove) = requests
        assertEquals("/functions/tournaments/t-1/registrations", add.url.encodedPath)
        assertEquals("10990001", add.json["emaId"]?.jsonPrimitive?.content)
        assertEquals("WAITING", add.json["status"]?.jsonPrimitive?.content)
        assertEquals("Dragons", add.json["teamName"]?.jsonPrimitive?.content)
        assertEquals(JsonPrimitive(false), add.json["notify"])

        assertEquals(HttpMethod.Put, update.method)
        assertEquals("/functions/tournaments/t-1/registrations/10990001", update.url.encodedPath)
        assertEquals(JsonPrimitive(true), update.json["paid"])
        assertEquals("Vegan", update.json["foodRestrictions"]?.jsonPrimitive?.content)

        assertEquals(HttpMethod.Delete, remove.method)
        assertEquals("true", remove.url.parameters["notify"])
    }

    @Test
    fun everyChangeOfThePlacesMarksTheTournamentListOld() = runTest {
        val repository = repository { request ->
            if (request.method == HttpMethod.Delete) ok("""{"ok":true}""") else ok("""{"emaId":"10990001","status":"CONFIRMED"}""")
        }

        repository.adminAdd("t-1", NewRegistration(emaId = "10990001", email = "a@example.test"))
        repository.adminUpdate("t-1", "10990001", RegistrationUpdate(paid = true))
        repository.adminRemove("t-1", "10990001", notify = false)

        // The list of tournaments shows how many people have a place and how many wait.
        assertEquals(List(3) { "global:tournaments" }, cache.stale)
    }

    @Test
    fun theDrawMarksTheWholeTournamentOld() = runTest {
        val repository = repository { ok("""{"id":"t-1","name":"Open","isTeams":false,"numPlayers":4,"numRounds":1}""") }

        repository.makeDraw("t-1", numTries = 1, players = emptyList(), tables = emptyList(), assignments = listOf(DrawAssignment(1, "10990001")))

        assertEquals(listOf("global:tournaments", "tournament:t-1:"), cache.stale)
    }

    @Test
    fun theDrawWorksWithoutACache() = runTest {
        val repository = repository(withCache = false) { ok("""{"id":"t-1","name":"Open","isTeams":false,"numPlayers":4,"numRounds":1}""") }

        assertIs<AppResult.Success<*>>(repository.makeDraw("t-1", numTries = 1, players = emptyList(), tables = emptyList(), assignments = emptyList()))
        assertTrue(cache.stale.isEmpty())
    }

    @Test
    fun aFailedChangeMarksNothingOld() = runTest {
        repository { HttpStatusCode.Conflict to """{"message":"No"}""" }.adminRemove("t-1", "10990001", notify = false)

        assertTrue(cache.stale.isEmpty())
    }

    @Test
    fun theSettingsAreSentWithTheSlugTrimmed() = runTest {
        val saved = repository { ok("""{"status":"CLOSED","maxPlayers":40,"closesOn":"2027-01-31","slug":"bcn27"}""") }
            .updateSettings("t-1", RegistrationSettings(RegistrationPhase.CLOSED, maxPlayers = 40, closesOn = "2027-01-31", slug = " bcn27 "))

        val request = requests.single()
        assertEquals(HttpMethod.Put, request.method)
        assertEquals("/functions/tournaments/t-1/registration-settings", request.url.encodedPath)
        assertEquals("CLOSED", request.json["status"]?.jsonPrimitive?.content)
        assertEquals("bcn27", request.json["slug"]?.jsonPrimitive?.content)
        assertEquals(RegistrationPhase.CLOSED, assertIs<AppResult.Success<RegistrationSettings>>(saved).value.status)
    }

    @Test
    fun theImportSendsTheFileInBase64AndAPreviewDoesNotApplyIt() = runTest {
        val repository = repository {
            ok(
                """{"rows":[{"line":2,"emaId":"10990001","firstName":"Ana","lastName":"García","email":"a@example.test",
                "country":"ES","team":"Dragons","registryName":"Ana Garcia","sharedEmail":true,"problems":["NAME_DIFFERS"]}],"imported":0}""",
            )
        }
        val bytes = "EMA;Name\n10990001;Ana".encodeToByteArray()

        val preview = assertIs<AppResult.Success<*>>(repository.previewImport("t-1", "registrations.csv", bytes)).value
            as com.etologic.mahjongtournamentsuite.domain.model.RegistrationImport
        repository.applyImport("t-1", "registrations.csv", bytes)

        assertEquals("/functions/tournaments/t-1/registrations/import", requests[0].url.encodedPath)
        assertEquals("registrations.csv", requests[0].json["fileName"]?.jsonPrimitive?.content)
        assertEquals(Base64.Default.encode(bytes), requests[0].json.values.firstOrNull { it is JsonPrimitive && it.isString && it.content == Base64.Default.encode(bytes) }?.jsonPrimitive?.content)
        assertEquals(JsonPrimitive(false), requests[0].json["apply"])
        assertEquals(JsonPrimitive(true), requests[1].json["apply"])
        with(preview.rows.single()) {
            assertEquals(2, line)
            assertEquals("Dragons", team)
            assertEquals("Ana Garcia", registryName)
            assertTrue(sharedEmail)
            assertEquals(listOf("NAME_DIFFERS"), problems)
        }
    }

    @Test
    fun theImportSaysHowManyItReplacesAndTheRowsWithProblemsComeBackMarkedInTheList() = runTest {
        val import = assertIs<AppResult.Success<*>>(
            repository { ok("""{"rows":[{"line":3,"emaId":"12x","problems":["INVALID_EMA"]}],"imported":1,"replaced":12}""") }
                .previewImport("t-1", "registrations.csv", "x".encodeToByteArray()),
        ).value as com.etologic.mahjongtournamentsuite.domain.model.RegistrationImport
        assertEquals(12, import.replaced)
        assertEquals(1, import.imported)

        requests.clear()
        val list = assertIs<AppResult.Success<*>>(
            repository {
                ok(
                    """{"settings":{"status":"CLOSED"},"isTeams":false,"drawPending":true,
                    "registrations":[{"emaId":"row-3","name":"Ana","status":"CONFIRMED","position":1,"source":"IMPORT",
                        "problems":["INVALID_EMA","INVALID_EMAIL"],"fileEmaId":"12x"},
                        {"emaId":"10990002","name":"Bo","status":"CONFIRMED","position":2}]}""",
                )
            }.loadAdminList("t-1"),
        ).value as com.etologic.mahjongtournamentsuite.domain.model.AdminRegistrations
        with(list.registrations[0]) {
            assertEquals(listOf("INVALID_EMA", "INVALID_EMAIL"), problems)
            assertEquals("12x", fileEmaId)
            assertEquals("12x", shownEmaId)
            assertTrue(hasEmaProblem)
        }
        assertEquals(emptyList(), list.registrations[1].problems)
        assertEquals(null, list.registrations[1].fileEmaId)
    }

    @Test
    fun choosingTheRightPersonForARowOfTheImportSendsTheNewEmaNumber() = runTest {
        val repository = repository { ok("""{"emaId":"10990001","status":"CONFIRMED"}""") }

        repository.adminUpdate("t-1", "row-3", RegistrationUpdate(newEmaId = "10990001"))
        repository.adminUpdate("t-1", "10990002", RegistrationUpdate(paid = true))

        assertEquals("/functions/tournaments/t-1/registrations/row-3", requests[0].url.encodedPath)
        assertEquals("10990001", requests[0].json["newEmaId"]?.jsonPrimitive?.content)
        assertTrue(requests[1].json["newEmaId"].let { it == null || it is kotlinx.serialization.json.JsonNull }, "only a fix sends another EMA number")
    }

    @Test
    fun aRefusalWithACodeIsShownWithItsReasonAndItsDetails() = runTest {
        val incomplete = repository {
            HttpStatusCode.Conflict to """{"error":"Incomplete teams","details":{"code":"INCOMPLETE_TEAMS","emaIds":["1","2"]}}"""
        }.loadAdminList("t-1")
        assertEquals(AppError.Registration(RegistrationErrorCode.INCOMPLETE_TEAMS, listOf("1", "2")), assertIs<AppResult.Failure>(incomplete).error)

        requests.clear()
        val badCount = repository {
            HttpStatusCode.BadRequest to """{"error":"Bad count","details":{"code":"BAD_PLAYER_COUNT","confirmed":"18"}}"""
        }.loadAdminList("t-1")
        assertEquals(AppError.Registration(RegistrationErrorCode.BAD_PLAYER_COUNT, listOf("18")), assertIs<AppResult.Failure>(badCount).error)
    }

    @Test
    fun aRefusalWithoutAKnownCodeOrAServerErrorIsNotAReasonOfTheRegistrations() = runTest {
        val unknown = repository { HttpStatusCode.BadRequest to """{"error":"x","details":{"code":"A_CODE_OF_THE_FUTURE"}}""" }.loadAdminList("t-1")
        assertTrue(assertIs<AppResult.Failure>(unknown).error !is AppError.Registration)

        val serverError = repository { HttpStatusCode.InternalServerError to """{"details":{"code":"INCOMPLETE_TEAMS"}}""" }.loadAdminList("t-1")
        assertTrue(assertIs<AppResult.Failure>(serverError).error !is AppError.Registration, "a 500 is never a refusal of the organizer")

        val notJson = repository { HttpStatusCode.BadRequest to "<html>bad gateway</html>" }.loadAdminList("t-1")
        assertTrue(assertIs<AppResult.Failure>(notJson).error !is AppError.Registration)
    }

    @Test
    fun theOwnRegistrationIsSavedTrimmed() = runTest {
        tokens.values["registration-token.t-1"] = "token-old"
        val saved = assertIs<AppResult.Success<*>>(repository(auth = NoSession()) { ok(own) }.updateOwn("t-1", " Vegan ", consent = false)).value
            as com.etologic.mahjongtournamentsuite.domain.model.OwnRegistration

        val request = requests.single()
        assertEquals(HttpMethod.Put, request.method)
        assertEquals("/functions/registrations/t-1/me", request.url.encodedPath)
        assertEquals("token-old", request.headers[REGISTRATION_TOKEN_HEADER])
        assertEquals("Vegan", request.json["foodRestrictions"]?.jsonPrimitive?.content)
        assertEquals("Vegan", saved.foodRestrictions)
    }

    @Test
    fun aPersonWithoutAnEmaNumberWritesToTheOrganizer() = runTest {
        val repository = repository(auth = NoSession()) { ok("""{"ok":true}""") }

        val result = repository.requestWithoutEmaNumber(
            "t-1",
            com.etologic.mahjongtournamentsuite.domain.model.NoEmaNumberRequest(name = " Ana ", country = " ES ", email = " a@b.c ", message = " Hi "),
        )

        assertEquals(AppResult.Success(Unit), result)
        val json = requests.single().json
        assertEquals(listOf("Ana", "ES", "a@b.c", "Hi"), listOf("name", "country", "email", "message").map { json[it]?.jsonPrimitive?.content })
        assertIs<AppResult.Failure>(
            repository(auth = NoSession()) { HttpStatusCode.TooManyRequests to "{}" }
                .requestWithoutEmaNumber("t-1", com.etologic.mahjongtournamentsuite.domain.model.NoEmaNumberRequest("A", "ES", "a@b.c", "")),
        )
    }

    @Test
    fun aRegistrationWithoutATokenKeepsTheSavedOne() = runTest {
        tokens.values["registration-token.t-1"] = "token-old"

        repository { ok("""{"status":"CONFIRMED","manageToken":" "}""") }
            .register("t-1", RegisterRequest("10990001", "a@b.c", "", consent = true))

        assertEquals("token-old", tokens.values["registration-token.t-1"], "a blank token is not saved")

        repository { ok("""{"status":"CONFIRMED"}""") }.register("t-1", RegisterRequest("10990001", "a@b.c", "", consent = true))
        assertEquals("token-old", tokens.values["registration-token.t-1"], "an answer without a token keeps the saved one")
    }

    @Test
    fun aConfirmationWithoutANewTokenKeepsTheTokenOfTheLink() = runTest {
        repository(auth = NoSession()) { ok(own.replace("token-new", "")) }.confirm("t-1", " token-link ")

        assertEquals("token-link", tokens.values["registration-token.t-1"])
    }

    @Test
    fun anOwnRegistrationThatFailsForAnotherReasonKeepsTheToken() = runTest {
        tokens.values["registration-token.t-1"] = "token-old"

        assertIs<AppResult.Failure>(repository(auth = NoSession()) { HttpStatusCode.InternalServerError to "{}" }.loadOwn("t-1"))
        assertEquals("token-old", tokens.values["registration-token.t-1"])
    }

    @Test
    fun aSignedInPersonWhoseRegistrationIsGoneHasNoTokenToForget() = runTest {
        assertEquals(AppResult.Success(null), repository { HttpStatusCode.NotFound to "{}" }.loadOwn("t-1"))
        assertNull(tokens.values["registration-token.t-1"])
    }

    @Test
    fun anOrganizerChangeSendsOnlyTheChangedFieldsTrimmed() = runTest {
        repository { ok("""{"emaId":"1","name":"A","country":"ES","status":"CONFIRMED","position":1}""") }.adminUpdate(
            "t-1",
            "1",
            RegistrationUpdate(email = " a@b.c ", status = RegistrationStatus.WAITING, teamName = " Dragons "),
        )

        val json = requests.single().json
        assertEquals("a@b.c", json["email"]?.jsonPrimitive?.content)
        assertEquals("WAITING", json["status"]?.jsonPrimitive?.content)
        assertEquals("Dragons", json["teamName"]?.jsonPrimitive?.content)
    }

    @Test
    fun aRefusalWithACodeButWithoutItsDetailsHasNoArguments() = runTest {
        val incomplete = repository { HttpStatusCode.Conflict to """{"details":{"code":"INCOMPLETE_TEAMS"}}""" }.loadAdminList("t-1")
        assertEquals(AppError.Registration(RegistrationErrorCode.INCOMPLETE_TEAMS, emptyList()), assertIs<AppResult.Failure>(incomplete).error)

        val badCount = repository { HttpStatusCode.Conflict to """{"details":{"code":"BAD_PLAYER_COUNT"}}""" }.loadAdminList("t-1")
        assertEquals(AppError.Registration(RegistrationErrorCode.BAD_PLAYER_COUNT, emptyList()), assertIs<AppResult.Failure>(badCount).error)

        val broken = repository { HttpStatusCode.Conflict to """{"details":{"code":"INCOMPLETE_TEAMS","emaIds":"not a list"}}""" }.loadAdminList("t-1")
        assertEquals(AppError.Registration(RegistrationErrorCode.INCOMPLETE_TEAMS, emptyList()), assertIs<AppResult.Failure>(broken).error)
    }
}
