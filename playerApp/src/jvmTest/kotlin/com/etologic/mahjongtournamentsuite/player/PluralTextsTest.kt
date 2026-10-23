package com.etologic.mahjongtournamentsuite.player

import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import mahjongtournamentsuite.playerapp.generated.resources.Res
import mahjongtournamentsuite.playerapp.generated.resources.age_hours
import mahjongtournamentsuite.playerapp.generated.resources.age_minutes
import mahjongtournamentsuite.playerapp.generated.resources.age_seconds
import mahjongtournamentsuite.playerapp.generated.resources.data_stale
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString

class PluralTextsTest {
    private val original = Locale.getDefault()

    @AfterTest
    fun restoreLocale() = Locale.setDefault(original)

    @Test
    fun theAgeIsWrittenWithTheRightPluralInEnglish() = runTest {
        Locale.setDefault(Locale.ENGLISH)
        assertEquals("1 second", getPluralString(Res.plurals.age_seconds, 1, 1))
        assertEquals("45 seconds", getPluralString(Res.plurals.age_seconds, 45, 45))
        assertEquals("1 minute", getPluralString(Res.plurals.age_minutes, 1, 1))
        assertEquals("2 hours", getPluralString(Res.plurals.age_hours, 2, 2))
    }

    @Test
    fun theAgeIsWrittenWithTheRightPluralInSpanish() = runTest {
        Locale.setDefault(Locale.forLanguageTag("es"))
        assertEquals("1 segundo", getPluralString(Res.plurals.age_seconds, 1, 1))
        assertEquals("45 segundos", getPluralString(Res.plurals.age_seconds, 45, 45))
        assertEquals("1 minuto", getPluralString(Res.plurals.age_minutes, 1, 1))
        assertEquals("2 horas", getPluralString(Res.plurals.age_hours, 2, 2))
    }

    @Test
    fun theStaleDataMessageHoldsTheAge() = runTest {
        Locale.setDefault(Locale.forLanguageTag("es"))
        assertEquals("Los datos no se actualizan desde hace 3 minutos. La app lo intenta de nuevo.", getString(Res.string.data_stale, "3 minutos"))
    }
}
