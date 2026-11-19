package com.etologic.mahjongtournamentsuite.data.repository

import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.data.backend.jsonResponse
import com.etologic.mahjongtournamentsuite.data.backend.mockBackendApi
import com.etologic.mahjongtournamentsuite.data.cache.RepositoryCache
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.Organization
import com.etologic.mahjongtournamentsuite.domain.model.OrganizationDraft
import com.etologic.mahjongtournamentsuite.domain.model.OrganizationPlace
import com.etologic.mahjongtournamentsuite.domain.model.OrganizationType
import com.etologic.mahjongtournamentsuite.domain.repository.RefreshMode
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** The organizations and the optional place of a club, as the app reads and sends them. */
class DefaultAdminRepositoryOrganizationsTest {
    private object NoCache : RepositoryCache {
        override suspend fun <T : Any> getOrLoad(
            ownerUid: String,
            key: String,
            refreshMode: RefreshMode,
            revision: suspend () -> Long,
            load: suspend () -> T,
        ): T = load()

        override suspend fun <T : Any> shared(key: String, maxAge: kotlin.time.Duration, fetch: suspend () -> T): T = fetch()

        override suspend fun markStale(vararg keyPrefixes: String) = Unit

        override suspend fun clear() = Unit
    }

    private val requests = mutableListOf<HttpRequestData>()

    private fun repository(answer: (HttpRequestData) -> String?) = DefaultAdminRepository(
        backendApi = mockBackendApi { request ->
            requests += request
            answer(request)?.let { jsonResponse(it) } ?: respond("{}", HttpStatusCode.Forbidden)
        },
        authRepository = FakeAuthRepository(),
        logger = Logger.withTag("DefaultAdminRepositoryOrganizationsTest"),
        cache = NoCache,
    )

    private val HttpRequestData.json: JsonObject get() = Json.parseToJsonElement((body as TextContent).text).jsonObject

    private val savedClub = """{"id":"org-1","name":"Madrid Club","type":"CLUB","country":"ES","primaryColor":"#123456",
        "place":{"mapUrl":"https://maps.app.goo.gl/abc","name":"Club room","address":"Calle Mayor 1","city":"Madrid"},
        "logoUrl":"","canEdit":true}"""

    @Test
    fun readsTheOrganizationsWithThePlaceOfAClub() = runTest {
        val list = assertIs<AppResult.Success<List<Organization>>>(
            repository {
                """{"organizations":[$savedClub,
                    {"id":"org-2","name":"Federation","type":"FEDERATION","country":"FR"}]}"""
            }.listOrganizations(),
        ).value

        assertEquals("/functions/admin/organizations", requests.single().url.encodedPath)
        assertEquals(OrganizationPlace("https://maps.app.goo.gl/abc", "Club room", "Calle Mayor 1", "Madrid"), list[0].place)
        assertEquals(null, list[0].logoUrl, "an empty logo is no logo")
        assertTrue(list[0].canEdit)
        // An answer without a place, from an older server, is an organization without a place.
        assertEquals(OrganizationType.FEDERATION, list[1].type)
        assertTrue(list[1].place.isEmpty)
        assertFalse(list[1].canEdit)
    }

    @Test
    fun sendsThePlaceTrimmedAndNoPlaceWhenItIsEmpty() = runTest {
        val repository = repository { savedClub }
        val draft = OrganizationDraft(
            name = " Madrid Club ",
            type = OrganizationType.CLUB,
            country = "es",
            primaryColor = "#abcdef",
            place = OrganizationPlace(mapUrl = " https://maps.app.goo.gl/abc ", name = " Club room ", address = " Calle Mayor 1 ", city = " Madrid "),
        )

        val saved = assertIs<AppResult.Success<Organization>>(repository.updateOrganization("org-1", draft)).value
        assertEquals("Club room", saved.place.name)
        val sent = requests.single()
        assertEquals(HttpMethod.Put, sent.method)
        assertEquals("/functions/admin/organizations/org-1", sent.url.encodedPath)
        val place = sent.json.getValue("place").jsonObject
        assertEquals("https://maps.app.goo.gl/abc", place.getValue("mapUrl").jsonPrimitive.content)
        assertEquals("Calle Mayor 1", place.getValue("address").jsonPrimitive.content)
        assertEquals("Madrid", place.getValue("city").jsonPrimitive.content)
        assertEquals("ES", sent.json.getValue("country").jsonPrimitive.content)

        requests.clear()
        repository.createOrganization(draft.copy(place = OrganizationPlace()))
        val created = requests.single()
        assertEquals(HttpMethod.Post, created.method)
        val emptyPlace = created.json["place"]
        assertTrue(emptyPlace == null || emptyPlace == JsonNull, "an empty place is sent as no place")
    }
}
