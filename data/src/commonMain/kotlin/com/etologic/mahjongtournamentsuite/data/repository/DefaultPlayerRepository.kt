package com.etologic.mahjongtournamentsuite.data.repository

import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.data.backend.FunctionsBackendApi
import com.etologic.mahjongtournamentsuite.data.backend.dto.CreatePlayerRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.PlayersResponseDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.UpdatePlayerPhotoRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.UpdatePlayerRequestDto
import com.etologic.mahjongtournamentsuite.data.cache.Persistence
import com.etologic.mahjongtournamentsuite.data.cache.RepositoryCache
import com.etologic.mahjongtournamentsuite.data.cache.globalManifest
import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.Player
import com.etologic.mahjongtournamentsuite.domain.repository.AuthRepository
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerRepository
import com.etologic.mahjongtournamentsuite.domain.repository.RefreshMode
import kotlin.io.encoding.Base64

class DefaultPlayerRepository(
    private val backendApi: FunctionsBackendApi,
    private val authRepository: AuthRepository,
    private val logger: Logger,
    private val cache: RepositoryCache,
) : PlayerRepository {
    override suspend fun listPlayers(refreshMode: RefreshMode): AppResult<List<Player>> =
        cachedRequest("Listing shared players", refreshMode)

    override suspend fun createPlayer(player: Player): AppResult<Player> = request("Creating shared player") { token ->
        backendApi.createPlayer(
            token,
            CreatePlayerRequestDto(player.emaId, player.firstName, player.lastName, player.country),
        ).toPlayer().also { cache.markStale(PLAYERS_CACHE_KEY) }
    }

    override suspend fun updatePlayer(previousEmaId: String, player: Player): AppResult<Unit> = request("Updating shared player") { token ->
        backendApi.updatePlayer(
            token,
            previousEmaId,
            UpdatePlayerRequestDto(player.emaId, player.firstName, player.lastName, player.country),
        )
        cache.markStale(PLAYERS_CACHE_KEY)
    }

    override suspend fun updatePlayerPhoto(
        emaId: String,
        contentType: String,
        bytes: ByteArray,
    ): AppResult<Player> = request("Updating shared player photo") { token ->
        backendApi.updatePlayerPhoto(
            idToken = token,
            emaId = emaId,
            request = UpdatePlayerPhotoRequestDto(
                contentType = contentType,
                dataBase64 = Base64.Default.encode(bytes),
            ),
        ).toPlayer().also { cache.markStale(PLAYERS_CACHE_KEY) }
    }

    private suspend fun <T> request(action: String, block: suspend (String) -> T): AppResult<T> = runCatching {
        withFreshIdToken(block)
    }.fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { throwable ->
            logger.w(throwable) { "$action failed." }
            AppResult.Failure(throwable.toAppError())
        },
    )

    private suspend fun cachedRequest(
        action: String,
        refreshMode: RefreshMode,
    ): AppResult<List<Player>> = runCatching {
        val session = authRepository.currentSession() ?: error("No active session")
        // The answer is also saved on the device, so a new start of the app does not download it again.
        cache.getOrLoadPersisted(
            ownerUid = session.uid,
            key = PLAYERS_CACHE_KEY,
            refreshMode = refreshMode,
            revision = {
                withFreshIdToken { token ->
                    cache.globalManifest(backendApi, token).resources["emaPlayers"]?.revision ?: 0
                }
            },
            persistence = Persistence(PLAYERS_CACHE_KEY, PlayersResponseDto.serializer()),
            load = { withFreshIdToken { token -> backendApi.listPlayers(token) } },
            map = { response -> response.players.map { it.toPlayer() } },
        )
    }.fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { throwable ->
            logger.w(throwable) { "$action failed." }
            AppResult.Failure(throwable.toAppError())
        },
    )

    private suspend fun <T> withFreshIdToken(block: suspend (String) -> T): T =
        authRepository.withFreshIdToken(block)

    private fun com.etologic.mahjongtournamentsuite.data.backend.dto.PlayerDto.toPlayer() = Player(
        emaId = emaId,
        firstName = firstName,
        lastName = lastName,
        country = country.toPlayerCountryCode(),
        photoUrl = photoUrl,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

private const val PLAYERS_CACHE_KEY = "global:emaPlayers"

private fun String.toPlayerCountryCode(): String =
    trim().uppercase().takeUnless { it == "EU" }.orEmpty()

private fun Throwable.toAppError(): AppError = toRepositoryAppError("Shared player request failed.")
