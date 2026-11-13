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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.presentation.telemetry.ReportScreenLoading
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import mahjongtournamentsuite.ui.generated.resources.Res
import mahjongtournamentsuite.ui.generated.resources.action_back
import mahjongtournamentsuite.ui.generated.resources.menu
import org.jetbrains.compose.resources.stringResource
import com.etologic.mahjongtournamentsuite.presentation.components.DebouncedIconButton as IconButton

private val AppTopBarSidePadding = MtsTheme.spacing.sm

/** The width in dp under which the top bar buttons show only their icons. */
private const val TopBarLabelsMinWidthDp = 600
private val AppLoadingBarHeight = 4.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold(
    title: String,
    subtitle: String? = null,
    titleAction: @Composable (() -> Unit)? = null,
    isLoading: Boolean = false,
    showProgress: Boolean = isLoading,
    onBack: (() -> Unit)? = null,
    navigationIcon: @Composable (() -> Unit)? = null,
    leadingActions: @Composable (() -> Unit)? = null,
    /**
     * Left of the title, after the back button. On a narrow screen they go into the three-dots menu
     * when the back button is present, and they stay visible when it is not.
     */
    rootLeadingActions: @Composable (() -> Unit)? = null,
    actions: @Composable (() -> Unit)? = null,
    floatingActionButton: @Composable (() -> Unit)? = null,
    floatingActionButtonPosition: FabPosition = FabPosition.End,
    autoFocusFirst: Boolean = true,
    /** The faint app logo behind the content. By default it shows while the screen loads. The web never shows it. */
    showBackgroundLogo: Boolean = isLoading,
    content: @Composable () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    // The time until the content shows. Telemetry counts it, and the benchmark reads it on the web.
    ReportScreenLoading(isLoading)

    LaunchedEffect(Unit) {
        if (autoFocusFirst) {
            // Wait until the first layout pass. Web focus changes can otherwise
            // run while Compose is still measuring the focus group.
            withFrameNanos { }
            focusManager.moveFocus(FocusDirection.Next)
        }
    }

    val adaptive = LocalAdaptiveTopBar.current
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val compact = adaptive && maxWidth.value < TopBarCompactWidthDp
        // On a phone the top bar buttons lose their labels and keep the icons.
        val topBarLabels = maxWidth.value >= TopBarLabelsMinWidthDp
        Scaffold(
            modifier = Modifier.appFocusGroup(),
            topBar = {
                CompositionLocalProvider(LocalTopBarLabels provides topBarLabels) {
                    // The progress bar covers the bottom edge of the top bar. It takes no height, so the content under it does not move.
                    Box {
                        // The player top bar has no extra padding. The top bar already has 4 dp, and the buttons have their own 12 dp,
                        // so the icons line up with the 16 dp of the content.
                        val sidePadding = if (rootLeadingActions != null) 0.dp else AppTopBarSidePadding
                        val titleContent: @Composable () -> Unit = {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(MtsTheme.spacing.xs),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(modifier = Modifier.weight(1f, fill = false)) {
                                    if (subtitle == null) {
                                        EllipsizedTitleText(title)
                                    } else {
                                        Column(
                                            verticalArrangement = Arrangement.spacedBy(MtsTheme.spacing.xxs),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                        ) {
                                            EllipsizedTitleText(title)
                                            EllipsizedTitleText(
                                                text = subtitle,
                                                style = MaterialTheme.typography.bodySmall,
                                                // Same color as the title, in the light and the dark theme.
                                                color = LocalContentColor.current,
                                            )
                                        }
                                    }
                                }
                                if (titleAction != null) {
                                    CompositionLocalProvider(LocalAppButtonsEnabled provides !isLoading) {
                                        titleAction()
                                    }
                                }
                            }
                        }

                        val navigationContent: @Composable () -> Unit = {
                            Row(
                                modifier = Modifier.padding(start = sidePadding),
                                horizontalArrangement = Arrangement.spacedBy(MtsTheme.spacing.xs),
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
                                if (rootLeadingActions != null) {
                                    CompositionLocalProvider(LocalAppButtonsEnabled provides !isLoading) {
                                        rootLeadingActions()
                                    }
                                }
                            }
                        }

                        val actionsContent: @Composable () -> Unit = {
                            if (actions != null) {
                                Box(
                                    modifier = Modifier.padding(end = sidePadding),
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(MtsTheme.spacing.xs),
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
                            titleContentColor = MtsTheme.colors.onHeader,
                            navigationIconContentColor = MtsTheme.colors.onHeaderMuted,
                            actionIconContentColor = MtsTheme.colors.onHeaderMuted,
                        )

                        when {
                            compact && rootLeadingActions != null -> {
                                // One icon at the left: the back button, or the root actions when there is no back button.
                                // One three-dots menu at the right. The root actions are in it when the back button is present.
                                val hasBack = onBack != null
                                CompositionLocalProvider(LocalTopBarLabels provides false) {
                                    CenterAlignedTopAppBar(
                                        title = titleContent,
                                        navigationIcon = {
                                            if (hasBack) {
                                                AppBackButton(onClick = onBack!!, showLabel = false)
                                            } else {
                                                CompositionLocalProvider(LocalAppButtonsEnabled provides !isLoading) {
                                                    rootLeadingActions()
                                                }
                                            }
                                        },
                                        actions = {
                                            if (hasBack || actions != null) {
                                                TopBarOverflowMenu(isLoading) {
                                                    if (hasBack) rootLeadingActions()
                                                    actions?.invoke()
                                                }
                                            }
                                        },
                                        colors = colors,
                                    )
                                }
                            }
                            compact -> {
                                CenterAlignedTopAppBar(
                                    title = titleContent,
                                    navigationIcon = {
                                        Row(
                                            modifier = Modifier.padding(start = AppTopBarSidePadding),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            if (onBack != null && navigationIcon == null) {
                                                // The buttons are collapsed, so the back button drops its label.
                                                AppBackButton(onClick = onBack, showLabel = false)
                                            }
                                            if (navigationIcon != null || leadingActions != null) {
                                                // The menu at the start has the profile and the other sections. Its icon is not the
                                                // three dots, so it does not look like the actions menu at the end.
                                                TopBarOverflowMenu(isLoading, icon = Icons.Default.Menu) {
                                                    navigationIcon?.invoke()
                                                    leadingActions?.invoke()
                                                }
                                            }
                                        }
                                    },
                                    actions = {
                                        if (actions != null) {
                                            TopBarOverflowMenu(isLoading) { actions() }
                                        }
                                    },
                                    colors = colors,
                                )
                            }
                            else -> CenterAlignedTopAppBar(
                                title = titleContent,
                                navigationIcon = navigationContent,
                                actions = { Row { actionsContent() } },
                                colors = colors,
                            )
                        }

                        if (showProgress) {
                            SlowLinearLoadingIndicator(Modifier.align(Alignment.BottomCenter))
                        }
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
                // The content stays in the same place whether the logo shows or not, so it keeps its state.
                AppBackground(showLogo = showBackgroundLogo && showsLoadingBackgroundLogo) {
                    CompositionLocalProvider(LocalAppButtonsEnabled provides !isLoading) {
                        content()
                    }
                }
            }
        }
    }
}

/** One-line text. It shows the full text in a tooltip when it is ellipsized. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EllipsizedTitleText(
    text: String,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
) {
    var isEllipsized by remember(text) { mutableStateOf(false) }
    AppTooltipBox(text, enabled = isEllipsized) {
        Text(
            text = text,
            style = style,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { isEllipsized = it.hasVisualOverflow },
        )
    }
}

@Composable
private fun SlowLinearLoadingIndicator(
    modifier: Modifier = Modifier,
    height: Dp = AppLoadingBarHeight,
) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    val indicatorColor = MaterialTheme.colorScheme.tertiary
    val shape = MtsTheme.shapes.pill
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

/** A three-dots button. Its menu shows [content] in a column. */
@Composable
private fun TopBarOverflowMenu(isLoading: Boolean, icon: ImageVector = Icons.Default.MoreVert, content: @Composable () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { menuOpen = true }) {
            Icon(icon, contentDescription = stringResource(Res.string.menu), tint = MtsTheme.colors.onHeader)
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            CompositionLocalProvider(
                LocalAppButtonsEnabled provides !isLoading,
                LocalTopBarMenu provides TopBarMenuScope { menuOpen = false },
            ) { content() }
        }
    }
}

@Composable
private fun AppBackButton(onClick: () -> Unit, showLabel: Boolean = true) {
    AppTopBarButton(text = stringResource(Res.string.action_back), icon = Icons.AutoMirrored.Filled.ArrowBack, onClick = onClick, showLabel = showLabel)
}
