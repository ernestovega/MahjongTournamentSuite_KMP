package com.etologic.mahjongtournamentsuite.presentation.telemetry

private fun pushScreenLoad(screen: String, tab: String, kind: String, millis: Double): Unit = js("{ (window.mtsScreenLoads = window.mtsScreenLoads || []).push({ screen: screen, tab: tab, kind: kind, ms: millis, at: performance.now() }); }")

internal actual fun publishScreenLoad(screen: String, tab: String?, kind: String, millis: Long) {
    pushScreenLoad(screen, tab.orEmpty(), kind, millis.toDouble())
}
