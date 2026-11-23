package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CropLandscape
import androidx.compose.material.icons.filled.CropPortrait
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.domain.model.IdCardSheetLayout
import com.etologic.mahjongtournamentsuite.presentation.telemetry.TrackedDialog
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import mahjongtournamentsuite.adminapp.generated.resources.Res
import mahjongtournamentsuite.adminapp.generated.resources.add_a_sheet
import mahjongtournamentsuite.adminapp.generated.resources.add_person
import mahjongtournamentsuite.adminapp.generated.resources.back_sheet
import mahjongtournamentsuite.adminapp.generated.resources.cancel
import mahjongtournamentsuite.adminapp.generated.resources.card_back
import mahjongtournamentsuite.adminapp.generated.resources.card_back_help
import mahjongtournamentsuite.adminapp.generated.resources.card_back_none
import mahjongtournamentsuite.adminapp.generated.resources.card_back_rounds
import mahjongtournamentsuite.adminapp.generated.resources.card_back_same_as_front
import mahjongtournamentsuite.adminapp.generated.resources.card_orientation
import mahjongtournamentsuite.adminapp.generated.resources.card_size
import mahjongtournamentsuite.adminapp.generated.resources.cards_per_sheet
import mahjongtournamentsuite.adminapp.generated.resources.create_id_cards
import mahjongtournamentsuite.adminapp.generated.resources.create_pdf
import mahjongtournamentsuite.adminapp.generated.resources.cut_marks
import mahjongtournamentsuite.adminapp.generated.resources.cut_marks_help
import mahjongtournamentsuite.adminapp.generated.resources.double_sided_printing
import mahjongtournamentsuite.adminapp.generated.resources.double_sided_printing_help
import mahjongtournamentsuite.adminapp.generated.resources.each_staff_name_can_have_up_to
import mahjongtournamentsuite.adminapp.generated.resources.extra_sheets
import mahjongtournamentsuite.adminapp.generated.resources.front_sheet
import mahjongtournamentsuite.adminapp.generated.resources.height_mm
import mahjongtournamentsuite.adminapp.generated.resources.id_cards_proportion_info
import mahjongtournamentsuite.adminapp.generated.resources.landscape
import mahjongtournamentsuite.adminapp.generated.resources.portrait
import mahjongtournamentsuite.adminapp.generated.resources.remove_a_sheet
import mahjongtournamentsuite.adminapp.generated.resources.remove_person
import mahjongtournamentsuite.adminapp.generated.resources.restore_settings
import mahjongtournamentsuite.adminapp.generated.resources.staff_name_placeholder
import mahjongtournamentsuite.adminapp.generated.resources.staff_names
import mahjongtournamentsuite.adminapp.generated.resources.staff_names_help
import mahjongtournamentsuite.adminapp.generated.resources.staff_sheets
import mahjongtournamentsuite.adminapp.generated.resources.staff_sheets_help
import mahjongtournamentsuite.adminapp.generated.resources.substitute_sheets
import mahjongtournamentsuite.adminapp.generated.resources.substitute_sheets_help
import mahjongtournamentsuite.adminapp.generated.resources.the_staff_names_need_more_sheets
import mahjongtournamentsuite.adminapp.generated.resources.the_width_and_the_height_keep
import mahjongtournamentsuite.adminapp.generated.resources.use_range
import mahjongtournamentsuite.adminapp.generated.resources.width_mm
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt
import kotlin.math.roundToLong

private fun Double.oneDecimal(): String {
    val tenths = (this * 10).roundToLong()
    return "${tenths / 10}.${tenths % 10}"
}

private fun mmToScalePercent(widthMm: Double): Double = widthMm / (IdCardSheetLayout.CARD_WIDTH_PT * IdCardSheetLayout.MM_PER_PT) * 100

private fun mmHeightToScalePercent(heightMm: Double): Double = heightMm / (IdCardSheetLayout.CARD_HEIGHT_PT * IdCardSheetLayout.MM_PER_PT) * 100

/** A chain icon between the width and the height fields. Its tooltip explains the fixed proportion. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProportionLinkIcon(originalWidthMm: Double, originalHeightMm: Double) {
    val ratio = (originalWidthMm / originalHeightMm * 100).roundToLong() / 100.0
    AppTooltipBox(stringResource(Res.string.id_cards_proportion_info, ratio.toString(), originalWidthMm.oneDecimal(), originalHeightMm.oneDecimal())) {
        Icon(
            Icons.Default.Link,
            contentDescription = stringResource(Res.string.the_width_and_the_height_keep),
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A slider with its label on the left and the current value written inside the thumb. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LabeledSlider(
    icon: ImageVector,
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    thumbText: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Text(label, modifier = Modifier.padding(start = 8.dp).width(110.dp))
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.weight(1f),
            thumb = {
                Box(
                    modifier = Modifier
                        .height(24.dp)
                        .widthIn(min = 32.dp)
                        .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium)
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        thumbText,
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            },
        )
    }
}

/** Lets the user choose how the ID cards are placed on A4 sheets. */
@Composable
fun IdCardsExportDialog(
    onExport: (IdCardSheetLayout) -> Unit,
    onDismiss: () -> Unit,
    initialLayout: IdCardSheetLayout = IdCardSheetLayout(),
    /** The names of the staff saved the last time. They arrive after the dialog opens. */
    savedStaffNames: List<String> = emptyList(),
) {
    var layout by remember { mutableStateOf(initialLayout) }
    LaunchedEffect(savedStaffNames) {
        // The saved names fill the list only while the user has not written any.
        if (savedStaffNames.isNotEmpty() && layout.cleanStaffNames().isEmpty()) layout = layout.copy(staffNames = savedStaffNames)
    }
    var widthText by remember { mutableStateOf(layout.cardWidthMm().oneDecimal()) }
    var heightText by remember { mutableStateOf(layout.cardHeightMm().oneDecimal()) }
    val maxScale = layout.maxScalePercent()
    val minScale = IdCardSheetLayout.MIN_SCALE_PERCENT
    val capacity = layout.capacity()
    val effective = layout.copy(cardsPerSheet = layout.cardsPerSheet.coerceIn(1, capacity.coerceAtLeast(1)))
    val minWidthMm = IdCardSheetLayout(scalePercent = minScale).cardWidthMm()
    val maxWidthMm = IdCardSheetLayout(scalePercent = maxScale).cardWidthMm()
    val minHeightMm = IdCardSheetLayout(scalePercent = minScale).cardHeightMm()
    val maxHeightMm = IdCardSheetLayout(scalePercent = maxScale).cardHeightMm()

    fun applyScale(scale: Double, updateWidthText: Boolean = true, updateHeightText: Boolean = true) {
        layout = layout.copy(scalePercent = scale.coerceIn(minScale, maxScale))
        if (updateWidthText) widthText = layout.cardWidthMm().oneDecimal()
        if (updateHeightText) heightText = layout.cardHeightMm().oneDecimal()
    }

    fun applyOrientation(landscape: Boolean) {
        layout = layout.copy(landscape = landscape)
        applyScale(layout.scalePercent)
    }

    val widthValue = widthText.replace(',', '.').toDoubleOrNull()
    val heightValue = heightText.replace(',', '.').toDoubleOrNull()
    val widthInvalid = widthValue == null || widthValue < minWidthMm - 0.05 || widthValue > maxWidthMm + 0.05
    val heightInvalid = heightValue == null || heightValue < minHeightMm - 0.05 || heightValue > maxHeightMm + 0.05

    val staffNamesInvalid = layout.staffSheets > 0 && layout.cleanStaffNames().any { it.length > IdCardSheetLayout.MAX_STAFF_NAME_LENGTH }
    // Names written before the staff sheets went back to 0 are not printed.
    val exported = if (effective.staffSheets == 0) effective.copy(staffNames = emptyList()) else effective

    val compact = LocalCompactLayout.current

    TrackedDialog("CreateIdCards") {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(Res.string.create_id_cards), modifier = Modifier.weight(1f, fill = false))
                    val restore = {
                        // The names of the staff are data, not settings, so they stay.
                        layout = IdCardSheetLayout(staffNames = layout.staffNames)
                        widthText = layout.cardWidthMm().oneDecimal()
                        heightText = layout.cardHeightMm().oneDecimal()
                    }
                    val restoreLabel = stringResource(Res.string.restore_settings)
                    if (compact) {
                        // On a phone the title takes the width, so the button keeps only its icon.
                        AppTooltipBox(restoreLabel) {
                            FocusedIconButton(onClick = restore) {
                                Icon(Icons.Default.RestartAlt, contentDescription = restoreLabel)
                            }
                        }
                    } else {
                        FocusedTextButton(onClick = restore) {
                            Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(restoreLabel, modifier = Modifier.padding(start = MtsTheme.spacing.sm))
                        }
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier.verticalScrollWithIndicators(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (capacity > 1) {
                        LabeledSlider(
                            icon = Icons.Default.GridView,
                            label = stringResource(Res.string.cards_per_sheet),
                            value = effective.cardsPerSheet.toFloat(),
                            onValueChange = { layout = layout.copy(cardsPerSheet = it.roundToInt()) },
                            valueRange = 1f..capacity.toFloat(),
                            steps = capacity - 2,
                            thumbText = effective.cardsPerSheet.toString(),
                        )
                    }
                    LabeledSlider(
                        icon = Icons.Default.AspectRatio,
                        label = stringResource(Res.string.card_size),
                        value = layout.scalePercent.toFloat(),
                        onValueChange = { applyScale((it * 10).roundToLong() / 10.0) },
                        valueRange = minScale.toFloat()..maxScale.toFloat(),
                        steps = 0,
                        thumbText = "${layout.scalePercent.roundToInt()}%",
                    )
                    val sizeFields: @Composable RowScope.() -> Unit = {
                        OutlinedTextField(
                            value = widthText,
                            onValueChange = { text ->
                                widthText = text
                                val mm = text.replace(',', '.').toDoubleOrNull()
                                if (mm != null && mm >= minWidthMm - 0.05 && mm <= maxWidthMm + 0.05) {
                                    applyScale(mmToScalePercent(mm), updateWidthText = false)
                                }
                            },
                            label = { Text(stringResource(Res.string.width_mm)) },
                            singleLine = true,
                            isError = widthInvalid,
                            supportingText = if (widthInvalid) {
                                { Text(stringResource(Res.string.use_range, minWidthMm.oneDecimal(), maxWidthMm.oneDecimal())) }
                            } else {
                                null
                            },
                            modifier = Modifier.weight(1f),
                        )
                        ProportionLinkIcon(
                            originalWidthMm = IdCardSheetLayout().cardWidthMm(),
                            originalHeightMm = IdCardSheetLayout().cardHeightMm(),
                        )
                        OutlinedTextField(
                            value = heightText,
                            onValueChange = { text ->
                                heightText = text
                                val mm = text.replace(',', '.').toDoubleOrNull()
                                if (mm != null && mm >= minHeightMm - 0.05 && mm <= maxHeightMm + 0.05) {
                                    applyScale(mmHeightToScalePercent(mm), updateHeightText = false)
                                }
                            },
                            label = { Text(stringResource(Res.string.height_mm)) },
                            singleLine = true,
                            isError = heightInvalid,
                            supportingText = if (heightInvalid) {
                                { Text(stringResource(Res.string.use_range, minHeightMm.oneDecimal(), maxHeightMm.oneDecimal())) }
                            } else {
                                null
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    // Orientation has its own line, with an icon and a title like the sliders above.
                    val orientation: @Composable () -> Unit = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ScreenRotation, contentDescription = null, modifier = Modifier.size(20.dp))
                            Text(stringResource(Res.string.card_orientation), modifier = Modifier.padding(start = 8.dp).width(110.dp))
                            FilledTonalIconToggleButton(
                                checked = !layout.landscape,
                                onCheckedChange = { applyOrientation(false) },
                            ) {
                                Icon(Icons.Default.CropPortrait, contentDescription = stringResource(Res.string.portrait))
                            }
                            FilledTonalIconToggleButton(
                                checked = layout.landscape,
                                onCheckedChange = { applyOrientation(true) },
                            ) {
                                Icon(Icons.Default.CropLandscape, contentDescription = stringResource(Res.string.landscape))
                            }
                        }
                    }
                    if (compact) {
                        // On a phone the fields need the whole width.
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { sizeFields() }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Default.Straighten, contentDescription = null, modifier = Modifier.size(20.dp))
                            sizeFields()
                        }
                    }
                    orientation()
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)) {
                        SheetSketch(effective, title = stringResource(Res.string.front_sheet), back = false)
                        if (layout.backs) SheetSketch(effective, title = stringResource(Res.string.back_sheet), back = true)
                    }
                    // The back is one choice, so "no back" and "repeat the front" cannot be chosen together.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SelectField(
                            label = stringResource(Res.string.card_back),
                            options = listOf(
                                SelectOption(BackChoice.ROUNDS, stringResource(Res.string.card_back_rounds)),
                                SelectOption(BackChoice.SAME_AS_FRONT, stringResource(Res.string.card_back_same_as_front)),
                                SelectOption(BackChoice.NONE, stringResource(Res.string.card_back_none)),
                            ),
                            selected = layout.backChoice(),
                            onSelect = { layout = layout.withBackChoice(it) },
                            modifier = Modifier.weight(1f),
                        )
                        InfoTooltipIcon(stringResource(Res.string.card_back_help), iconSize = 18.dp)
                    }
                    // The checkboxes use a smaller touch target, so the lines stay close to each other.
                    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 32.dp) {
                        Column {
                            CheckboxLine(
                                checked = layout.cutMarks,
                                onCheckedChange = { layout = layout.copy(cutMarks = it) },
                                label = stringResource(Res.string.cut_marks),
                                help = stringResource(Res.string.cut_marks_help),
                            )
                            // Without backs there is nothing to print on the other side of the paper.
                            if (layout.backs) {
                                CheckboxLine(
                                    checked = layout.doubleSided,
                                    onCheckedChange = { layout = layout.copy(doubleSided = it) },
                                    label = stringResource(Res.string.double_sided_printing),
                                    help = stringResource(Res.string.double_sided_printing_help),
                                )
                            }
                        }
                    }
                    ExtraSheets(
                        layout = layout,
                        cardsOnOneSheet = effective.cardsPerSheet,
                        onLayoutChange = { layout = it },
                    )
                }
            },
            confirmButton = {
                MtsDialogButtons {
                    FocusedButton(
                        enabled = !widthInvalid && !heightInvalid && !staffNamesInvalid,
                        onClick = { onExport(exported) },
                    ) {
                        Text(stringResource(Res.string.create_pdf))
                    }

                    FocusedTextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
                }
            },
        )
    }
}

/** What the back of the cards shows. */
private enum class BackChoice { ROUNDS, SAME_AS_FRONT, NONE }

private fun IdCardSheetLayout.backChoice(): BackChoice = when {
    !backs -> BackChoice.NONE
    backSameAsFront -> BackChoice.SAME_AS_FRONT
    else -> BackChoice.ROUNDS
}

private fun IdCardSheetLayout.withBackChoice(choice: BackChoice): IdCardSheetLayout = copy(
    backs = choice != BackChoice.NONE,
    backSameAsFront = choice == BackChoice.SAME_AS_FRONT,
)

/** A checkbox with a short label and its explanation in the tooltip of an info icon. */
@Composable
private fun CheckboxLine(checked: Boolean, onCheckedChange: (Boolean) -> Unit, label: String, help: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label, modifier = Modifier.weight(1f, fill = false))
        InfoTooltipIcon(help, iconSize = 18.dp)
    }
}

/**
 * The sheets of blank cards printed after the cards of the players: for substitutes (no name and no number) and for
 * the staff (STAFF instead of the number), with the names of the staff when the user writes them.
 */
@Composable
private fun ExtraSheets(
    layout: IdCardSheetLayout,
    cardsOnOneSheet: Int,
    onLayoutChange: (IdCardSheetLayout) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.extra_sheets), style = MaterialTheme.typography.titleSmall)
        SheetCounter(
            icon = Icons.Default.PersonOutline,
            label = stringResource(Res.string.substitute_sheets),
            help = stringResource(Res.string.substitute_sheets_help),
            value = layout.substituteSheets,
            onValueChange = { onLayoutChange(layout.copy(substituteSheets = it)) },
        )
        SheetCounter(
            icon = Icons.Default.Badge,
            label = stringResource(Res.string.staff_sheets),
            help = stringResource(Res.string.staff_sheets_help),
            value = layout.staffSheets,
            onValueChange = { sheets ->
                // The first staff sheet opens the list with one empty name to write.
                val names = if (sheets > 0 && layout.staffNames.isEmpty()) listOf("") else layout.staffNames
                onLayoutChange(layout.copy(staffSheets = sheets, staffNames = names))
            },
        )
        if (layout.staffSheets > 0) {
            StaffNames(
                names = layout.staffNames,
                printedSheets = layout.printedStaffSheets(cardsOnOneSheet),
                chosenSheets = layout.staffSheets,
                onNamesChange = { onLayoutChange(layout.copy(staffNames = it)) },
            )
        }
    }
}

/** One field for the name of each person of the staff, with a button to remove it and a button to add one more. */
@Composable
private fun StaffNames(
    names: List<String>,
    printedSheets: Int,
    chosenSheets: Int,
    onNamesChange: (List<String>) -> Unit,
) {
    var focusLast by remember { mutableStateOf(false) }
    val lastFocus = remember { FocusRequester() }
    LaunchedEffect(focusLast, names.size) {
        if (focusLast) {
            lastFocus.requestFocus()
            focusLast = false
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.staff_names), style = MaterialTheme.typography.labelLarge)
            InfoTooltipIcon(stringResource(Res.string.staff_names_help), iconSize = 18.dp)
        }
        val removeLabel = stringResource(Res.string.remove_person)
        names.forEachIndexed { index, name ->
            val tooLong = name.trim().length > IdCardSheetLayout.MAX_STAFF_NAME_LENGTH
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { text -> onNamesChange(names.toMutableList().also { it[index] = text.replace("\n", " ") }) },
                    placeholder = { Text(stringResource(Res.string.staff_name_placeholder)) },
                    singleLine = true,
                    isError = tooLong,
                    supportingText = if (tooLong) {
                        { Text(stringResource(Res.string.each_staff_name_can_have_up_to, IdCardSheetLayout.MAX_STAFF_NAME_LENGTH)) }
                    } else {
                        null
                    },
                    modifier = Modifier
                        .weight(1f)
                        .then(if (index == names.lastIndex) Modifier.focusRequester(lastFocus) else Modifier),
                )
                AppTooltipBox(removeLabel) {
                    FocusedIconButton(onClick = { onNamesChange(names.filterIndexed { i, _ -> i != index }) }) {
                        Icon(Icons.Default.Close, contentDescription = removeLabel)
                    }
                }
            }
        }
        FocusedTextButton(
            onClick = {
                onNamesChange(names + "")
                focusLast = true
            },
            enabled = names.size < IdCardSheetLayout.MAX_STAFF_NAMES,
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(Res.string.add_person), modifier = Modifier.padding(start = MtsTheme.spacing.sm))
        }
        if (printedSheets > chosenSheets) {
            Text(
                stringResource(Res.string.the_staff_names_need_more_sheets, printedSheets),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A number of sheets with buttons to remove and to add one. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SheetCounter(
    icon: ImageVector,
    label: String,
    help: String,
    value: Int,
    onValueChange: (Int) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        // The explanation lives in the tooltip of the info icon, so each counter takes one line.
        Row(modifier = Modifier.padding(start = 8.dp).weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(label, modifier = Modifier.weight(1f, fill = false))
            InfoTooltipIcon(help, iconSize = 18.dp)
        }
        val removeLabel = stringResource(Res.string.remove_a_sheet)
        AppTooltipBox(removeLabel) {
            FocusedIconButton(onClick = { onValueChange(value - 1) }, enabled = value > 0) {
                Icon(Icons.Default.Remove, contentDescription = removeLabel)
            }
        }
        Text(
            value.toString(),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 24.dp),
        )
        val addLabel = stringResource(Res.string.add_a_sheet)
        AppTooltipBox(addLabel) {
            FocusedIconButton(onClick = { onValueChange(value + 1) }, enabled = value < IdCardSheetLayout.MAX_EXTRA_SHEETS) {
                Icon(Icons.Default.Add, contentDescription = addLabel)
            }
        }
    }
}

/**
 * Draws a rough A4 sheet with the card grid. It is only a sketch to give an idea of the result.
 * Each card shows the number of its player in the list, so the arrangement of the back sheet is clear.
 */
@Composable
private fun SheetSketch(layout: IdCardSheetLayout, title: String, back: Boolean) {
    val grid = layout.grid()
    val outline = MaterialTheme.colorScheme.outline
    val cardFill = MaterialTheme.colorScheme.primary.copy(alpha = if (back) 0.12f else 0.25f)
    val textColor = MaterialTheme.colorScheme.onSurface
    val textMeasurer = rememberTextMeasurer()
    val sketchHeight = 160.dp
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(
            modifier = Modifier
                .height(sketchHeight)
                .width(sketchHeight * (layout.pageWidthPt / layout.pageHeightPt).toFloat())
                .padding(1.dp),
        ) {
            val k = size.height / layout.pageHeightPt.toFloat()
            drawRect(Color.White, size = size)
            drawRect(outline, size = size, style = Stroke(width = 1.5f))
            if (grid == null) return@Canvas
            val cardW = (IdCardSheetLayout.CARD_WIDTH_PT * grid.scale).toFloat() * k
            val cardH = (IdCardSheetLayout.CARD_HEIGHT_PT * grid.scale).toFloat() * k
            val left = (size.width - cardW * grid.columns) / 2f
            val top = (size.height - cardH * grid.rows) / 2f
            val shown = layout.cardsPerSheet.coerceAtMost(grid.columns * grid.rows)
            val numberStyle = TextStyle(fontSize = (cardH * 0.45f).toSp(), color = Color.Black)
            for (index in 0 until shown) {
                // The back sheet is mirrored in the same way as the generated PDF file.
                val cell = layout.cell(index, back) ?: continue
                val origin = Offset(left + cell.column * cardW, top + cell.row * cardH)
                drawRect(cardFill, topLeft = origin, size = Size(cardW, cardH))
                drawRect(outline, topLeft = origin, size = Size(cardW, cardH), style = Stroke(width = 1f))
                val number = textMeasurer.measure((index + 1).toString(), numberStyle)
                drawText(
                    number,
                    topLeft = Offset(
                        origin.x + (cardW - number.size.width) / 2f,
                        origin.y + (cardH - number.size.height) / 2f,
                    ),
                )
            }
            if (layout.cutMarks && !back) {
                val length = 8f * k
                val gap = 3f * k
                val right = left + cardW * grid.columns
                val bottom = top + cardH * grid.rows
                for (column in 0..grid.columns) {
                    val x = left + cardW * column
                    drawLine(Color.Black, Offset(x, top - gap), Offset(x, top - gap - length), 1f)
                    drawLine(Color.Black, Offset(x, bottom + gap), Offset(x, bottom + gap + length), 1f)
                }
                for (row in 0..grid.rows) {
                    val y = top + cardH * row
                    drawLine(Color.Black, Offset(left - gap, y), Offset(left - gap - length, y), 1f)
                    drawLine(Color.Black, Offset(right + gap, y), Offset(right + gap + length, y), 1f)
                }
            }
        }
        Text(title, style = MaterialTheme.typography.bodySmall)
    }
}
