package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val AppTopBarSidePadding = 8.dp
private val AppLoadingBarHeight = 4.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold(
    title: String,
    subtitle: String? = null,
    isLoading: Boolean = false,
    onBack: (() -> Unit)? = null,
    navigationIcon: @Composable (() -> Unit)? = null,
    leadingActions: @Composable (RowScope.() -> Unit)? = null,
    actions: @Composable (RowScope.() -> Unit)? = null,
    floatingActionButton: @Composable (() -> Unit)? = null,
    floatingActionButtonPosition: FabPosition = FabPosition.End,
    content: @Composable () -> Unit,
) {
    Scaffold(
        topBar = {
            Column {
                val titleContent: @Composable () -> Unit = {
                    Box {
                        if (subtitle == null) {
                            Text(title)
                        } else {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(title)
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                val navigationContent: @Composable () -> Unit = {
                    Row(
                        modifier = Modifier.padding(start = AppTopBarSidePadding),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        when {
                            navigationIcon != null -> navigationIcon()
                            onBack != null -> {
                                AppBackButton(onClick = onBack)
                            }
                        }

                        if (leadingActions != null) {
                            leadingActions()
                        }
                    }
                }

                val actionsContent: @Composable RowScope.() -> Unit = {
                    if (actions != null) {
                        Box(
                            modifier = Modifier.padding(end = AppTopBarSidePadding),
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                actions()
                            }
                        }
                    }
                }

                val colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White.copy(alpha = 0.85f),
                    actionIconContentColor = Color.White.copy(alpha = 0.85f),
                )

                CenterAlignedTopAppBar(
                    title = titleContent,
                    navigationIcon = navigationContent,
                    actions = actionsContent,
                    colors = colors,
                )

                if (isLoading) {
                    SlowLinearLoadingIndicator()
                }
            }
        },
        floatingActionButton = {
            floatingActionButton?.invoke()
        },
        floatingActionButtonPosition = floatingActionButtonPosition,
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding),
        ) {
            content()
        }
    }
}

@Composable
private fun SlowLinearLoadingIndicator(
    modifier: Modifier = Modifier,
    height: Dp = AppLoadingBarHeight,
) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    val indicatorColor = MaterialTheme.colorScheme.tertiary
    val shape = RoundedCornerShape(percent = 50)

    LinearProgressIndicator(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape),
        color = indicatorColor,
        trackColor = trackColor,
    )
}

@Composable
private fun AppBackButton(onClick: () -> Unit) {
    AppTextButton(onClick = onClick) {
        Text(
            text = "Back",
            color = Color.White,
        )
    }
}
