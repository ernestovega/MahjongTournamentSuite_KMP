package com.etologic.mahjongtournamentsuite.presentation.util

import com.etologic.mahjongtournamentsuite.domain.model.AppError

fun AppError.toUiMessage(): String = when (this) {
    AppError.Network -> "Network request failed."
    is AppError.Conflict -> message
    is AppError.Unexpected -> message.toFriendlyErrorMessage()
}

private fun String?.toFriendlyErrorMessage(): String {
    val raw = orEmpty()
    val normalized = raw.lowercase()
    val backendMessage = Regex("\\\"message\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"")
        .find(raw)
        ?.groupValues
        ?.getOrNull(1)
        ?.lowercase()
        .orEmpty()
    val details = "$normalized $backendMessage"

    return when {
        details.contains("missing email") || details.contains("missing identifier") ||
            details.contains("missing password") -> "Enter your email and password."
        details.contains("invalid_login_credentials") || details.contains("invalid password") ||
            details.contains("email_not_found") || details.contains("user not found") ->
            "The email or password is incorrect."
        details.contains("invalid_email") || details.contains("invalid email") ->
            "Enter a valid email address."
        details.contains("too_many_attempts") || details.contains("too many") ->
            "Too many attempts. Wait a moment and try again."
        details.contains("permission denied") || details.contains("forbidden") ->
            "You do not have permission to perform this action."
        details.contains("unauthorized") || details.contains("unauthenticated") ->
            "Your session has expired. Sign in again."
        details.contains("timed out") -> "The request took too long. Try again."
        else -> "Something went wrong. Please try again."
    }
}
