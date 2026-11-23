package com.etologic.mahjongtournamentsuite.domain.model

/** The word before the number of a table in the link of its QR, such as `/6thmmc/table/5`. */
const val TABLE_PATH_WORD = "table"

/**
 * The link of the QR of physical table [tableId] of this tournament, on the web of the players at [site] (such as
 * `https://mts.mahjongmadrid.com`). The player app opens that table in the round in play. A tournament with a public
 * link uses it (`/6thmmc/table/5`); the others use their id (`/t/<id>/table/5`). See docs/table-qr.md.
 */
fun Tournament.tableQrLink(site: String, tableId: Int): String {
    val base = site.trimEnd('/')
    val slug = registration?.slug?.trim().orEmpty()
    val tournamentPath = if (slug.isNotEmpty()) slug else "t/$id"
    return "$base/$tournamentPath/$TABLE_PATH_WORD/$tableId"
}

/** The number of physical tables of the tournament: four players sit at each one. */
val Tournament.physicalTableCount: Int get() = numPlayers / 4
