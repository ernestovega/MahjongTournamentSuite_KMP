package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.domain.model.UserProfile
import com.etologic.mahjongtournamentsuite.presentation.telemetry.LocalWithdrawConsent
import com.etologic.mahjongtournamentsuite.presentation.theme.LocalThemeController
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import com.etologic.mahjongtournamentsuite.presentation.theme.ThemeController
import com.etologic.mahjongtournamentsuite.presentation.theme.ThemePreference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What TalkBack and a finger need: every control that can be tapped says what it is (a text or a content description),
 * and its touch area is at least 48 x 48 dp. The checks walk the whole semantics tree of a screen.
 */
@OptIn(ExperimentalTestApi::class)
class AccessibilityChecksUiTest {
    private fun SemanticsNode.walk(): List<SemanticsNode> = listOf(this) + children.flatMap { it.walk() }

    private fun SemanticsNode.label(): String = listOfNotNull(
        config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(),
        config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text },
        config.getOrNull(SemanticsProperties.EditableText)?.text,
    ).joinToString(" ").trim()

    private fun ComposeUiTest.clickables(): List<SemanticsNode> = onAllNodes(isRoot()).fetchSemanticsNodes().flatMap { it.walk() }
        .filter { SemanticsActions.OnClick in it.config && it.config.getOrNull(SemanticsProperties.Disabled) == null }

    /** The controls without a name, as "#id at bounds". */
    private fun ComposeUiTest.unnamed(): List<String> = clickables()
        .filter { node -> node.label().isEmpty() && node.children.none { it.label().isNotEmpty() } }
        .map { "#${it.id} at ${it.boundsInRoot}" }

    /** The controls (dialogs and menus are roots of their own) smaller than [min], as "label: width x height". */
    private fun ComposeUiTest.small(min: Dp = 48.dp): List<String> {
        val density = density.density
        return clickables()
            .filter { it.touchBoundsInRoot.width / density < min.value - 0.5f || it.touchBoundsInRoot.height / density < min.value - 0.5f }
            .map { "${it.label().ifEmpty { "#${it.id}" }}: ${(it.touchBoundsInRoot.width / density).toInt()} x ${(it.touchBoundsInRoot.height / density).toInt()} dp" }
    }

    @Composable
    private fun Themed(content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalThemeController provides ThemeController(ThemePreference.Light, false, {})) {
            MtsTheme(useDarkTheme = false) { content() }
        }
    }

    @Test
    fun everyControlOfTheProfileHasANameAndAFingerSize() = runComposeUiTest {
        setContent {
            Themed {
                CompositionLocalProvider(LocalWithdrawConsent provides {}) {
                    ProfileDialog(
                        profile = UserProfile(uid = "u1", email = "ada@example.test", alias = "Ada", emaId = "10001", photoUrl = "https://example.test/a.png", hasOwnPhoto = true),
                        onSaveAlias = { error("unused") },
                        onSavePhoto = { _, _ -> error("unused") },
                        onRemovePhoto = { error("unused") },
                        onSaved = {},
                        onDismiss = {},
                        onSignOut = {},
                        onDeleteAccount = { error("unused") },
                    )
                }
            }
        }
        waitForIdle()

        assertTrue(clickables().size >= 5, "the checks found the controls: ${clickables().map { it.label() }}")
        assertEquals(emptyList(), unnamed())
        assertEquals(emptyList(), small())
    }

    @Test
    fun theMenuOfThePhotoHasNamedItems() = runComposeUiTest {
        setContent {
            Themed {
                PhotoEditor(photoUrl = "https://example.test/a.png", hasOwnPhoto = true, name = "Ada", onUpload = { _, _ -> null }, onRemove = { null }, editBadge = true)
            }
        }
        onNodeWithContentDescription("Edit photo").performClick()
        waitForIdle()

        assertTrue(clickables().any { it.label() == "Remove photo" }, "the menu is open")
        assertEquals(emptyList(), unnamed())
        assertEquals(emptyList(), small())
    }
}
