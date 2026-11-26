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
import androidx.compose.material3.ProvideTextStyle
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
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
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
    /**
     * In place of [title] when it does not fit even at its smallest size, such as the short name of a tournament.
     * Its tooltip still shows the full [title].
     */
    shortTitle: String? = null,
    /** In place of [subtitle] when it does not fit even at its smallest size. */
    shortSubtitle: String? = null,
    /** Drawn in place of [subtitle], for a subtitle with more than text, such as a flag. */
    subtitleContent: @Composable (() -> Unit)? = null,
    /** Left of the title and the subtitle, as tall as both, such as the photo of a player. */
    titleLeading: @Composable (() -> Unit)? = null,
    titleAction: @Composable (() -> Unit)? = null,
    isLoading: Boolean = false,
    /**
     * True while the screen has nothing to show yet, on its first load. The logo with a progress below it covers
     * the content, as on the splash and the loading page of the web, in place of a text such as "Loading…".
     * Later loads, such as a refresh or a save, only show the bar below the top bar.
     */
    contentLoading: Boolean = false,
    showProgress: Boolean = isLoading && !contentLoading,
    onBack: (() -> Unit)? = null,
    navigationIcon: @Composable (() -> Unit)? = null,
    leadingActions: @Composable (() -> Unit)? = null,
    /**
     * Left of the title, after the back button. On a narrow screen they go into the three-dots menu
     * when the back button is present, and they stay visible when it is not.
     */
    rootLeadingActions: @Composable (() -> Unit)? = null,
    /**
     * Right of the title. On a narrow screen they go into the three-dots menu. Pass null, never an empty lambda,
     * when the screen or the role of the user has no buttons here: an empty lambda shows a menu that opens nothing.
     */
    actions: @Composable (() -> Unit)? = null,
    floatingActionButton: @Composable (() -> Unit)? = null,
    floatingActionButtonPosition: FabPosition = FabPosition.End,
    /** A short message at the bottom, such as "Link copied", in place of a dialog. Usually a SnackbarHost. */
    snackbarHost: @Composable () -> Unit = {},
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
                        val shownTitle = TopBarText(title, shortTitle)
                        val shownSubtitle = subtitle?.let { TopBarText(it, shortSubtitle) }
                        val titleContent: @Composable () -> Unit = {
                            TopBarTitle(shownTitle, shownSubtitle, subtitleContent, titleLeading, titleAction, isLoading)
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
                            compact -> {
                                // On a phone the bar never has more than two icons: one at the left and one at the right.
                                // The left one is the back button. Without a back button it is the buttons left of the
                                // title, or one menu with them. With a back button those buttons go first in the
                                // three-dots menu at the right, so there is never a second menu at the left.
                                val hasBack = onBack != null && navigationIcon == null
                                val leftButtons: (@Composable () -> Unit)? =
                                    if (navigationIcon == null && leadingActions == null && rootLeadingActions == null) {
                                        null
                                    } else {
                                        {
                                            navigationIcon?.invoke()
                                            leadingActions?.invoke()
                                            rootLeadingActions?.invoke()
                                        }
                                    }
                                val hasLeftIcon = hasBack || leftButtons != null
                                val hasMenuButtons = actions != null || (hasBack && leftButtons != null)
                                // The title button, such as the pencil, joins the menu when the bar already has two icons.
                                val titleActionInMenu = titleAction != null && hasLeftIcon && hasMenuButtons
                                CompositionLocalProvider(LocalTopBarLabels provides false) {
                                    CenterAlignedTopAppBar(
                                        title = if (titleActionInMenu) {
                                            { TopBarTitle(shownTitle, shownSubtitle, subtitleContent, titleLeading, titleAction = null, isLoading) }
                                        } else {
                                            titleContent
                                        },
                                        navigationIcon = {
                                            Box(modifier = Modifier.padding(start = sidePadding)) {
                                                when {
                                                    hasBack -> AppBackButton(onClick = onBack!!, showLabel = false)
                                                    // The root buttons of the player stay visible while they fit. More buttons share one menu.
                                                    rootLeadingActions != null && navigationIcon == null && leadingActions == null -> {
                                                        val rightIcons = if (hasMenuButtons || titleAction != null) 1 else 0
                                                        TopBarIconsOrMenu(maxIcons = 2 - rightIcons, isLoading = isLoading, content = rootLeadingActions)
                                                    }
                                                    leftButtons != null -> {
                                                        // Its icon is not the three dots, so it does not look like the menu at the right.
                                                        TopBarOverflowMenu(isLoading, icon = Icons.Default.Menu, content = leftButtons)
                                                    }
                                                }
                                            }
                                        },
                                        actions = {
                                            if (hasMenuButtons || titleActionInMenu) {
                                                TopBarOverflowMenu(isLoading) {
                                                    if (hasBack) leftButtons?.invoke()
                                                    if (titleActionInMenu) titleAction?.invoke()
                                                    actions?.invoke()
                                                }
                                            }
                                        },
                                        colors = colors,
                                    )
                                }
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
            snackbarHost = snackbarHost,
            containerColor = MaterialTheme.colorScheme.background,
        ) { padding ->
            Box(modifier = Modifier.padding(padding)) {
                // The content stays in the same place whether the logo shows or not, so it keeps its state.
                AppBackground(showLogo = showBackgroundLogo && showsLoadingBackgroundLogo && !contentLoading) {
                    CompositionLocalProvider(LocalAppButtonsEnabled provides !isLoading) {
                        content()
                    }
                }
                // Over the content, so the content keeps its state.
                if (contentLoading) AppLoading()
            }
        }
    }
}

/** A text of the top bar, and the [short] one that takes its place when it does not fit. */
private class TopBarText(val full: String, short: String?) {
    val short: String? = short?.takeIf { it.isNotBlank() && it != full }
}

/** The title and the subtitle of the top bar, with the picture at the left and the title button at the right. */
@Composable
private fun TopBarTitle(
    title: TopBarText,
    subtitle: TopBarText?,
    subtitleContent: @Composable (() -> Unit)?,
    titleLeading: @Composable (() -> Unit)?,
    titleAction: @Composable (() -> Unit)?,
    isLoading: Boolean,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(if (titleLeading != null) MtsTheme.spacing.sm else MtsTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (titleLeading != null) titleLeading()
        Box(modifier = Modifier.weight(1f, fill = false)) {
            if (subtitle == null && subtitleContent == null) {
                EllipsizedTitleText(title)
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(MtsTheme.spacing.xxs),
                    // With a picture on the left, the texts line up on its side.
                    horizontalAlignment = if (titleLeading != null) Alignment.Start else Alignment.CenterHorizontally,
                ) {
                    EllipsizedTitleText(title)
                    if (subtitleContent != null) {
                        ProvideTextStyle(MaterialTheme.typography.bodySmall) { subtitleContent() }
                    } else if (subtitle != null) {
                        EllipsizedTitleText(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            minFontSize = TopBarSubtitleMinFontSize,
                            // Same color as the title, in the light and the dark theme.
                            color = LocalContentColor.current,
                        )
                    }
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

/**
 * The top bar buttons in a row while there are at most [maxIcons] of them. With more, one menu holds them all,
 * so that the bar keeps its maximum of two icons on a phone.
 */
@Composable
private fun TopBarIconsOrMenu(maxIcons: Int, isLoading: Boolean, content: @Composable () -> Unit) {
    SubcomposeLayout { constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val icons = subcompose("icons") {
            CompositionLocalProvider(LocalAppButtonsEnabled provides !isLoading) { content() }
        }
        val placeables = if (icons.size <= maxIcons) {
            icons.map { it.measure(loose) }
        } else {
            subcompose("menu") { TopBarOverflowMenu(isLoading, icon = Icons.Default.Menu, content = content) }.map { it.measure(loose) }
        }
        val height = placeables.maxOfOrNull { it.height } ?: 0
        layout(placeables.sumOf { it.width }, height) {
            var x = 0
            placeables.forEach {
                it.placeRelative(x, (height - it.height) / 2)
                x += it.width
            }
        }
    }
}

/** The smallest sizes of the title and the subtitle of the top bar. Under them the text ends with "…". */
private val TopBarTitleMinFontSize = 15.sp
private val TopBarSubtitleMinFontSize = 10.sp
private const val TopBarFontSizeStep = 0.5f

/**
 * One-line text. A long text gets smaller, down to [minFontSize]. When it still does not fit, its short text takes its place,
 * and the text ends with "…" only when even that one does not fit. The tooltip shows the full text when it is cut or shortened.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EllipsizedTitleText(
    text: TopBarText,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    minFontSize: TextUnit = TopBarTitleMinFontSize,
) {
    val maxFontSize = style.fontSize.takeIf { it.isSpecified } ?: MaterialTheme.typography.titleLarge.fontSize
    val smallest = minOf(minFontSize.value, maxFontSize.value).sp
    BoxWithConstraints {
        val measurer = rememberTextMeasurer()
        val maxWidthPx = constraints.maxWidth
        // The text and its size: the full text at the largest size that fits, else the short one, else "…" at the smallest size.
        val (shown, fontSize) = remember(text.full, text.short, style, smallest, maxFontSize, maxWidthPx) {
            fun widthOf(value: String, size: TextUnit) = measurer.measure(value, style.copy(fontSize = size), maxLines = 1, softWrap = false).size.width
            fun largestSizeThatFits(value: String): TextUnit? {
                if (!constraints.hasBoundedWidth) return maxFontSize
                var size = maxFontSize.value
                while (size >= smallest.value) {
                    if (widthOf(value, size.sp) <= maxWidthPx) return size.sp
                    size -= TopBarFontSizeStep
                }
                return null
            }
            largestSizeThatFits(text.full)?.let { text.full to it }
                ?: text.short?.let { short -> largestSizeThatFits(short)?.let { short to it } ?: (short to smallest) }
                ?: (text.full to smallest)
        }
        var isEllipsized by remember(shown) { mutableStateOf(false) }
        AppTooltipBox(text.full, enabled = isEllipsized || shown != text.full) {
            Text(
                text = shown,
                style = style,
                fontSize = fontSize,
                color = color,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { isEllipsized = it.hasVisualOverflow },
            )
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
