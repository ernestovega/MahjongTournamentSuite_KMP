package com.etologic.mahjongtournamentsuite.data.repository

import com.etologic.mahjongtournamentsuite.data.backend.dto.CountryUsageDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.DailyUsageDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.FeatureUsageDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.FunnelStepDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.TournamentUsageDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.UsageAccountsDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.UsagePeriodDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.UsageReportDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.UserUsageDto
import com.etologic.mahjongtournamentsuite.domain.model.CountryUsage
import com.etologic.mahjongtournamentsuite.domain.model.DailyUsage
import com.etologic.mahjongtournamentsuite.domain.model.FeatureUsage
import com.etologic.mahjongtournamentsuite.domain.model.FunnelStep
import com.etologic.mahjongtournamentsuite.domain.model.TournamentUsage
import com.etologic.mahjongtournamentsuite.domain.model.TournamentUsageStatus
import com.etologic.mahjongtournamentsuite.domain.model.UsageAccounts
import com.etologic.mahjongtournamentsuite.domain.model.UsagePeriod
import com.etologic.mahjongtournamentsuite.domain.model.UsageReport
import com.etologic.mahjongtournamentsuite.domain.model.UserStatus
import com.etologic.mahjongtournamentsuite.domain.model.UserUsage

internal fun UsageReportDto.toDomain(): UsageReport = UsageReport(
    generatedAt = generatedAt,
    periodDays = periodDays,
    current = current.toDomain(),
    previous = previous.toDomain(),
    accounts = accounts.toDomain(),
    daily = daily.map { it.toDomain() },
    heatmap = heatmap,
    users = users.map { it.toDomain() },
    features = features.map { it.toDomain() },
    funnel = funnel.map { it.toDomain() },
    countries = countries.map { it.toDomain() },
    tournaments = tournaments.map { it.toDomain() },
)

private fun UsagePeriodDto.toDomain() = UsagePeriod(
    activeUsers = activeUsers,
    saves = saves,
    handChanges = handChanges,
    playerSaves = playerSaves,
    editorSaves = editorSaves,
    actions = actions,
    activeTournaments = activeTournaments,
    tournamentsCreated = tournamentsCreated,
    newAccounts = newAccounts,
    conflicts = conflicts,
    resets = resets,
)

private fun UsageAccountsDto.toDomain() = UsageAccounts(
    total = total,
    admins = admins,
    editors = editors,
    players = players,
    disabled = disabled,
    active = active,
    visitors = visitors,
    dormant = dormant,
    neverSignedIn = neverSignedIn,
)

private fun DailyUsageDto.toDomain() = DailyUsage(date, saves, actions, activeUsers)

private fun UserUsageDto.toDomain() = UserUsage(
    uid = uid,
    name = name,
    role = role,
    status = UserStatus.entries.firstOrNull { it.name == status } ?: UserStatus.NEVER,
    saves = saves,
    handChanges = handChanges,
    actions = actions,
    tournaments = tournaments,
    activeDays = activeDays,
    lastActiveDaysAgo = lastActiveDaysAgo,
    lastSeenDaysAgo = lastSeenDaysAgo,
    createdDaysAgo = createdDaysAgo,
)

private fun FeatureUsageDto.toDomain() = FeatureUsage(
    key = key,
    label = label,
    group = group,
    isPeriod = scope == "PERIOD",
    uses = uses,
    users = users,
    adoption = adoption,
    lastUsedDaysAgo = lastUsedDaysAgo,
)

private fun FunnelStepDto.toDomain() = FunnelStep(key, label, count)

private fun CountryUsageDto.toDomain() = CountryUsage(country, tournaments, players, saves)

private fun TournamentUsageDto.toDomain() = TournamentUsage(
    tournamentId = tournamentId,
    name = name,
    country = country,
    startDate = startDate,
    endDate = endDate,
    status = TournamentUsageStatus.entries.firstOrNull { it.name == status } ?: TournamentUsageStatus.DONE,
    tables = tables,
    completedTables = completedTables,
    handsTables = handsTables,
    totalsOnlyTables = totalsOnlyTables,
    manualTotalsTables = manualTotalsTables,
    saves = saves,
    savesInPeriod = savesInPeriod,
    playerSaves = playerSaves,
    editorSaves = editorSaves,
    resets = resets,
    conflicts = conflicts,
    tablesTouched = tablesTouched,
    savesPerTouchedTable = savesPerTouchedTable,
    activeHours = activeHours,
    lastActivityDaysAgo = lastActivityDaysAgo,
    slots = slots,
    assignedSlots = assignedSlots,
    slotsWithAccount = slotsWithAccount,
    invitationsSent = invitationsSent,
    invitationsAccepted = invitationsAccepted,
    invitationsPending = invitationsPending,
)
