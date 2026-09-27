package com.etologic.mahjongtournamentsuite.domain.export

import kotlin.test.Test
import kotlin.test.assertTrue

class EmaResultsCsvExporterTest {
    @Test
    fun exportsLegacyEmaColumnOrderAndEscapesValues() {
        val csv = EmaResultsCsvExporter.export(
            listOf(
                EmaResultsRow(
                    place = 1,
                    firstName = "Ana",
                    lastName = "García",
                    emaNumber = "12345",
                    tablePoints = "10.5",
                    score = 2500,
                    emaMember = "YES",
                    country = "ES",
                ),
                EmaResultsRow(
                    place = 2,
                    firstName = "O,Neil",
                    lastName = "Test",
                    emaNumber = "",
                    tablePoints = "0",
                    score = 0,
                    emaMember = "NO",
                    country = "",
                ),
            ),
        )

        assertTrue(csv.startsWith("\uFEFF\"Place\",\"First Name\",\"Last name\",\"EMA number\""))
        assertTrue(csv.contains("\"1\",\"Ana\",\"García\",\"12345\",\"10.5\",\"2500\",\"YES\",\"ES\""))
        assertTrue(csv.contains("\"2\",\"O,Neil\",\"Test\",\"\",\"0\",\"0\",\"NO\",\"\""))
    }
}
