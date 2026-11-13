package com.etologic.mahjongtournamentsuite.presentation.theme

import androidx.compose.ui.test.ExperimentalTestApi
import com.etologic.mahjongtournamentsuite.screenshots.screenshotsInMtsTheme
import kotlin.test.Test

/** The catalog shows every token of the design system, so a change of a color, a text style or a spacing shows here first. */
@OptIn(ExperimentalTestApi::class)
class MtsCatalogScreenshotTest {
    @Test
    fun catalog() = screenshotsInMtsTheme("catalog") { MtsCatalog() }
}
