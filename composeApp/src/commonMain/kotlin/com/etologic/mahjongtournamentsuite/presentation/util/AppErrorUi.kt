package com.etologic.mahjongtournamentsuite.presentation.util

import com.etologic.mahjongtournamentsuite.domain.model.AppError

fun AppError.toUiMessage(): String = when (this) {
    AppError.Network -> "Network request failed."
    is AppError.Unexpected -> message ?: "Something went wrong."
}
