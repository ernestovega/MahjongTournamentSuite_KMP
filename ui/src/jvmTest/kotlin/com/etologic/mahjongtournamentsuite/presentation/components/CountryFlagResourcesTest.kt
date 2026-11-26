package com.etologic.mahjongtournamentsuite.presentation.components

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Each flag image of the resources has its country code, and an unknown code has no flag. */
class CountryFlagResourcesTest {

    @Test
    fun everyFlagImageHasItsCode() {
        val codes = File("src/commonMain/composeResources/drawable").listFiles().orEmpty()
            .map { it.name }
            .filter { it.startsWith("flag_") && it != "flag_placeholder.png" }
            .map { it.removePrefix("flag_").substringBefore('.').uppercase() }
        assertEquals(true, codes.size > 200, "the scan found too few flags")
        val missing = codes.filter { flagResourceFor(it) == null }
        assertEquals(emptyList(), missing)
        assertNotNull(flagResourceFor("ES"))
    }

    @Test
    fun anUnknownCodeHasNoFlag() {
        assertNull(flagResourceFor("XX"))
        assertNull(flagResourceFor("es"))
    }
}
