package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.presentation.components.appFocusGroup

/**
 * Editor of one table. It has three separate blocks: a results table
 * (players, scores, points) and a hands table. Every hand shows in the hands table. The block folds.
 */
@Composable
internal fun TableEditorPanel(
    editor: TableManagerEditorState,
    enabled: Boolean,
    playerNamesById: Map<Int, String>,
    modifier: Modifier = Modifier,
    scoreFocusRequester: FocusRequester? = null,
    /** Best hand scores of every other table of the tournament. They decide which hands are tournament best hands. */
    otherTablesBestHandScores: List<Int> = emptyList(),
) {
    val dependentSectionsEnabled = enabled && editor.hasCompleteSeatPositions
    Column(
        modifier = modifier.fillMaxWidth().appFocusGroup(),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        TableResultsCard(
            editor = editor,
            enabled = enabled,
            dependentEnabled = dependentSectionsEnabled,
            playerNamesById = playerNamesById,
            scoreFocusRequester = scoreFocusRequester,
            otherTablesBestHandScores = otherTablesBestHandScores,
        )
        TableHandsCard(
            editor = editor,
            enabled = dependentSectionsEnabled,
            playerNamesById = playerNamesById,
        )
    }
}
