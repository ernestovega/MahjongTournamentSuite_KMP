package com.etologic.mahjongtournamentsuite.presentation.presenter

import com.etologic.mahjongtournamentsuite.domain.model.AdminRegistrations
import com.etologic.mahjongtournamentsuite.domain.model.Player
import com.etologic.mahjongtournamentsuite.domain.model.RegistrationStatus

/**
 * The registrations of a tournament as a CSV file with the columns that the import reads
 * (firebase/functions/src/services/registrationImport.ts finds them by their title), so the file is also the example
 * of the import and goes back in as it is. The import leaves out the last two columns, Status and Paid: they are
 * only for the organizers. See docs/registrations.md.
 */
object RegistrationsCsv {
    /** The titles of the columns. The import finds each one by its title, in any order. */
    val HEADER = listOf("Timestamp", "Email", "First name", "Last name", "EMA number", "Country", "Team", "Food restrictions", "Status", "Paid")

    /** The order of the rows: the places, the waiting list and the people who did not confirm the email yet. */
    private val STATUS_ORDER = listOf(RegistrationStatus.CONFIRMED, RegistrationStatus.WAITING, RegistrationStatus.PENDING)

    /** The name of the file: `registrations-<short name of the tournament>.csv`. */
    fun fileName(tournamentName: String): String {
        val slug = tournamentName.lowercase()
            .map { if (it in 'a'..'z' || it in '0'..'9') it else '-' }
            .joinToString("")
            .replace(Regex("-+"), "-")
            .trim('-')
        return if (slug.isEmpty()) "registrations.csv" else "registrations-$slug.csv"
    }

    /**
     * The text of the file. The first and last names come from the EMA [registry] when the person is there, and
     * from the name of the registration otherwise. Lines end with CRLF, as RFC 4180 and Excel expect, and the text
     * starts with a byte order mark so Excel reads the accents as UTF-8 (the import skips it).
     */
    fun build(data: AdminRegistrations, registry: List<Player>): String {
        val byEma = registry.associateBy { it.emaId }
        val rows = STATUS_ORDER.flatMap { status ->
            data.registrations.filter { it.status == status }.sortedBy { it.position }
        }.map { registration ->
            val player = byEma[registration.emaId]
            val firstName = player?.firstName ?: registration.name
            val lastName = player?.lastName.orEmpty()
            listOf(
                registration.registeredAt,
                registration.email,
                firstName,
                lastName,
                registration.emaId,
                registration.country,
                if (data.isTeams) data.teamOf(registration)?.name.orEmpty() else "",
                registration.foodRestrictions,
                registration.status.csvName,
                if (registration.paid) "Yes" else "No",
            )
        }
        return BOM + (listOf(HEADER) + rows).joinToString(separator = "\r\n", postfix = "\r\n") { cells -> cells.joinToString(",") { escape(it) } }
    }

    const val BOM = "\uFEFF"

    private val RegistrationStatus.csvName: String
        get() = when (this) {
            RegistrationStatus.CONFIRMED -> "With a place"
            RegistrationStatus.WAITING -> "Waiting list"
            RegistrationStatus.PENDING -> "Email not confirmed"
        }

    /**
     * Quotes a cell with a comma, a semicolon, a quote or a line break, and doubles its quotes. A cell that starts
     * like a formula gets an apostrophe first, so Excel or Sheets show it as text and never run it: the names, teams
     * and food restrictions come from anybody who registers. The import takes the apostrophe off again.
     */
    internal fun escape(value: String): String {
        val text = if (value.firstOrNull() in FORMULA_STARTS) "'$value" else value
        return if (text.any { it == ',' || it == ';' || it == '"' || it == '\n' || it == '\r' }) "\"" + text.replace("\"", "\"\"") + "\"" else text
    }

    /** The first characters that make a spreadsheet read a cell as a formula (OWASP, CSV injection). */
    private val FORMULA_STARTS = setOf('=', '+', '-', '@', '\t', '\r')
}
