package com.etologic.mahjongtournamentsuite.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.etologic.mahjongtournamentsuite.presentation.components.AppScaffold
import com.etologic.mahjongtournamentsuite.presentation.components.LazyColumnWithScrollbar
import mahjongtournamentsuite.playerapp.generated.resources.Res
import mahjongtournamentsuite.playerapp.generated.resources.allStringResources
import mahjongtournamentsuite.playerapp.generated.resources.bg_table_landscape
import mahjongtournamentsuite.playerapp.generated.resources.bg_table_portrait
import mahjongtournamentsuite.playerapp.generated.resources.combinations_empty
import mahjongtournamentsuite.playerapp.generated.resources.combinations_hide_tiles
import mahjongtournamentsuite.playerapp.generated.resources.combinations_points
import mahjongtournamentsuite.playerapp.generated.resources.combinations_search_hint
import mahjongtournamentsuite.playerapp.generated.resources.combinations_show_tiles
import mahjongtournamentsuite.playerapp.generated.resources.combinations_title
import mahjongtournamentsuite.playerapp.generated.resources.filter_clear
import mahjongtournamentsuite.playerapp.generated.resources.filter_search_label
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import com.etologic.mahjongtournamentsuite.presentation.components.DebouncedIconButton as IconButton

/**
 * One of the 81 combinations (fan) of the Mahjong Competition Rules. [tiles] is an example hand in [TileNotation].
 * A combination without tiles, such as a way of winning, has a description instead.
 */
internal data class Combination(val id: String, val points: Int, val tiles: String?) {
    val name: StringResource get() = Res.allStringResources.getValue("comb_$id")
    val description: StringResource? get() = if (tiles == null) Res.allStringResources.getValue("comb_desc_$id") else null
    val groups: List<List<String>> by lazy { tiles?.let(TileNotation::parse).orEmpty() }
}

/** The combinations from the cheapest to the most valuable, in the order of the rules. From Mahjong Scoring 2. */
internal val Combinations: List<Combination> = listOf(
    Combination("pure_double_chow", 1, "789s 789s"),
    Combination("mixed_double_chow", 1, "678p 678s"),
    Combination("short_straight", 1, "345s 678s"),
    Combination("two_terminal_chows", 1, "123s 789s"),
    Combination("pung_of_terminals_or_honors", 1, "111m"),
    Combination("melded_kong", 1, "8888s"),
    Combination("one_voided_suit", 1, "123p 444p 888s SSS CC"),
    Combination("no_honors", 1, "123p 444p 789s 456m 99m"),
    Combination("edge_wait", 1, null),
    Combination("closed_wait", 1, null),
    Combination("single_waiting", 1, null),
    Combination("self_drawn", 1, null),
    Combination("flower_tiles", 1, "2f"),
    Combination("dragon_pung", 2, "FFF"),
    Combination("prevalent_wind", 2, "EEE"),
    Combination("seat_wind", 2, "NNN"),
    Combination("concealed_hand", 2, null),
    Combination("all_chows", 2, "123p 345p 567m 789s 88s"),
    Combination("tile_hog", 2, "678s 888s"),
    Combination("double_pungs", 2, "888s 888m"),
    Combination("two_concealed_pungs", 2, "XXX XXX"),
    Combination("concealed_kong", 2, "XXXX"),
    Combination("all_simples", 2, "234p 345p 678m 567s 88s"),
    Combination("outside_hand", 4, "123m 789s 789p EEE 99m"),
    Combination("fully_concealed_hand", 4, null),
    Combination("two_melded_kongs", 4, "8888s CCCC"),
    Combination("last_tile", 4, null),
    Combination("all_pungs", 6, "333p 888s EEE FFF 88m"),
    Combination("half_flush", 6, "111s 456s 888s EEE FF"),
    Combination("mixed_shifted_chows", 6, "456m 567p 678s"),
    Combination("all_types", 6, "789m 888s 456p CCC SS"),
    Combination("melded_hand", 6, null),
    Combination("two_dragon_pungs", 6, "PPP CCC"),
    Combination("mixed_straight", 8, "123p 456m 789s"),
    Combination("reversible_tiles", 8, "456s 999s 123p 888p PP"),
    Combination("mixed_triple_chow", 8, "789m 789s 789p"),
    Combination("mixed_shifted_pungs", 8, "777m 888s 999p"),
    Combination("chicken_hand", 8, "123m 345m 888s 567p NN"),
    Combination("last_tile_draw", 8, null),
    Combination("last_tile_claim", 8, null),
    Combination("out_with_replacement_tile", 8, null),
    Combination("robbing_the_kong", 8, null),
    Combination("two_concealed_kongs", 8, "XXXX XXXX"),
    Combination("lesser_honors_and_knitted_tiles", 12, "147m 258s 36p SWNPFC"),
    Combination("knitted_straight", 12, "147m 258s 369p"),
    Combination("upper_four", 12, "789p 678m 789m 888s 99s"),
    Combination("lower_four", 12, "123m 123p 123p 444s 44m"),
    Combination("big_three_winds", 12, "EEE SSS NNN"),
    Combination("pure_straight", 16, "123s 456s 789s"),
    Combination("three_suited_terminal_chows", 16, "123p 789p 123s 789s 55m"),
    Combination("pure_shifted_chows", 16, "345s 567s 789s"),
    Combination("all_fives", 16, "456m 345s 567s 345p 55p"),
    Combination("triple_pungs", 16, "888p 888s 888m"),
    Combination("three_concealed_pungs", 16, "XXX XXX XXX"),
    Combination("seven_pairs", 24, "22p 88p 11s 88s 44m 88m PP"),
    Combination("greater_honors_and_knitted_tiles", 24, "147m 58s 36p ESWNPFC"),
    Combination("all_even_pungs", 24, "444m 666s 888s 888p 22m"),
    Combination("full_flush", 24, "123s 345s 456s 678s 88s"),
    Combination("pure_triple_chow", 24, "678s 678s 678s"),
    Combination("pure_shifted_pungs", 24, "666s 777s 888s"),
    Combination("upper_tiles", 24, "789m 789p 777s 888s 77m"),
    Combination("medium_tiles", 24, "456m 456m 444p 456s 66s"),
    Combination("lower_tiles", 24, "123m 123m 111s 333p 11m"),
    Combination("four_pure_shifted_chows", 32, "456s 567s 678s 789s"),
    Combination("three_kongs", 32, "3333m 4444p 8888s"),
    Combination("all_terminals_and_honors", 32, "999m 111p SSS FFF WW"),
    Combination("quadruple_chow", 48, "678s 678s 678s 678s"),
    Combination("four_pure_shifted_pungs", 48, "555s 666s 777s 888s"),
    Combination("all_terminals", 64, "999m 111p 111s 999p 11m"),
    Combination("all_honors", 64, "EEE SSS PPP FFF NN"),
    Combination("little_four_winds", 64, "EEE SSS WWW NN"),
    Combination("little_three_dragons", 64, "PPP FFF CC"),
    Combination("four_concealed_pungs", 64, "XXX XXX XXX XXX"),
    Combination("pure_terminal_chows", 64, "123s 789s 123s 789s 55s"),
    Combination("thirteen_orphans", 88, "19s19p19m ESWNPFC 1s"),
    Combination("big_three_dragons", 88, "PPP FFF CCC"),
    Combination("big_four_winds", 88, "EEE SSS WWW NNN"),
    Combination("all_green", 88, "234s 234s 888s FFF 66s"),
    Combination("nine_gates", 88, "1112345678999s 8s"),
    Combination("four_kongs", 88, "3333m 4444p 8888s CCCC"),
    Combination("seven_shifted_pairs", 88, "22s 33s 44s 55s 66s 77s 88s"),
)

internal const val COMBINATIONS_SEARCH_TAG = "combinations-search"
internal const val COMBINATION_ROW_TAG = "combination-row"

/** The list of the combinations, with a search and their tiles, which fold open with a tap. */
@Composable
fun CombinationsScreen(navController: NavHostController) {
    var query by rememberSaveable { mutableStateOf("") }
    var showAll by rememberSaveable { mutableStateOf(false) }
    // The combinations that a tap turned the other way from [showAll].
    var toggled by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val names = Combinations.associate { it.id to stringResource(it.name) }
    val descriptions = Combinations.mapNotNull { c -> c.description?.let { c.id to stringResource(it) } }.toMap()
    val shown = Combinations.withIndex().filter { (_, c) -> c.matches(query, names.getValue(c.id), descriptions[c.id]) }

    AppScaffold(
        title = stringResource(Res.string.combinations_title),
        autoFocusFirst = false,
        onBack = { navController.popBackStack() },
    ) {
        TableBackground {
            LazyColumnWithScrollbar(
                state = rememberLazyListState(),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                item(key = "search") {
                    // The button beside the search opens or closes all the combinations. A tap on one opens or closes only it.
                    Row(
                        Modifier.widthIn(max = 720.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CombinationSearch(query, onChange = { query = it }, Modifier.weight(1f))
                        IconButton(
                            onClick = {
                                showAll = !showAll
                                toggled = emptyList()
                            },
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .size(48.dp)
                                .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                        ) {
                            Icon(
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                imageVector = if (showAll) Icons.Default.UnfoldLess else Icons.Default.UnfoldMore,
                                contentDescription = stringResource(if (showAll) Res.string.combinations_hide_tiles else Res.string.combinations_show_tiles),
                            )
                        }
                    }
                }
                if (shown.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            stringResource(Res.string.combinations_empty),
                            Modifier.padding(24.dp),
                            color = MaterialTheme.colorScheme.inverseOnSurface,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                items(shown, key = { it.value.id }) { (index, combination) ->
                    CombinationRow(
                        position = index + 1,
                        combination = combination,
                        name = names.getValue(combination.id),
                        description = descriptions[combination.id],
                        expanded = showAll != (combination.id in toggled),
                        onClick = {
                            toggled = if (combination.id in toggled) toggled - combination.id else toggled + combination.id
                        },
                        modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/** A combination matches the text when its name, its description or its points have it. */
internal fun Combination.matches(query: String, name: String, description: String?): Boolean {
    val text = query.trim()
    if (text.isEmpty()) return true
    return name.contains(text, ignoreCase = true) ||
        description?.contains(text, ignoreCase = true) == true ||
        points.toString() == text
}

@Composable
private fun CombinationSearch(query: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = query,
        onValueChange = onChange,
        modifier = modifier.testTag(COMBINATIONS_SEARCH_TAG),
        singleLine = true,
        label = { Text(stringResource(Res.string.filter_search_label)) },
        placeholder = { Text(stringResource(Res.string.combinations_search_hint), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = if (query.isEmpty()) {
            null
        } else {
            {
                IconButton(onClick = { onChange("") }) {
                    Icon(Icons.Default.Clear, contentDescription = stringResource(Res.string.filter_clear))
                }
            }
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
    )
}

@Composable
private fun CombinationRow(
    position: Int,
    combination: Combination,
    name: String,
    description: String?,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.testTag(COMBINATION_ROW_TAG),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)),
    ) {
        Column(Modifier.clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PointsBadge(combination.points)
                Text(
                    name,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "#$position",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AnimatedVisibility(visible = expanded) {
                Box(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    if (description != null) {
                        Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        TileGroupsRow(combination.groups, Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

@Composable
private fun PointsBadge(points: Int) {
    val label = stringResource(Res.string.combinations_points, points)
    Box(
        Modifier
            .size(36.dp)
            .background(MaterialTheme.colorScheme.primary, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            points.toString(),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.semantics { contentDescription = label },
        )
    }
}

/** The wooden table behind the combinations. One small picture, upright on a phone and wide on a big screen. */
@Composable
internal fun TableBackground(content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val picture = if (maxWidth > maxHeight) Res.drawable.bg_table_landscape else Res.drawable.bg_table_portrait
        Image(painterResource(picture), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        content()
    }
}
