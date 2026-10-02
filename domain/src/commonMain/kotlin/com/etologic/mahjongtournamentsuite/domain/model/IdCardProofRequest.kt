package com.etologic.mahjongtournamentsuite.domain.model

data class IdCardProofRequest(
    val shortName: String,
    val year: String,
    val primaryColor: String,
    val associationLogoContentType: String? = null,
    val associationLogoBytes: ByteArray? = null,
    val associationLogoUrl: String? = null,
    val roundSchedules: List<TournamentRoundSchedule> = emptyList(),
)
