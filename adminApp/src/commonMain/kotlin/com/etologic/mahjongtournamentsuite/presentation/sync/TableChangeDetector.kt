package com.etologic.mahjongtournamentsuite.presentation.sync

import com.etologic.mahjongtournamentsuite.domain.model.TableKey
import com.etologic.mahjongtournamentsuite.domain.model.TournamentManifest

object TableChangeDetector {
    /**
     * Returns the tables that someone saved after this client loaded them.
     *
     * - Only a server version that is **higher** than the known version counts. A manifest that was read just before
     *   this client's own save has a lower version. It must not look like a change.
     * - A table without a server version was never saved since the backend tracks versions. It did not change.
     * - A table that the client does not know is not compared.
     */
    fun changedTables(known: Map<TableKey, Long>, manifest: TournamentManifest): Set<TableKey> =
        known.mapNotNullTo(mutableSetOf()) { (key, knownVersion) ->
            val serverVersion = manifest.tableVersions[key] ?: return@mapNotNullTo null
            key.takeIf { serverVersion > knownVersion }
        }
}
