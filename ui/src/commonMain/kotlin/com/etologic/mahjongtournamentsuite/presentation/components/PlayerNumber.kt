package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf

/**
 * The digits of the player numbers of the tournament on screen: 2, or 3 when it has more than 99 players. The screens
 * of a tournament provide it with [ProvidePlayerNumberDigits].
 */
val LocalPlayerNumberDigits = compositionLocalOf { MIN_PLAYER_NUMBER_DIGITS }

private const val MIN_PLAYER_NUMBER_DIGITS = 2

/** The digits of the player numbers of a tournament of [playerCount] players: never less than 2. */
fun playerNumberDigits(playerCount: Int): Int = maxOf(MIN_PLAYER_NUMBER_DIGITS, playerCount.toString().length)

/** The number of a player with "#" and zeros before it, so all the numbers of a tournament are as long: "#07", "#011". */
fun formatPlayerNumber(number: Int, digits: Int = MIN_PLAYER_NUMBER_DIGITS): String = "#" + number.toString().padStart(digits, '0')

/** [formatPlayerNumber] with the digits of the tournament on screen. */
@Composable
fun playerNumber(number: Int): String = formatPlayerNumber(number, LocalPlayerNumberDigits.current)

/** The number of a player as the hands store it (text): "#07". A blank or "-" stays "-". */
@Composable
fun playerNumber(text: String): String = text.trim().let { trimmed -> trimmed.toIntOrNull()?.let { playerNumber(it) } ?: trimmed.ifEmpty { "-" } }

/** Gives the screens inside the digits of the player numbers of a tournament of [playerCount] players. */
@Composable
fun ProvidePlayerNumberDigits(playerCount: Int, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalPlayerNumberDigits provides playerNumberDigits(playerCount), content = content)
}
