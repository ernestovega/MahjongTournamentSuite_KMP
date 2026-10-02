package com.etologic.mahjongtournamentsuite.data.repository

import com.etologic.mahjongtournamentsuite.data.backend.dto.TournamentDto
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.TournamentAgendaItem
import com.etologic.mahjongtournamentsuite.domain.model.TournamentRoundSchedule

internal fun TournamentDto.toDomain(): Tournament = Tournament(
    id = id,
    name = name,
    isTeams = isTeams,
    numPlayers = numPlayers,
    numRounds = numRounds,
    shortName = shortName,
    primaryColor = primaryColor,
    associationLogoUrl = associationLogoUrl.normalizedOrNull(),
    hostCountry = hostCountry,
    hostCity = hostCity,
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
            endTime = item.endTime.normalizedOrNull(),
        )
    },
    numTries = numTries,
    isCompleted = isCompleted,
    createdByUid = createdByUid.normalizedOrNull() ?: createdBy.normalizedOrNull(),
    createdByName = createdByName.normalizedOrNull(),
    createdAt = createdAt.normalizedOrNull() ?: created.normalizedOrNull(),
    updatedAt = updatedAt.normalizedOrNull() ?: updated.normalizedOrNull(),
)

private fun String?.normalizedOrNull(): String? = this?.trim()?.takeIf(String::isNotBlank)
