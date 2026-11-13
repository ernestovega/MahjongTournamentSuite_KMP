package com.etologic.mahjongtournamentsuite.presentation.theme

import com.etologic.mahjongtournamentsuite.domain.model.PlayerLevel

/** The colors of a player level. The design system keeps them in order and knows nothing of the domain. */
fun MtsColors.level(level: PlayerLevel): MtsLevelColors = levels[level.ordinal]
