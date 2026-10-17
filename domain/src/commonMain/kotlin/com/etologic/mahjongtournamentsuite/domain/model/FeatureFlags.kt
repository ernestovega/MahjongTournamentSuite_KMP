package com.etologic.mahjongtournamentsuite.domain.model

/** Switches that the server controls with Firebase Remote Config. */
data class FeatureFlags(
    /** Shows the player levels, the medals and the skill score. */
    val levelsEnabled: Boolean = false,
)
