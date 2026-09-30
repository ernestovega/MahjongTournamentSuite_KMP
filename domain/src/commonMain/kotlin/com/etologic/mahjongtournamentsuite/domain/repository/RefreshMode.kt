package com.etologic.mahjongtournamentsuite.domain.repository

/** Controls if a repository can return cached data or must wait for the remote source. */
enum class RefreshMode {
    IF_CHANGED,
    FORCE,
}
