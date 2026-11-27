package com.etologic.mahjongtournamentsuite.data.repository

import com.etologic.mahjongtournamentsuite.data.backend.dto.TournamentDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.RoundClockActionRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.RoundClockDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.RoundClocksResponseDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.RoundControlDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.RoundControlRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.TableControlDto
import com.etologic.mahjongtournamentsuite.domain.model.RoundClock
import com.etologic.mahjongtournamentsuite.domain.model.RoundClockAction
import com.etologic.mahjongtournamentsuite.domain.model.RoundClocks
import com.etologic.mahjongtournamentsuite.domain.model.RoundControl
import com.etologic.mahjongtournamentsuite.domain.model.RoundControlChange
import com.etologic.mahjongtournamentsuite.domain.model.ControlMode
import com.etologic.mahjongtournamentsuite.domain.model.DEFAULT_MATCH_DURATION_MINUTES
import com.etologic.mahjongtournamentsuite.domain.model.DEFAULT_MATCH_EXTRA_MINUTES
import com.etologic.mahjongtournamentsuite.domain.model.MatchEndMode
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.OrganizationType
import com.etologic.mahjongtournamentsuite.domain.model.Permission
import com.etologic.mahjongtournamentsuite.domain.model.TournamentAgendaItem
import com.etologic.mahjongtournamentsuite.domain.model.TournamentOrganizer
import com.etologic.mahjongtournamentsuite.domain.model.TournamentRoundSchedule
import com.etologic.mahjongtournamentsuite.domain.model.TournamentVenue

internal fun TournamentDto.toDomain(): Tournament = Tournament(
    id = id,
    name = name,
    isTeams = isTeams,
    countBestHands = countBestHands,
    countChickenHands = countChickenHands,
    numPlayers = numPlayers,
    numRounds = numRounds,
    shortName = shortName,
    organizerIds = organizerIds,
    organizers = organizers.map { organizer ->
        TournamentOrganizer(
            id = organizer.id,
            name = organizer.name,
            type = if (organizer.type == OrganizationType.FEDERATION.name) OrganizationType.FEDERATION else OrganizationType.CLUB,
            country = organizer.country,
            primaryColor = organizer.primaryColor,
            logoUrl = organizer.logoUrl.normalizedOrNull(),
        )
    },
    primaryColor = primaryColor,
    associationLogoUrl = associationLogoUrl.normalizedOrNull(),
    associationLogoHash = associationLogoHash.normalizedOrNull(),
    hostCountry = hostCountry,
    hostCity = hostCity,
    venue = TournamentVenue(
        name = venueName.trim(),
        address = venueAddress.trim(),
        mapUrl = venueMapUrl.trim(),
        contactEmail = contactEmail.trim(),
    ),
    eventStartDate = eventStartDate.normalizedOrNull(),
    eventEndDate = eventEndDate.normalizedOrNull(),
    roundSchedules = roundSchedules.map { schedule ->
        TournamentRoundSchedule(
            roundId = schedule.roundId,
            date = schedule.date.normalizedOrNull(),
            startTime = schedule.startTime.normalizedOrNull(),
        )
    },
    roundClocks = roundClocks.map { it.toDomain() },
    roundControls = roundControls.map { it.toDomain() },
    agendaItems = agendaItems.map { item ->
        TournamentAgendaItem(
            title = item.title.trim(),
            date = item.date.normalizedOrNull(),
            startTime = item.startTime.normalizedOrNull(),
        )
    },
    matchDurationMinutes = matchDurationMinutes.takeIf { it > 0 } ?: DEFAULT_MATCH_DURATION_MINUTES,
    matchEndMode = MatchEndMode.entries.firstOrNull { it.name == matchEndMode } ?: MatchEndMode.STOP,
    matchExtraMinutes = matchExtraMinutes.takeIf { it > 0 } ?: DEFAULT_MATCH_EXTRA_MINUTES,
    timeZone = timeZone.normalizedOrNull(),
    numTries = numTries,
    isCompleted = isCompleted,
    hasTableData = hasTableData,
    permissions = Permission.fromKeys(permissions),
    isPublic = isPublic,
    showTableDiffs = showTableDiffs,
    hideResultsFromRound = hideResultsFromRound,
    resultsRevealed = resultsRevealed,
    resultsHiddenFromRound = resultsHiddenFromRound,
    createdByUid = createdByUid.normalizedOrNull(),
    createdByName = createdByName.normalizedOrNull(),
    createdAt = createdAt.normalizedOrNull(),
    updatedAt = updatedAt.normalizedOrNull(),
    drawPending = drawPending,
    registration = registration?.toDomain(),
)

private fun String?.normalizedOrNull(): String? = this?.trim()?.takeIf(String::isNotBlank)

internal fun RoundClockDto.toDomain() = RoundClock(
    roundId = roundId,
    startedAtMs = startedAtMs,
    pausedAtMs = pausedAtMs,
    pausedMs = pausedMs,
    adjustMs = adjustMs,
)

internal fun RoundClocksResponseDto.toDomain() = RoundClocks(
    clocks = roundClocks.map { it.toDomain() },
    serverNowMs = serverNowMs,
    controls = roundControls.map { it.toDomain() },
)

private fun controlModeOf(value: String): ControlMode = when (value) {
    "open" -> ControlMode.OPEN
    "closed" -> ControlMode.CLOSED
    else -> ControlMode.AUTO
}

private fun ControlMode.toDto(): String = name.lowercase()

internal fun RoundControlDto.toDomain() = RoundControl(
    roundId = roundId,
    hands = controlModeOf(hands),
    seats = controlModeOf(seats),
    tables = tables.associate { it.tableId to controlModeOf(it.hands) },
)

internal fun RoundControlChange.toDto() = RoundControlRequestDto(
    hands = hands?.toDto(),
    seats = seats?.toDto(),
    table = table?.let { (tableId, mode) -> TableControlDto(tableId, mode.toDto()) },
    reopenTables = reopenTables.ifEmpty { null },
    completeTables = completeTables.ifEmpty { null },
)

internal fun RoundClockAction.toDto(): RoundClockActionRequestDto = when (this) {
    is RoundClockAction.Start -> RoundClockActionRequestDto(action = "start", agoSeconds = agoSeconds, closePrevious = closePrevious.takeIf { it })
    is RoundClockAction.Pause -> RoundClockActionRequestDto(action = "pause", agoSeconds = agoSeconds)
    is RoundClockAction.Resume -> RoundClockActionRequestDto(action = "resume", agoSeconds = agoSeconds)
    is RoundClockAction.Reset -> RoundClockActionRequestDto(action = "reset", force = force)
    is RoundClockAction.SetRemaining -> RoundClockActionRequestDto(action = "setRemaining", remainingSeconds = remainingSeconds)
    RoundClockAction.Stop -> RoundClockActionRequestDto(action = "stop")
}
