package com.etologic.mahjongtournamentsuite.domain.model

/** The last segment of the QR link of a tournament, such as `/6thmmc/my-table`. */
const val MY_TABLE_PATH_WORD = "my-table"

/**
 * The link of the QR of this tournament, on the web of the players at [site] (such as `https://mts.mahjongmadrid.com`).
 * The player app opens My tables and the table of the player in play or next. A tournament with a public link uses it
 * (`/6thmmc/my-table`); the others use their id (`/t/<id>/my-table`). See docs/table-qr.md.
 */
fun Tournament.myTableQrLink(site: String): String {
    val base = site.trimEnd('/')
    val slug = registration?.slug?.trim().orEmpty()
    val tournamentPath = if (slug.isNotEmpty()) slug else "t/$id"
    return "$base/$tournamentPath/$MY_TABLE_PATH_WORD"
}

/** The number of physical tables of the tournament: four players sit at each one. */
val Tournament.physicalTableCount: Int get() = numPlayers / 4
