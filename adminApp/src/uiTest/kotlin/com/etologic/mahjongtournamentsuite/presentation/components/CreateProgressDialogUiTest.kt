package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import com.etologic.mahjongtournamentsuite.presentation.presenter.CreateTournamentPresenter.Phase
import com.etologic.mahjongtournamentsuite.presentation.presenter.CreateTournamentPresenter.Progress
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import kotlin.test.Test

/** The progress of the schedule search draws the load of the parallel tries as a line once it has two samples. */
@OptIn(ExperimentalTestApi::class)
class CreateProgressDialogUiTest {
    @Test
    fun eachNewProgressAddsASampleToTheLoadLine() = runComposeUiTest {
        var progress by mutableStateOf(Progress(Phase.CALCULATING, 1, 10, 4))
        setContent { MtsTheme(useDarkTheme = false) { CreateProgressDialog(progress, startMark = null, onCancel = {}) } }
        waitForIdle()
        (2..4).forEach { running ->
            progress = Progress(Phase.CALCULATING, running, 10L * running, 4)
            waitForIdle()
        }
        onNodeWithText("Tried: 40").assertExists()
    }
}
