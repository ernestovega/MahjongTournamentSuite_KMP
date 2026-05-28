package com.etologic.mahjongtournamentsuite.data.network

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.okhttp.OkHttp

actual fun httpClientEngineFactory(): HttpClientEngineFactory<*> = OkHttp

