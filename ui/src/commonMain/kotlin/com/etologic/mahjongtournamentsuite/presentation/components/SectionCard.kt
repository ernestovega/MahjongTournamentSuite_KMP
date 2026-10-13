package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    verticalSpacing: Dp = 12.dp,
    titleStyle: TextStyle = MaterialTheme.typography.titleMedium,
    titleAction: @Composable (() -> Unit)? = null,
    centerTitle: Boolean = false,
    titleActionAtStart: Boolean = false,
    /** Content at the start (left) of the title row. It works only with [centerTitle]. */
    startContent: @Composable (() -> Unit)? = null,
    actions: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(verticalSpacing),
        ) {
            if (centerTitle && (title != null || subtitle != null || titleAction != null || actions != null || startContent != null)) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (!title.isNullOrBlank()) Text(text = title, style = titleStyle)
                            if (!titleActionAtStart) titleAction?.invoke()
                        }
                        if (!subtitle.isNullOrBlank()) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (startContent != null) {
                        Box(modifier = Modifier.align(Alignment.CenterStart)) { startContent() }
                    }
                    if (titleActionAtStart && titleAction != null) {
                        Box(modifier = Modifier.align(Alignment.CenterStart)) { titleAction() }
                    }
                    if (actions != null) {
                        Box(modifier = Modifier.align(Alignment.CenterEnd)) { actions() }
                    }
                }
            } else if (title != null || subtitle != null || titleAction != null || actions != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        if (!title.isNullOrBlank() || titleAction != null) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (!title.isNullOrBlank()) {
                                    Text(
                                        text = title,
                                        style = titleStyle,
                                    )
                                }
                                titleAction?.invoke()
                            }
                        }
                        if (!subtitle.isNullOrBlank()) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    actions?.invoke()
                }
            }

            content()
        }
    }
}

@Preview(device = Devices.DESKTOP)
@Composable
private fun SectionCardPreview() {
    MtsTheme(useDarkTheme = false) {
        SectionCard(
            title = "Section title",
            subtitle = "Section subtitle",
            actions = {
                AppTextButton(onClick = {}) {
                    Text("Action")
                }
            },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Primary content line.")
                Text(
                    text = "Secondary detail line.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
