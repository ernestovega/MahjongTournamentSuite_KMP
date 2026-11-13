package com.etologic.mahjongtournamentsuite.presentation.screenshots

import androidx.compose.ui.test.ExperimentalTestApi
import com.etologic.mahjongtournamentsuite.presentation.screen.DesignSystemScreen
import kotlin.test.Test

/** The catalog of the design system inside the admin app, as administrators open it from their profile. */
@OptIn(ExperimentalTestApi::class)
class DesignSystemScreenshotTest {
    @Test
    fun designSystem() = adminScreenshots("design-system") { nav -> DesignSystemScreen(nav) }
}
