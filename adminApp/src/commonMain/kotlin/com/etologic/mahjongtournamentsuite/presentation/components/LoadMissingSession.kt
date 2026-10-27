package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.etologic.mahjongtournamentsuite.presentation.presenter.TournamentsPresenter
import com.etologic.mahjongtournamentsuite.presentation.store.AppMemoryStore
import com.etologic.mahjongtournamentsuite.presentation.store.loadMissingSession
import org.koin.compose.koinInject

/** Makes sure the store has the tournaments and the role. Use it in each screen that opens after a page refresh. */
@Composable
fun LoadMissingSession() {
    val presenter = koinInject<TournamentsPresenter>()
    val store = koinInject<AppMemoryStore>()
    LaunchedEffect(Unit) { presenter.loadMissingSession(store) }
}
