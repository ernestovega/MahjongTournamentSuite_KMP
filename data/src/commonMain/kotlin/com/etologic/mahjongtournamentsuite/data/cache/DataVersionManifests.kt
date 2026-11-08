package com.etologic.mahjongtournamentsuite.data.cache

import com.etologic.mahjongtournamentsuite.data.backend.FunctionsBackendApi
import com.etologic.mahjongtournamentsuite.data.backend.dto.DataVersionsManifestDto
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * How long one manifest answer serves the revision checks of all repositories. The reads of one screen ask for the
 * same revisions many times in a row, and they share one request.
 */
internal val MANIFEST_SHARE_TIME: Duration = 2.seconds

internal const val GLOBAL_MANIFEST_KEY = "manifest:global"

internal fun tournamentManifestKey(tournamentId: String) = "manifest:tournament:$tournamentId"

/**
 * The global revisions (tournaments, EMA players, countries, users). A tournament manifest also has them, so after
 * one, this does not ask the server again within [MANIFEST_SHARE_TIME].
 */
internal suspend fun RepositoryCache.globalManifest(
    backendApi: FunctionsBackendApi,
    token: String,
): DataVersionsManifestDto = shared(GLOBAL_MANIFEST_KEY, MANIFEST_SHARE_TIME) { backendApi.globalDataVersions(token) }

/** The revisions of one tournament. Its global revisions also answer [globalManifest] for a short time. */
internal suspend fun RepositoryCache.tournamentManifest(
    backendApi: FunctionsBackendApi,
    token: String,
    tournamentId: String,
    maxAge: Duration = MANIFEST_SHARE_TIME,
): DataVersionsManifestDto = sharedAndSeed(
    key = tournamentManifestKey(tournamentId),
    maxAge = maxAge,
    alsoShares = { manifest ->
        // An answer without global revisions must not hide them as zeros.
        if (manifest.globalResources.isEmpty()) {
            emptyMap()
        } else {
            mapOf(GLOBAL_MANIFEST_KEY to DataVersionsManifestDto(resources = manifest.globalResources))
        }
    },
) { backendApi.tournamentDataVersions(token, tournamentId) }
