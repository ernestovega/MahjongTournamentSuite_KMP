package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.runtime.Composable
import com.etologic.mahjongtournamentsuite.domain.model.PlayerLevel
import mahjongtournamentsuite.ui.generated.resources.Res
import mahjongtournamentsuite.ui.generated.resources.level_advanced
import mahjongtournamentsuite.ui.generated.resources.level_beginner
import mahjongtournamentsuite.ui.generated.resources.level_expert
import mahjongtournamentsuite.ui.generated.resources.level_intermediate
import mahjongtournamentsuite.ui.generated.resources.level_master
import org.jetbrains.compose.resources.stringResource

/** The name of the level in the language of the app. */
@Composable
fun PlayerLevel.label(): String = stringResource(
    when (this) {
        PlayerLevel.BEGINNER -> Res.string.level_beginner
        PlayerLevel.INTERMEDIATE -> Res.string.level_intermediate
        PlayerLevel.ADVANCED -> Res.string.level_advanced
        PlayerLevel.EXPERT -> Res.string.level_expert
        PlayerLevel.MASTER -> Res.string.level_master
    },
)
