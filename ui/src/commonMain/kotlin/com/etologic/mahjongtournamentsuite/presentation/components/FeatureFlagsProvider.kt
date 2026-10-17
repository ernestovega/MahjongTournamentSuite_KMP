package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.runtime.compositionLocalOf
import com.etologic.mahjongtournamentsuite.domain.model.FeatureFlags

/** The feature flags of the server. Until the load ends, all features are off. */
val LocalFeatureFlags = compositionLocalOf { FeatureFlags() }
