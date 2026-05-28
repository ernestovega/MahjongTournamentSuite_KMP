package com.etologic.mahjongtournamentsuite.data.network

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.js.Js

actual fun httpClientEngineFactory(): HttpClientEngineFactory<*> = Js

