package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/** A button text is drawn in capitals, keeps its written text for screen readers, and keeps its styles when it can. */
@OptIn(ExperimentalTestApi::class)
class MtsButtonTextTest {
    @Test
    fun aTextWithStylesKeepsItsWrittenTextForScreenReaders() = runComposeUiTest {
        val styled = buildAnnotatedString {
            append("Save ")
            withStyle(SpanStyle(fontWeight = FontWeight.Black)) { append("all") }
        }
        setContent { MtsTheme { ButtonText(styled) } }

        onNodeWithText("Save all").assertExists()
    }

    @Test
    fun aLongWordInANarrowButtonStillShows() = runComposeUiTest {
        setContent {
            MtsTheme { ButtonText("Penalización", Modifier.width(40.dp), fontSize = 20.sp) }
        }

        onNodeWithText("Penalización").assertExists()
    }

    @Test
    fun theCapitalsKeepTheStylesOnlyWhenNoLetterGrows() {
        val styled = AnnotatedString("go", listOf(AnnotatedString.Range(SpanStyle(fontWeight = FontWeight.Bold), 0, 2)))

        assertEquals(AnnotatedString("GO", styled.spanStyles), buttonCaps(styled))
        assertEquals(AnnotatedString("STRASSE"), buttonCaps(AnnotatedString("straße", styled.spanStyles)))
    }
}
