package com.etologic.mahjongtournamentsuite.presentation.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * A text for the screen that is not yet translated. States and presenters keep a [UiText].
 * The screen calls [asString] while it draws, so the text follows the language of the app.
 */
sealed interface UiText {
    /** A text that needs no translation, for example a name or a message from a person. */
    data class Plain(val value: String) : UiText

    /** A text from the string resources. [args] fill the placeholders of the text. */
    data class Resource(val resource: StringResource, val args: List<Any> = emptyList()) : UiText

    /** A text with a plural form. [quantity] picks the form. [args] fill the placeholders of the text. */
    data class Plural(val resource: PluralStringResource, val quantity: Int, val args: List<Any> = emptyList()) : UiText

    /** Several texts one after the other, with [separator] between them. */
    data class Joined(val parts: List<UiText>, val separator: String = "") : UiText

    companion object {
        fun of(resource: StringResource, vararg args: Any): UiText = Resource(resource, args.toList())
    }
}

@Composable
fun UiText.asString(): String = when (this) {
    is UiText.Plain -> value
    is UiText.Resource -> stringResource(resource, *args.map { it.resolved() }.toTypedArray())
    is UiText.Joined -> parts.map { it.asString() }.joinToString(separator)
    is UiText.Plural -> pluralStringResource(resource, quantity, *args.map { it.resolved() }.toTypedArray())
}

/** An argument that is a [UiText] becomes its own text, so a text can hold other texts. */
@Composable
private fun Any.resolved(): Any = if (this is UiText) asString() else this

private const val TEMPLATE_MARK = "\u0000"

/**
 * Gives a function that fills the placeholder of a text with one value. Use it where code that is not
 * a composable needs the text, for example a lambda that gives the name of a player.
 */
@Composable
fun rememberTemplate(resource: StringResource): (Any) -> String {
    val template = stringResource(resource, TEMPLATE_MARK)
    return remember(template) { { value -> template.replace(TEMPLATE_MARK, value.toString()) } }
}
