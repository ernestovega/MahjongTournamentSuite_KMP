package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.CompositionLocalProvider
import com.etologic.mahjongtournamentsuite.presentation.theme.LocalThemeController
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import com.etologic.mahjongtournamentsuite.presentation.theme.ThemeController
import com.etologic.mahjongtournamentsuite.presentation.theme.ThemePreference

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
        Box(modifier = Modifier.padding(padding)) {
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
    val transition = rememberInfiniteTransition(label = "app-loading")
    val phase by transition.animateFloat(
        initialValue = -0.35f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4200, easing = LinearEasing),
        ),
        label = "phase",
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape),
    ) {
        val barWidth = maxWidth * 0.35f
        val barOffset = maxWidth * phase
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .background(trackColor),
        )
        Box(
            modifier = Modifier
                .width(barWidth)
                .height(height)
                .offset(x = barOffset)
                .background(indicatorColor),
        )
    }
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

@Preview(device = Devices.DESKTOP)
@Composable
private fun AppScaffoldPreview() {
    val themeController = ThemeController(
        preference = ThemePreference.Light,
        isDarkTheme = false,
        onTogglePreference = {},
    )

    CompositionLocalProvider(LocalThemeController provides themeController) {
        MtsTheme(useDarkTheme = false) {
            AppScaffold(
                title = "Preview",
                subtitle = "AppScaffold",
                isLoading = true,
                onBack = {},
                leadingActions = {
                    AppTopBarLeadingActions(showThemeToggle = true, onTimer = {}, onRanking = {})
                },
                actions = {
                    AppTopBarActions(onPlayers = {}, onRefresh = {}, onNewTournament = {})
                },
            ) {
                Box(modifier = Modifier.padding(24.dp)) {
                    Text("Preview body.")
                }
            }
        }
    }
}
