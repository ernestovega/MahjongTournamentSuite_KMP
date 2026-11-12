package com.etologic.mahjongtournamentsuite.data.repository

import com.etologic.mahjongtournamentsuite.data.backend.dto.ActivityEntryDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.ActivityPageDto
import com.etologic.mahjongtournamentsuite.domain.model.ActivityEntry
import com.etologic.mahjongtournamentsuite.domain.model.ActivityFieldChange
import com.etologic.mahjongtournamentsuite.domain.model.ActivityPage

internal fun ActivityEntryDto.toDomain() = ActivityEntry(
    id = id,
    isTableSave = kind == "table",
    at = at,
    action = action,
    uid = uid,
    name = name,
    source = source,
    tournamentId = tournamentId,
    tournamentName = tournamentName,
    target = target,
    details = details.map { it.key to it.value },
    roundId = roundId,
    tableId = tableId,
    version = version,
    changes = changes.map { ActivityFieldChange(it.field, it.from, it.to, it.handId) },
)

internal fun ActivityPageDto.toDomain() = ActivityPage(entries.map { it.toDomain() }, nextCursor)
