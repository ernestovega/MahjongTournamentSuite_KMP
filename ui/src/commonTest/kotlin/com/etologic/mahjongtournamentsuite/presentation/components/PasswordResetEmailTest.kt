package com.etologic.mahjongtournamentsuite.presentation.components

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PasswordResetEmailTest {
    @Test
    fun aWellFormedEmailNeverGetsAnError() {
        listOf("lucia@example.test", "  lucia.p+mts@mail.example.es ", "a@b.co").forEach { assertNull(passwordResetEmailError(it), it) }
    }

    @Test
    fun aMalformedEmailGetsAnError() {
        listOf("lucia", "lucia@", "lucia@example", "@example.test", "lu cia@example.test", "lucia@@example.test").forEach {
            assertNotNull(passwordResetEmailError(it), it)
        }
    }
}
