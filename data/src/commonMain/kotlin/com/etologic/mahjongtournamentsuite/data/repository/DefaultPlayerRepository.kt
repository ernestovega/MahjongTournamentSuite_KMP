package com.etologic.mahjongtournamentsuite.data.repository

import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.data.backend.BackendHttpException
import com.etologic.mahjongtournamentsuite.data.backend.FunctionsBackendApi
import com.etologic.mahjongtournamentsuite.data.backend.dto.CreatePlayerRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.UpdatePlayerRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.UpdatePlayerPhotoRequestDto
import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.Player
import com.etologic.mahjongtournamentsuite.domain.repository.AuthRepository
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerRepository
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlin.io.encoding.Base64

class DefaultPlayerRepository(
    private val backendApi: FunctionsBackendApi,
    private val authRepository: AuthRepository,
    private val logger: Logger,
) : PlayerRepository {
    override suspend fun listPlayers(): AppResult<List<Player>> = request("Listing shared players") { token ->
        backendApi.listPlayers(token).players.map { it.toPlayer() }
    }

    override suspend fun createPlayer(player: Player): AppResult<Player> = request("Creating shared player") { token ->
        backendApi.createPlayer(token, CreatePlayerRequestDto(player.emaId, player.name, player.country)).toPlayer()
    }

    override suspend fun updatePlayer(previousEmaId: String, player: Player): AppResult<Unit> = request("Updating shared player") { token ->
        backendApi.updatePlayer(token, previousEmaId, UpdatePlayerRequestDto(player.emaId, player.name, player.country))
        Unit
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
        ).toPlayer()
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

    private suspend fun <T> withFreshIdToken(block: suspend (String) -> T): T {
        val session = authRepository.currentSession() ?: error("No active session")
        return try {
            block(session.idToken)
        } catch (error: BackendHttpException) {
            if (error.status != HttpStatusCode.Unauthorized) throw error
            when (val refreshed = authRepository.refreshSession()) {
                is AppResult.Success -> block(refreshed.value.idToken)
                is AppResult.Failure -> throw error
            }
        }
    }

    private fun com.etologic.mahjongtournamentsuite.data.backend.dto.PlayerDto.toPlayer() = Player(
        emaId = emaId,
        name = name.uppercase(),
        country = country,
        photoUrl = photoUrl,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

private fun Throwable.toAppError(): AppError = when (this) {
    is CancellationException -> throw this
    is BackendHttpException -> AppError.Unexpected("Backend error ${status.value}: ${responseBody}")
    else -> AppError.Unexpected(message ?: "Shared player request failed.")
}
