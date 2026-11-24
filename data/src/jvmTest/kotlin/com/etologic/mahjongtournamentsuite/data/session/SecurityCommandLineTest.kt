package com.etologic.mahjongtournamentsuite.data.session

import kotlin.test.Test
import kotlin.test.assertEquals

class SecurityCommandLineTest {
    @Test
    fun quotesEveryArgument() {
        assertEquals(
            "\"add-generic-password\" \"-w\" \"secret pass\"",
            securityCommandLine(listOf("add-generic-password", "-w", "secret pass")),
        )
    }

    @Test
    fun escapesQuotesAndBackslashesOfThePassword() {
        assertEquals("\"a\\\"b\\\\c\"", securityCommandLine(listOf("a\"b\\c")))
    }
}
