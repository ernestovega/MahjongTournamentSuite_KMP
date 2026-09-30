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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
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
    autoFocusFirst: Boolean = true,
    showBackgroundLogo: Boolean = true,
    content: @Composable () -> Unit,
) {
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) {
        if (autoFocusFirst) {
            // Wait until the first layout pass. Web focus changes can otherwise
            // run while Compose is still measuring the focus group.
            withFrameNanos { }
            focusManager.moveFocus(FocusDirection.Next)
        }
    }

    Scaffold(
        modifier = Modifier.appFocusGroup(),
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
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        when {
                            navigationIcon != null -> {
                                CompositionLocalProvider(LocalAppButtonsEnabled provides !isLoading) {
                                    navigationIcon()
                                }
                            }
                            onBack != null -> {
                                AppBackButton(onClick = onBack)
                            }
                        }

                        if (leadingActions != null) {
                            CompositionLocalProvider(LocalAppButtonsEnabled provides !isLoading) {
                                leadingActions()
                            }
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
                                CompositionLocalProvider(LocalAppButtonsEnabled provides !isLoading) {
                                    actions()
                                }
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
            CompositionLocalProvider(LocalAppButtonsEnabled provides !isLoading) {
                floatingActionButton?.invoke()
            }
        },
        floatingActionButtonPosition = floatingActionButtonPosition,
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            val contentWithBackground: @Composable () -> Unit = {
                CompositionLocalProvider(LocalAppButtonsEnabled provides !isLoading) {
                    content()
                }
            }
            if (showBackgroundLogo) {
                AppBackground(contentWithBackground)
            } else {
                contentWithBackground()
            }
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
    AppTopBarButton(text = "Back", icon = Icons.AutoMirrored.Filled.ArrowBack, onClick = onClick)
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
                    AppTopBarActions(onPlayers = {}, onNewTournament = {})
                },
            ) {
                Box(modifier = Modifier.padding(24.dp)) {
                    Text("Preview body.")
                }
            }
        }
    }
}
