package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test

/** A flag without a drawing shows its code, and a code that is not a country shows the empty flag. */
@OptIn(ExperimentalTestApi::class)
class CountryFlagUiTest {
    @Test
    fun aCodeWithoutADrawingShowsTheCodeAndAnUnknownCodeTheEmptyFlag() = runComposeUiTest {
        setContent {
            Column {
                CountryFlag("QZ", contentDescription = "Code only")
                CountryFlag("123", contentDescription = "Not a country")
            }
        }
        onNodeWithContentDescription("Code only").assertExists()
        onNodeWithContentDescription("Not a country").assertExists()
    }
}
