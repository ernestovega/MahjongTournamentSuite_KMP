package com.etologic.mahjongtournamentsuite.data.repository

import com.etologic.mahjongtournamentsuite.data.backend.dto.ErrorGroupDetailDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.ErrorGroupDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.ErrorGroupsResponseDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.ErrorLogCursorDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.ErrorReportDto
import com.etologic.mahjongtournamentsuite.domain.model.ErrorGroup
import com.etologic.mahjongtournamentsuite.domain.model.ErrorGroupDetail
import com.etologic.mahjongtournamentsuite.domain.model.ErrorGroupsPage
import com.etologic.mahjongtournamentsuite.domain.model.ErrorLogCursor
import com.etologic.mahjongtournamentsuite.domain.model.ErrorReportEntry

internal fun ErrorGroupDto.toDomain() = ErrorGroup(
    id = id,
    errorType = errorType,
    area = area,
    message = message,
    count = count,
    firstSeen = firstSeen,
    lastSeen = lastSeen,
    lastKind = lastKind,
    lastApp = lastApp,
    lastPlatform = lastPlatform,
    lastVersion = lastVersion,
    lastEnv = lastEnv,
    platforms = platforms,
    apps = apps,
)

internal fun ErrorGroupsResponseDto.toDomain() = ErrorGroupsPage(groups.map { it.toDomain() }, nextCursor?.toDomain())

private fun ErrorLogCursorDto.toDomain() = ErrorLogCursor(at, id)

internal fun ErrorGroupDetailDto.toDomain() = ErrorGroupDetail(
    group = ErrorGroup(
        id = id,
        errorType = errorType,
        area = area,
        message = message,
        count = count,
        firstSeen = firstSeen,
        lastSeen = lastSeen,
        lastKind = lastKind,
        lastApp = lastApp,
        lastPlatform = lastPlatform,
        lastVersion = lastVersion,
        lastEnv = lastEnv,
        platforms = platforms,
        apps = apps,
    ),
    stackTrace = stackTrace,
    reports = reports.map { it.toDomain() },
    nextReportCursor = nextReportCursor?.toDomain(),
)

private fun ErrorReportDto.toDomain() = ErrorReportEntry(
    reportId = reportId,
    createdAt = createdAt,
    app = app,
    platform = platform,
    kind = kind,
    env = env,
    role = role,
    appVersion = appVersion,
    reason = reason,
    httpStatus = httpStatus,
    message = message,
    language = language,
)
