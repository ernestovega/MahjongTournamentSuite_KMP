package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.runtime.Composable
import com.etologic.mahjongtournamentsuite.domain.model.GlobalUserRole
import mahjongtournamentsuite.adminapp.generated.resources.Res
import mahjongtournamentsuite.adminapp.generated.resources.role_country_admin
import mahjongtournamentsuite.adminapp.generated.resources.role_editor
import mahjongtournamentsuite.adminapp.generated.resources.role_player
import mahjongtournamentsuite.adminapp.generated.resources.role_super_admin
import org.jetbrains.compose.resources.stringResource

/** The name of the role in the language of the app. */
@Composable
fun GlobalUserRole.label(): String = stringResource(
    when (this) {
        GlobalUserRole.SUPER_ADMIN -> Res.string.role_super_admin
        GlobalUserRole.COUNTRY_ADMIN -> Res.string.role_country_admin
        GlobalUserRole.EDITOR -> Res.string.role_editor
        GlobalUserRole.PLAYER -> Res.string.role_player
    },
)
