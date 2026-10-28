package com.etologic.mahjongtournamentsuite.presentation.screen

import com.etologic.mahjongtournamentsuite.domain.model.FeatureUsage
import com.etologic.mahjongtournamentsuite.domain.model.FunnelStep
import com.etologic.mahjongtournamentsuite.presentation.util.UiText
import mahjongtournamentsuite.adminapp.generated.resources.Res
import mahjongtournamentsuite.adminapp.generated.resources.usage_feature_accounts
import mahjongtournamentsuite.adminapp.generated.resources.usage_feature_agenda
import mahjongtournamentsuite.adminapp.generated.resources.usage_feature_assign
import mahjongtournamentsuite.adminapp.generated.resources.usage_feature_editors
import mahjongtournamentsuite.adminapp.generated.resources.usage_feature_hand_entry
import mahjongtournamentsuite.adminapp.generated.resources.usage_feature_invitations
import mahjongtournamentsuite.adminapp.generated.resources.usage_feature_logo
import mahjongtournamentsuite.adminapp.generated.resources.usage_feature_manual_totals
import mahjongtournamentsuite.adminapp.generated.resources.usage_feature_match_timer
import mahjongtournamentsuite.adminapp.generated.resources.usage_feature_player_app
import mahjongtournamentsuite.adminapp.generated.resources.usage_feature_registry
import mahjongtournamentsuite.adminapp.generated.resources.usage_feature_resets
import mahjongtournamentsuite.adminapp.generated.resources.usage_feature_settings
import mahjongtournamentsuite.adminapp.generated.resources.usage_feature_substitution
import mahjongtournamentsuite.adminapp.generated.resources.usage_feature_team_editing
import mahjongtournamentsuite.adminapp.generated.resources.usage_feature_teams
import mahjongtournamentsuite.adminapp.generated.resources.usage_feature_totals_only
import mahjongtournamentsuite.adminapp.generated.resources.usage_feature_venue_map
import mahjongtournamentsuite.adminapp.generated.resources.usage_funnel_accounts
import mahjongtournamentsuite.adminapp.generated.resources.usage_funnel_assigned
import mahjongtournamentsuite.adminapp.generated.resources.usage_funnel_completed
import mahjongtournamentsuite.adminapp.generated.resources.usage_funnel_created
import mahjongtournamentsuite.adminapp.generated.resources.usage_funnel_data
import mahjongtournamentsuite.adminapp.generated.resources.usage_group_administration
import mahjongtournamentsuite.adminapp.generated.resources.usage_group_dataentry
import mahjongtournamentsuite.adminapp.generated.resources.usage_group_playeraccounts
import mahjongtournamentsuite.adminapp.generated.resources.usage_group_players
import mahjongtournamentsuite.adminapp.generated.resources.usage_group_tournamentsetup
import org.jetbrains.compose.resources.StringResource

/** The server sends English names with stable keys. The app shows the name from its own resources when it has one. */
internal val FeatureLabels: Map<String, StringResource> = mapOf(
    "invitations" to Res.string.usage_feature_invitations,
    "assign" to Res.string.usage_feature_assign,
    "substitution" to Res.string.usage_feature_substitution,
    "teamEditing" to Res.string.usage_feature_team_editing,
    "registry" to Res.string.usage_feature_registry,
    "settings" to Res.string.usage_feature_settings,
    "editors" to Res.string.usage_feature_editors,
    "accounts" to Res.string.usage_feature_accounts,
    "teams" to Res.string.usage_feature_teams,
    "matchTimer" to Res.string.usage_feature_match_timer,
    "agenda" to Res.string.usage_feature_agenda,
    "venueMap" to Res.string.usage_feature_venue_map,
    "logo" to Res.string.usage_feature_logo,
    "handEntry" to Res.string.usage_feature_hand_entry,
    "totalsOnly" to Res.string.usage_feature_totals_only,
    "manualTotals" to Res.string.usage_feature_manual_totals,
    "playerApp" to Res.string.usage_feature_player_app,
    "resets" to Res.string.usage_feature_resets,
)

private val GroupLabels: Map<String, StringResource> = mapOf(
    "Player accounts" to Res.string.usage_group_playeraccounts,
    "Players" to Res.string.usage_group_players,
    "Tournament setup" to Res.string.usage_group_tournamentsetup,
    "Administration" to Res.string.usage_group_administration,
    "Data entry" to Res.string.usage_group_dataentry,
)

private val FunnelLabels: Map<String, StringResource> = mapOf(
    "created" to Res.string.usage_funnel_created,
    "assigned" to Res.string.usage_funnel_assigned,
    "accounts" to Res.string.usage_funnel_accounts,
    "data" to Res.string.usage_funnel_data,
    "completed" to Res.string.usage_funnel_completed,
)

internal fun groupLabelText(group: String): UiText = GroupLabels[group]?.let { UiText.of(it) } ?: UiText.Plain(group)

internal fun funnelLabelText(step: FunnelStep): UiText = FunnelLabels[step.key]?.let { UiText.of(it) } ?: UiText.Plain(step.label)
