package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.runtime.Composable
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState

/** The back gesture of iOS, a swipe from the left edge, does the same as the back of Android. */
@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = NavigationBackHandler(
    state = rememberNavigationEventState(NavigationEventInfo.None),
    isBackEnabled = enabled,
    onBackCompleted = onBack,
)
