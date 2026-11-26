package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.etologic.mahjongtournamentsuite.domain.model.PlayerSubstitution
import com.etologic.mahjongtournamentsuite.domain.model.SubstitutionReason
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Small pieces of the screens: the info notice, the update dialog, the substitution texts, a compact tab and the app logo. */
@OptIn(ExperimentalTestApi::class)
class SmallPiecesUiTest {
    private val original = Locale.getDefault()

    @BeforeTest
    fun english() = Locale.setDefault(Locale.ENGLISH)

    @AfterTest
    fun restore() = Locale.setDefault(original)

    @Test
    fun theInfoNoticeShowsItsText() = runDesktopComposeUiTest {
        setContent { MtsTheme(useDarkTheme = false) { InfoNotice("The draw needs 16 players") } }
        onNodeWithText("The draw needs 16 players").assertIsDisplayed()
    }

    @Test
    fun theUpdateDialogDownloadsOrWaits() = runDesktopComposeUiTest {
        val clicks = mutableListOf<String>()
        setContent { MtsTheme(useDarkTheme = false) { UpdateAvailableDialog("1.0.500", onDownload = { clicks += "download" }, onDismiss = { clicks += "later" }) } }
        onNodeWithText("New version").assertIsDisplayed()
        onNodeWithText("Version 1.0.500", substring = true).assertIsDisplayed()
        onNodeWithText("DOWNLOAD", ignoreCase = true).performClick()
        onNodeWithText("LATER", ignoreCase = true).performClick()
        assertEquals(listOf("download", "later"), clicks)
    }

    @Test
    fun eachSubstitutionHasItsText() = runDesktopComposeUiTest {
        val substitutions = listOf(
            PlayerSubstitution(SubstitutionReason.LATE, fromRound = 2),
            PlayerSubstitution(SubstitutionReason.ILLNESS, fromRound = 3),
            PlayerSubstitution(SubstitutionReason.ILLNESS, fromRound = 4, excludedFromEma = true),
            PlayerSubstitution(SubstitutionReason.DISQUALIFIED, fromRound = 5),
        )
        val remembered = mutableListOf<String>()
        setContent {
            MtsTheme(useDarkTheme = false) {
                val label = rememberSubstitutionLabel()
                substitutions.forEach { substitution ->
                    Text(substitution.label())
                    remembered += label(substitution)
                }
            }
        }
        val expected = listOf(
            "substituted from round 2",
            "ill, substituted from round 3",
            "ill, substituted from round 4, excluded from the EMA ranking",
            "disqualified from round 5",
        )
        expected.forEach { onNodeWithText(it).assertIsDisplayed() }
        waitForIdle()
        assertEquals(expected, remembered.takeLast(4))
    }

    @Test
    fun aCompactTabShowsItsTitleAndAnswersAClick() = runDesktopComposeUiTest {
        var clicks = 0
        setContent {
            MtsTheme(useDarkTheme = false) {
                CompactTab(selected = true, onClick = { clicks++ }, title = "RANKING", trackingName = "ranking")
                CompactTab(selected = false, onClick = { clicks++ }, title = "STATS", trackingName = "stats")
            }
        }
        onNodeWithText("STATS").performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun theLogoFitsTheThemeAndTheBackgroundAndTheLoadingShowIt() = runDesktopComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalAppLogo provides ColorPainter(Color.Green), LocalAppLogoOnDark provides ColorPainter(Color.White)) {
                MtsTheme(useDarkTheme = false) {
                    AppBackground(showLogo = true) { AppLoading() }
                }
            }
        }
        onNodeWithContentDescription("Mahjong Tournament Suite logo").assertIsDisplayed()
    }

    @Test
    fun withoutALogoNothingIsDrawn() = runDesktopComposeUiTest {
        setContent { MtsTheme(useDarkTheme = true) { AppBackground { AppLogo() } } }
        onAllNodesWithContentDescription("Mahjong Tournament Suite logo").assertCountEquals(0)
    }

    @Test
    fun theDarkThemeUsesTheWhiteLogo() = runDesktopComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalAppLogoOnDark provides ColorPainter(Color.White)) {
                MtsTheme(useDarkTheme = true) { AppLogo() }
            }
        }
        onNodeWithContentDescription("Mahjong Tournament Suite logo").assertIsDisplayed()
    }
}
