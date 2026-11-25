package com.etologic.mahjongtournamentsuite.data.network

import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.compression.ContentEncoding

/** CIO sends no Accept-Encoding, so the answers of the API came whole. The plugin asks for gzip and unpacks it. */
internal actual fun HttpClientConfig<*>.configureCompression() {
    install(ContentEncoding) {
        gzip()
    }
}
