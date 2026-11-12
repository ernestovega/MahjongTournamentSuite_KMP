package com.etologic.mahjongtournamentsuite.presentation.telemetry

/**
 * Keeps a load time where a benchmark can read it. On the web it goes to `window.mtsScreenLoads`, a list that the
 * benchmark of the web apps reads (see docs/benchmark.md). The other platforms keep nothing.
 */
internal expect fun publishScreenLoad(screen: String, tab: String?, kind: String, millis: Long)
