package com.etologic.mahjongtournamentsuite.presentation.util

import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.ConflictCode
import com.etologic.mahjongtournamentsuite.domain.model.ImportFileErrorCode
import com.etologic.mahjongtournamentsuite.domain.model.RegistrationErrorCode
import mahjongtournamentsuite.ui.generated.resources.Res
import mahjongtournamentsuite.ui.generated.resources.error_email_invalid
import mahjongtournamentsuite.ui.generated.resources.error_generic
import mahjongtournamentsuite.ui.generated.resources.import_file_missing_columns
import mahjongtournamentsuite.ui.generated.resources.registration_error_import_problems
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Every code that the server sends has its own text in the language of the user, never the text of the server. */
class AppErrorCodesTest {
    private val generic = UiText.of(Res.string.error_generic)

    @Test
    fun everyImportFileCodeHasItsOwnText() {
        val texts = ImportFileErrorCode.entries.map { AppError.ImportFile(it, listOf("A")).toUiText() }
        texts.forEach { assertNotEquals(generic, it) }
        assertEquals(ImportFileErrorCode.entries.size, texts.toSet().size)
        assertEquals(
            UiText.of(Res.string.import_file_missing_columns, "Email"),
            AppError.ImportFile(ImportFileErrorCode.MISSING_COLUMNS, listOf("Email")).toUiText(),
        )
        // A code without its detail still has a text.
        assertEquals(
            UiText.of(Res.string.import_file_missing_columns, ""),
            AppError.ImportFile(ImportFileErrorCode.MISSING_COLUMNS).toUiText(),
        )
    }

    @Test
    fun everyRegistrationCodeHasItsOwnText() {
        val texts = RegistrationErrorCode.entries.map { AppError.Registration(it, listOf("1", "2")).toUiText() }
        texts.forEach { assertNotEquals(generic, it) }
        assertEquals(RegistrationErrorCode.entries.size, texts.toSet().size)
        assertEquals(
            UiText.of(Res.string.registration_error_import_problems, "3, 7"),
            AppError.Registration(RegistrationErrorCode.IMPORT_PROBLEMS, listOf("3", "7")).toUiText(),
        )
    }

    @Test
    fun everyConflictCodeIsTranslatedInsteadOfTheTextOfTheServer() {
        ConflictCode.entries.forEach { code ->
            val text = AppError.Conflict("Server text", code = code, args = listOf("1", "2")).toUiText()
            assertNotEquals(UiText.Plain("Server text"), text, code.name)
            assertNotEquals(generic, text, code.name)
        }
        // Most codes have a text of their own. A few share the text of the same refusal of the registrations.
        val distinct = ConflictCode.entries.map { AppError.Conflict("x", code = it).toUiText() }.toSet().size
        assertTrue(distinct >= ConflictCode.entries.size - 2)
    }

    @Test
    fun anInvalidEmailFromTheServerIsExplained() {
        assertEquals(UiText.of(Res.string.error_email_invalid), AppError.Unexpected("INVALID_EMAIL").toUiText())
    }
}
