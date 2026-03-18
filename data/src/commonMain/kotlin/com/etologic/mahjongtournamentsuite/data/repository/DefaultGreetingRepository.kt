package com.etologic.mahjongtournamentsuite.data.repository

import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.data.platform.PlatformNameProvider
import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.repository.GreetingRepository
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.time.Clock

class DefaultGreetingRepository(
    private val platformNameProvider: PlatformNameProvider,
    private val logger: Logger,
    private val json: Json,
) : GreetingRepository {
    override suspend fun getGreeting(): AppResult<String> = runCatching {
        val platformName = platformNameProvider.platformName()
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

        logger.d {
            json.encodeToString(
                GreetingLogPayload(
                    platform = platformName,
                    date = today.toString(),
                ),
            )
        }

        "Hello, $platformName! Today is $today."
    }.fold(
        onSuccess = { greeting -> AppResult.Success(greeting) },
        onFailure = { throwable ->
            logger.e(throwable) { "Failed to build greeting message." }
            AppResult.Failure(AppError.Unexpected(throwable.message))
        },
    )
}

@Serializable
private data class GreetingLogPayload(
    val platform: String,
    val date: String,
)
