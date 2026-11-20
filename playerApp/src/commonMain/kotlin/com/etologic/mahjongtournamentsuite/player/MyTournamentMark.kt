package com.etologic.mahjongtournamentsuite.player

import com.etologic.mahjongtournamentsuite.domain.model.MyTournaments

/** How the signed-in account takes part in a tournament of the list. */
internal enum class MyTournamentMark {
    /** It has a slot: it plays, played or will play. */
    PLAYS,

    /** It is registered with a place, before the draw. */
    REGISTERED,

    /** It is on the waiting list, before the draw. */
    WAITING,
}

/** The mark of each own tournament. A slot wins over a registration of the same tournament. */
internal fun myTournamentMarksOf(mine: MyTournaments): Map<String, MyTournamentMark> = buildMap {
    mine.registrations.forEach { put(it.tournamentId, if (it.waiting) MyTournamentMark.WAITING else MyTournamentMark.REGISTERED) }
    mine.slots.forEach { put(it.tournamentId, MyTournamentMark.PLAYS) }
}
