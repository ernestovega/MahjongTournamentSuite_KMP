package com.etologic.mahjongtournamentsuite.player

@JsModule("@js-joda/timezone")
external object JsJodaTimeZoneModule

private val jsJodaTz = JsJodaTimeZoneModule

/** Loads the time zone database. Without it, kotlinx-datetime knows only the system zone in the browser. */
internal fun loadTimeZones() {
    jsJodaTz
}
