package com.etologic.mahjongtournamentsuite.data.repository

import com.etologic.mahjongtournamentsuite.data.backend.dto.TournamentDto
import com.etologic.mahjongtournamentsuite.domain.model.DEFAULT_MATCH_DURATION_MINUTES
import com.etologic.mahjongtournamentsuite.domain.model.DEFAULT_MATCH_EXTRA_MINUTES
import com.etologic.mahjongtournamentsuite.domain.model.MatchEndMode
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.TournamentAgendaItem
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
    createdByUid = createdByUid.normalizedOrNull() ?: createdBy.normalizedOrNull(),
    createdByName = createdByName.normalizedOrNull(),
    createdAt = createdAt.normalizedOrNull() ?: created.normalizedOrNull(),
    updatedAt = updatedAt.normalizedOrNull() ?: updated.normalizedOrNull(),
)

private fun String?.normalizedOrNull(): String? = this?.trim()?.takeIf(String::isNotBlank)
