package com.etologic.mahjongtournamentsuite.presentation.util

import com.etologic.mahjongtournamentsuite.presentation.util.UiText
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import mahjongtournamentsuite.adminapp.generated.resources.Res
import mahjongtournamentsuite.adminapp.generated.resources.data_checked
import mahjongtournamentsuite.adminapp.generated.resources.data_loaded
import mahjongtournamentsuite.adminapp.generated.resources.data_results
import org.jetbrains.compose.resources.stringResource

/**
 * One number for the results the app holds: the sum of the versions of all tables.
 * Every save and every reset adds 1. The editor, the ranking and the file names use the same number,
 * so "r123" on the screen is the same data as "r123" in a file name.
 */
fun tournamentDataRevision(tableVersions: Iterable<Long>): Long = tableVersions.sum()

/** `r123`, or an empty text when the revision is not known. */
fun dataRevisionLabel(revision: Long?): String = revision?.let { "r$it" }.orEmpty()

private fun Int.two() = toString().padStart(2, '0')

/** `20261011-1530`. It sorts in time order and has no character that a file system refuses. */
fun fileStamp(now: Instant, zone: TimeZone = TimeZone.currentSystemDefault()): String {
    val time = now.toLocalDateTime(zone)
    return "${time.year}${(time.month.ordinal + 1).two()}${time.day.two()}-${time.hour.two()}${time.minute.two()}"
}

/** `15:30:12`, for the screen. */
fun clockLabel(now: Instant, zone: TimeZone = TimeZone.currentSystemDefault()): String {
    val time = now.toLocalDateTime(zone)
    return "${time.hour.two()}:${time.minute.two()}:${time.second.two()}"
}

/** The text under the title of the tournament screen, and in a corner of the ranking: `Results r123 · checked 15:30:12`. */
fun dataStatus(
    revision: Long?,
    at: Instant?,
    loaded: Boolean = false,
    zone: TimeZone = TimeZone.currentSystemDefault(),
): UiText = UiText.Joined(
    listOfNotNull(
        revision?.let { UiText.of(Res.string.data_results, dataRevisionLabel(it)) },
        at?.let { UiText.of(if (loaded) Res.string.data_loaded else Res.string.data_checked, clockLabel(it, zone)) },
    ),
    separator = " · ",
)

private val unsafeFileCharacters = Regex("[^A-Za-z0-9_-]+")

/**
 * Names a generated file so it tells what it is and which data it holds:
 * `<tournament>-<kind>-r<revision>-<yyyymmdd-hhmm>.<extension>`. The revision part is left out when the file does not
 * depend on the results.
 */
fun generatedFileName(
    tournamentName: String,
    kind: String,
    extension: String,
    now: Instant,
    revision: Long? = null,
    year: String? = null,
    zone: TimeZone = TimeZone.currentSystemDefault(),
): String {
    val safeName = tournamentName.replace(unsafeFileCharacters, "-").trim('-').ifBlank { "tournament" }
    val parts = listOfNotNull(safeName, year, kind, revision?.let { dataRevisionLabel(it) }, fileStamp(now, zone))
    return parts.joinToString("-") + "." + extension
}
