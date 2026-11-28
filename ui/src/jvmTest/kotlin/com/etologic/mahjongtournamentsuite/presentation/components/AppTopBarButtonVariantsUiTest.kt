package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/** A filled button in the menu of the top bar keeps its fill behind the icon, and an icon-only button can draw its own icon. */
@OptIn(ExperimentalTestApi::class)
class AppTopBarButtonVariantsUiTest {
    @Test
    fun aFilledMenuItemAndAnIconOnlyButtonWithItsOwnIconWork() = runComposeUiTest {
        val clicks = mutableListOf<String>()
        var dismissed = 0
        setContent {
            MtsTheme(useDarkTheme = false) {
                Column {
                    CompositionLocalProvider(LocalTopBarMenu provides TopBarMenuScope { dismissed++ }) {
                        AppTopBarButton(text = "Admin", icon = Icons.Default.Star, onClick = { clicks += "admin" }, containerColor = Color.Magenta)
                    }
                    AppTopBarButton(text = "Own", icon = Icons.Default.Star, onClick = { clicks += "own" }, showLabel = false, iconContent = { Text("★") })
                }
            }
        }
        onNodeWithText("Admin").performClick()
        onNodeWithText("★").performClick()
        waitForIdle()
        assertEquals(listOf("admin", "own"), clicks)
        assertEquals(1, dismissed)
    }
}
