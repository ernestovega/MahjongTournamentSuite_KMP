package com.etologic.mahjongtournamentsuite.presentation.store

import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.presentation.presenter.TournamentsPresenter

/**
 * Fills the parts of the store that the tournament screens read: the profile, the role and the tournaments.
 * A page refresh empties the store, and only the tournaments list screen fills it again.
 * A screen that opens after a refresh calls this function. It loads only the parts that are missing.
 */
suspend fun TournamentsPresenter.loadMissingSession(store: AppMemoryStore) {
    if (store.profile.value == null) {
        (loadProfile() as? AppResult.Success)?.let { store.profile.value = it.value }
    }
    if (store.adminStatus.value == null) {
        (loadAdminStatus() as? AppResult.Success)?.let { store.adminStatus.value = it.value }
    }
    if (store.tournaments.value.isEmpty()) {
        (loadTournaments() as? AppResult.Success)?.let { store.upsertTournaments(it.value) }
    }
}
