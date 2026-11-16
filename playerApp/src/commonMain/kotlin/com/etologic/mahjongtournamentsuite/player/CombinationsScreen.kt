package com.etologic.mahjongtournamentsuite.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavHostController
import com.etologic.mahjongtournamentsuite.presentation.components.AppScaffold
import com.etologic.mahjongtournamentsuite.presentation.components.LazyColumnWithScrollbar
import com.etologic.mahjongtournamentsuite.presentation.telemetry.TrackedDialog
import mahjongtournamentsuite.playerapp.generated.resources.Res
import mahjongtournamentsuite.playerapp.generated.resources.allStringResources
import mahjongtournamentsuite.playerapp.generated.resources.combinations_close
import mahjongtournamentsuite.playerapp.generated.resources.combinations_empty
import mahjongtournamentsuite.playerapp.generated.resources.combinations_fold
import mahjongtournamentsuite.playerapp.generated.resources.combinations_fold_all
import mahjongtournamentsuite.playerapp.generated.resources.combinations_points
import mahjongtournamentsuite.playerapp.generated.resources.combinations_search_hint
import mahjongtournamentsuite.playerapp.generated.resources.combinations_tiles_credit
import mahjongtournamentsuite.playerapp.generated.resources.combinations_title
import mahjongtournamentsuite.playerapp.generated.resources.combinations_unfold
import mahjongtournamentsuite.playerapp.generated.resources.combinations_unfold_all
import mahjongtournamentsuite.playerapp.generated.resources.filter_clear
import mahjongtournamentsuite.playerapp.generated.resources.filter_search_label
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import com.etologic.mahjongtournamentsuite.presentation.components.DebouncedIconButton as IconButton
import com.etologic.mahjongtournamentsuite.presentation.components.DebouncedTextButton as TextButton

/**
 * One of the 81 combinations (fan) of the Mahjong Competition Rules. [tiles] is an example hand in [TileNotation].
 * A combination without tiles, such as a way of winning, shows only its texts.
 */
internal data class Combination(val id: String, val points: Int, val tiles: String?) {
    val name: StringResource get() = Res.allStringResources.getValue("comb_$id")

    /** One or two lines, under the name in the list. */
    val description: StringResource get() = Res.allStringResources.getValue("comb_desc_$id")

    /** The explanation of the rules, with what the combination does not combine with, in its dialog. */
    val detail: StringResource get() = Res.allStringResources.getValue("comb_detail_$id")
    val groups: List<List<String>> by lazy { tiles?.let(TileNotation::parse).orEmpty() }
}

/** The values of the combinations, from 1 to 88 points. */
internal val CombinationValues: List<Int> by lazy { Combinations.map { it.points }.distinct() }

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
internal const val POINTS_HEADER_TAG = "combination-points-header"
internal const val POINTS_CHIP_TAG = "combination-points-chip"
internal const val COMBINATION_DIALOG_TAG = "combination-dialog"

/** The widest the list grows on a big screen, so the tiles of a long hand can be larger. */
private val ListMaxWidth = 960.dp

/**
 * The list of the combinations, grouped by value, with a search and a chip for each value.
 * The arrow of a combination folds its description and tiles open. A tap on it opens its dialog.
 */
@Composable
fun CombinationsScreen(navController: NavHostController) {
    var query by rememberSaveable { mutableStateOf("") }
    var showAll by rememberSaveable { mutableStateOf(true) }
    // The combinations that a tap on their arrow turned the other way from [showAll].
    var toggled by rememberSaveable { mutableStateOf(emptyList<String>()) }
    // No value chosen shows every value.
    var values by rememberSaveable { mutableStateOf(emptyList<Int>()) }
    var opened by rememberSaveable { mutableStateOf<String?>(null) }
    val names = Combinations.associate { it.id to stringResource(it.name) }
    val descriptions = Combinations.associate { it.id to stringResource(it.description) }
    val found = Combinations.withIndex().filter { (_, c) -> c.matches(query, names.getValue(c.id), descriptions[c.id]) }
    val shown = found.filter { values.isEmpty() || it.value.points in values }
    val rowModifier = Modifier.widthIn(max = ListMaxWidth).fillMaxWidth()

    AppScaffold(
        title = stringResource(Res.string.combinations_title),
        autoFocusFirst = false,
        onBack = { navController.popBackStack() },
        actions = { ContactTopBarButton(navController) },
    ) {
        LazyColumnWithScrollbar(
            state = rememberLazyListState(),
            modifier = Modifier.fillMaxSize(),
            // No top padding, so that a value header sticks right at the top.
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item(key = "search") {
                CombinationTools(
                    query = query,
                    onQuery = { query = it },
                    showAll = showAll,
                    onShowAll = {
                        showAll = !showAll
                        toggled = emptyList()
                    },
                    modifier = rowModifier.padding(top = 12.dp),
                )
            }
            item(key = "values") {
                PointsChips(
                    counts = CombinationValues.associateWith { value -> found.count { it.value.points == value } },
                    selected = values,
                    onToggle = { value -> values = if (value in values) values - value else values + value },
                    modifier = rowModifier,
                )
            }
            if (shown.isEmpty()) {
                item(key = "empty") {
                    Text(
                        stringResource(Res.string.combinations_empty),
                        Modifier.padding(24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            // One group for each value, under a header that stays at the top while its combinations scroll.
            shown.groupBy { it.value.points }.forEach { (points, group) ->
                stickyHeader(key = "points-$points") { PointsHeader(points, rowModifier) }
                items(group, key = { it.value.id }) { (index, combination) ->
                    CombinationRow(
                        position = index + 1,
                        combination = combination,
                        name = names.getValue(combination.id),
                        description = descriptions.getValue(combination.id),
                        expanded = showAll != (combination.id in toggled),
                        onFold = {
                            toggled = if (combination.id in toggled) toggled - combination.id else toggled + combination.id
                        },
                        onOpen = { opened = combination.id },
                        modifier = rowModifier,
                    )
                }
            }
            item(key = "credit") { TilesCredit(rowModifier) }
        }
    }
    Combinations.withIndex().firstOrNull { it.value.id == opened }?.let { (index, combination) ->
        CombinationDialog(index + 1, combination, onDismiss = { opened = null })
    }
}

/** The link that the licence of the clear tiles asks for. */
@Composable
private fun TilesCredit(modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    Row(modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Text(
            stringResource(Res.string.combinations_tiles_credit),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = { uriHandler.openUri(MARTIN_PERSSON_URL) }) {
            Text("martinpersson.org", style = MaterialTheme.typography.bodySmall)
        }
    }
}

private const val MARTIN_PERSSON_URL = "http://www.martinpersson.org"

/** A combination matches the text when its name, its description or its points have it. */
internal fun Combination.matches(query: String, name: String, description: String?): Boolean {
    val text = query.trim()
    if (text.isEmpty()) return true
    return name.contains(text, ignoreCase = true) ||
        description?.contains(text, ignoreCase = true) == true ||
        points.toString() == text
}

/** The search and the button that folds all the combinations open or closed. The button has its label when there is room. */
@Composable
private fun CombinationTools(query: String, onQuery: (String) -> Unit, showAll: Boolean, onShowAll: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(if (showAll) Res.string.combinations_fold_all else Res.string.combinations_unfold_all)
    val icon = if (showAll) Icons.Default.UnfoldLess else Icons.Default.UnfoldMore
    BoxWithConstraints(modifier) {
        val wide = maxWidth >= 600.dp
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CombinationSearch(query, onChange = onQuery, Modifier.weight(1f))
            if (wide) {
                TextButton(
                    onClick = onShowAll,
                    modifier = Modifier.padding(top = 8.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.textButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Icon(icon, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(label)
                }
            } else {
                IconButton(
                    onClick = onShowAll,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                ) {
                    Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
        }
    }
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
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
    )
}

/** A chip for each value, with how many combinations of the search it has, like the country chips of the players. */
@Composable
private fun PointsChips(counts: Map<Int, Int>, selected: List<Int>, onToggle: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        counts.forEach { (points, count) ->
            val isSelected = points in selected
            TextButton(
                onClick = { onToggle(points) },
                modifier = Modifier.testTag(POINTS_CHIP_TAG),
                shape = CircleShape,
                colors = if (isSelected) {
                    ButtonDefaults.textButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    ButtonDefaults.textButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Text("${pluralStringResource(Res.plurals.combinations_points, points, points)} ($count)")
            }
        }
    }
}

/** The value of the combinations below it, like the round headers of the tables. Its band is opaque, so the rows scroll under it. */
@Composable
private fun PointsHeader(points: Int, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier
            .testTag(POINTS_HEADER_TAG)
            .layout { measurable, constraints ->
                // The content padding of the list narrows the header, so it grows back over it.
                val extra = 24.dp.roundToPx()
                val placeable = measurable.measure(constraints.copy(minWidth = constraints.maxWidth + extra, maxWidth = constraints.maxWidth + extra))
                layout(constraints.maxWidth, placeable.height) { placeable.place(-12.dp.roundToPx(), 0) }
            }
            .background(colors.background)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            pluralStringResource(Res.plurals.combinations_points, points, points),
            Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = colors.onSurface,
        )
    }
}

@Composable
private fun CombinationRow(
    position: Int,
    combination: Combination,
    name: String,
    description: String,
    expanded: Boolean,
    onFold: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val turn by animateFloatAsState(if (expanded) 180f else 0f)
    Card(
        modifier = modifier.testTag(COMBINATION_ROW_TAG),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.clickable(onClick = onOpen).padding(start = 12.dp, top = 4.dp, end = 4.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                IconButton(onClick = onFold) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = stringResource(if (expanded) Res.string.combinations_fold else Res.string.combinations_unfold),
                        modifier = Modifier.rotate(turn),
                    )
                }
            }
            AnimatedVisibility(visible = expanded) {
                Column(
                    Modifier.fillMaxWidth().padding(end = 8.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (combination.groups.isNotEmpty()) TileGroupsRow(combination.groups, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

/**
 * A combination in large: its name, value and tiles centered, and the explanation of the rules.
 * The first paragraph of the explanation, the definition, is bold. The others, such as what it does not combine with, are not.
 * It is a plain dialog and not an AlertDialog, because an AlertDialog never grows wider than 560 dp and the tiles need more.
 */
@Composable
private fun CombinationDialog(position: Int, combination: Combination, onDismiss: () -> Unit) {
    val paragraphs = stringResource(combination.detail).split("\n\n").map { it.trim() }.filter { it.isNotEmpty() }
    TrackedDialog("Combination") {
        Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(
                // Wide on a big screen, so that the tiles of a long hand show large.
                modifier = Modifier.padding(16.dp).widthIn(max = 1200.dp).fillMaxWidth().testTag(COMBINATION_DIALOG_TAG),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                    ) {
                        Text(
                            stringResource(combination.name),
                            Modifier.weight(1f, fill = false),
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center,
                        )
                        Text("#$position", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(
                        Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(
                            pluralStringResource(Res.plurals.combinations_points, combination.points, combination.points),
                            Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                        )
                        if (combination.groups.isNotEmpty()) {
                            // A big screen shows the detailed tiles large. A phone keeps the clear ones, which read better small.
                            BoxWithConstraints(Modifier.fillMaxWidth()) {
                                val large = maxWidth >= 600.dp
                                TileGroupsRow(
                                    combination.groups,
                                    Modifier.fillMaxWidth(),
                                    maxTileWidth = if (large) LargeTileWidth else 64.dp,
                                    alignment = Alignment.TopCenter,
                                    set = if (large) TileSet.Luxury else TileSet.Simple,
                                )
                            }
                        }
                        paragraphs.forEachIndexed { index, paragraph ->
                            Text(
                                paragraph,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (index == 0) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onDismiss) { Text(stringResource(Res.string.combinations_close)) }
                    }
                }
            }
        }
    }
}
