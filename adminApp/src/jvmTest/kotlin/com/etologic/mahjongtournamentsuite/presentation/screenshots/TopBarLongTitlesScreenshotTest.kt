package com.etologic.mahjongtournamentsuite.presentation.screenshots

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.presentation.components.AppScaffold
import com.etologic.mahjongtournamentsuite.presentation.components.AppTopBarButton
import com.etologic.mahjongtournamentsuite.screenshots.ScreenSize
import com.etologic.mahjongtournamentsuite.screenshots.Variant
import com.etologic.mahjongtournamentsuite.screenshots.narrowInEveryLanguage
import com.etologic.mahjongtournamentsuite.screenshots.phoneInEveryLanguage
import mahjongtournamentsuite.adminapp.generated.resources.Res
import mahjongtournamentsuite.adminapp.generated.resources.players
import mahjongtournamentsuite.adminapp.generated.resources.round_control_title
import mahjongtournamentsuite.adminapp.generated.resources.statistics
import mahjongtournamentsuite.adminapp.generated.resources.tournament_players
import org.jetbrains.compose.resources.stringResource
import kotlin.test.Test

/**
 * Top bars with long names of tournaments on a phone. A long title gets smaller, then the short name of the tournament
 * takes its place, and only a text without a short one ends with "…".
 */
@OptIn(ExperimentalTestApi::class)
class TopBarLongTitlesScreenshotTest {
    @Test
    fun topBarLongTitles() = adminScreenshots(
        "components/top-bar-long-titles",
        variants = listOf(Variant(ScreenSize.Phone, dark = false), Variant(ScreenSize.Phone, dark = true)) +
            phoneInEveryLanguage + narrowInEveryLanguage.filter { it.language == "es" },
    ) {
        Column {
            // A name a little too long: it only gets smaller.
            Bar(title = "Barcelona Riichi Open 2027", shortTitle = "BRO27")
            // A very long name: its short name takes its place.
            Bar(title = "Campeonato de España de Riichi Mahjong 2027", shortTitle = "CERM 2027")
            // The tournament as the subtitle of a screen.
            Bar(
                title = stringResource(Res.string.tournament_players),
                subtitle = "Campeonato de España de Riichi Mahjong 2027",
                shortSubtitle = "CERM 2027",
            )
            // No short name: the text gets smaller and ends with "…".
            Bar(title = stringResource(Res.string.round_control_title), subtitle = "Campeonato de España de Riichi Mahjong 2027")
            Bar(title = "Campeonato de España de Riichi Mahjong 2027")
        }
    }

    @Composable
    private fun Bar(title: String, shortTitle: String? = null, subtitle: String? = null, shortSubtitle: String? = null) {
        Box(Modifier.height(96.dp)) {
            AppScaffold(
                title = title,
                shortTitle = shortTitle,
                subtitle = subtitle,
                shortSubtitle = shortSubtitle,
                titleAction = { AppTopBarButton("Edit", Icons.Default.Edit, {}) },
                onBack = {},
                actions = {
                    AppTopBarButton(stringResource(Res.string.players), Icons.Default.People, {})
                    AppTopBarButton(stringResource(Res.string.statistics), Icons.Default.QueryStats, {})
                },
                autoFocusFirst = false,
            ) {}
        }
    }
}
