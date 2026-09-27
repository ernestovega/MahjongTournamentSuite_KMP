package com.etologic.mahjongtournamentsuite.domain.validation

/** Validates inclusive tournament dates in ISO-8601 calendar format. */
object TournamentDateRangeValidator {
    fun isValidDate(value: String): Boolean {
        if (!DATE_PATTERN.matches(value)) return false
        val year = value.substring(0, 4).toInt()
        val month = value.substring(5, 7).toInt()
        val day = value.substring(8, 10).toInt()
        if (month !in 1..12) return false
        val daysInMonth = when (month) {
            2 -> if (isLeapYear(year)) 29 else 28
            4, 6, 9, 11 -> 30
            else -> 31
        }
        return day in 1..daysInMonth
    }

    fun isValidRange(startDate: String, endDate: String): Boolean =
        isValidDate(startDate) && isValidDate(endDate) && startDate <= endDate

    private fun isLeapYear(year: Int): Boolean =
        year % 400 == 0 || year % 4 == 0 && year % 100 != 0

    private val DATE_PATTERN = Regex("\\d{4}-\\d{2}-\\d{2}")
}
