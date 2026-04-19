package com.etologic.mahjongtournamentsuite.data.network

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.cio.CIO

actual fun httpClientEngineFactory(): HttpClientEngineFactory<*> = CIO

