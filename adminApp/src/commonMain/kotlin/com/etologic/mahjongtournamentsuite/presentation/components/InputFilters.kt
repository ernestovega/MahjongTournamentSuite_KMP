package com.etologic.mahjongtournamentsuite.presentation.components

/** Input filters for text fields. Each one drops the characters that the field cannot hold. */

/** Digits only. */
fun String.toDigitsInput(maxLength: Int = Int.MAX_VALUE): String = filter(Char::isDigit).take(maxLength)

/** Digits and "/" for a DD/MM/YYYY date. */
fun String.toDateInput(): String = filter { it.isDigit() || it == '/' }.take(10)

/** Digits and ":" for an HH:mm time. */
fun String.toTimeInput(): String = filter { it.isDigit() || it == ':' }.take(5)

/** Letters only, in upper case. For country codes. */
fun String.toCountryCodeInput(): String = filter(Char::isLetter).uppercase().take(3)

/** An optional "#" first, then hexadecimal digits. For a #RRGGBB color. */
fun String.toHexColorInput(): String = filterIndexed { index, char ->
    (index == 0 && char == '#') || char in '0'..'9' || char in 'a'..'f' || char in 'A'..'F'
}.take(7)
