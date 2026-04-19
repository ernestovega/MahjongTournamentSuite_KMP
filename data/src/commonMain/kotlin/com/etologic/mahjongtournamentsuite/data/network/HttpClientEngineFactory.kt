package com.etologic.mahjongtournamentsuite.data.network

import io.ktor.client.engine.HttpClientEngineFactory

expect fun httpClientEngineFactory(): HttpClientEngineFactory<*>

