package com.etologic.mahjongtournamentsuite

/**
 * The time zones of 1996 to 2056 (the bundle of @js-joda/timezone 3.1.0 with the rules of 30 years before and after
 * 2026), not of all years. kotlinx-datetime reads the whole database the first time the app asks for any zone, also the
 * zone of the device, and the full one (715 KB) took about half a second of a mid-range phone on every page load. The
 * dates of the app are all in that range. Outside it a zone keeps its first or last offset of the range.
 */
@JsModule("@js-joda/timezone/dist/js-joda-timezone-60-year-range.esm.js")
external object JsJodaTimeZoneModule

private val jsJodaTz = JsJodaTimeZoneModule

/** Loads the time zone database. Without it, kotlinx-datetime knows only the system zone in the browser. */
internal fun loadTimeZones() {
    jsJodaTz
}
