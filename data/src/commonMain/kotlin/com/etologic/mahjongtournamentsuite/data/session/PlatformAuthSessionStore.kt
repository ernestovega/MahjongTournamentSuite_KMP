package com.etologic.mahjongtournamentsuite.data.session

import kotlinx.serialization.json.Json

expect class PlatformAuthSessionStore(
    json: Json,
) : AuthSessionStore

