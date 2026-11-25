package com.etologic.mahjongtournamentsuite.data.network

import io.ktor.client.HttpClientConfig

/** NSURLSession asks for gzip and unpacks it by itself. */
internal actual fun HttpClientConfig<*>.configureCompression() = Unit
