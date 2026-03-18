package com.etologic.mahjongtournamentsuite.domain.model

sealed interface AppError {
    data object Network : AppError
    data class Unexpected(val message: String?) : AppError
}
