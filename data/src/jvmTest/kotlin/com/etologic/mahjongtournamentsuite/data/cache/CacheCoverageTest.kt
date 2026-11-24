package com.etologic.mahjongtournamentsuite.data.cache

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Every request of [com.etologic.mahjongtournamentsuite.data.backend.FunctionsBackendApi] has a cache policy here, so a
 * new screen or write cannot forget the local cache (docs/multi-editor-sync.md). The test reads the sources:
 *
 * - every request of the API is in [policies], and every entry of [policies] is still a request;
 * - a [Cached] read is called inside the cache: `cachedRequest`, `persistedRequest`, `getOrLoad`, the table store…;
 * - a [Write] is called in a function that marks the cached data old (`markStale`), or in the function it names;
 * - every revision of the server manifest (firebase dataVersionsService.ts) is read by the tournament repository;
 * - the public CDN keeps the manifest and the tables of a tournament in play no longer than the fastest check.
 *
 * It runs with the other tests of the module, every night in CI. Run it alone with
 * `./gradlew :data:jvmTest --tests '*CacheCoverageTest*'`.
 */
class CacheCoverageTest {
    private sealed interface Policy

    /** A read that goes through the cache and is checked against a revision of the manifest. */
    private data object Cached : Policy

    /** A read that always asks the server, and why that is right. */
    private data class Live(val reason: String) : Policy

    /** A write. [via] names the function that marks the cache old, when it is not the one with the call. */
    private data class Write(val via: String? = null) : Policy

    /** A write whose data the app never caches, and why. */
    private data class Uncached(val reason: String) : Policy

    private val policies: Map<String, Policy> = mapOf(
        // --- Revisions: the cache itself asks for them. ---
        "globalDataVersions" to Cached,
        "tournamentDataVersions" to Cached,

        // --- Session and own account ---
        "featureFlags" to Live("Read once when the app starts."),
        "signIn" to Write(),
        "refresh" to Uncached("A new token. The cache belongs to the account, which does not change."),
        "requestPasswordReset" to Uncached("Sends an email."),
        "sendSupportMessage" to Uncached("Sends an email."),
        "me" to Cached,
        "myTournaments" to Live("GET /me: the own slots and registrations. Read when the list opens and by the table reminder, never polled fast."),
        "updateOwnAlias" to Write(),
        "updateOwnPhoto" to Write(via = "profilePhotoRequest"),
        "removeOwnPhoto" to Write(via = "profilePhotoRequest"),
        "deleteOwnAccount" to Write(via = "signOut"),

        // --- Accounts, roles and organizations (admin) ---
        "whoAmI" to Cached,
        "listUsers" to Cached,
        "lookupUser" to Live("One search typed by an admin."),
        "createUser" to Write(),
        "updateUser" to Write(),
        "setUserDisabled" to Write(),
        "deleteUser" to Write(),
        "deleteUsers" to Write(),
        "updateUserPhoto" to Write(),
        "removeUserPhoto" to Write(),
        "setUserPermissions" to Write(),
        "removeFromMyOrganizations" to Write(),
        "sendPasswordResets" to Uncached("Sends emails."),
        "listAccountRequests" to Live("A short list that an admin opens now and then."),
        "resolveAccountRequest" to Write(),
        "listRoles" to Live("Read when the roles screen or the permissions dialog opens."),
        "createRole" to Uncached("Nobody has a new role yet, and the roles are not cached."),
        "updateRole" to Write(),
        "deleteRole" to Write(),
        "listOrganizations" to Live("Read when the organizations screen or the tournament editor opens."),
        "createOrganization" to Write(),
        "updateOrganization" to Write(),
        "deleteOrganization" to Write(),
        "errorGroups" to Live("The error log: always the newest."),
        "errorGroup" to Live("The error log: always the newest."),
        "activity" to Live("The activity log: always the newest, page by page."),

        // --- Statistics: sums the server keeps; the public routes have a CDN cache of 60 s. ---
        "statisticsOverview" to Live("Server sums. The screen checks every 5 minutes, the public CDN keeps it 60 s."),
        "statisticsGroup" to Live("Server sums. The screen checks every 5 minutes, the public CDN keeps it 60 s."),
        "personStatistics" to Live("Server sums. The screen checks every 5 minutes, the public CDN keeps it 60 s."),
        "personStandings" to Live("Asked again only when the revisions of the own tournaments change (standingsRevision)."),

        // --- Tournaments ---
        "listTournaments" to Cached,
        "createTournament" to Write(),
        "renameTournament" to Write(),
        "updateTournamentSettings" to Write(),
        "deleteTournament" to Write(),
        "resolveVenueLink" to Live("Resolves a map link typed in the editor."),
        "listCountries" to Cached,
        "listTournamentMembers" to Cached,
        "upsertTournamentMember" to Write(),
        "removeTournamentMember" to Write(),
        "listTournamentPlayers" to Cached,
        "listTournamentTeams" to Cached,
        "updateTournamentTeam" to Write(),
        "assignTournamentPlayer" to Write(),
        "setPlayerSubstitution" to Write(),
        "removePlayerSubstitution" to Write(),
        "changePlayerCount" to Write(),
        "listTournamentRounds" to Cached,
        "listTournamentTables" to Cached,
        "getTableWithHands" to Cached,
        "getRoundTablesWithHands" to Cached,
        "getAllTablesWithHands" to Cached,
        "saveTableState" to Write(),
        "resetTable" to Write(),
        "completeTables" to Write(),
        "roundClocks" to Live("The clocks of the rounds come fresh with each check: the hand buttons follow the Timer."),
        "updateRoundClock" to Write(),
        "updateRoundControl" to Write(),

        // --- Generated files: cached until the data they hold changes. ---
        "generateTournamentIdCards" to Cached,
        "generateTournamentIdList" to Cached,
        "generateTableQrCodes" to Cached,
        "generateEmaReport" to Cached,
        "generateIdCardProof" to Live("A proof of the editor's unsaved design."),
        "getIdCardStaffNames" to Live("Read when the ID cards dialog opens."),
        "getIdCardStaffCandidates" to Live("Read when the ID cards dialog opens."),
        "saveIdCardStaffNames" to Uncached("The staff names are not cached, and the ID card files have them in their key."),

        // --- EMA players ---
        "listPlayers" to Cached,
        "createPlayer" to Write(),
        "updatePlayer" to Write(),
        "updatePlayerPhoto" to Write(),
        "deleteEmaPlayers" to Write(),
        "previewEmaPlayerDeletes" to Live("A preview before a delete."),

        // --- Hands written by the players ---
        "previewHand" to Live("A preview of a hand, nothing is saved."),
        "recordHand" to Write(),
        "addPenalties" to Write(),
        "assignSeats" to Write(),

        // --- Player accounts, import and export ---
        "sendPlayerAccounts" to Write(),
        "savePlayerEmails" to Write(),
        "exportPlayers" to Live("A file made on demand."),
        "previewPlayerImport" to Live("A preview before the import."),
        "applyPlayerImport" to Write(),

        // --- Registrations: the lists are not cached and poll every minute. ---
        "publicRegistrations" to Live("The public list polls every minute; the CDN keeps it 15 s."),
        "registrationLink" to Live("Resolves a link once."),
        "adminRegistrations" to Live("The admin list loads when it opens and after each change."),
        "ownRegistration" to Live("The own registration polls with the public list."),
        "register" to Write(),
        "confirmRegistration" to Write(),
        "cancelOwnRegistration" to Write(),
        "updateOwnRegistration" to Uncached("Food restrictions and consent: no cached data shows them."),
        "createOwnTeam" to Uncached("The teams of the registrations are not cached."),
        "renameOwnTeam" to Uncached("The teams of the registrations are not cached."),
        "leaveOwnTeam" to Uncached("The teams of the registrations are not cached."),
        "addOwnTeamMember" to Uncached("The teams of the registrations are not cached."),
        "removeOwnTeamMember" to Uncached("The teams of the registrations are not cached."),
        "requestWithoutEmaNumber" to Uncached("Sends an email to the organizer."),
        "adminAddRegistration" to Write(),
        "adminUpdateRegistration" to Write(),
        "adminRemoveRegistration" to Write(),
        "updateRegistrationSettings" to Write(),
        "importRegistrations" to Write(),
        "makeDraw" to Write(),
    )

    /** Gradle runs the tests with the module folder as the working directory. */
    private val main = File("src/commonMain/kotlin/com/etologic/mahjongtournamentsuite/data")
    private val apiFile = File(main, "backend/FunctionsBackendApi.kt")
    private val sources by lazy {
        main.walkTopDown().filter { it.isFile && it.extension == "kt" && it != apiFile }.map { it.name to it.readText() }.toList()
    }

    private val cacheUse = Regex("""getOrLoad|cachedRequest\(|persistedRequest\(|cachedGeneratedFile\(|tableStore\.all\(|\bshared(AndSeed)?\(""")
    private val staleMark = Regex("""markStale\(|tableStore\.forget\(|countersChanged\(\)|cache\.clear\(\)""")
    private val functionStart = Regex("""\n {4}(?:override |private |internal )*(?:suspend )?fun (?:<[^>]*> )?(?:[\w.]+\.)?(\w+)""")

    private fun apiRequests(): List<String> = Regex("""\n {4}suspend fun (\w+)""").findAll(apiFile.readText()).map { it.groupValues[1] }.toList()

    /** The function around each call of [request]: its name and its text. */
    private fun callSites(request: String): List<Triple<String, String, String>> = sources.flatMap { (file, text) ->
        val starts = functionStart.findAll(text).map { it.range.first to it.groupValues[1] }.toList()
        Regex("""backendApi\.$request\b""").findAll(text).map { call ->
            val start = starts.lastOrNull { it.first < call.range.first }
            val end = starts.firstOrNull { it.first > call.range.first }?.first ?: text.length
            Triple(file, start?.second ?: "?", text.substring(start?.first ?: 0, end))
        }.toList()
    }

    private fun functionText(name: String): String? = sources.firstNotNullOfOrNull { (_, text) ->
        val starts = functionStart.findAll(text).toList()
        val index = starts.indexOfFirst { it.groupValues[1] == name }.takeIf { it >= 0 } ?: return@firstNotNullOfOrNull null
        text.substring(starts[index].range.first, starts.getOrNull(index + 1)?.range?.first ?: text.length)
    }

    @Test
    fun theTestFindsTheSources() {
        assertTrue(apiFile.exists(), "Run the test from the data module: ${apiFile.absolutePath}")
        assertTrue(apiRequests().size > 100, "The requests of the API were not found")
    }

    @Test
    fun everyRequestHasACachePolicy() {
        val requests = apiRequests().toSet()
        val missing = requests - policies.keys
        val gone = policies.keys - requests
        assertTrue(
            missing.isEmpty(),
            "These requests have no cache policy. Cache the read, mark the cache old after the write, or add it to " +
                "CacheCoverageTest.policies with the reason: $missing",
        )
        assertTrue(gone.isEmpty(), "These policies name requests that no longer exist: $gone")
    }

    @Test
    fun everyCachedReadGoesThroughTheCache() {
        val problems = policies.filterValues { it == Cached }.keys.flatMap { request ->
            val sites = callSites(request)
            if (sites.isEmpty()) {
                listOf("$request: nothing calls it")
            } else {
                sites.filterNot { (_, _, body) -> cacheUse.containsMatchIn(body) }.map { (file, function, _) -> "$file:$function calls $request outside the cache" }
            }
        }
        assertEquals(emptyList(), problems)
    }

    @Test
    fun everyWriteMarksTheCacheOld() {
        val problems = policies.mapNotNull { (request, policy) -> (policy as? Write)?.let { request to it } }.flatMap { (request, policy) ->
            val sites = callSites(request)
            if (sites.isEmpty()) return@flatMap listOf("$request: nothing calls it")
            sites.filterNot { (_, _, body) ->
                staleMark.containsMatchIn(body) || policy.via?.let { via -> body.contains("$via(") && functionText(via)?.let(staleMark::containsMatchIn) == true } == true
            }.map { (file, function, _) -> "$file:$function calls $request and marks nothing old" }
        }
        assertEquals(emptyList(), problems)
    }

    @Test
    fun everyRevisionOfTheServerIsReadByTheApp() {
        val server = File("../firebase/functions/src/services/dataVersionsService.ts")
        if (!server.exists()) fail("The backend sources are missing: ${server.absolutePath}")
        val text = server.readText()
        val resources = listOf("GlobalDataResource", "TournamentDataResource").flatMap { type ->
            val union = Regex("""export type $type = ([^;]+);""").find(text)?.groupValues?.get(1) ?: fail("$type not found")
            Regex(""""(\w+)"""").findAll(union).map { it.groupValues[1] }.toList()
        }
        val repository = sources.filter { it.first.startsWith("DefaultTournamentRepository") || it.first.startsWith("TournamentRepository") }.joinToString("\n") { it.second }
        val unread = resources.filterNot { repository.contains("\"$it\"") }
        assertTrue(unread.isEmpty(), "The manifest has revisions that the app never reads, so their changes never reach the cache: $unread")
    }

    @Test
    fun theCdnKeepsTheHandsNoLongerThanTheFastestCheck() {
        val routes = File("../firebase/functions/src/api/routes/public.routes.ts").readText()
        val cdnSeconds = Regex("""\bplaying: (\d+),""").find(routes)?.groupValues?.get(1)?.toInt() ?: fail("PUBLIC_CACHE_SECONDS.playing not found")
        val schedule = File("../playerApp/src/commonMain/kotlin/com/etologic/mahjongtournamentsuite/player/PollSchedule.kt").readText()
        val fastest = Regex("""OWN_TABLE_IN_PLAY: Duration = (\d+)\.seconds""").find(schedule)?.groupValues?.get(1)?.toInt() ?: fail("OWN_TABLE_IN_PLAY not found")
        assertTrue(
            cdnSeconds <= fastest,
            "The CDN keeps the manifest and the tables $cdnSeconds s, but the own table checks every $fastest s: most checks would get the same answer.",
        )
    }
}
