package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
    PrimaryTabRow(
        selectedTabIndex = selectedTabIndex,
        modifier = modifier,
        containerColor = mtsTabRowColor(),
        contentColor = content,
        indicator = { TabRowDefaults.PrimaryIndicator(Modifier.tabIndicatorOffset(selectedTabIndex), color = content) },
        tabs = tabs,
    )
}

/**
 * A low tab with the icon over the label. The height is fixed, so all tab rows have the same height.
 * A tab without an [icon] shows only the label. With many tabs on a phone, a long label gets smaller before it is cut.
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
    Tab(
        selected = selected,
        onClick = onClick,
        modifier = modifier.height(if (icon != null) 52.dp else 40.dp),
        selectedContentColor = content,
        unselectedContentColor = content.copy(alpha = 0.7f),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(MtsTheme.spacing.xxs)) {
            icon?.invoke()
            val style = MaterialTheme.typography.labelMedium
            Text(
                title,
                Modifier.padding(horizontal = MtsTheme.spacing.xxs),
                style = style,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = style.fontSize, stepSize = 0.5.sp),
            )
        }
    }
}
