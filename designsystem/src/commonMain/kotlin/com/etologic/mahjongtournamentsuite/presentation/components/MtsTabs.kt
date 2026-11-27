package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isUnspecified
import androidx.compose.ui.unit.sp
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme

/** The color of every tab row: the color of the top bar. */
@Composable
fun mtsTabRowColor(): Color = MaterialTheme.colorScheme.primaryContainer

/** The color of the text, the icons and the indicator of a tab row: the color of the title of the top bar. */
@Composable
fun mtsTabContentColor(): Color = MtsTheme.colors.onHeader

/** The tab row of the suite: the color of the top bar, white content and a white indicator. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MtsTabRow(
    selectedTabIndex: Int,
    modifier: Modifier = Modifier,
    tabs: @Composable () -> Unit,
) {
    val content = mtsTabContentColor()
    // All the labels of a row share one size: the size of the label that needs the smallest to fit. Each label getting
    // smaller on its own made tabs of different sizes, as with long Spanish words (Ernesto, 2026-11-26). It starts again
    // when the row changes its width or the text its scale, so a wider row can show bigger labels.
    var rowWidth by remember { mutableIntStateOf(0) }
    val labelSizes = remember(rowWidth, LocalDensity.current.fontScale) { TabLabelSizes() }
    CompositionLocalProvider(LocalTabLabelSizes provides labelSizes) {
    PrimaryTabRow(
        selectedTabIndex = selectedTabIndex,
        modifier = modifier.onSizeChanged { rowWidth = it.width },
        containerColor = mtsTabRowColor(),
        contentColor = content,
        indicator = { TabRowDefaults.PrimaryIndicator(Modifier.tabIndicatorOffset(selectedTabIndex), color = content) },
        tabs = tabs,
    )
    }
}

/** The smallest size that a label of a tab row needed to fit. Unspecified until a label needs to get smaller. */
private class TabLabelSizes {
    var smallest by mutableStateOf(TextUnit.Unspecified)
        private set

    fun report(size: TextUnit) {
        if (size.isUnspecified) return
        if (smallest.isUnspecified || size.value < smallest.value) smallest = size
    }
}

private val LocalTabLabelSizes = staticCompositionLocalOf<TabLabelSizes?> { null }

/** [MtsTabRow] that scrolls sideways when its tabs do not fit. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MtsScrollableTabRow(
    selectedTabIndex: Int,
    modifier: Modifier = Modifier,
    edgePadding: Dp = 0.dp,
    tabs: @Composable () -> Unit,
) {
    val content = mtsTabContentColor()
    PrimaryScrollableTabRow(
        selectedTabIndex = selectedTabIndex,
        modifier = modifier,
        edgePadding = edgePadding,
        containerColor = mtsTabRowColor(),
        contentColor = content,
        indicator = { TabRowDefaults.PrimaryIndicator(Modifier.tabIndicatorOffset(selectedTabIndex), color = content) },
        tabs = tabs,
    )
}

/**
 * A low tab with the icon over the label. The height is fixed, so all tab rows have the same height.
 * A tab without an [icon] shows only the label. With many tabs on a phone, a long label gets smaller before it is cut,
 * and in an [MtsTabRow] the other labels get as small as it, so every tab looks the same.
 */
@Composable
fun MtsTab(
    selected: Boolean,
    onClick: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
) {
    val content = mtsTabContentColor()
    val source = remember { MutableInteractionSource() }
    Tab(
        selected = selected,
        onClick = onClick,
        modifier = modifier.height(if (icon != null) 52.dp else 40.dp).keyboardFocusRing(source, MaterialTheme.shapes.small),
        interactionSource = source,
        selectedContentColor = content,
        unselectedContentColor = content.copy(alpha = 0.7f),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(MtsTheme.spacing.xxs)) {
            icon?.invoke()
            // Without the extra space between letters, so long words like CLASSEMENT fit on a phone.
            val style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.sp)
            val labelSizes = LocalTabLabelSizes.current
            val label = buttonCaps(title)
            val measurer = rememberTextMeasurer()
            // The labels of the tabs are in capitals, like the buttons (Ernesto, 2026-11-25). Screen readers and the
            // tests still get the label as it is written.
            val semantics = Modifier.clearAndSetSemantics { text = AnnotatedString(title) }
            if (labelSizes == null) {
                Text(
                    label,
                    Modifier.padding(horizontal = MtsTheme.spacing.xxs).then(semantics),
                    style = style,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                    autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = style.fontSize, stepSize = 0.5.sp),
                )
            } else {
                // In a row, each label finds, while it is laid out and its room is known, the biggest size down to 9 sp
                // at which it fits in one line, and tells the row. All the labels use the smallest of them. It only goes
                // down, so the layout settles at once.
                val shared = labelSizes.smallest.takeUnless { it.isUnspecified } ?: style.fontSize
                Text(
                    label,
                    Modifier
                        .padding(horizontal = MtsTheme.spacing.xxs)
                        .layout { measurable, constraints ->
                            if (constraints.hasBoundedWidth && constraints.maxWidth > 0) {
                                var size = style.fontSize.value
                                while (size > 9f && measurer.measure(label, style.copy(fontSize = size.sp), maxLines = 1, softWrap = false).size.width > constraints.maxWidth) {
                                    size -= 0.5f
                                }
                                labelSizes.report(size.sp)
                            }
                            val placeable = measurable.measure(constraints)
                            layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                        }
                        .then(semantics),
                    style = style.copy(fontSize = shared),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                )
            }
        }
    }
}
