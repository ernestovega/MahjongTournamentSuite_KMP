package com.etologic.mahjongtournamentsuite.presentation.util

import com.etologic.mahjongtournamentsuite.presentation.language.IosLanguage
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarIdentifierGregorian
import platform.Foundation.NSDateComponents
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.currentLocale

/** The pattern comes from a template, so each language puts the day, the month and the weekday in its own order. */
internal actual fun platformFormatDate(year: Int, month: Int, day: Int, withWeekday: Boolean, withYear: Boolean, fullNames: Boolean): String {
    val calendar = NSCalendar(calendarIdentifier = NSCalendarIdentifierGregorian)
    val components = NSDateComponents().apply {
        this.year = year.toLong()
        this.month = month.toLong()
        this.day = day.toLong()
    }
    val date = calendar.dateFromComponents(components) ?: return "$day/$month/$year"
    val template = buildString {
        if (withWeekday) append(if (fullNames) "EEEE" else "EEE")
        append("d")
        append(if (fullNames) "MMMM" else "MMM")
        if (withYear) append("y")
    }
    val formatter = NSDateFormatter().apply {
        this.calendar = calendar
        locale = IosLanguage.current?.let { NSLocale(localeIdentifier = it) } ?: NSLocale.currentLocale
        setLocalizedDateFormatFromTemplate(template)
    }
    return formatter.stringFromDate(date)
}
