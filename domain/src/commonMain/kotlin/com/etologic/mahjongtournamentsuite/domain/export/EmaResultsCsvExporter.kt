package com.etologic.mahjongtournamentsuite.domain.export

/** The column order used by the old EMA report grid. */
data class EmaResultsRow(
    val place: Int,
    val firstName: String,
    val lastName: String,
    val emaNumber: String,
    val tablePoints: String,
    val score: Int,
    val emaMember: String,
    val country: String,
)

object EmaResultsCsvExporter {
    private val headers = listOf(
        "Place",
        "First Name",
        "Last name",
        "EMA number",
        "Table points",
        "Score",
        "Ema Member",
        "Country",
    )

    /** Creates an Excel-friendly UTF-8 CSV with the legacy EMA report columns. */
    fun export(rows: List<EmaResultsRow>): String = buildString {
        append('\uFEFF')
        appendLine(headers.joinToString(",", transform = ::escape))
        rows.sortedBy { it.place }.forEach { row ->
            appendLine(
                listOf(
                    row.place.toString(),
                    row.firstName,
                    row.lastName,
                    row.emaNumber,
                    row.tablePoints,
                    row.score.toString(),
                    row.emaMember,
                    row.country,
                ).joinToString(",", transform = ::escape),
            )
        }
    }

    private fun escape(value: String): String =
        "\"${value.replace("\"", "\"\"")}\""
}
