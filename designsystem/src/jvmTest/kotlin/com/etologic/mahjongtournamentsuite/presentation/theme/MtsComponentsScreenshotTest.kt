package com.etologic.mahjongtournamentsuite.presentation.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import com.etologic.mahjongtournamentsuite.presentation.components.MtsOutlinedTag
import com.etologic.mahjongtournamentsuite.presentation.components.MtsScrollableTabRow
import com.etologic.mahjongtournamentsuite.presentation.components.MtsTab
import com.etologic.mahjongtournamentsuite.presentation.components.MtsTabRow
import com.etologic.mahjongtournamentsuite.presentation.components.MtsTintedChip
import com.etologic.mahjongtournamentsuite.presentation.components.signedColor
import com.etologic.mahjongtournamentsuite.presentation.components.signedText
import com.etologic.mahjongtournamentsuite.screenshots.componentVariants
import com.etologic.mahjongtournamentsuite.screenshots.screenshotsInMtsTheme
import kotlin.test.Test

/** The components of the design system module, each one in its states. */
@OptIn(ExperimentalTestApi::class)
class MtsComponentsScreenshotTest {
    private fun gallery(name: String, content: @Composable () -> Unit) = screenshotsInMtsTheme("components/$name", componentVariants) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(MtsTheme.layout.screenGutter), verticalArrangement = Arrangement.spacedBy(MtsSpacing.lg)) { content() }
        }
    }

    @Test
    fun tabs() = gallery("tabs") {
        MtsTabRow(selectedTabIndex = 0) {
            MtsTab(selected = true, onClick = {}, title = "Ranking", icon = { Icon(Icons.Default.Star, null) })
            MtsTab(selected = false, onClick = {}, title = "Players", icon = { Icon(Icons.Default.Person, null) })
        }
        MtsTabRow(selectedTabIndex = 1) {
            MtsTab(selected = false, onClick = {}, title = "Info")
            MtsTab(selected = true, onClick = {}, title = "Tables")
            MtsTab(selected = false, onClick = {}, title = "Teams")
        }
        MtsScrollableTabRow(selectedTabIndex = 2) {
            listOf("Info", "Ranking", "Tables", "Players", "Teams", "Statistics").forEachIndexed { index, title ->
                MtsTab(selected = index == 2, onClick = {}, title = title)
            }
        }
    }

    @Test
    fun tags() = gallery("tags") {
        val colors = MtsTheme.colors
        Row(horizontalArrangement = Arrangement.spacedBy(MtsSpacing.sm)) {
            MtsOutlinedTag(colors.info) { Text("MOM26", color = colors.info) }
            MtsOutlinedTag(colors.warning) { Text("Private", color = colors.warning) }
            MtsOutlinedTag(MaterialTheme.colorScheme.error) { Text("Disabled", color = MaterialTheme.colorScheme.error) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(MtsSpacing.sm)) {
            MtsTintedChip(Icons.Default.Star, "Best hand", colors.medalGold)
            MtsTintedChip(Icons.Default.Person, "16 players", colors.info)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(MtsSpacing.sm)) {
            MtsTintedChip(Icons.Default.Star, "Small", colors.gain, small = true)
            MtsTintedChip(Icons.Default.Person, "Small", colors.changed, small = true)
        }
    }

    @Test
    fun signedValues() = gallery("signed-values") {
        Row(horizontalArrangement = Arrangement.spacedBy(MtsSpacing.lg)) {
            listOf(1250, 0, -875).forEach { value ->
                Text(signedText(value), color = signedColor(value), style = MtsTheme.typography.scoreLarge)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(MtsSpacing.lg)) {
            listOf(2.5, 0.0, -1.75).forEach { value -> Text(signedText(value), color = signedColor(value)) }
        }
    }
}
