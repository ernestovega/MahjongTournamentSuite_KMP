package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.foundation.layout.Arrangement
import mahjongtournamentsuite.composeapp.generated.resources.Res
import mahjongtournamentsuite.composeapp.generated.resources.icon_wind_east
import mahjongtournamentsuite.composeapp.generated.resources.icon_wind_north
import mahjongtournamentsuite.composeapp.generated.resources.icon_wind_south
import mahjongtournamentsuite.composeapp.generated.resources.icon_wind_west
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.zIndex
import androidx.compose.runtime.Stable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.PopupProperties
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.Popup
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import androidx.navigation.NavHostController
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import com.etologic.mahjongtournamentsuite.presentation.TournamentsRoute
import com.etologic.mahjongtournamentsuite.presentation.components.TournamentEditTitleAction
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.TableHand
import com.etologic.mahjongtournamentsuite.domain.model.TableState
import com.etologic.mahjongtournamentsuite.domain.model.TournamentTable
import com.etologic.mahjongtournamentsuite.domain.model.topHandScores
import com.etologic.mahjongtournamentsuite.presentation.components.AppErrorDialog
import com.etologic.mahjongtournamentsuite.presentation.components.AppScaffold
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedIconButton as IconButton
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedExtendedFloatingActionButton as ExtendedFloatingActionButton
import com.etologic.mahjongtournamentsuite.presentation.components.FocusHighlightContainer
import com.etologic.mahjongtournamentsuite.presentation.components.InfoTooltipIcon
import com.etologic.mahjongtournamentsuite.presentation.components.AppTopBarActions
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedOutlinedButton
import com.etologic.mahjongtournamentsuite.presentation.components.ResetTableDialog
import com.etologic.mahjongtournamentsuite.presentation.components.ScreenColumn
import com.etologic.mahjongtournamentsuite.presentation.components.SectionCard
import com.etologic.mahjongtournamentsuite.presentation.components.ChickenIcon
import com.etologic.mahjongtournamentsuite.presentation.components.LocalHandStatOptions
import com.etologic.mahjongtournamentsuite.presentation.components.HintTooltip
import com.etologic.mahjongtournamentsuite.presentation.components.TrophyIcon
import com.etologic.mahjongtournamentsuite.presentation.components.TrophyMedal
import com.etologic.mahjongtournamentsuite.presentation.components.UnsavedChangesDialog
import com.etologic.mahjongtournamentsuite.presentation.components.ScrollableColumnWithScrollbar
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedButton
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedTextButton
import com.etologic.mahjongtournamentsuite.presentation.components.appFocusGroup
import com.etologic.mahjongtournamentsuite.presentation.components.focusLoop
import com.etologic.mahjongtournamentsuite.presentation.presenter.TableManagerPresenter
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import com.etologic.mahjongtournamentsuite.presentation.util.toUiMessage
import io.ktor.http.HttpHeaders.From
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Editor of one table. It has three separate blocks: a results table
 * (players, scores, points) and a hands table. Every hand shows in the hands table. The block folds.
 */
@Composable
internal fun TableEditorPanel(
    editor: TableManagerEditorState,
    enabled: Boolean,
    playerNamesById: Map<Int, String>,
    modifier: Modifier = Modifier,
    scoreFocusRequester: FocusRequester? = null,
    /** Best hand scores of every other table of the tournament. They decide which hands are tournament best hands. */
    otherTablesBestHandScores: List<Int> = emptyList(),
) {
    val dependentSectionsEnabled = enabled && editor.hasCompleteSeatPositions
    Column(
        modifier = modifier.fillMaxWidth().appFocusGroup(),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        TableResultsCard(
            editor = editor,
            enabled = enabled,
            dependentEnabled = dependentSectionsEnabled,
            playerNamesById = playerNamesById,
            scoreFocusRequester = scoreFocusRequester,
            otherTablesBestHandScores = otherTablesBestHandScores,
        )
        TableHandsCard(
            editor = editor,
            enabled = dependentSectionsEnabled,
            playerNamesById = playerNamesById,
        )
    }
}

// ---- Grid building blocks. Every row of one table uses the same column widths, so the lines match. ----

private val GridHeaderHeight = 52.dp
private val GridRowHeight = 64.dp
private val HandSubtotalRowHeight = 36.dp
private val GridPlayerRowHeight = 76.dp
private val GridLabelColumnWidth = 128.dp
private val HandsIndexColumnWidth = 48.dp
private val HandsScoreColumnWidth = 88.dp
private val HandsChickenColumnWidth = 84.dp
private val HandsPenaltyColumnWidth = 92.dp
private val HandsDoneColumnWidth = 76.dp
private const val HandsPlayerColumnWeight = 1.6f
private const val HandsSeatColumnWeight = 1f

private val SeatLabels = listOf("East", "South", "West", "North")

/** Wind characters drawn as SVG paths, so they do not depend on a font. Same order as [SeatLabels]. */
private val SeatWindIcons = listOf(
    Res.drawable.icon_wind_east,
    Res.drawable.icon_wind_south,
    Res.drawable.icon_wind_west,
    Res.drawable.icon_wind_north,
)

/**
 * Lets a table header stay visible while the page scrolls. The screen that owns the scroll provides it.
 * Without it, headers scroll with the page.
 */
@Stable
internal class StickyHeaderHost(val scrollState: ScrollState) {
    /** Top edge of the scroll viewport in root coordinates. */
    var viewportTop by mutableStateOf(0f)
}

internal val LocalStickyHeaderHost = compositionLocalOf<StickyHeaderHost?> { null }

/** A table frame with a thin border and round corners. */
@Composable
private fun GridTable(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = MaterialTheme.shapes.small
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
        content = content,
    )
}

@Composable
private fun GridRow(
    height: Dp,
    background: Color = Color.Transparent,
    topDivider: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    if (topDivider) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Row(
        modifier = modifier.fillMaxWidth().height(height).background(background),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** One cell. It draws a divider on its right side, unless it is the last cell of the row. */
@Composable
private fun RowScope.GridCell(
    modifier: Modifier,
    last: Boolean = false,
    background: Color = Color.Transparent,
    alignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit,
) {
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    Box(
        modifier = modifier
            .fillMaxHeight()
            .background(background)
            .drawBehind {
                if (!last) {
                    val width = 1.dp.toPx()
                    drawLine(
                        color = lineColor,
                        start = Offset(size.width - width / 2, 0f),
                        end = Offset(size.width - width / 2, size.height),
                        strokeWidth = width,
                    )
                }
            }
            .padding(horizontal = 8.dp),
        contentAlignment = alignment,
        content = content,
    )
}

@Composable
private fun RowScope.GridHeaderCell(
    text: String,
    modifier: Modifier,
    subtext: String? = null,
    icon: DrawableResource? = null,
    last: Boolean = false,
) {
    GridCell(
        modifier = modifier,
        last = last,
        background = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (icon != null) {
                    Image(
                        painter = painterResource(icon),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        colorFilter = ColorFilter.tint(LocalContentColor.current),
                    )
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (subtext != null) {
                Text(
                    text = subtext,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun RowScope.GridLabelCell(text: String, last: Boolean = false) {
    GridCell(
        modifier = Modifier.width(GridLabelColumnWidth),
        last = last,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            textAlign = TextAlign.Center,
        )
    }
}

private fun playerNameOrNull(id: String, playerNamesById: Map<Int, String>): String? {
    val number = id.trim().toIntOrNull() ?: return null
    return playerNamesById[number] ?: "Player $number"
}

/** Seat marks (E, S, W, N) shown next to a player in the seat selectors. */
private fun seatMarksByPlayerId(east: String, south: String, west: String, north: String): Map<Int, String> {
    val marks = linkedMapOf<Int, MutableList<String>>()
    listOf(east to "E", south to "S", west to "W", north to "N").forEach { (value, seat) ->
        val id = value.trim().toIntOrNull() ?: return@forEach
        marks.getOrPut(id) { mutableListOf() }.add(seat)
    }
    return marks.mapValues { (_, seats) -> seats.joinToString(separator = ",") }
}

// ---- Results block ----

@Composable
private fun TableResultsCard(
    editor: TableManagerEditorState,
    enabled: Boolean,
    dependentEnabled: Boolean,
    playerNamesById: Map<Int, String>,
    scoreFocusRequester: FocusRequester?,
    otherTablesBestHandScores: List<Int>,
) {
    var editingSeats by remember(editor.roundId, editor.tableId) { mutableStateOf(false) }
    val showSeatSelectors = !editor.hasCompleteSeatPositions || editingSeats
    val seatIds = listOf(editor.playerEastId, editor.playerSouthId, editor.playerWestId, editor.playerNorthId)
    val seatChanged = listOf(
        editor.hasEastSeatChanged,
        editor.hasSouthSeatChanged,
        editor.hasWestSeatChanged,
        editor.hasNorthSeatChanged,
    )
    val scores = listOf(editor.displayEastScore, editor.displaySouthScore, editor.displayWestScore, editor.displayNorthScore)
    val scoreChanged = listOf(
        editor.hasEastScoreChanged,
        editor.hasSouthScoreChanged,
        editor.hasWestScoreChanged,
        editor.hasNorthScoreChanged,
    )
    val points = listOf(editor.displayEastPoints, editor.displaySouthPoints, editor.displayWestPoints, editor.displayNorthPoints)
    val marks = seatMarksByPlayerId(editor.playerEastId, editor.playerSouthId, editor.playerWestId, editor.playerNorthId)
    // Names, total scores and table points share this style. The Gang of Three font is only for titles and selectors.
    val bigNumberStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)

    SectionCard(
        title = "Players & results",
        titleStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
        subtitle = if (!editor.hasCompleteSeatPositions) "Select all four seat positions first" else null,
        verticalSpacing = 20.dp,
        centerTitle = true,
        titleActionAtStart = true,
        titleAction = { HandSummaryTitleAction(editor, otherTablesBestHandScores) },
        actions = {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                if (editor.hasCompleteSeatPositions) {
                    FocusedTextButton(onClick = { editingSeats = !editingSeats }, enabled = enabled) {
                        Text(if (editingSeats) "Done" else "Edit seats")
                    }
                }
                LabeledSwitch(
                    label = "Manual scores",
                    description = "When on, enter each player's final score yourself. The hands do not count (except for counting chicken and best hands).",
                    checked = editor.useTotalsOnly,
                    enabled = dependentEnabled,
                    onCheckedChange = { manual ->
                        if (manual) editor.enableManualTotals() else editor.disableManualTotals()
                    },
                )
            }
        },
    ) {
        GridTable {
            GridRow(height = GridHeaderHeight, topDivider = false) {
                GridHeaderCell(text = "", modifier = Modifier.width(GridLabelColumnWidth))
                SeatLabels.forEachIndexed { seat, label ->
                    GridHeaderCell(text = label, modifier = Modifier.weight(1f), icon = SeatWindIcons[seat], last = seat == 3)
                }
            }
            GridRow(height = GridPlayerRowHeight) {
                GridLabelCell("Player")
                seatIds.forEachIndexed { seat, id ->
                    GridCell(modifier = Modifier.weight(1f), last = seat == 3) {
                        if (showSeatSelectors) {
                            PlayerDropdown(
                                modifier = Modifier.fillMaxWidth(),
                                label = null,
                                textStyle = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.Center),
                                playerIds = editor.playerIds,
                                value = id,
                                enabled = enabled,
                                isChanged = seatChanged[seat],
                                onChange = { editor.setSeatAssignment(seat, it) },
                                playerNamesById = playerNamesById,
                                showPlayerId = true,
                                optionSuffixById = marks,
                            )
                        } else {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = playerNameOrNull(id, playerNamesById) ?: "-",
                                    style = bigNumberStyle,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                Text(
                                    text = "#${id.trim()}",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
            val seatScoreFocus = remember(scoreFocusRequester) {
                List(4) { seat -> if (seat == 0 && scoreFocusRequester != null) scoreFocusRequester else FocusRequester() }
            }
            fun moveScoreFocus(seat: Int): Boolean {
                val target = seatScoreFocus.getOrNull(seat) ?: return false
                return runCatching { target.requestFocus(); true }.getOrDefault(false)
            }
            GridRow(height = GridPlayerRowHeight) {
                GridLabelCell("Total Scores")
                scores.forEachIndexed { seat, value ->
                    GridCell(modifier = Modifier.weight(1f), last = seat == 3) {
                        if (editor.useTotalsOnly) {
                            CompactOutlinedTextField(
                                value = value,
                                onValueChange = { editor.updateManualScore(seat, it) },
                                enabled = dependentEnabled,
                                isChanged = scoreChanged[seat],
                                textStyle = bigNumberStyle,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(seatScoreFocus[seat]),
                                onNavigateLeft = { moveScoreFocus(seat - 1) },
                                onNavigateRight = { moveScoreFocus(seat + 1) },
                            )
                        } else {
                            Text(text = value.ifBlank { "-" }, style = bigNumberStyle, maxLines = 1)
                        }
                    }
                }
            }
            GridRow(height = GridPlayerRowHeight) {
                GridLabelCell("Table points")
                points.forEachIndexed { seat, value ->
                    GridCell(modifier = Modifier.weight(1f), last = seat == 3) {
                        Text(text = value.ifBlank { "-" }, style = bigNumberStyle, maxLines = 1)
                    }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ScoreSumCheck(editor)
        }
    }
}

// ---- Hands block ----

@Composable
private fun TableHandsCard(
    editor: TableManagerEditorState,
    enabled: Boolean,
    playerNamesById: Map<Int, String>,
) {
    val isHandsActive = !editor.useTotalsOnly && editor.usePointsCalculation
    var handsExpanded by remember { mutableStateOf(isHandsActive) }
    LaunchedEffect(isHandsActive) { handsExpanded = isHandsActive }
    val handsEnabled = enabled && isHandsActive
    val showChicken = LocalHandStatOptions.current.countChickenHands
    val doneCount = editor.hands.count { it.isDone }
    val subtitle = when {
        !editor.hasCompleteSeatPositions -> "Select all four seat positions first"
        !isHandsActive -> "Not used while manual scores are on"
        else -> null
    }
    val handSubtotals = editor.cumulativeHandScoreSubtotals
    val handResults = editor.handResultScores
    val rowNavigators = remember(editor.hands.map { it.handId }) {
        editor.hands.map { HandRowKeyboardNavigator() }
    }
    val stickyHost = LocalStickyHeaderHost.current
    val headerLineColor = MaterialTheme.colorScheme.outline
    val density = LocalDensity.current
    val headerHeightPx = with(density) { GridHeaderHeight.toPx() }
    // Top of the table inside the scrolled content. It does not change while the page scrolls.
    var tableContentTop by remember { mutableStateOf(0f) }
    var tableHeight by remember { mutableStateOf(0f) }

    SectionCard(
        title = "Hands",
        subtitle = subtitle,
        verticalSpacing = 20.dp,
        centerTitle = true,
        startContent = if (subtitle == null) {
            {
                Text(
                    text = "$doneCount of ${editor.hands.size} hands done.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            null
        },
        titleAction = {
            IconButton(
                onClick = { handsExpanded = !handsExpanded },
                buttonModifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = if (handsExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (handsExpanded) "Fold hands" else "Unfold hands",
                )
            }
        },
        actions = {
            LabeledSwitch(
                label = "Completed",
                description = "When on, mark the table complete and lock the hands. Every hand must be valid before you can turn it on.",
                checked = editor.isCompleted,
                enabled = handsEnabled,
                onCheckedChange = { editor.updateCompletedState(it) },
            )
        },
    ) {
        if (!handsExpanded) return@SectionCard
        if (editor.hands.isEmpty()) {
            Text(
                text = "No hands found.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@SectionCard
        }
        if (editor.hasInvalidHands) {
            Text(
                text = "At least one hand is invalid. It cannot be marked as done or completed.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Column {
        GridTable(
            modifier = Modifier.onGloballyPositioned { coordinates ->
                tableHeight = coordinates.size.height.toFloat()
                if (stickyHost != null) {
                    tableContentTop = coordinates.positionInRoot().y + stickyHost.scrollState.value
                }
            },
        ) {
            GridRow(
                height = GridHeaderHeight,
                topDivider = false,
                modifier = Modifier
                    .zIndex(1f)
                    .drawWithContent {
                        drawContent()
                        // While the header is stuck, close it with a line twice as thick as the table borders.
                        if (stickyHost != null && stickyHost.scrollState.value - (tableContentTop - stickyHost.viewportTop) > 0f) {
                            val thickness = 2.dp.toPx()
                            drawRect(
                                color = headerLineColor,
                                topLeft = Offset(0f, size.height - thickness),
                                size = Size(size.width, thickness),
                            )
                        }
                    }
                    .graphicsLayer {
                        if (stickyHost != null) {
                            val hidden = stickyHost.scrollState.value - (tableContentTop - stickyHost.viewportTop)
                            translationY = hidden.coerceIn(0f, (tableHeight - headerHeightPx).coerceAtLeast(0f))
                        }
                    },
            ) {
                GridHeaderCell(text = "#", modifier = Modifier.width(HandsIndexColumnWidth))
                GridHeaderCell(text = "Winner", modifier = Modifier.weight(HandsPlayerColumnWeight))
                GridHeaderCell(text = "Loser", modifier = Modifier.weight(HandsPlayerColumnWeight))
                GridHeaderCell(text = "Score", modifier = Modifier.width(HandsScoreColumnWidth))
                if (showChicken) GridHeaderCell(text = "Chicken", modifier = Modifier.width(HandsChickenColumnWidth))
                listOf(editor.playerEastId, editor.playerSouthId, editor.playerWestId, editor.playerNorthId)
                    .forEachIndexed { seat, id ->
                        GridHeaderCell(
                            text = SeatLabels[seat],
                            icon = SeatWindIcons[seat],
                            subtext = playerNameOrNull(id, playerNamesById),
                            modifier = Modifier.weight(HandsSeatColumnWeight),
                        )
                    }
                GridHeaderCell(text = "Penalty", modifier = Modifier.width(HandsPenaltyColumnWidth))
                GridHeaderCell(text = "Done", modifier = Modifier.width(HandsDoneColumnWidth), last = true)
            }
            editor.hands.forEachIndexed { index, hand ->
                HandTableRow(
                    index = index,
                    hand = hand,
                    playerIds = editor.playerIds,
                    playerNamesById = playerNamesById,
                    enabled = handsEnabled,
                    forceDoneChecked = editor.isCompleted,
                    result = if (hand.isBlank) SeatTextValues.EMPTY else handResults.getOrNull(index).orZero(),
                    subtotal = if (hand.isBlank) SeatTextValues.EMPTY else handSubtotals.getOrNull(index).orZero(),
                    navigation = rowNavigators.getOrNull(index) ?: HandRowKeyboardNavigator(),
                    previousNavigation = rowNavigators.getOrNull(index - 1),
                    nextNavigation = rowNavigators.getOrNull(index + 1),
                )
            }
        }
        HandsTotalsRow(totals = editor.calculatedHandScoreTotals)
        }
    }
}

/** Totals under the hands table. They have no borders and use the same columns as the table. */
@Composable
private fun HandsTotalsRow(totals: SeatTextValues) {
    GridRow(height = GridRowHeight, topDivider = false, modifier = Modifier.padding(horizontal = 1.dp)) {
        GridCell(modifier = Modifier.width(HandsIndexColumnWidth), last = true) {}
        GridCell(modifier = Modifier.weight(HandsPlayerColumnWeight), last = true, alignment = Alignment.CenterStart) {
            Text(text = "Total Scores", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
        }
        GridCell(modifier = Modifier.weight(HandsPlayerColumnWeight), last = true) {}
        GridCell(modifier = Modifier.width(HandsScoreColumnWidth), last = true) {}
        if (LocalHandStatOptions.current.countChickenHands) {
            GridCell(modifier = Modifier.width(HandsChickenColumnWidth), last = true) {}
        }
        listOf(totals.east, totals.south, totals.west, totals.north).forEach { value ->
            GridCell(modifier = Modifier.weight(HandsSeatColumnWeight), last = true) {
                Text(
                    text = value.ifBlank { "-" },
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
        GridCell(modifier = Modifier.width(HandsPenaltyColumnWidth), last = true) {}
        GridCell(modifier = Modifier.width(HandsDoneColumnWidth), last = true) {}
    }
}

/** One hand as a table row. The penalty rows open below it and use the same columns as the seat totals. */
@Composable
private fun HandTableRow(
    index: Int,
    hand: HandDraftState,
    playerIds: List<Int>,
    playerNamesById: Map<Int, String>,
    enabled: Boolean,
    forceDoneChecked: Boolean,
    result: SeatTextValues,
    subtotal: SeatTextValues,
    navigation: HandRowKeyboardNavigator,
    previousNavigation: HandRowKeyboardNavigator?,
    nextNavigation: HandRowKeyboardNavigator?,
) {
    val showChicken = LocalHandStatOptions.current.countChickenHands
    val rowLocked = forceDoneChecked || hand.isDone
    val rowEnabled = enabled && !rowLocked
    val doneEnabled = enabled && !forceDoneChecked
    val rowBackground = if (rowLocked) {
        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f)
    } else {
        Color.Transparent
    }

    // A hidden chicken column is skipped: moving across it starts from the chicken slot.
    fun move(slot: HandFieldSlot, direction: HandArrowDirection): Boolean = moveHandFieldFocus(
        current = if (!showChicken && handFieldSlotAtOffset(slot, direction) == HandFieldSlot.Chicken) {
            HandFieldSlot.Chicken
        } else {
            slot
        },
        direction = direction,
        rowEnabled = rowEnabled,
        rowNavigation = navigation,
        previousNavigation = previousNavigation,
        nextNavigation = nextNavigation,
    )

    val penaltyFocus = remember(hand.penaltyRows.size) {
        List(hand.penaltyRows.size) { List(4) { FocusRequester() } }
    }

    // The row to focus after it is composed. A new row has no focus target until the next composition.
    var penaltyRowToFocus by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(penaltyRowToFocus, penaltyFocus) {
        val row = penaltyRowToFocus ?: return@LaunchedEffect
        val target = penaltyFocus.getOrNull(row)?.firstOrNull() ?: return@LaunchedEffect
        withFrameNanos { }
        runCatching { target.requestFocus() }
        penaltyRowToFocus = null
    }

    fun movePenalty(row: Int, seat: Int, direction: HandArrowDirection): Boolean {
        if (!rowEnabled) return false
        val target = when (direction) {
            HandArrowDirection.Left -> penaltyFocus.getOrNull(row)?.getOrNull(seat - 1)
            HandArrowDirection.Right -> penaltyFocus.getOrNull(row)?.getOrNull(seat + 1)
            HandArrowDirection.Up -> penaltyFocus.getOrNull(row - 1)?.getOrNull(seat)
            HandArrowDirection.Down -> penaltyFocus.getOrNull(row + 1)?.getOrNull(seat)
        } ?: return false
        return runCatching {
            target.requestFocus()
            true
        }.getOrDefault(false)
    }

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        GridCell(modifier = Modifier.width(HandsIndexColumnWidth).fillMaxHeight(), background = rowBackground) {
            Text(
                text = "${index + 1}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (rowEnabled) 1f else 0.5f),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
        GridRow(height = GridRowHeight, background = rowBackground, topDivider = false) {
            GridCell(modifier = Modifier.weight(HandsPlayerColumnWeight)) {
                PlayerDropdown(
                    modifier = Modifier.fillMaxWidth(),
                    label = null,
                    playerIds = playerIds,
                    value = hand.playerWinnerId,
                    enabled = rowEnabled,
                    isChanged = hand.hasWinnerChanged,
                    onChange = { hand.setWinnerPlayerId(it) },
                    onDismiss = { hand.markResultFieldsTouched() },
                    onFocusLost = { hand.markResultFieldsTouched() },
                    playerNamesById = playerNamesById,
                    showPlayerId = true,
                    excludedPlayerIds = setOfNotNull(hand.selectedLoserPlayerId),
                    emptyOptionLabel = "-",
                    onNavigateLeft = { move(HandFieldSlot.Winner, HandArrowDirection.Left) },
                    onNavigateRight = { move(HandFieldSlot.Winner, HandArrowDirection.Right) },
                    onNavigateUp = { move(HandFieldSlot.Winner, HandArrowDirection.Up) },
                    onNavigateDown = { move(HandFieldSlot.Winner, HandArrowDirection.Down) },
                    focusRequester = navigation.winner,
                )
            }
            GridCell(modifier = Modifier.weight(HandsPlayerColumnWeight)) {
                PlayerDropdown(
                    modifier = Modifier.fillMaxWidth(),
                    label = null,
                    playerIds = playerIds,
                    value = hand.playerLooserId,
                    enabled = rowEnabled,
                    isChanged = hand.hasLoserChanged,
                    onChange = { hand.setLoserPlayerId(it) },
                    onDismiss = { hand.markResultFieldsTouched() },
                    onFocusLost = { hand.markResultFieldsTouched() },
                    playerNamesById = playerNamesById,
                    showPlayerId = true,
                    excludedPlayerIds = setOfNotNull(hand.selectedWinnerPlayerId),
                    emptyOptionLabel = "-",
                    onNavigateLeft = { move(HandFieldSlot.Loser, HandArrowDirection.Left) },
                    onNavigateRight = { move(HandFieldSlot.Loser, HandArrowDirection.Right) },
                    onNavigateUp = { move(HandFieldSlot.Loser, HandArrowDirection.Up) },
                    onNavigateDown = { move(HandFieldSlot.Loser, HandArrowDirection.Down) },
                    focusRequester = navigation.loser,
                )
            }
            GridCell(modifier = Modifier.width(HandsScoreColumnWidth)) {
                KeyboardAwareOutlinedTextField(
                    value = hand.handScore,
                    onValueChange = { hand.updateHandScore(it) },
                    signedIntegerOnly = true,
                    enabled = rowEnabled,
                    isChanged = hand.hasScoreChanged,
                    isError = hand.showValidationError,
                    currentSlot = HandFieldSlot.Score,
                    rowEnabled = rowEnabled,
                    rowNavigation = navigation,
                    previousNavigation = previousNavigation,
                    nextNavigation = nextNavigation,
                    focusRequester = navigation.score,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focusState ->
                            if (!focusState.isFocused) hand.markResultFieldsTouched()
                        },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(textAlign = TextAlign.Center),
                )
            }
            if (showChicken) {
                GridCell(modifier = Modifier.width(HandsChickenColumnWidth)) {
                    HandToggleControl(
                        width = 48.dp,
                        label = null,
                        checked = hand.isChickenHand,
                        enabled = rowEnabled,
                        isChanged = hand.hasChickenHandChanged,
                        onCheckedChange = { hand.updateChickenHand(it) },
                        focusRequester = navigation.chicken,
                        onNavigateLeft = { move(HandFieldSlot.Chicken, HandArrowDirection.Left) },
                        onNavigateRight = { move(HandFieldSlot.Chicken, HandArrowDirection.Right) },
                        onNavigateUp = { move(HandFieldSlot.Chicken, HandArrowDirection.Up) },
                        onNavigateDown = { move(HandFieldSlot.Chicken, HandArrowDirection.Down) },
                    )
                }
            }
            listOf(result.east, result.south, result.west, result.north).forEach { value ->
                GridCell(modifier = Modifier.weight(HandsSeatColumnWeight)) {
                    Text(
                        text = value.ifBlank { "-" },
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
            }
            GridCell(modifier = Modifier.width(HandsPenaltyColumnWidth)) {
                IconButton(onClick = { penaltyRowToFocus = hand.addPenaltyRow() }, enabled = rowEnabled, buttonModifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add penalty to hand ${index + 1}",
                    )
                }
            }
            GridCell(modifier = Modifier.width(HandsDoneColumnWidth), last = true) {
                HandToggleControl(
                    width = 48.dp,
                    label = null,
                    checked = hand.isDone,
                    enabled = doneEnabled,
                    isChanged = hand.hasDoneChanged,
                    onCheckedChange = { hand.updateDoneState(it) },
                    focusRequester = navigation.done,
                    onNavigateLeft = { move(HandFieldSlot.Done, HandArrowDirection.Left) },
                    onNavigateRight = { move(HandFieldSlot.Done, HandArrowDirection.Right) },
                    onNavigateUp = { move(HandFieldSlot.Done, HandArrowDirection.Up) },
                    onNavigateDown = { move(HandFieldSlot.Done, HandArrowDirection.Down) },
                )
            }
        }

        val penaltyBackground = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.2f)
        hand.penaltyRows.forEachIndexed { penaltyIndex, penalty ->
            GridRow(height = GridRowHeight, background = penaltyBackground) {
                GridCell(modifier = Modifier.weight(HandsPlayerColumnWeight), alignment = Alignment.CenterStart) {
                    Text(
                        text = "Penalty ${penaltyIndex + 1}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                GridCell(modifier = Modifier.weight(HandsPlayerColumnWeight)) {}
                GridCell(modifier = Modifier.width(HandsScoreColumnWidth)) {}
                if (showChicken) GridCell(modifier = Modifier.width(HandsChickenColumnWidth)) {}
                (0..3).forEach { seat ->
                    GridCell(modifier = Modifier.weight(HandsSeatColumnWeight)) {
                        SimpleHandCellField(
                            value = penalty.value(seat),
                            onValueChange = { hand.updatePenaltyValue(penaltyIndex, seat, it) },
                            signedIntegerOnly = true,
                            enabled = rowEnabled,
                            isChanged = hand.isPenaltyCellChanged(penaltyIndex, seat),
                            placeholder = "",
                            modifier = Modifier.fillMaxWidth(),
                            focusRequester = penaltyFocus.getOrNull(penaltyIndex)?.getOrNull(seat),
                            onNavigateLeft = { movePenalty(penaltyIndex, seat, HandArrowDirection.Left) },
                            onNavigateRight = { movePenalty(penaltyIndex, seat, HandArrowDirection.Right) },
                            onNavigateUp = { movePenalty(penaltyIndex, seat, HandArrowDirection.Up) },
                            onNavigateDown = { movePenalty(penaltyIndex, seat, HandArrowDirection.Down) },
                        )
                    }
                }
                GridCell(modifier = Modifier.width(HandsPenaltyColumnWidth)) {
                    IconButton(
                        onClick = { hand.removePenaltyRow(penaltyIndex) },
                        enabled = rowEnabled,
                        buttonModifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Remove penalty ${penaltyIndex + 1} of hand ${index + 1}",
                        )
                    }
                }
                GridCell(modifier = Modifier.width(HandsDoneColumnWidth), last = true) {}
            }
        }
        }
    }

    if (hand.showValidationError) {
        GridRow(height = 40.dp, background = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)) {
            Text(
                text = listOfNotNull(hand.validationErrorMessage).plus(hand.validationDetailMessages)
                    .joinToString(" ")
                    .ifBlank { "This hand is invalid and cannot be marked as done or completed." },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = HandsIndexColumnWidth + 8.dp),
            )
        }
    }

    GridRow(height = HandSubtotalRowHeight) {
        GridCell(modifier = Modifier.width(HandsIndexColumnWidth), last = true) {}
        GridCell(modifier = Modifier.weight(HandsPlayerColumnWeight), last = true) {}
        GridCell(modifier = Modifier.weight(HandsPlayerColumnWeight), last = true) {}
        GridCell(modifier = Modifier.width(HandsScoreColumnWidth), last = true) {}
        if (showChicken) GridCell(modifier = Modifier.width(HandsChickenColumnWidth), last = true) {}
        listOf(subtotal.east, subtotal.south, subtotal.west, subtotal.north).forEach { value ->
            GridCell(modifier = Modifier.weight(HandsSeatColumnWeight), last = true) {
                Text(
                    text = value.ifBlank { "-" },
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
        GridCell(modifier = Modifier.width(HandsPenaltyColumnWidth), last = true) {}
        GridCell(modifier = Modifier.width(HandsDoneColumnWidth), last = true) {}
    }
}

/** Shows whether the four manual scores add up to zero. It shows nothing before all four scores are valid. */
@Composable
private fun ScoreSumCheck(editor: TableManagerEditorState) {
    val sum = editor.scoreSum ?: return
    if (editor.useTotalsOnly) {
        // Manual totals already include the penalties, so any sum is valid. The sum is only shown.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (sum == 0L) "Sum 0 ✓" else "Sum ${if (sum > 0) "+$sum" else "$sum"}",
                style = MaterialTheme.typography.bodyMedium,
                color = if (sum == 0L) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    val expected = editor.expectedScoreSum
    fun signed(value: Long) = if (value > 0) "+$value" else "$value"
    Row(verticalAlignment = Alignment.CenterVertically) {
        val penalties = if (expected == 0L) "" else " · Penalties SUM ${signed(expected)}"
        if (sum == expected) {
            Text(
                "Sum ${if (sum == 0L) "0" else signed(sum)}$penalties ✓",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            Text(
                if (expected == 0L) {
                    "Sum is ${signed(sum)}. The scores must add up to 0."
                } else {
                    "Sum is ${signed(sum)}$penalties. The scores must add up to the penalties sum."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** Chicken hand count and best hand scores, next to the section title. */
@Composable
private fun HandSummaryTitleAction(editor: TableManagerEditorState, otherTablesBestHandScores: List<Int>) {
    // Only the best hands of the tournament show. The medal is the place in the tournament.
    val tournamentScores = topHandScores(otherTablesBestHandScores + editor.bestCompletedHands.map { it.first })
    val threshold = tournamentScores.lastOrNull()
    val options = LocalHandStatOptions.current
    val bestHands = editor.bestCompletedHands.filter { options.countBestHands && threshold != null && it.first >= threshold }
    val chickenHandCount = if (options.countChickenHands) editor.chickenHandCount else 0
    if (chickenHandCount == 0 && bestHands.isEmpty()) return
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (chickenHandCount > 0) {
            ChickenIcon(contentDescription = "Chicken hands")
            Text("$chickenHandCount", style = MaterialTheme.typography.titleLarge)
            if (bestHands.isNotEmpty()) Spacer(Modifier.width(20.dp))
        }
        bestHands.forEach { (score, winnerId) ->
            // Tied scores share the place.
            val place = tournamentScores.count { it > score } + 1
            val medal = when (place) {
                1 -> TrophyMedal.Gold
                2 -> TrophyMedal.Silver
                else -> TrophyMedal.Bronze
            }
            TrophyIcon(contentDescription = "Tournament best hand $place", medal = medal)
            Text("$score", style = MaterialTheme.typography.titleLarge)
            winnerId?.let {
                Text(
                    "#$it",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        InfoTooltipIcon(
            description = "Calculated from hands. Trophies show the best hands of the tournament that this table holds.",
            contentDescription = "Show how the chicken and best hand values are calculated",
        )
    }
}

@Composable
private fun TableSummaryRow(
    label: String,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.width(TableSummaryRowLabelWidth),
        )
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

internal fun previewTableManagerEditorState(): TableManagerEditorState {
    return TableManagerEditorState.from(
        table = TableState(
            roundId = 2,
            tableId = 1,
            playerIds = listOf(101, 102, 103, 104),
            playerEastId = "101",
            playerSouthId = "102",
            playerWestId = "103",
            playerNorthId = "104",
            playerEastScore = "48",
            playerSouthScore = "32",
            playerWestScore = "-16",
            playerNorthScore = "-64",
            playerEastPoints = "4",
            playerSouthPoints = "2",
            playerWestPoints = "1",
            playerNorthPoints = "0",
            isCompleted = false,
            useTotalsOnly = true,
            usePointsCalculation = true,
        ),
        hands = listOf(
            TableHand(
                handId = 1,
                playerWinnerId = "101",
                playerLooserId = "",
                handScore = "16",
                isChickenHand = false,
                isDone = false,
                playerEastPenalty = "",
                playerSouthPenalty = "",
                playerWestPenalty = "",
                playerNorthPenalty = "",
            ),
            TableHand(
                handId = 2,
                playerWinnerId = "102",
                playerLooserId = "104",
                handScore = "24",
                isChickenHand = true,
                isDone = true,
                playerEastPenalty = "",
                playerSouthPenalty = "",
                playerWestPenalty = "-8",
                playerNorthPenalty = "",
            ),
            TableHand(
                handId = 3,
                playerWinnerId = "103",
                playerLooserId = "",
                handScore = "12",
                isChickenHand = false,
                isDone = false,
                playerEastPenalty = "",
                playerSouthPenalty = "-4",
                playerWestPenalty = "",
                playerNorthPenalty = "",
            ),
        ),
    )
}

internal fun previewTablePlayerNamesById(): Map<Int, String> = mapOf(
    101 to "Aiko Tan",
    102 to "Bruno Lee",
    103 to "Carla Ruiz",
    104 to "Diego Mora",
)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun LabeledSwitch(
    label: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    FocusHighlightContainer(
        modifier = Modifier,
        interactionSource = interactionSource,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Switch(
                checked = checked,
                enabled = enabled,
                onCheckedChange = onCheckedChange,
                interactionSource = interactionSource,
            )
            InfoTooltipIcon(
                description = description,
                contentDescription = "Show $label information",
            )
        }
    }
}

@Composable
private fun HandToggleControl(
    width: Dp,
    label: String?,
    checked: Boolean,
    enabled: Boolean,
    isChanged: Boolean = false,
    onCheckedChange: ((Boolean) -> Unit)?,
    focusRequester: FocusRequester? = null,
    onNavigateLeft: (() -> Boolean)? = null,
    onNavigateRight: (() -> Boolean)? = null,
    onNavigateUp: (() -> Boolean)? = null,
    onNavigateDown: (() -> Boolean)? = null,
) {
    val shape = MaterialTheme.shapes.extraSmall
    var focused by remember { mutableStateOf(false) }
    val focusModifier = Modifier
        .clip(shape)
        .background(
            if (focused && enabled) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
            } else {
                Color.Transparent
            },
        )
    val checkboxColors = when {
        !enabled -> CheckboxDefaults.colors(
            checkedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
            uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
            checkmarkColor = MaterialTheme.colorScheme.surface,
            disabledCheckedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
            disabledUncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
            disabledIndeterminateColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
        )
        isChanged -> CheckboxDefaults.colors(
            checkedColor = MaterialTheme.colorScheme.tertiary,
            uncheckedColor = MaterialTheme.colorScheme.tertiary,
            checkmarkColor = MaterialTheme.colorScheme.onTertiary,
        )
        else -> CheckboxDefaults.colors()
    }

    Column(
        modifier = Modifier
            .width(width)
            .then(focusModifier)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = when {
                    !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                    isChanged -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Checkbox(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
            colors = checkboxColors,
            modifier = (focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
                .onFocusChanged { focused = it.isFocused }
                .onPreviewKeyEvent { event ->
                    if (!enabled || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionLeft -> onNavigateLeft?.invoke() ?: false
                        Key.DirectionRight -> onNavigateRight?.invoke() ?: false
                        Key.DirectionUp -> onNavigateUp?.invoke() ?: false
                        Key.DirectionDown -> onNavigateDown?.invoke() ?: false
                        else -> false
                    }
                },
        )
    }
}

/** Keeps digits and a hyphen at the start. Other characters are dropped. */
internal fun String.toSignedIntegerInput(): String =
    filterIndexed { index, char -> char.isDigit() || (index == 0 && char == '-') }

private fun SeatTextValues?.orZero(): SeatTextValues = this ?: SeatTextValues.ZERO

@Composable
private fun CompactOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false,
    isChanged: Boolean = false,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    compactHeight: Dp? = null,
    colors: androidx.compose.material3.TextFieldColors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(),
    onNavigateLeft: (() -> Boolean)? = null,
    onNavigateRight: (() -> Boolean)? = null,
) {
    val resolvedColors = if (enabled && isChanged && !isError) {
        OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = MaterialTheme.colorScheme.tertiary,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedLabelColor = MaterialTheme.colorScheme.tertiary,
        )
    } else {
        colors
    }
    var fieldValue by remember(value) {
        mutableStateOf(TextFieldValue(text = value, selection = TextRange(value.length)))
    }
    LaunchedEffect(value) {
        if (fieldValue.text != value) {
            fieldValue = fieldValue.copy(text = value, selection = TextRange(value.length))
        }
    }
    val keyModifier = Modifier.onPreviewKeyEvent { event ->
        if (!enabled || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        val selection = fieldValue.selection
        when (event.key) {
            Key.DirectionLeft ->
                selection.collapsed && selection.start == 0 && (onNavigateLeft?.invoke() ?: false)
            Key.DirectionRight ->
                selection.collapsed && selection.end == fieldValue.text.length && (onNavigateRight?.invoke() ?: false)
            else -> false
        }
    }
    OutlinedTextField(
        value = fieldValue,
        onValueChange = { typed ->
            fieldValue = typed
            onValueChange(typed.text)
        },
        modifier = (if (compactHeight != null) modifier.height(compactHeight) else modifier).then(keyModifier),
        enabled = enabled,
        isError = isError,
        readOnly = readOnly,
        singleLine = singleLine,
        label = label,
        placeholder = placeholder,
        supportingText = supportingText,
        trailingIcon = trailingIcon,
        textStyle = textStyle,
        colors = resolvedColors,
    )
}

@Composable
private fun KeyboardAwareOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    signedIntegerOnly: Boolean = false,
    currentSlot: HandFieldSlot,
    rowEnabled: Boolean,
    rowNavigation: HandRowKeyboardNavigator,
    previousNavigation: HandRowKeyboardNavigator? = null,
    nextNavigation: HandRowKeyboardNavigator? = null,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    enabled: Boolean = true,
    isError: Boolean = false,
    isChanged: Boolean = false,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    compactHeight: Dp? = null,
    colors: androidx.compose.material3.TextFieldColors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(),
) {
    val resolvedColors = if (enabled && isChanged && !isError) {
        OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = MaterialTheme.colorScheme.tertiary,
            focusedBorderColor = MaterialTheme.colorScheme.tertiary,
            unfocusedLabelColor = MaterialTheme.colorScheme.tertiary,
        )
    } else {
        colors
    }
    var fieldValue by remember(value) {
        mutableStateOf(TextFieldValue(text = value, selection = TextRange(value.length)))
    }

    LaunchedEffect(value) {
        if (fieldValue.text != value) {
            fieldValue = fieldValue.copy(text = value, selection = TextRange(value.length))
        }
    }

    OutlinedTextField(
        value = fieldValue,
        onValueChange = { typed ->
            val newValue = if (signedIntegerOnly) typed.copy(text = typed.text.toSignedIntegerInput()) else typed
            fieldValue = newValue
            onValueChange(newValue.text)
        },
        modifier = (if (compactHeight != null) modifier.height(compactHeight) else modifier)
            .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
            .onPreviewKeyEvent { event ->
                if (!enabled || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val selection = fieldValue.selection
                val textLength = fieldValue.text.length
                when (event.key) {
                    Key.DirectionLeft -> {
                        if (selection.collapsed && selection.start == 0) {
                            moveHandFieldFocus(
                                current = currentSlot,
                                direction = HandArrowDirection.Left,
                                rowEnabled = rowEnabled,
                                rowNavigation = rowNavigation,
                                previousNavigation = previousNavigation,
                                nextNavigation = nextNavigation,
                            )
                        } else {
                            false
                        }
                    }

                    Key.DirectionRight -> {
                        if (selection.collapsed && selection.end == textLength) {
                            moveHandFieldFocus(
                                current = currentSlot,
                                direction = HandArrowDirection.Right,
                                rowEnabled = rowEnabled,
                                rowNavigation = rowNavigation,
                                previousNavigation = previousNavigation,
                                nextNavigation = nextNavigation,
                            )
                        } else {
                            false
                        }
                    }

                    Key.DirectionUp -> moveHandFieldFocus(
                        current = currentSlot,
                        direction = HandArrowDirection.Up,
                        rowEnabled = rowEnabled,
                        rowNavigation = rowNavigation,
                        previousNavigation = previousNavigation,
                        nextNavigation = nextNavigation,
                    )

                    Key.DirectionDown -> moveHandFieldFocus(
                        current = currentSlot,
                        direction = HandArrowDirection.Down,
                        rowEnabled = rowEnabled,
                        rowNavigation = rowNavigation,
                        previousNavigation = previousNavigation,
                        nextNavigation = nextNavigation,
                    )

                    else -> false
                }
            },
        enabled = enabled,
        isError = isError,
        readOnly = readOnly,
        singleLine = singleLine,
        label = label,
        placeholder = placeholder,
        supportingText = supportingText,
        trailingIcon = trailingIcon,
        textStyle = textStyle,
        colors = resolvedColors,
    )
}

@Composable
private fun SimpleHandCellField(
    value: String,
    onValueChange: (String) -> Unit,
    signedIntegerOnly: Boolean = false,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isChanged: Boolean = false,
    placeholder: String,
    focusRequester: FocusRequester? = null,
    onNavigateLeft: (() -> Boolean)? = null,
    onNavigateRight: (() -> Boolean)? = null,
    onNavigateUp: (() -> Boolean)? = null,
    onNavigateDown: (() -> Boolean)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val borderColor = when {
        focused -> MaterialTheme.colorScheme.primary
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        isChanged -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.outline
    }
    val shape = MaterialTheme.shapes.extraSmall
    val borderWidth = if (focused) 2.dp else 1.dp
    var textFieldValue by remember(value) {
        mutableStateOf(TextFieldValue(text = value, selection = TextRange(value.length)))
    }

    LaunchedEffect(value) {
        if (textFieldValue.text != value) {
            textFieldValue = textFieldValue.copy(text = value, selection = TextRange(value.length))
        }
    }

    Box(
        modifier = modifier
            .height(HandMiniFieldHeight)
            .border(borderWidth, borderColor, shape),
        contentAlignment = Alignment.Center,
    ) {
        BasicTextField(
            value = textFieldValue,
            onValueChange = { typed ->
                val newValue = if (signedIntegerOnly) typed.copy(text = typed.text.toSignedIntegerInput()) else typed
                textFieldValue = newValue
                onValueChange(newValue.text)
            },
            enabled = enabled,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                textAlign = TextAlign.Center,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .fillMaxSize()
                .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
                .onFocusChanged { focused = it.isFocused }
                .onPreviewKeyEvent { event ->
                    if (!enabled || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val selection = textFieldValue.selection
                    val textLength = textFieldValue.text.length
                    when (event.key) {
                        Key.DirectionLeft -> {
                            if (selection.collapsed && selection.start == 0) {
                                onNavigateLeft?.invoke() ?: false
                            } else {
                                false
                            }
                        }

                        Key.DirectionRight -> {
                            if (selection.collapsed && selection.end == textLength) {
                                onNavigateRight?.invoke() ?: false
                            } else {
                                false
                            }
                        }

                        Key.DirectionUp -> onNavigateUp?.invoke() ?: false
                        Key.DirectionDown -> onNavigateDown?.invoke() ?: false
                        else -> false
                    }
                },
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    if (value.isBlank() && !focused) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.58f),
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    innerTextField()
                }
            },
        )
    }
}

internal enum class ConflictChoice { MINE, SERVER }

internal data class ConflictField(
    val id: String,
    val label: String,
    val base: Any?,
    val mine: Any?,
    val server: Any?,
)

internal data class TableSaveConflict(
    val baseTable: TableState,
    val baseHands: List<TableHand>,
    val serverTable: TableState,
    val serverHands: List<TableHand>,
    val tablePatch: Map<String, Any?>,
    val handPatches: Map<Int, Map<String, Any?>>,
) {
    val fields: List<ConflictField> = buildList {
        tablePatch.forEach { (field, mine) ->
            val base = baseTable.fieldValue(field)
            val server = serverTable.fieldValue(field)
            if (server != base && mine != server) {
                add(ConflictField("table:$field", field.toFieldLabel(), base, mine, server))
            }
        }
        handPatches.forEach { (handId, patch) ->
            val baseHand = baseHands.firstOrNull { it.handId == handId }
            val serverHand = serverHands.firstOrNull { it.handId == handId }
            patch.forEach { (field, mine) ->
                val base = baseHand?.fieldValue(field)
                val server = serverHand?.fieldValue(field)
                if (server != base && mine != server) {
                    add(ConflictField("hand:$handId:$field", "Hand $handId · ${field.toFieldLabel()}", base, mine, server))
                }
            }
        }
    }

    fun resolvedTablePatch(choices: Map<String, ConflictChoice>): Map<String, Any?> =
        tablePatch.filterKeys { field -> choices["table:$field"] != ConflictChoice.SERVER }

    fun resolvedHandPatches(choices: Map<String, ConflictChoice>): Map<Int, Map<String, Any?>> =
        handPatches.mapValues { (handId, patch) ->
            patch.filterKeys { field -> choices["hand:$handId:$field"] != ConflictChoice.SERVER }
        }.filterValues { it.isNotEmpty() }
}

@Composable
internal fun TableSaveConflictDialog(
    conflict: TableSaveConflict,
    choices: Map<String, ConflictChoice>,
    isSaving: Boolean,
    onChoice: (String, ConflictChoice) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val scrollState = rememberScrollState()
    val confirmFocusRequester = remember { FocusRequester() }
    val cancelFocusRequester = remember { FocusRequester() }
    val allChosen = conflict.fields.all { it.id in choices }

    LaunchedEffect(conflict) { cancelFocusRequester.requestFocus() }

    AlertDialog(
        modifier = Modifier.appFocusGroup(),
        onDismissRequest = { if (!isSaving) onCancel() },
        title = { Text("Resolve table changes") },
        text = {
            ScrollableColumnWithScrollbar(
                state = scrollState,
                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
            ) {
                Text(
                    "The table changed after you opened it. Server-only changes are accepted automatically. " +
                        "Choose a value for each field changed in both places.",
                )
                Spacer(Modifier.height(12.dp))
                if (conflict.fields.isEmpty()) {
                    Text("No field needs a choice. Select Continue to keep your independent edits.")
                }
                conflict.fields.forEach { field ->
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(field.label, fontWeight = FontWeight.Bold)
                        Text("Base: ${field.base.displayConflictValue()}")
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            FocusedOutlinedButton(
                                buttonModifier = Modifier.fillMaxWidth(),
                                onClick = { onChoice(field.id, ConflictChoice.MINE) },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (choices[field.id] == ConflictChoice.MINE) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        Color.Transparent
                                    },
                                ),
                            ) { Text("Mine: ${field.mine.displayConflictValue()}") }
                            FocusedOutlinedButton(
                                buttonModifier = Modifier.fillMaxWidth(),
                                onClick = { onChoice(field.id, ConflictChoice.SERVER) },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (choices[field.id] == ConflictChoice.SERVER) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        Color.Transparent
                                    },
                                ),
                            ) { Text("Server: ${field.server.displayConflictValue()}") }
                        }
                    }
                    HorizontalDivider()
                }
            }
        },
        confirmButton = {
            FocusedButton(
                enabled = !isSaving && allChosen,
                onClick = onConfirm,
                focusRequester = confirmFocusRequester,
                buttonModifier = Modifier.focusLoop(cancelFocusRequester, cancelFocusRequester),
            ) { Text("Continue") }
        },
        dismissButton = {
            FocusedTextButton(
                enabled = !isSaving,
                onClick = onCancel,
                focusRequester = cancelFocusRequester,
                buttonModifier = Modifier.focusLoop(confirmFocusRequester, confirmFocusRequester),
            ) { Text("Keep editing") }
        },
    )
}

private fun Any?.displayConflictValue(): String = when (this) {
    null -> "empty"
    is String -> if (isBlank()) "empty" else this
    else -> toString()
}

private fun String.toFieldLabel(): String =
    replace(Regex("([a-z])([A-Z])"), "$1 $2").replaceFirstChar { it.uppercase() }

private fun TableState.fieldValue(field: String): Any? = when (field) {
    "playerEastId" -> playerEastId
    "playerSouthId" -> playerSouthId
    "playerWestId" -> playerWestId
    "playerNorthId" -> playerNorthId
    "playerEastScore" -> playerEastScore
    "playerSouthScore" -> playerSouthScore
    "playerWestScore" -> playerWestScore
    "playerNorthScore" -> playerNorthScore
    "playerEastPoints" -> playerEastPoints
    "playerSouthPoints" -> playerSouthPoints
    "playerWestPoints" -> playerWestPoints
    "playerNorthPoints" -> playerNorthPoints
    "manualPlayerEastScore" -> manualPlayerEastScore
    "manualPlayerSouthScore" -> manualPlayerSouthScore
    "manualPlayerWestScore" -> manualPlayerWestScore
    "manualPlayerNorthScore" -> manualPlayerNorthScore
    "manualPlayerEastPoints" -> manualPlayerEastPoints
    "manualPlayerSouthPoints" -> manualPlayerSouthPoints
    "manualPlayerWestPoints" -> manualPlayerWestPoints
    "manualPlayerNorthPoints" -> manualPlayerNorthPoints
    "isCompleted" -> isCompleted
    "useTotalsOnly" -> useTotalsOnly
    "usePointsCalculation" -> usePointsCalculation
    else -> null
}

private fun TableHand.fieldValue(field: String): Any? = when (field) {
    "playerWinnerId" -> playerWinnerId
    "playerLooserId" -> playerLooserId
    "handScore" -> handScore
    "isChickenHand" -> isChickenHand
    "isDone" -> isDone
    "playerEastPenalty" -> playerEastPenalty
    "playerSouthPenalty" -> playerSouthPenalty
    "playerWestPenalty" -> playerWestPenalty
    "playerNorthPenalty" -> playerNorthPenalty
    else -> null
}

private val HandMiniFieldHeight = 38.dp
private val TableSummaryRowLabelWidth = 84.dp
private const val MIN_HAND_SCORE = 8
private const val MAX_CHICKEN_HAND_SCORE = 12

private enum class HandArrowDirection {
    Left,
    Right,
    Up,
    Down,
}

private enum class HandFieldSlot {
    Winner,
    Loser,
    Score,
    Chicken,
    SubtotalEast,
    SubtotalSouth,
    SubtotalWest,
    SubtotalNorth,
    PenaltyEast,
    PenaltySouth,
    PenaltyWest,
    PenaltyNorth,
    Done,
}

private class HandRowKeyboardNavigator {
    val winner = FocusRequester()
    val loser = FocusRequester()
    val score = FocusRequester()
    val chicken = FocusRequester()
    val subtotalEast = FocusRequester()
    val subtotalSouth = FocusRequester()
    val subtotalWest = FocusRequester()
    val subtotalNorth = FocusRequester()
    val penaltyEast = FocusRequester()
    val penaltySouth = FocusRequester()
    val penaltyWest = FocusRequester()
    val penaltyNorth = FocusRequester()
    val done = FocusRequester()
}

private fun handFieldSlotAtOffset(slot: HandFieldSlot, direction: HandArrowDirection): HandFieldSlot? {
    val order = listOf(
        HandFieldSlot.Winner,
        HandFieldSlot.Loser,
        HandFieldSlot.Score,
        HandFieldSlot.Chicken,
        HandFieldSlot.Done,
    )
    val index = order.indexOf(slot)
    return when (direction) {
        HandArrowDirection.Left -> order.getOrNull(index - 1)
        HandArrowDirection.Right -> order.getOrNull(index + 1)
        else -> slot
    }
}

private fun HandRowKeyboardNavigator.focusRequesterFor(slot: HandFieldSlot): FocusRequester = when (slot) {
    HandFieldSlot.Winner -> winner
    HandFieldSlot.Loser -> loser
    HandFieldSlot.Score -> score
    HandFieldSlot.Chicken -> chicken
    HandFieldSlot.SubtotalEast -> subtotalEast
    HandFieldSlot.SubtotalSouth -> subtotalSouth
    HandFieldSlot.SubtotalWest -> subtotalWest
    HandFieldSlot.SubtotalNorth -> subtotalNorth
    HandFieldSlot.PenaltyEast -> penaltyEast
    HandFieldSlot.PenaltySouth -> penaltySouth
    HandFieldSlot.PenaltyWest -> penaltyWest
    HandFieldSlot.PenaltyNorth -> penaltyNorth
    HandFieldSlot.Done -> done
}

private fun moveHandFieldFocus(
    current: HandFieldSlot,
    direction: HandArrowDirection,
    rowEnabled: Boolean,
    rowNavigation: HandRowKeyboardNavigator,
    previousNavigation: HandRowKeyboardNavigator?,
    nextNavigation: HandRowKeyboardNavigator?,
): Boolean {
    if (!rowEnabled) return false
    val target = when (direction) {
        HandArrowDirection.Left, HandArrowDirection.Right -> {
            val slot = handFieldSlotAtOffset(current, direction) ?: return false
            rowNavigation.focusRequesterFor(slot)
        }

        HandArrowDirection.Up -> previousNavigation?.focusRequesterFor(current) ?: return false
        HandArrowDirection.Down -> nextNavigation?.focusRequesterFor(current) ?: return false
    }

    return runCatching {
        target.requestFocus()
        true
    }.getOrDefault(false)
}

/**
 * Holds the editable values for one table.
 *
 * The editor keeps hands and manual scores at the same time. Switching modes only changes which
 * values apply. It never clears values from an inactive mode.
 *
 * - Hands mode applies calculated hand totals and calculated table points.
 * - Manual Scores applies manual scores and calculated table points.
 * Legacy point fields remain readable for migration compatibility. The application never exposes
 * them as an editing mode. Saving writes all changed hand and manual values, including values from
 * inactive modes. Loading the table again restores those values when the related mode becomes
 * active. Discard restores the values from the last saved table state.
 */
@Stable
internal class TableManagerEditorState private constructor(
    private val initialTable: TableState,
    initialHands: List<TableHand>,
) {
    val roundId: Int = initialTable.roundId
    val tableId: Int = initialTable.tableId
    val version: Long = initialTable.version
    val playerIds: List<Int> = initialTable.playerIds

    var isCompleted by mutableStateOf(initialTable.isCompleted)
    var useTotalsOnly by mutableStateOf(initialTable.useTotalsOnly)
    var usePointsCalculation by mutableStateOf(initialTable.usePointsCalculation)

    var playerEastId by mutableStateOf("")
    var playerSouthId by mutableStateOf("")
    var playerWestId by mutableStateOf("")
    var playerNorthId by mutableStateOf("")

    private val initialSeatAssignments: SeatAssignments

    var playerEastScore by mutableStateOf(initialManualScores().east)
    var playerSouthScore by mutableStateOf(initialManualScores().south)
    var playerWestScore by mutableStateOf(initialManualScores().west)
    var playerNorthScore by mutableStateOf(initialManualScores().north)

    var playerEastPoints by mutableStateOf(initialManualPoints().east)
    var playerSouthPoints by mutableStateOf(initialManualPoints().south)
    var playerWestPoints by mutableStateOf(initialManualPoints().west)
    var playerNorthPoints by mutableStateOf(initialManualPoints().north)

    /** Sum of the four manual scores, or null until all four are whole numbers. */
    val manualScoreSum: Long?
        get() {
            val scores = manualTableScores()
            return listOf(scores.east, scores.south, scores.west, scores.north)
                .map { it.toLongOrNull() ?: return null }
                .sum()
        }

    /**
     * Sum of the four table scores, or null while it cannot be known. The scores are the manual totals or
     * the ones calculated from the hands. A calculated sum needs at least one counted hand.
     */
    val scoreSum: Long?
        get() {
            if (useTotalsOnly) return manualScoreSum
            if (!isCompleted && hands.none { it.isDone }) return null
            val totals = calculatedHandScoreTotals
            return listOf(totals.east, totals.south, totals.west, totals.north)
                .map { it.toLongOrNull() ?: return null }
                .sum()
        }

    /**
     * The sum that the calculated scores must have. Penalties take points from a player and give them
     * to nobody, so the scores add up to the total of the penalties.
     */
    val expectedScoreSum: Long
        get() {
            val seats = currentSeatIds() ?: return 0L
            return hands
                .filter { (it.isDone || isCompleted) && calculateHandSeatScores(it, seats) != null }
                .sumOf { hand -> (0..3).sumOf { seat -> parsePenalty(hand.currentPenalty(seat)).toLong() } }
        }

    val hands = mutableStateListOf<HandDraftState>()

    val calculatedHandScoreTotals: SeatTextValues
        get() = calculateHandScoreTotals()

    val cumulativeHandScoreSubtotals: List<SeatTextValues>
        get() = calculateCumulativeHandScoreSubtotals()

    /** The result of each hand alone, with its penalties. A hand that does not count has no value. */
    val handResultScores: List<SeatTextValues>
        get() {
            val seats = currentSeatIds() ?: return List(hands.size) { SeatTextValues.EMPTY }
            return hands.map { hand ->
                if (hand.isDone || isCompleted) {
                    calculateHandSeatScores(hand, seats)?.asTextValues() ?: SeatTextValues.EMPTY
                } else {
                    SeatTextValues.EMPTY
                }
            }
        }

    val calculatedHandPointTotals: SeatTextValues
        get() = calculatePointsFromScores(calculatedHandScoreTotals)

    val displayEastScore: String
        get() = displayedTableScores().east
    val displaySouthScore: String
        get() = displayedTableScores().south
    val displayWestScore: String
        get() = displayedTableScores().west
    val displayNorthScore: String
        get() = displayedTableScores().north

    val displayEastPoints: String
        get() = effectiveTablePoints().east
    val displaySouthPoints: String
        get() = effectiveTablePoints().south
    val displayWestPoints: String
        get() = effectiveTablePoints().west
    val displayNorthPoints: String
        get() = effectiveTablePoints().north

    val hasUnsavedChanges: Boolean
        get() = buildTablePatch().isNotEmpty() || buildHandPatches().isNotEmpty()

    /** Manual totals ignore the hands, except to count chicken hands and best hands. */
    val hasInvalidHands: Boolean
        get() = !useTotalsOnly && hands.any { it.isResultSelectionInvalid }

    val chickenHandCount: Int
        get() = hands.count { it.isChickenHand }

    /**
     * Done hands with a score that are among the best hands of this table, from highest to lowest.
     * Same rule as the server (`calculateBestHandScores`): the three highest scores and their ties.
     * Each entry has the score and the winner id, or null when the hand has no winner.
     */
    val bestCompletedHands: List<Pair<Int, String?>>
        get() {
            val scored = hands.filter { it.isDone }.mapNotNull { hand ->
                hand.handScore.trim().toIntOrNull()?.let { it to hand.playerWinnerId.trim().takeIf(String::isNotEmpty) }
            }
            val threshold = topHandScores(scored.map { it.first }).lastOrNull() ?: return emptyList()
            return scored.filter { it.first >= threshold }.sortedByDescending { it.first }
        }

    val hasCompleteSeatPositions: Boolean
        get() = currentSeatIds() != null

    /** Same rules as the server (`tableManagerService.ts`), so the card matches the tables list. */
    private val summaryHasProgress: Boolean
        get() = isCompleted || summaryHasData

    private val summaryHasData: Boolean
        get() {
            val scores = manualTableScores().let { listOf(it.east, it.south, it.west, it.north) }
            val points = manualTablePoints().let { listOf(it.east, it.south, it.west, it.north) }
            return listOf(playerEastId, playerSouthId, playerWestId, playerNorthId).any { it.isNotBlank() } ||
                scores.any { it.isNotBlank() } ||
                points.any { it.isNotBlank() } ||
                hands.any { hand ->
                    hand.isDone || hand.isChickenHand || listOf(
                        hand.playerWinnerId, hand.normalizedLoserId, hand.handScore,
                        hand.playerEastPenalty, hand.playerSouthPenalty,
                        hand.playerWestPenalty, hand.playerNorthPenalty,
                    ).any { it.isNotBlank() }
                }
        }

    private val summaryHasValidManualTotals: Boolean
        get() {
            val scores = manualTableScores().let { listOf(it.east, it.south, it.west, it.north) }
            val points = manualTablePoints().let { listOf(it.east, it.south, it.west, it.north) }
            val validScores = scores.all { it.matches(Regex("^-?\\d+$")) }
            val validPoints = points.all { it.matches(Regex("^-?\\d+(?:[,.]\\d+)?$")) } &&
                kotlin.math.abs(points.sumOf { it.replace(',', '.').toDouble() } - 7.0) < 0.011
            return if (useTotalsOnly) validScores else !usePointsCalculation && validPoints
        }

    /** Hands mode needs valid effective scores. It needs no hands, because scores fall back to the manual ones. */
    private val hasValidEffectiveScores: Boolean
        get() = effectiveTableScores().let { listOf(it.east, it.south, it.west, it.north) }
            .all { it.matches(Regex("^-?\\d+$")) }

    /** Same rules as `calculateTableCompletionStatus` on the server. */
    val completionStatus: CompletionStatus
        get() = when {
            !summaryHasData -> CompletionStatus.Empty
            !hasCompleteSeatPositions -> CompletionStatus.InProgress
            useTotalsOnly -> if (summaryHasValidManualTotals) CompletionStatus.Manual else CompletionStatus.InProgress
            hasValidEffectiveScores -> CompletionStatus.Completed
            else -> CompletionStatus.InProgress
        }

    /** Copies this saved table state into the summary shown in the rounds and tables columns. */
    fun applyTo(summary: TournamentTable): TournamentTable = summary.copy(
        isCompleted = isCompleted,
        useTotalsOnly = useTotalsOnly,
        usePointsCalculation = usePointsCalculation,
        hasProgress = summaryHasProgress,
        hasValidManualTotals = summaryHasValidManualTotals,
        completionStatus = when (completionStatus) {
            CompletionStatus.Empty -> "empty"
            CompletionStatus.InProgress -> "incomplete"
            CompletionStatus.Manual -> "partial"
            CompletionStatus.Completed -> "completed"
        },
        bestHandScores = bestCompletedHands.map { it.first },
        chickenHandCount = chickenHandCount,
        version = version,
    )

    val hasEastSeatChanged: Boolean get() = playerEastId.trim() != initialSeatAssignments.east
    val hasSouthSeatChanged: Boolean get() = playerSouthId.trim() != initialSeatAssignments.south
    val hasWestSeatChanged: Boolean get() = playerWestId.trim() != initialSeatAssignments.west
    val hasNorthSeatChanged: Boolean get() = playerNorthId.trim() != initialSeatAssignments.north

    val hasEastScoreChanged: Boolean get() = playerEastScore.trim() != initialManualScores().east
    val hasSouthScoreChanged: Boolean get() = playerSouthScore.trim() != initialManualScores().south
    val hasWestScoreChanged: Boolean get() = playerWestScore.trim() != initialManualScores().west
    val hasNorthScoreChanged: Boolean get() = playerNorthScore.trim() != initialManualScores().north

    val hasEastPointsChanged: Boolean get() = playerEastPoints.trim() != initialManualPoints().east
    val hasSouthPointsChanged: Boolean get() = playerSouthPoints.trim() != initialManualPoints().south
    val hasWestPointsChanged: Boolean get() = playerWestPoints.trim() != initialManualPoints().west
    val hasNorthPointsChanged: Boolean get() = playerNorthPoints.trim() != initialManualPoints().north

    init {
        val initialAssignments = sanitizeSeatAssignments(
            playerIds = playerIds,
            east = initialTable.playerEastId,
            south = initialTable.playerSouthId,
            west = initialTable.playerWestId,
            north = initialTable.playerNorthId,
        )
        initialSeatAssignments = initialAssignments
        playerEastId = initialAssignments.east
        playerSouthId = initialAssignments.south
        playerWestId = initialAssignments.west
        playerNorthId = initialAssignments.north

        hands.addAll(initialHands.map { HandDraftState.from(it) })
    }

    fun setSeatAssignment(seatIndex: Int, playerId: String) {
        val trimmed = playerId.trim()
        when (seatIndex) {
            0 -> {
                playerEastId = trimmed
                if (trimmed.isBlank()) return
                if (playerSouthId.trim() == trimmed) playerSouthId = ""
                if (playerWestId.trim() == trimmed) playerWestId = ""
                if (playerNorthId.trim() == trimmed) playerNorthId = ""
            }

            1 -> {
                playerSouthId = trimmed
                if (trimmed.isBlank()) return
                if (playerEastId.trim() == trimmed) playerEastId = ""
                if (playerWestId.trim() == trimmed) playerWestId = ""
                if (playerNorthId.trim() == trimmed) playerNorthId = ""
            }

            2 -> {
                playerWestId = trimmed
                if (trimmed.isBlank()) return
                if (playerEastId.trim() == trimmed) playerEastId = ""
                if (playerSouthId.trim() == trimmed) playerSouthId = ""
                if (playerNorthId.trim() == trimmed) playerNorthId = ""
            }

            3 -> {
                playerNorthId = trimmed
                if (trimmed.isBlank()) return
                if (playerEastId.trim() == trimmed) playerEastId = ""
                if (playerSouthId.trim() == trimmed) playerSouthId = ""
                if (playerWestId.trim() == trimmed) playerWestId = ""
            }

            else -> Unit
        }
    }

    fun enableManualTotals() {
        useTotalsOnly = true
        usePointsCalculation = true
        isCompleted = false
    }

    fun updateManualScore(seatIndex: Int, rawValue: String) {
        val value = rawValue.toSignedIntegerInput()
        if (value != manualTableScores().bySeat(seatIndex)) isCompleted = false
        when (seatIndex) {
            0 -> playerEastScore = value
            1 -> playerSouthScore = value
            2 -> playerWestScore = value
            3 -> playerNorthScore = value
        }
    }

    fun disableManualTotals() {
        useTotalsOnly = false
        usePointsCalculation = true
    }

    @Deprecated("Legacy data compatibility. Manual Points is not an application mode.")
    fun enableManualPoints() {
        usePointsCalculation = false
        useTotalsOnly = false
    }

    @Deprecated("Legacy data compatibility. Manual Points is not an application mode.")
    fun disableManualPoints() {
        usePointsCalculation = true
    }

    /** Returns the patch used by the application. Manual Points is not an application mode. */
    fun buildApplicationTablePatch(): Map<String, Any?> {
        val patch = buildTablePatch().toMutableMap()
        if (!usePointsCalculation) patch["usePointsCalculation"] = true
        return patch
    }

    fun baseTableSnapshot(): TableState = initialTable

    fun baseHandsSnapshot(): List<TableHand> = initialHandsSnapshot

    fun applyHandDrafts(drafts: List<HandDraftState>) {
        drafts.forEach { draft ->
            hands.firstOrNull { it.handId == draft.handId }?.copyValuesFrom(draft)
        }
    }

    private val initialHandsSnapshot: List<TableHand> = initialHands.map { it }

    fun updateCompletedState(value: Boolean) {
        if (!value) {
            isCompleted = false
            return
        }

        hands.forEach { it.markResultFieldsTouched() }
        if (hasInvalidHands) return
        isCompleted = true
    }

    fun buildTablePatch(): Map<String, Any?> {
        val patch = linkedMapOf<String, Any?>()
        val handPatches = buildHandPatches()

        fun putIfChanged(key: String, current: Any?, initial: Any?) {
            if (current != initial) patch[key] = current
        }

        putIfChanged("isCompleted", isCompleted, initialTable.isCompleted)
        putIfChanged("useTotalsOnly", useTotalsOnly, initialTable.useTotalsOnly)
        putIfChanged("usePointsCalculation", usePointsCalculation, initialTable.usePointsCalculation)

        putIfChanged("playerEastId", playerEastId.trim(), initialSeatAssignments.east)
        putIfChanged("playerSouthId", playerSouthId.trim(), initialSeatAssignments.south)
        putIfChanged("playerWestId", playerWestId.trim(), initialSeatAssignments.west)
        putIfChanged("playerNorthId", playerNorthId.trim(), initialSeatAssignments.north)

        val manualScores = manualTableScores()
        val initialManualScores = initialManualScores()
        putIfChanged("manualPlayerEastScore", manualScores.east, initialManualScores.east)
        putIfChanged("manualPlayerSouthScore", manualScores.south, initialManualScores.south)
        putIfChanged("manualPlayerWestScore", manualScores.west, initialManualScores.west)
        putIfChanged("manualPlayerNorthScore", manualScores.north, initialManualScores.north)

        val manualPoints = manualTablePoints()
        val initialManualPoints = initialManualPoints()
        putIfChanged("manualPlayerEastPoints", manualPoints.east, initialManualPoints.east)
        putIfChanged("manualPlayerSouthPoints", manualPoints.south, initialManualPoints.south)
        putIfChanged("manualPlayerWestPoints", manualPoints.west, initialManualPoints.west)
        putIfChanged("manualPlayerNorthPoints", manualPoints.north, initialManualPoints.north)

        if (shouldPersistEffectiveTotals(handPatches.isNotEmpty())) {
            val effectiveScores = effectiveTableScores()
            val effectivePoints = effectiveTablePoints()

            putIfChanged("playerEastScore", effectiveScores.east, initialTable.playerEastScore)
            putIfChanged("playerSouthScore", effectiveScores.south, initialTable.playerSouthScore)
            putIfChanged("playerWestScore", effectiveScores.west, initialTable.playerWestScore)
            putIfChanged("playerNorthScore", effectiveScores.north, initialTable.playerNorthScore)

            putIfChanged("playerEastPoints", effectivePoints.east, initialTable.playerEastPoints)
            putIfChanged("playerSouthPoints", effectivePoints.south, initialTable.playerSouthPoints)
            putIfChanged("playerWestPoints", effectivePoints.west, initialTable.playerWestPoints)
            putIfChanged("playerNorthPoints", effectivePoints.north, initialTable.playerNorthPoints)
        }

        return patch
    }

    fun buildHandPatches(): List<Pair<Int, Map<String, Any?>>> {
        return hands.mapNotNull { draft ->
            val patch = draft.buildPatch()
            if (patch.isEmpty()) null else draft.handId to patch
        }
    }

    fun discard() {
        isCompleted = initialTable.isCompleted
        useTotalsOnly = initialTable.useTotalsOnly
        usePointsCalculation = initialTable.usePointsCalculation
        playerEastId = initialSeatAssignments.east
        playerSouthId = initialSeatAssignments.south
        playerWestId = initialSeatAssignments.west
        playerNorthId = initialSeatAssignments.north
        val scores = initialManualScores()
        playerEastScore = scores.east
        playerSouthScore = scores.south
        playerWestScore = scores.west
        playerNorthScore = scores.north
        val points = initialManualPoints()
        playerEastPoints = points.east
        playerSouthPoints = points.south
        playerWestPoints = points.west
        playerNorthPoints = points.north
        hands.forEach { it.reset() }
    }

    companion object {
        private const val MIN_HAND_SCORE = 8
        private const val LOSER_COUNT = 3

        fun from(
            table: TableState,
            hands: List<TableHand>,
        ): TableManagerEditorState = TableManagerEditorState(table, hands)
    }

    private fun effectiveTableScores(): SeatTextValues {
        return if (useTotalsOnly) {
            manualTableScores()
        } else {
            calculatedHandScoreTotals.ifEmpty { manualTableScores() }
        }
    }

    private fun displayedTableScores(): SeatTextValues {
        return if (!usePointsCalculation) {
            manualTableScores()
        } else {
            effectiveTableScores()
        }
    }

    private fun effectiveTablePoints(): SeatTextValues {
        return when {
            !usePointsCalculation -> manualTablePoints()
            else -> calculatePointsFromScores(effectiveTableScores()).ifEmpty { manualTablePoints() }
        }
    }

    private fun manualTableScores(): SeatTextValues = SeatTextValues(
        east = playerEastScore.trim(),
        south = playerSouthScore.trim(),
        west = playerWestScore.trim(),
        north = playerNorthScore.trim(),
    )

    private fun manualTablePoints(): SeatTextValues = SeatTextValues(
        east = playerEastPoints.trim(),
        south = playerSouthPoints.trim(),
        west = playerWestPoints.trim(),
        north = playerNorthPoints.trim(),
    )

    private fun shouldPersistEffectiveTotals(hasHandChanges: Boolean): Boolean {
        val manualScoresChanged = manualTableScores() != initialManualScores()
        val manualPointsChanged = manualTablePoints() != initialManualPoints()
        if (initialTable.usePointsCalculation != usePointsCalculation) return true
        if (initialTable.useTotalsOnly != useTotalsOnly) return true
        if (useTotalsOnly && manualScoresChanged) return true
        if (!usePointsCalculation && manualPointsChanged) return true
        if (hasHandChanges) return true
        if (playerEastId.trim() != initialSeatAssignments.east) return true
        if (playerSouthId.trim() != initialSeatAssignments.south) return true
        if (playerWestId.trim() != initialSeatAssignments.west) return true
        if (playerNorthId.trim() != initialSeatAssignments.north) return true
        return false
    }

    private fun initialManualScores(): SeatTextValues = SeatTextValues(
        east = initialTable.manualPlayerEastScore.ifBlank { initialTable.playerEastScore.takeIf { initialTable.useTotalsOnly }.orEmpty() },
        south = initialTable.manualPlayerSouthScore.ifBlank { initialTable.playerSouthScore.takeIf { initialTable.useTotalsOnly }.orEmpty() },
        west = initialTable.manualPlayerWestScore.ifBlank { initialTable.playerWestScore.takeIf { initialTable.useTotalsOnly }.orEmpty() },
        north = initialTable.manualPlayerNorthScore.ifBlank { initialTable.playerNorthScore.takeIf { initialTable.useTotalsOnly }.orEmpty() },
    )

    private fun initialManualPoints(): SeatTextValues = SeatTextValues(
        east = initialTable.manualPlayerEastPoints.ifBlank {
            initialTable.playerEastPoints.takeIf { !initialTable.usePointsCalculation }.orEmpty()
        },
        south = initialTable.manualPlayerSouthPoints.ifBlank {
            initialTable.playerSouthPoints.takeIf { !initialTable.usePointsCalculation }.orEmpty()
        },
        west = initialTable.manualPlayerWestPoints.ifBlank {
            initialTable.playerWestPoints.takeIf { !initialTable.usePointsCalculation }.orEmpty()
        },
        north = initialTable.manualPlayerNorthPoints.ifBlank {
            initialTable.playerNorthPoints.takeIf { !initialTable.usePointsCalculation }.orEmpty()
        },
    )

    private fun calculateHandScoreTotals(): SeatTextValues {
        val seats = currentSeatIds() ?: return SeatTextValues.EMPTY
        var totals = SeatIntValues.ZERO
        var hasCalculatedHand = false

        for (hand in hands) {
            if (!hand.isDone && !isCompleted) continue
            val handTotals = calculateHandSeatScores(hand, seats) ?: continue
            totals += handTotals
            hasCalculatedHand = true
        }

        return if (hasCalculatedHand) totals.asTextValues() else SeatTextValues.EMPTY
    }

    private fun calculateCumulativeHandScoreSubtotals(): List<SeatTextValues> =
        cumulativeSubtotalsFor(hands, blankUncalculatedHands = false, allHandsDone = isCompleted)

    /**
     * Running totals for [source]. The hands dialog passes its draft hands, so the subtotals follow
     * unsaved edits. A hand counts only when it is done or [allHandsDone] is true. With
     * [blankUncalculatedHands], a hand that does not count shows no value.
     */
    fun cumulativeSubtotalsFor(
        source: List<HandDraftState>,
        blankUncalculatedHands: Boolean,
        allHandsDone: Boolean,
    ): List<SeatTextValues> {
        val seats = currentSeatIds() ?: return List(source.size) { SeatTextValues.EMPTY }
        var totals = SeatIntValues.ZERO
        var hasCalculatedHand = false

        return source.map { hand ->
            val handTotals = if (hand.isDone || allHandsDone) calculateHandSeatScores(hand, seats) else null
            if (handTotals != null) {
                totals += handTotals
                hasCalculatedHand = true
            }
            when {
                blankUncalculatedHands && handTotals == null -> SeatTextValues.EMPTY
                hasCalculatedHand -> totals.asTextValues()
                else -> SeatTextValues.EMPTY
            }
        }
    }

    private fun calculateHandSeatScores(
        hand: HandDraftState,
        seats: SeatIds,
    ): SeatIntValues? {
        val score = hand.handScore.trim()
        val winnerId = hand.playerWinnerId.trim()
        val loserId = hand.normalizedLoserId

        val base = when {
            hand.isIgnoredForCalculation -> return null
            score.isEmpty() -> return null
            winnerId.isEmpty() && loserId.isEmpty() && score == "0" -> SeatIntValues.ZERO
            winnerId.isEmpty() -> return null
            loserId.isEmpty() -> {
                val handScore = score.toIntOrNull() ?: return null
                val winnerPoints = (handScore + MIN_HAND_SCORE) * LOSER_COUNT
                val loserPoints = -(handScore + MIN_HAND_SCORE)
                seatValuesForWinnerOnly(
                    winnerId = winnerId,
                    seats = seats,
                    winnerValue = winnerPoints,
                    otherValue = loserPoints,
                ) ?: return null
            }
            else -> {
                val handScore = score.toIntOrNull() ?: return null
                val winnerPoints = handScore + (MIN_HAND_SCORE * LOSER_COUNT)
                val loserPoints = -(handScore + MIN_HAND_SCORE)
                seatValuesForWinnerAndLoser(
                    winnerId = winnerId,
                    loserId = loserId,
                    seats = seats,
                    winnerValue = winnerPoints,
                    loserValue = loserPoints,
                    otherValue = -MIN_HAND_SCORE,
                ) ?: return null
            }
        }

        return base.copy(
            east = base.east + parsePenalty(hand.currentPenalty(0)),
            south = base.south + parsePenalty(hand.currentPenalty(1)),
            west = base.west + parsePenalty(hand.currentPenalty(2)),
            north = base.north + parsePenalty(hand.currentPenalty(3)),
        )
    }

    private fun calculatePointsFromScores(scores: SeatTextValues): SeatTextValues {
        return try {
            val ranked = listOf(
                RankedSeat(Seat.EAST, scores.east.toIntOrNull()),
                RankedSeat(Seat.SOUTH, scores.south.toIntOrNull()),
                RankedSeat(Seat.WEST, scores.west.toIntOrNull()),
                RankedSeat(Seat.NORTH, scores.north.toIntOrNull()),
            )
            if (ranked.any { it.score == null }) return SeatTextValues.EMPTY

            val sorted = ranked.sortedByDescending { it.score ?: Int.MIN_VALUE }
            val assigned = when {
                sorted[0].score == sorted[1].score && sorted[1].score == sorted[2].score && sorted[2].score == sorted[3].score ->
                    listOf("1,75", "1,75", "1,75", "1,75")
                sorted[0].score == sorted[1].score && sorted[1].score == sorted[2].score ->
                    listOf("2,33", "2,33", "2,33", "0")
                sorted[1].score == sorted[2].score && sorted[2].score == sorted[3].score ->
                    listOf("4", "1", "1", "1")
                sorted[0].score == sorted[1].score && sorted[2].score == sorted[3].score ->
                    listOf("3", "3", "0,5", "0,5")
                sorted[0].score == sorted[1].score ->
                    listOf("3", "3", "1", "0")
                sorted[1].score == sorted[2].score ->
                    listOf("4", "1,5", "1,5", "0")
                sorted[2].score == sorted[3].score ->
                    listOf("4", "2", "0,5", "0,5")
                else ->
                    listOf("4", "2", "1", "0")
            }

            val bySeat = sorted.mapIndexed { index, rankedSeat -> rankedSeat.seat to assigned[index] }.toMap()
            SeatTextValues(
                east = bySeat[Seat.EAST].orEmpty(),
                south = bySeat[Seat.SOUTH].orEmpty(),
                west = bySeat[Seat.WEST].orEmpty(),
                north = bySeat[Seat.NORTH].orEmpty(),
            )
        } catch (_: Exception) {
            SeatTextValues.EMPTY
        }
    }

    private fun currentSeatIds(): SeatIds? {
        val east = playerEastId.trim()
        val south = playerSouthId.trim()
        val west = playerWestId.trim()
        val north = playerNorthId.trim()
        if (east.isEmpty() || south.isEmpty() || west.isEmpty() || north.isEmpty()) return null
        if (setOf(east, south, west, north).size != 4) return null
        return SeatIds(east = east, south = south, west = west, north = north)
    }

    private fun seatValuesForWinnerOnly(
        winnerId: String,
        seats: SeatIds,
        winnerValue: Int,
        otherValue: Int,
    ): SeatIntValues? {
        return when (winnerId) {
            seats.east -> SeatIntValues(winnerValue, otherValue, otherValue, otherValue)
            seats.south -> SeatIntValues(otherValue, winnerValue, otherValue, otherValue)
            seats.west -> SeatIntValues(otherValue, otherValue, winnerValue, otherValue)
            seats.north -> SeatIntValues(otherValue, otherValue, otherValue, winnerValue)
            else -> null
        }
    }

    private fun seatValuesForWinnerAndLoser(
        winnerId: String,
        loserId: String,
        seats: SeatIds,
        winnerValue: Int,
        loserValue: Int,
        otherValue: Int,
    ): SeatIntValues? {
        if (winnerId == loserId) return null
        if (winnerId !in seats.all || loserId !in seats.all) return null
        return SeatIntValues(
            east = when (seats.east) {
                winnerId -> winnerValue
                loserId -> loserValue
                else -> otherValue
            },
            south = when (seats.south) {
                winnerId -> winnerValue
                loserId -> loserValue
                else -> otherValue
            },
            west = when (seats.west) {
                winnerId -> winnerValue
                loserId -> loserValue
                else -> otherValue
            },
            north = when (seats.north) {
                winnerId -> winnerValue
                loserId -> loserValue
                else -> otherValue
            },
        )
    }

    private fun parsePenalty(value: String): Int = value
        .split(';')
        .sumOf { it.trim().toIntOrNull() ?: 0 }
}

@Stable
internal class PenaltyDraftState(
    east: String = "",
    south: String = "",
    west: String = "",
    north: String = "",
) {
    var east by mutableStateOf(east)
    var south by mutableStateOf(south)
    var west by mutableStateOf(west)
    var north by mutableStateOf(north)

    fun value(seatIndex: Int): String = when (seatIndex) {
        0 -> east
        1 -> south
        2 -> west
        else -> north
    }

    fun setValue(seatIndex: Int, value: String) {
        when (seatIndex) {
            0 -> east = value
            1 -> south = value
            2 -> west = value
            else -> north = value
        }
    }
}

@Stable
internal class HandDraftState private constructor(
    private val initial: TableHand,
) {
    val handId: Int = initial.handId

    var playerWinnerId by mutableStateOf(initial.playerWinnerId)
    var playerLooserId by mutableStateOf(initial.playerLooserId)
    var handScore by mutableStateOf(initial.handScore)
    var isChickenHand by mutableStateOf(initial.isChickenHand)
    var isDone by mutableStateOf(initial.isDone)
    var playerEastPenalty by mutableStateOf(initial.playerEastPenalty)
    var playerSouthPenalty by mutableStateOf(initial.playerSouthPenalty)
    var playerWestPenalty by mutableStateOf(initial.playerWestPenalty)
    var playerNorthPenalty by mutableStateOf(initial.playerNorthPenalty)
    val penaltyRows = mutableStateListOf<PenaltyDraftState>()
    private var penaltyRowsEdited by mutableStateOf(false)
    private var resultFieldsTouched by mutableStateOf(false)

    init {
        penaltyRows += initialPenaltyRows()
    }

    val hasLoserSelected: Boolean
        get() = normalizedLoserId.isNotEmpty()

    val selectedWinnerPlayerId: Int?
        get() = playerWinnerId.trim().toIntOrNull()

    val selectedLoserPlayerId: Int?
        get() = normalizedLoserId.toIntOrNull()

    val normalizedLoserId: String
        get() = playerLooserId.trim().takeUnless { it == "-" }.orEmpty()

    val showValidationError: Boolean
        get() = resultFieldsTouched && isResultSelectionInvalid

    val validationErrorMessage: String?
        get() = when {
            !isResultSelectionInvalid -> null
            isCompletelyEmpty -> null
            else -> "This hand is invalid and cannot be marked as done or completed."
        }

    val validationDetailMessages: List<String>
        get() {
            if (!isResultSelectionInvalid || isCompletelyEmpty) return emptyList()

            val winnerId = playerWinnerId.trim()
            val loserId = normalizedLoserId
            val score = handScore.trim()
            val parsedScore = score.toIntOrNull()
            val messages = mutableListOf<String>()

            if (winnerId.isEmpty() || score.isEmpty()) {
                messages += "There are missing fields."
            }

            if (winnerId.isEmpty() && loserId.isNotEmpty()) {
                messages += "A loser cannot be selected without a winner."
            }

            if (score.isNotEmpty() && parsedScore == null) {
                messages += "The score must be a whole number."
            }

            if (parsedScore != null && parsedScore < MIN_HAND_SCORE) {
                messages += "The minimum score to win a hand is 8."
            }

            if (parsedScore != null && isChickenHand && parsedScore > MAX_CHICKEN_HAND_SCORE) {
                messages += "A chicken hand cannot score more than 12 points."
            }

            return messages.distinct()
        }

    val hasWinnerChanged: Boolean get() = playerWinnerId.trim() != initial.playerWinnerId
    val hasLoserChanged: Boolean get() = normalizedLoserId != initial.playerLooserId
    val hasScoreChanged: Boolean get() = handScore.trim() != initial.handScore
    val hasChickenHandChanged: Boolean get() = isChickenHand != initial.isChickenHand
    val hasDoneChanged: Boolean get() = isDone != initial.isDone
    val hasEastPenaltyChanged: Boolean get() = currentPenalty(0) != initial.playerEastPenalty
    val hasSouthPenaltyChanged: Boolean get() = currentPenalty(1) != initial.playerSouthPenalty
    val hasWestPenaltyChanged: Boolean get() = currentPenalty(2) != initial.playerWestPenalty
    val hasNorthPenaltyChanged: Boolean get() = currentPenalty(3) != initial.playerNorthPenalty

    /** A penalty cell shows as changed only when its text differs from the saved text. An empty new cell does not. */
    fun isPenaltyCellChanged(rowIndex: Int, seatIndex: Int): Boolean {
        val current = penaltyRows.getOrNull(rowIndex)?.value(seatIndex)?.trim().orEmpty()
        val saved = initialPenaltyRows().getOrNull(rowIndex)?.value(seatIndex)?.trim().orEmpty()
        return current != saved
    }

    /** True when the hand has no data at all. It is not done and has no penalty. */
    val isBlank: Boolean
        get() = !isDone && !isChickenHand &&
            playerWinnerId.trim().isEmpty() && normalizedLoserId.isEmpty() && handScore.trim().isEmpty() &&
            penaltyRows.none { row -> (0..3).any { row.value(it).isNotBlank() } }

    val isIgnoredForCalculation: Boolean
        get() = isResultSelectionInvalid || isCompletelyEmpty

    private val isCompletelyEmpty: Boolean
        get() = playerWinnerId.trim().isEmpty() && normalizedLoserId.isEmpty() && handScore.trim().isEmpty() &&
            !isChickenHand

    val isResultSelectionInvalid: Boolean
        get() {
            val winnerId = playerWinnerId.trim()
            val loserId = normalizedLoserId
            val score = handScore.trim()

            if (winnerId.isEmpty() && loserId.isEmpty() && score.isEmpty()) return isChickenHand
            if (winnerId.isEmpty() && loserId.isEmpty() && score == "0") return isChickenHand
            if (winnerId.isEmpty() && loserId.isNotEmpty()) return true
            if (winnerId.isEmpty() || score.isEmpty()) return true

            val parsedScore = score.toIntOrNull() ?: return true
            if (parsedScore < MIN_HAND_SCORE) return true
            if (isChickenHand && parsedScore > MAX_CHICKEN_HAND_SCORE) return true
            return false
        }

    fun setWinnerPlayerId(value: String) {
        val trimmed = value.trim().takeUnless { it == "-" }.orEmpty()
        playerWinnerId = trimmed
        if (trimmed.isNotEmpty() && normalizedLoserId == trimmed) {
            playerLooserId = "-"
            isChickenHand = false
        }
    }

    fun setLoserPlayerId(value: String) {
        playerLooserId = value.trim().ifBlank { "-" }
        if (!hasLoserSelected) {
            isChickenHand = false
        }
        markResultFieldsTouched()
    }

    fun updateHandScore(value: String) {
        handScore = value.toSignedIntegerInput()
        markResultFieldsTouched()
    }

    fun updateChickenHand(value: Boolean) {
        isChickenHand = value
        markResultFieldsTouched()
    }

    fun updateDoneState(value: Boolean) {
        if (!value) {
            isDone = false
            markResultFieldsTouched()
            return
        }
        if (isResultSelectionInvalid) {
            isDone = false
            markResultFieldsTouched()
            return
        }
        // A done hand with no winner, loser or score is a draw. It scores zero.
        if (playerWinnerId.trim().isEmpty() && normalizedLoserId.isEmpty() && handScore.trim().isEmpty()) {
            handScore = "0"
        }
        removeEmptyPenaltyRows()
        isDone = true
        markResultFieldsTouched()
    }

    fun markResultFieldsTouched() {
        resultFieldsTouched = true
    }

    fun updatePenaltyValue(rowIndex: Int, seatIndex: Int, value: String) {
        penaltyRows[rowIndex].setValue(seatIndex, value.toSignedIntegerInput())
        penaltyRowsEdited = true
    }

    /** Adds a penalty row and returns its index. If an empty row already exists, it returns that row instead. */
    fun addPenaltyRow(): Int {
        val emptyIndex = penaltyRows.indexOfFirst { row -> (0..3).all { row.value(it).isBlank() } }
        if (emptyIndex >= 0) return emptyIndex
        penaltyRows += PenaltyDraftState()
        penaltyRowsEdited = true
        return penaltyRows.lastIndex
    }

    fun removePenaltyRow(rowIndex: Int) {
        penaltyRows.removeAt(rowIndex)
        penaltyRowsEdited = true
    }

    private fun removeEmptyPenaltyRows() {
        val removed = penaltyRows.removeAll { row -> (0..3).all { row.value(it).isBlank() } }
        if (removed) penaltyRowsEdited = true
    }

    /** One row for each ";" separated part of the saved penalties. No row when the hand has no penalty. */
    private fun initialPenaltyRows(): List<PenaltyDraftState> {
        val parts = listOf(
            initial.playerEastPenalty,
            initial.playerSouthPenalty,
            initial.playerWestPenalty,
            initial.playerNorthPenalty,
        ).map { value -> value.split(';').map { it.trim() } }
        val rowCount = parts.maxOf { it.size }
        val hasAny = parts.any { seat -> seat.any { it.isNotEmpty() } }
        if (!hasAny) return emptyList()
        return List(rowCount) { row ->
            PenaltyDraftState(
                east = parts[0].getOrElse(row) { "" },
                south = parts[1].getOrElse(row) { "" },
                west = parts[2].getOrElse(row) { "" },
                north = parts[3].getOrElse(row) { "" },
            )
        }
    }

    fun reset() {
        playerWinnerId = initial.playerWinnerId
        playerLooserId = initial.playerLooserId.ifBlank { "-" }
        handScore = initial.handScore
        isChickenHand = initial.isChickenHand
        isDone = initial.isDone
        playerEastPenalty = initial.playerEastPenalty
        playerSouthPenalty = initial.playerSouthPenalty
        playerWestPenalty = initial.playerWestPenalty
        playerNorthPenalty = initial.playerNorthPenalty
        penaltyRows.clear()
        penaltyRows += initialPenaltyRows()
        penaltyRowsEdited = false
        resultFieldsTouched = false
    }

    fun snapshot(): TableHand = TableHand(
        handId = handId,
        playerWinnerId = playerWinnerId.trim(),
        playerLooserId = normalizedLoserId,
        handScore = handScore.trim(),
        isChickenHand = isChickenHand,
        isDone = isDone,
        playerEastPenalty = currentPenalty(0),
        playerSouthPenalty = currentPenalty(1),
        playerWestPenalty = currentPenalty(2),
        playerNorthPenalty = currentPenalty(3),
    )

    fun copyValuesFrom(other: HandDraftState) {
        playerWinnerId = other.playerWinnerId
        playerLooserId = other.playerLooserId
        handScore = other.handScore
        isChickenHand = other.isChickenHand
        isDone = other.isDone
        playerEastPenalty = other.playerEastPenalty
        playerSouthPenalty = other.playerSouthPenalty
        playerWestPenalty = other.playerWestPenalty
        playerNorthPenalty = other.playerNorthPenalty
        penaltyRows.clear()
        other.penaltyRows.forEach { row ->
            penaltyRows += PenaltyDraftState(row.east, row.south, row.west, row.north)
        }
        penaltyRowsEdited = other.penaltyRowsEdited
        resultFieldsTouched = other.resultFieldsTouched
    }

    fun buildPatch(): Map<String, Any?> {
        val patch = linkedMapOf<String, Any?>()

        fun putIfChanged(key: String, current: Any?, initialValue: Any?) {
            if (current != initialValue) patch[key] = current
        }

        putIfChanged("playerWinnerId", playerWinnerId.trim(), initial.playerWinnerId)
        putIfChanged("playerLooserId", normalizedLoserId, initial.playerLooserId)
        putIfChanged("handScore", handScore.trim(), initial.handScore)
        putIfChanged("isChickenHand", isChickenHand, initial.isChickenHand)
        putIfChanged("isDone", isDone, initial.isDone)
        putIfChanged("playerEastPenalty", currentPenalty(0), initial.playerEastPenalty)
        putIfChanged("playerSouthPenalty", currentPenalty(1), initial.playerSouthPenalty)
        putIfChanged("playerWestPenalty", currentPenalty(2), initial.playerWestPenalty)
        putIfChanged("playerNorthPenalty", currentPenalty(3), initial.playerNorthPenalty)

        return patch
    }

    /** The penalties of one seat as saved text. Several rows join with ";". */
    fun currentPenalty(seatIndex: Int): String {
        if (!penaltyRowsEdited) return when (seatIndex) {
            0 -> playerEastPenalty.trim()
            1 -> playerSouthPenalty.trim()
            2 -> playerWestPenalty.trim()
            else -> playerNorthPenalty.trim()
        }
        // Empty rows at the end carry no data, so they do not change the saved text.
        return penaltyRows.joinToString(";") { it.value(seatIndex).trim() }
    }

    companion object {
        fun from(hand: TableHand): HandDraftState = HandDraftState(hand).also { draft ->
            if (draft.playerLooserId.trim().isEmpty()) {
                draft.playerLooserId = "-"
            }
        }
    }
}

private data class NormalizedSeats(
    val east: String,
    val south: String,
    val west: String,
    val north: String,
)

private data class SeatIds(
    val east: String,
    val south: String,
    val west: String,
    val north: String,
) {
    val all: Set<String> = setOf(east, south, west, north)
}

private data class SeatIntValues(
    val east: Int,
    val south: Int,
    val west: Int,
    val north: Int,
) {
    operator fun plus(other: SeatIntValues): SeatIntValues = SeatIntValues(
        east = east + other.east,
        south = south + other.south,
        west = west + other.west,
        north = north + other.north,
    )

    fun asTextValues(): SeatTextValues = SeatTextValues(
        east = east.toString(),
        south = south.toString(),
        west = west.toString(),
        north = north.toString(),
    )

    companion object {
        val ZERO = SeatIntValues(0, 0, 0, 0)
    }
}

internal data class SeatTextValues(
    val east: String,
    val south: String,
    val west: String,
    val north: String,
) {
    fun bySeat(seatIndex: Int): String = when (seatIndex) {
        0 -> east
        1 -> south
        2 -> west
        3 -> north
        else -> ""
    }

    inline fun ifEmpty(fallback: () -> SeatTextValues): SeatTextValues =
        if (this == EMPTY) fallback() else this

    companion object {
        val EMPTY = SeatTextValues("", "", "", "")
        val ZERO = SeatTextValues("0", "0", "0", "0")
    }
}

private enum class Seat {
    EAST,
    SOUTH,
    WEST,
    NORTH,
}

private data class RankedSeat(
    val seat: Seat,
    val score: Int?,
)

private typealias SeatAssignments = NormalizedSeats

private fun sanitizeSeatAssignments(
    playerIds: List<Int>,
    east: String,
    south: String,
    west: String,
    north: String,
): SeatAssignments {
    fun parseValid(value: String): Int? {
        val id = value.trim().toIntOrNull() ?: return null
        return id.takeIf { it in playerIds }
    }

    return SeatAssignments(
        east = parseValid(east)?.toString().orEmpty(),
        south = parseValid(south)?.toString().orEmpty(),
        west = parseValid(west)?.toString().orEmpty(),
        north = parseValid(north)?.toString().orEmpty(),
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun PlayerDropdown(
    modifier: Modifier = Modifier,
    label: String?,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    playerIds: List<Int>,
    value: String,
    enabled: Boolean,
    onChange: (String) -> Unit,
    onDismiss: () -> Unit = {},
    onFocusLost: () -> Unit = {},
    playerNamesById: Map<Int, String> = emptyMap(),
    showPlayerId: Boolean = false,
    optionSuffixById: Map<Int, String> = emptyMap(),
    excludedPlayerIds: Set<Int> = emptySet(),
    emptyOptionLabel: String? = null,
    isChanged: Boolean = false,
    focusRequester: FocusRequester? = null,
    onNavigateLeft: (() -> Boolean)? = null,
    onNavigateRight: (() -> Boolean)? = null,
    onNavigateUp: (() -> Boolean)? = null,
    onNavigateDown: (() -> Boolean)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    var lastDismissMark by remember { mutableStateOf<TimeMark?>(null) }
    val anchorFocusRequester = remember { FocusRequester() }
    data class DropdownOption(
        val value: String,
        val label: String,
        val onSelect: () -> Unit,
    )

    val trimmed = value.trim()
    val selectedId = trimmed.toIntOrNull()
    fun playerLabel(id: Int): String {
        val name = playerNamesById[id] ?: "Player $id"
        return if (showPlayerId) "$id - $name" else name
    }
    val displayValue = when {
        trimmed.isBlank() || selectedId == null && trimmed == "-" -> emptyOptionLabel.orEmpty()
        selectedId == null -> trimmed
        else -> playerLabel(selectedId)
    }
    val options = buildList {
        emptyOptionLabel?.let { emptyLabel ->
            add(
                DropdownOption(
                    value = emptyLabel,
                    label = emptyLabel,
                    onSelect = {
                        expanded = false
                        onChange(emptyLabel)
                        onDismiss()
                    },
                ),
            )
        }
        playerIds.forEach { id ->
            if (id in excludedPlayerIds) return@forEach
            val suffix = optionSuffixById[id]
            val base = playerLabel(id)
            add(
                DropdownOption(
                    value = id.toString(),
                    label = if (suffix.isNullOrBlank()) base else "$base ($suffix)",
                    onSelect = {
                        expanded = false
                        onChange(id.toString())
                        onDismiss()
                    },
                ),
            )
        }
    }
    val selectedOptionIndex = options.indexOfFirst { it.value == trimmed }.takeIf { it >= 0 } ?: 0
    var activeOptionIndex by remember { mutableStateOf(selectedOptionIndex) }

    fun openMenu() {
        activeOptionIndex = selectedOptionIndex
        expanded = true
        anchorFocusRequester.requestFocus()
    }

    fun moveActive(delta: Int) {
        if (options.isEmpty()) return
        val nextIndex = (activeOptionIndex + delta).coerceIn(0, options.lastIndex)
        activeOptionIndex = nextIndex
    }

    fun moveAway(key: Key): Boolean {
        if (expanded) expanded = false
        return when (key) {
            Key.DirectionLeft -> onNavigateLeft?.invoke() ?: false
            Key.DirectionRight -> onNavigateRight?.invoke() ?: false
            Key.DirectionUp -> onNavigateUp?.invoke() ?: false
            Key.DirectionDown -> onNavigateDown?.invoke() ?: false
            else -> false
        }
    }

    LaunchedEffect(expanded, selectedOptionIndex, options.size) {
        if (!expanded) {
            activeOptionIndex = selectedOptionIndex
        } else {
            activeOptionIndex = activeOptionIndex.coerceIn(0, options.lastIndex.coerceAtLeast(0))
        }
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        // The menu remains inspectable when the field is disabled. Its options stay disabled below.
        onExpandedChange = { shouldExpand ->
            // The popup closes on the press and the anchor toggles on the release.
            // Ignore that release so a click on the open anchor does not reopen the menu.
            if (shouldExpand && (lastDismissMark?.elapsedNow() ?: 1.seconds) < 400.milliseconds) {
                return@ExposedDropdownMenuBox
            }
            expanded = shouldExpand
        },
        modifier = modifier,
    ) {
        CompactOutlinedTextField(
            modifier = Modifier
                .menuAnchor(
                    type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                    enabled = true,
                )
                .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
                .focusRequester(anchorFocusRequester)
                .onFocusChanged { if (!it.isFocused) onFocusLost() }
                .onPreviewKeyEvent { e: KeyEvent ->
                    if (!enabled || e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (e.key) {
                        Key.DirectionDown -> if (expanded) {
                            moveActive(1)
                            true
                        } else {
                            openMenu()
                            true
                        }

                        Key.DirectionUp -> if (expanded) {
                            moveActive(-1)
                            true
                        } else {
                            openMenu()
                            true
                        }

                        Key.DirectionLeft, Key.DirectionRight -> moveAway(e.key)

                        Key.Enter, Key.NumPadEnter, Key.Spacebar -> {
                            if (expanded) {
                                options.getOrNull(activeOptionIndex)?.onSelect()
                            } else {
                                openMenu()
                            }
                            true
                        }
                        Key.Escape -> {
                            if (expanded) {
                                expanded = false
                                true
                            } else {
                                false
                            }
                        }
                        else -> false
                    }
                }
                .fillMaxWidth(),
            value = displayValue,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = label?.let { { Text(it) } },
            textStyle = textStyle,
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
        colors = if (enabled && isChanged) {
            androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = MaterialTheme.colorScheme.tertiary,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedLabelColor = MaterialTheme.colorScheme.tertiary,
            )
        } else {
            ExposedDropdownMenuDefaults.outlinedTextFieldColors()
        },
        )
        // A non-focusable popup lets the click reach another dropdown below it.
        // A focusable popup would swallow that click and need a second one.
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                lastDismissMark = TimeSource.Monotonic.markNow()
                expanded = false
                onDismiss()
            },
            modifier = Modifier.exposedDropdownSize(),
            properties = PopupProperties(focusable = false, dismissOnClickOutside = true),
        ) {
            Column(
                modifier = Modifier
                    .onPreviewKeyEvent { e ->
                        if (!enabled || !expanded || e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (e.key) {
                            Key.DirectionDown -> {
                                if (activeOptionIndex < options.lastIndex) {
                                    moveActive(1)
                                    true
                                } else {
                                    moveAway(Key.DirectionDown)
                                }
                            }

                            Key.DirectionUp -> {
                                if (activeOptionIndex > 0) {
                                    moveActive(-1)
                                    true
                                } else {
                                    moveAway(Key.DirectionUp)
                                }
                            }

                            Key.DirectionLeft, Key.DirectionRight -> moveAway(e.key)
                            Key.Enter, Key.NumPadEnter, Key.Spacebar -> {
                                options.getOrNull(activeOptionIndex)?.onSelect()
                                true
                            }

                            Key.Escape -> {
                                expanded = false
                                true
                            }

                            else -> false
                        }
                    },
            ) {
                options.forEachIndexed { index, option ->
                    DropdownMenuItem(
                        modifier = Modifier.background(
                            if (index == activeOptionIndex) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                Color.Transparent
                            },
                        ),
                        text = { Text(option.label) },
                        enabled = enabled,
                        onClick = {
                            activeOptionIndex = index
                            option.onSelect()
                            anchorFocusRequester.requestFocus()
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                    )
                }
            }
        }
    }
}
