package com.etologic.mahjongtournamentsuite.player

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** The web offers the apps: a button that opens their page, and a notice that closes for this visit or for good. */
@OptIn(ExperimentalTestApi::class)
class WebAppsUiTest {
    private val original = Locale.getDefault()

    @BeforeTest
    fun english() = Locale.setDefault(Locale.ENGLISH)

    @AfterTest
    fun restore() = Locale.setDefault(original)

    @Test
    fun theButtonOpensThePageOfTheAppsOnlyOnTheWeb() = runComposeUiTest {
        var web: WebApps? by mutableStateOf(null)
        setContent {
            MtsTheme(useDarkTheme = false) {
                CompositionLocalProvider(LocalWebApps provides web) {
                    val nav = rememberNavController()
                    NavHost(nav, startDestination = "home") {
                        composable("home") { AppsTopBarButton(nav) }
                        composable<AppsRoute> { Text("Apps page") }
                    }
                }
            }
        }
        onAllNodesWithText("Get the app").assertCountEquals(0)
        web = WebApps(AppPlatform.ANDROID, { false }, {})
        waitForIdle()
        onNodeWithText("Get the app").performClick()
        waitForIdle()
        onNodeWithText("Apps page").assertExists()
    }

    @Test
    fun theNoticeClosesForThisVisitOrForGood() = runComposeUiTest {
        var hidden = 0
        val web = WebApps(AppPlatform.ANDROID, { false }, { hidden++ })
        setContent {
            MtsTheme(useDarkTheme = false) {
                CompositionLocalProvider(LocalWebApps provides web) { AppsNotice() }
            }
        }
        onNodeWithText("MTS apps").assertExists()
        onNodeWithText("Don't show again").performClick()
        waitForIdle()
        onAllNodesWithText("MTS apps").assertCountEquals(0)
        assertEquals(1, hidden)
    }

    @Test
    fun aHiddenNoticeAndTheAppsShowNoNotice() = runComposeUiTest {
        var web: WebApps? by mutableStateOf(WebApps(AppPlatform.IOS, { true }, {}))
        setContent {
            MtsTheme(useDarkTheme = false) {
                CompositionLocalProvider(LocalWebApps provides web) { AppsNotice() }
            }
        }
        onAllNodesWithText("MTS apps").assertCountEquals(0)
        web = null
        waitForIdle()
        onAllNodesWithText("MTS apps").assertCountEquals(0)
    }
}
