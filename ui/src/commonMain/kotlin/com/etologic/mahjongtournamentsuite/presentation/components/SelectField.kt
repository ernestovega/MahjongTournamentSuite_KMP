package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuBoxScope
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.etologic.mahjongtournamentsuite.presentation.util.matchesSearch
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import mahjongtournamentsuite.ui.generated.resources.Res
import mahjongtournamentsuite.ui.generated.resources.dropdown_no_match
import mahjongtournamentsuite.ui.generated.resources.dropdown_search
import org.jetbrains.compose.resources.stringResource

/**
 * One choice of a [SelectField] or a [SelectMenu].
 * The search looks at [label] and at [searchText].
 */
@Stable
class SelectOption<out K>(
    val key: K,
    val label: String,
    val enabled: Boolean = true,
    /** More text that the search matches, for example a country code. */
    val searchText: String = "",
    val leadingIcon: (@Composable () -> Unit)? = null,
    val trailingIcon: (@Composable () -> Unit)? = null,
)

/** The menus show a search field as first item when they have more options than this. */
const val SELECT_SEARCH_THRESHOLD = 8

/** The test tag of the search field of a menu. */
const val SELECT_SEARCH_TAG = "select-search"

/** The most options that a menu of a very long list composes at once. The search narrows the list. */
const val SELECT_MAX_SHOWN = 50

/**
 * The open state of a select menu.
 * A tap on the field while the menu is open closes the popup on the press and calls the click on the release.
 * Without the guard, that release opens the menu again.
 */
@Stable
class SelectMenuState {
    var expanded by mutableStateOf(false)
        private set
    private var dismissMark: TimeMark? = null

    fun open() {
        if ((dismissMark?.elapsedNow() ?: 1.seconds) < REOPEN_GUARD) return
        expanded = true
    }

    /** Closes the menu after the user chose an option. The user can open it again at once. */
    fun choose() {
        expanded = false
    }

    /** Closes the menu without a choice, for example after a tap outside of it. */
    fun close() {
        dismissMark = TimeSource.Monotonic.markNow()
        expanded = false
    }

    fun request(open: Boolean) = if (open) open() else close()

    private companion object {
        val REOPEN_GUARD = 400.milliseconds
    }
}

@Composable
fun rememberSelectMenuState(): SelectMenuState = remember { SelectMenuState() }

/** The height of one item of a Material 3 dropdown menu. */
private val MENU_ITEM_HEIGHT = 48.dp

/**
 * The scroll state of a select menu. Each time the menu opens, it scrolls so that the item at [selectedIndex] is the first one
 * below the search row. Without a selected item, it scrolls to [defaultIndex]. The default is the start of the menu.
 */
@Composable
internal fun rememberMenuScrollState(expanded: Boolean, selectedIndex: Int?, defaultIndex: Int = 0): ScrollState {
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    val currentSelected by rememberUpdatedState(selectedIndex)
    val currentDefault by rememberUpdatedState(defaultIndex)
    LaunchedEffect(expanded) {
        if (!expanded) return@LaunchedEffect
        val target = currentSelected?.takeIf { it >= 0 } ?: currentDefault
        scroll.scrollTo(with(density) { (MENU_ITEM_HEIGHT * target).roundToPx() })
    }
    return scroll
}

/**
 * A field that shows the chosen option and opens a menu with all options.
 * The field is not a text field. The user cannot type in it, put a cursor in it or select its text.
 * A menu with many options has a search field as sticky first item.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <K> SelectField(
    label: String?,
    options: List<SelectOption<K>>,
    selected: K?,
    onSelect: (K) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false,
    supportingText: String? = null,
    /** Shows a red star after the label. */
    required: Boolean = false,
    /** A field of 40 dp height with a magnifier icon in place of the label. The standard field is 56 dp high. */
    compact: Boolean = false,
    /** The text of the field. The default is the label of the selected option. */
    valueText: String? = null,
    /** The icon in front of the text. The default is the icon of the selected option. */
    leadingIcon: (@Composable () -> Unit)? = null,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    /** The font of the text of the field and of the options. The default is the font of the theme. */
    fontFamily: FontFamily? = null,
    searchable: Boolean = options.size > SELECT_SEARCH_THRESHOLD,
    maxShown: Int = Int.MAX_VALUE,
    /** The item that the menu shows first when nothing is selected. */
    defaultIndex: Int = 0,
    /** Gives the field more modifiers, for example the focus and the key handling. */
    fieldModifier: Modifier = Modifier,
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors(),
    /** True lets the user open the menu of a disabled field to read it. The caller disables the options. */
    openWhenDisabled: Boolean = false,
    /** Runs when the menu closes, with or without a choice. */
    onClosed: () -> Unit = {},
    state: SelectMenuState = rememberSelectMenuState(),
) {
    val selectedOption = options.firstOrNull { it.key == selected }
    val text = valueText ?: selectedOption?.label.orEmpty()
    val icon = leadingIcon ?: selectedOption?.leadingIcon
    val canOpen = enabled || openWhenDisabled
    val expanded = state.expanded && canOpen
    val scroll = rememberMenuScrollState(expanded, options.indexOfFirst { it.key == selected }.takeIf { it >= 0 }, defaultIndex)
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { if (canOpen) state.request(it) }, modifier = modifier) {
        SelectFieldBox(
            label = label,
            required = required,
            text = text,
            expanded = expanded,
            enabled = enabled,
            canOpen = canOpen,
            isError = isError,
            supportingText = supportingText,
            compact = compact,
            icon = icon,
            textStyle = if (fontFamily == null) textStyle else textStyle.copy(fontFamily = fontFamily),
            colors = colors,
            fieldModifier = fieldModifier,
            onOpen = state::open,
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                state.close()
                onClosed()
            },
            scrollState = scroll,
        ) {
            SelectMenuItems(options, selected, scroll, searchable, maxShown, fontFamily) {
                state.choose()
                onSelect(it)
                onClosed()
            }
        }
    }
}

/** A [SelectField] for options that are a key and a text. It is for long lists such as all players, so it shows 50 options at most. */
@Composable
fun <K> SelectField(
    label: String,
    options: List<Pair<K, String>>,
    selected: K?,
    onSelect: (K) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val selectOptions = remember(options) { options.map { SelectOption(it.first, it.second) } }
    SelectField(label, selectOptions, selected, onSelect, modifier, compact = compact, maxShown = SELECT_MAX_SHOWN)
}

/**
 * The menu of a button that chooses one option, for example the round button.
 * The caller opens it with [SelectMenuState.open] and puts the menu in the same box as the button.
 */
@Composable
fun <K> SelectMenu(
    state: SelectMenuState,
    options: List<SelectOption<K>>,
    selected: K?,
    onSelect: (K) -> Unit,
    modifier: Modifier = Modifier,
    searchable: Boolean = options.size > SELECT_SEARCH_THRESHOLD,
    maxShown: Int = Int.MAX_VALUE,
    defaultIndex: Int = 0,
) {
    val scroll = rememberMenuScrollState(state.expanded, options.indexOfFirst { it.key == selected }.takeIf { it >= 0 }, defaultIndex)
    DropdownMenu(expanded = state.expanded, onDismissRequest = state::close, modifier = modifier, scrollState = scroll) {
        SelectMenuItems(options, selected, scroll, searchable, maxShown, fontFamily = null) {
            state.choose()
            onSelect(it)
        }
    }
}

/**
 * The box of a [SelectField]: a border, a label, the text, an icon in front and an arrow at the end.
 * It draws the same decoration as an outlined text field, but it holds no text field, so a tap always opens the menu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExposedDropdownMenuBoxScope.SelectFieldBox(
    label: String?,
    required: Boolean,
    text: String,
    expanded: Boolean,
    enabled: Boolean,
    canOpen: Boolean,
    isError: Boolean,
    supportingText: String?,
    compact: Boolean,
    icon: (@Composable () -> Unit)?,
    textStyle: TextStyle,
    colors: TextFieldColors,
    fieldModifier: Modifier,
    onOpen: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focus = remember { FocusRequester() }
    val textColor = MaterialTheme.colorScheme.onSurface.let { if (enabled) it else it.copy(alpha = DISABLED_ALPHA) }
    Box(
        modifier = Modifier
            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = canOpen)
            .fillMaxWidth()
            .then(fieldModifier)
            // An outlined text field with a label leaves this space above its border for the label. The same space keeps the two in line in a row.
            .padding(top = if (compact || label == null) 0.dp else OUTLINED_FIELD_TOP_PADDING)
            .defaultMinSize(
                minWidth = if (compact) 0.dp else OutlinedTextFieldDefaults.MinWidth,
                minHeight = if (compact) COMPACT_FIELD_HEIGHT else OutlinedTextFieldDefaults.MinHeight,
            )
            .semantics(mergeDescendants = true) {
                role = Role.DropdownList
                if (!enabled) disabled()
            }
            .pointerHoverIcon(if (canOpen) PointerIcon.Hand else PointerIcon.Default)
            // A tap gives the field the focus, as a tap in a text field does. The tap itself goes on to the menu anchor.
            .pointerInput(canOpen) {
                if (!canOpen) return@pointerInput
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial).let { event ->
                            if (event.changes.any { it.pressed && !it.previousPressed }) focus.requestFocus()
                        }
                    }
                }
            }
            .onKeyEvent { event ->
                val opens = event.key == Key.Enter || event.key == Key.NumPadEnter || event.key == Key.Spacebar
                if (canOpen && opens && event.type == KeyEventType.KeyDown) {
                    onOpen()
                    true
                } else {
                    false
                }
            }
            .focusRequester(focus)
            .focusable(enabled = canOpen, interactionSource = interactionSource),
    ) {
        OutlinedTextFieldDefaults.DecorationBox(
            value = text,
            innerTextField = {
                Text(text = text, style = textStyle, color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
            },
            enabled = enabled,
            singleLine = true,
            visualTransformation = androidx.compose.ui.text.input.VisualTransformation.None,
            interactionSource = interactionSource,
            isError = isError,
            label = if (compact || label == null) null else ({ SelectLabel(label, required) }),
            placeholder = if (compact) ({ Icon(Icons.Default.Search, contentDescription = label.orEmpty(), modifier = Modifier.size(20.dp)) }) else null,
            leadingIcon = icon,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            supportingText = supportingText?.let { message -> { Text(message) } },
            colors = colors,
            contentPadding = if (compact) {
                PaddingValues(horizontal = 10.dp, vertical = 6.dp)
            } else {
                OutlinedTextFieldDefaults.contentPadding()
            },
        )
    }
}

private val COMPACT_FIELD_HEIGHT = 40.dp
private val OUTLINED_FIELD_TOP_PADDING = 8.dp
private const val DISABLED_ALPHA = 0.38f

@Composable
private fun SelectLabel(text: String, required: Boolean) {
    if (!required) {
        Text(text)
        return
    }
    Row {
        Text(text)
        Text(" *", color = MaterialTheme.colorScheme.error)
    }
}

/** The content of every select menu: an optional sticky search field, then the options. */
@Composable
private fun <K> ColumnScope.SelectMenuItems(
    options: List<SelectOption<K>>,
    selected: K?,
    scroll: ScrollState,
    searchable: Boolean,
    maxShown: Int,
    fontFamily: FontFamily?,
    onSelect: (K) -> Unit,
) {
    // The menu content leaves the composition when the menu closes, so the query starts empty at each opening.
    var query by remember { mutableStateOf("") }
    val trimmed = query.trim()
    val shown = remember(options, trimmed) {
        val matching = if (trimmed.isEmpty()) options else options.filter { it.label.matchesSearch(trimmed) || it.searchText.matchesSearch(trimmed) }
        matching.take(maxShown)
    }
    LaunchedEffect(trimmed) { if (trimmed.isNotEmpty()) scroll.scrollTo(0) }
    if (searchable) {
        SelectSearchRow(query, { query = it }, scroll) { shown.firstOrNull { it.enabled }?.let { option -> onSelect(option.key) } }
    }
    if (shown.isEmpty()) {
        DropdownMenuItem(text = { Text(stringResource(Res.string.dropdown_no_match)) }, onClick = {}, enabled = false)
    }
    shown.forEach { option ->
        DropdownMenuItem(
            text = { if (fontFamily == null) Text(option.label) else Text(option.label, fontFamily = fontFamily) },
            leadingIcon = option.leadingIcon,
            trailingIcon = option.trailingIcon,
            enabled = option.enabled,
            modifier = if (option.key == selected) Modifier.background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)) else Modifier,
            onClick = { onSelect(option.key) },
        )
    }
}

/**
 * The search field at the top of a long menu. It stays at the top while the options scroll below it.
 * It does not take the focus when the menu opens, so the keyboard of a phone stays closed until the user taps it.
 * Enter chooses the first match.
 */
@Composable
private fun SelectSearchRow(query: String, onQueryChange: (String) -> Unit, scroll: ScrollState, onEnter: () -> Unit) {
    Row(
        modifier = Modifier
            .zIndex(1f)
            // The column scrolls this row up. The same offset in the other direction keeps it in place.
            .graphicsLayer { translationY = scroll.value.toFloat() }
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            placeholder = { Text(stringResource(Res.string.dropdown_search)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(SELECT_SEARCH_TAG)
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && (event.key == Key.Enter || event.key == Key.NumPadEnter)) {
                        onEnter()
                        true
                    } else {
                        false
                    }
                },
        )
    }
}
