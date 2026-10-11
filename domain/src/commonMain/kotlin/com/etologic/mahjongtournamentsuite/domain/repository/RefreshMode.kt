package com.etologic.mahjongtournamentsuite.domain.repository

/** Controls if a repository can return cached data or must wait for the remote source. */
enum class RefreshMode {
    /** Use the cached value if the server confirmed it recently. Otherwise check the server first. */
    IF_CHANGED,

    /** Always load from the server. */
    FORCE,

    /**
     * Use any cached value and never ask the server. Load only if there is no cached value, or a write made it old.
     * A screen that must stay still while it is open uses this mode.
     */
    CACHED,
}
