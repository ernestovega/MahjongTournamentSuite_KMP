package com.etologic.mahjongtournamentsuite.presentation.presenter

import com.etologic.mahjongtournamentsuite.domain.telemetry.NoTelemetry
import com.etologic.mahjongtournamentsuite.domain.telemetry.Telemetry
import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryEvent
import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryProperty
import com.etologic.mahjongtournamentsuite.domain.telemetry.telemetryRole
import com.etologic.mahjongtournamentsuite.domain.telemetry.track
import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.AuthSession
import com.etologic.mahjongtournamentsuite.domain.model.GlobalUserRole
import com.etologic.mahjongtournamentsuite.domain.model.UserProfile
import com.etologic.mahjongtournamentsuite.domain.repository.AuthRepository
import com.etologic.mahjongtournamentsuite.domain.model.SavedCredentials

class AuthPresenter(
    private val authRepository: AuthRepository,
    private val logger: Logger,
    private val telemetry: Telemetry = NoTelemetry,
) {
    suspend fun hasActiveSession(): Boolean = authRepository.currentSession() != null

    suspend fun signIn(
        email: String,
        password: String,
    ): AppResult<AuthSession> {
        logger.i { "Signing in user." }
        return authRepository.signIn(
            email = email,
            password = password,
        ).track(telemetry, TelemetryEvent.LOGIN)
    }

    /**
     * Success(true) when the signed-in account is an admin or an editor. This app is only for them.
     * Failure when the profile cannot load. The caller must not let the account pass then.
     */
    suspend fun isManager(): AppResult<Boolean> = when (val result = authRepository.getMe()) {
        is AppResult.Success -> {
            telemetry.userProperty(TelemetryProperty.ROLE, result.value.roles.telemetryRole())
            AppResult.Success(result.value.roles.any { it == GlobalUserRole.ADMIN || it == GlobalUserRole.EDITOR })
        }
        is AppResult.Failure -> result
    }

    suspend fun requestPasswordReset(email: String): AppResult<Unit> {
        logger.i { "Requesting password reset." }
        return authRepository.requestPasswordReset(email).track(telemetry, TelemetryEvent.PASSWORD_RESET_REQUESTED)
    }

    suspend fun savedCredentials(): SavedCredentials? = authRepository.savedCredentials()

    suspend fun clearSavedCredentials() = authRepository.clearSavedCredentials()

    suspend fun signOut() {
        logger.i { "Signing out user." }
        authRepository.signOut()
        telemetry.event(TelemetryEvent.LOGOUT)
        telemetry.userProperty(TelemetryProperty.ROLE, null)
    }

    suspend fun loadProfile(): AppResult<UserProfile> {
        logger.i { "Loading user profile." }
        return authRepository.getMe().also { result ->
            if (result is AppResult.Success) telemetry.userProperty(TelemetryProperty.ROLE, result.value.roles.telemetryRole())
        }
    }
}
