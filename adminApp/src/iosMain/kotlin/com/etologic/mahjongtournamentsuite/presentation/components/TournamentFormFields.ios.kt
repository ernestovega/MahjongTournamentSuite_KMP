package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.material3.CalendarLocale
import androidx.compose.material3.ExperimentalMaterial3Api
import platform.Foundation.NSLocale

@OptIn(ExperimentalMaterial3Api::class)
internal actual fun mondayFirstCalendarLocale(): CalendarLocale = NSLocale(localeIdentifier = "en_GB")
