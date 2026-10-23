package com.etologic.mahjongtournamentsuite.presentation.util

import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString

/** Gives the text of a [UiText] in the language of the test run. The tests run in English. */
suspend fun UiText?.render(): String = when (this) {
    null -> ""
    is UiText.Plain -> value
    is UiText.Resource -> getString(resource, *args.map { it.renderArg() }.toTypedArray())
    is UiText.Plural -> getPluralString(resource, quantity, *args.map { it.renderArg() }.toTypedArray())
    is UiText.Joined -> parts.map { it.render() }.joinToString(separator)
}

private suspend fun Any.renderArg(): Any = if (this is UiText) render() else this
