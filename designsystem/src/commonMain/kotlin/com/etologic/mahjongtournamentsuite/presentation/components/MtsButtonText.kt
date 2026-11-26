package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified

/*
 * The text of a button. Every button of the apps shows its text in bold capitals (Ernesto, 25-nov-2026), so the content
 * of a button uses this instead of `Text`. `ButtonTextConventionTest` checks it.
 *
 * Only the drawn text is in capitals. Screen readers and the tests still get the text as it is written, because a
 * screen reader may spell a word in capitals letter by letter.
 *
 * A button never splits a word ("PENALIZACIÓ" / "N"): the text goes to several lines between words, and when one word
 * alone does not fit the width, the letters get smaller until it does (down to [MinButtonTextScale]).
 */

@Composable
fun ButtonText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = FontWeight.Bold,
    fontFamily: FontFamily? = null,
    textAlign: TextAlign? = null,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    style: TextStyle = LocalTextStyle.current,
) = ScaledButtonText(
    written = AnnotatedString(text),
    shown = AnnotatedString(buttonCaps(text)),
    modifier = modifier,
    color = color,
    fontSize = fontSize,
    fontWeight = fontWeight,
    fontFamily = fontFamily,
    textAlign = textAlign,
    overflow = overflow,
    softWrap = softWrap,
    maxLines = maxLines,
    minLines = minLines,
    style = style,
)

@Composable
fun ButtonText(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = FontWeight.Bold,
    fontFamily: FontFamily? = null,
    textAlign: TextAlign? = null,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    style: TextStyle = LocalTextStyle.current,
) = ScaledButtonText(
    written = text,
    shown = buttonCaps(text),
    modifier = modifier,
    color = color,
    fontSize = fontSize,
    fontWeight = fontWeight,
    fontFamily = fontFamily,
    textAlign = textAlign,
    overflow = overflow,
    softWrap = softWrap,
    maxLines = maxLines,
    minLines = minLines,
    style = style,
)

@Composable
private fun ScaledButtonText(
    written: AnnotatedString,
    shown: AnnotatedString,
    modifier: Modifier,
    color: Color,
    fontSize: TextUnit,
    fontWeight: FontWeight?,
    fontFamily: FontFamily?,
    textAlign: TextAlign?,
    overflow: TextOverflow,
    softWrap: Boolean,
    maxLines: Int,
    minLines: Int,
    style: TextStyle,
) {
    val baseSize = if (fontSize.isSpecified) fontSize else style.fontSize
    // The scale of the letters. It only goes down, so it always settles: a layout measured twice at two widths (a row
    // that tries the buttons side by side, then stacks them) cannot make it go up and down for ever.
    var scale by remember(shown, baseSize) { mutableFloatStateOf(1f) }
    // True while a layout splits a word and a smaller one is on its way: that layout is not drawn.
    var shrinking by remember(shown, baseSize) { mutableStateOf(false) }
    // The box keeps the text as written for accessibility. It passes its size to the text, so a weight, a full width
    // and the alignment of the text still work.
    Box(modifier.clearAndSetSemantics { this.text = written }, propagateMinConstraints = true) {
        Text(
            text = shown,
            color = color,
            fontSize = if (baseSize.isSpecified && scale < 1f) baseSize * scale else fontSize,
            fontWeight = fontWeight,
            fontFamily = fontFamily,
            textAlign = textAlign,
            overflow = overflow,
            softWrap = softWrap,
            maxLines = maxLines,
            minLines = minLines,
            style = style,
            onTextLayout = { layout ->
                shrinking = baseSize.isSpecified && scale > MinButtonTextScale && layout.splitsAWord()
                if (shrinking) scale = (scale - ButtonTextScaleStep).coerceAtLeast(MinButtonTextScale)
            },
            // A layout that splits a word is not drawn: the next one, with smaller letters, comes in the next frame.
            modifier = Modifier.drawWithContent { if (!shrinking) drawContent() },
        )
    }
}

/** The smallest the letters of a button get so that a long word fits: 70 % of the size of the style. */
const val MinButtonTextScale = 0.7f

private const val ButtonTextScaleStep = 0.05f

/** True when a line ends inside a word: the line break has letters on both sides. */
internal fun TextLayoutResult.splitsAWord(): Boolean {
    val text = layoutInput.text.text
    for (line in 0 until lineCount - 1) {
        val end = getLineEnd(line)
        if (end <= 0 || end >= text.length) continue
        if (!text[end - 1].isWhitespace() && !text[end].isWhitespace() && text[end - 1] != '-') return true
    }
    return false
}

/** The capitals of a button text. Independent of the device language, so "ß" becomes "SS" and "i" stays "I" everywhere. */
fun buttonCaps(text: String): String = text.uppercase()

/**
 * The capitals of a text with styles. The styles stay when the capitals have the same length; a letter like "ß" turns
 * into two ("SS"), and then the text keeps no styles, because they would fall on the wrong letters.
 */
fun buttonCaps(text: AnnotatedString): AnnotatedString {
    val caps = buttonCaps(text.text)
    return if (caps.length == text.length) {
        AnnotatedString(caps, text.spanStyles, text.paragraphStyles)
    } else {
        AnnotatedString(caps)
    }
}
