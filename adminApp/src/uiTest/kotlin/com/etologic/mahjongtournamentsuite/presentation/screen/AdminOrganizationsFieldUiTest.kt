package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.runComposeUiTest
import com.etologic.mahjongtournamentsuite.domain.model.Organization
import com.etologic.mahjongtournamentsuite.presentation.theme.LocalThemeController
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import com.etologic.mahjongtournamentsuite.presentation.theme.ThemeController
import com.etologic.mahjongtournamentsuite.presentation.theme.ThemePreference
import kotlin.test.Test
import kotlin.test.assertEquals

/** The organizations of a club admin: the ones of another admin have no remove, and an account needs at least one. */
@OptIn(ExperimentalTestApi::class)
class AdminOrganizationsFieldUiTest {
    private val madrid = Organization("org-es", "Madrid Club")
    private val paris = Organization("org-fr", "Paris Club")

    private fun ComposeUiTest.show(organizations: List<Organization>, selected: Set<String>, editable: Set<String>?) {
        setContent {
            CompositionLocalProvider(LocalThemeController provides ThemeController(ThemePreference.Light, false, {})) {
                MtsTheme(useDarkTheme = false) {
                    AdminOrganizationsField(organizations = organizations, selected = selected, editable = editable, enabled = true, onChange = {})
                }
            }
        }
        waitForIdle()
    }

    private fun ComposeUiTest.count(text: String) = onAllNodesWithText(text, substring = true).fetchSemanticsNodes().size

    @Test
    fun anOrganizationOfAnotherAdminHasNoRemove() = runComposeUiTest {
        show(listOf(madrid, paris), selected = setOf("org-es", "org-fr"), editable = setOf("org-es"))

        assertEquals(1, onAllNodes(hasContentDescription("Remove Madrid Club"), useUnmergedTree = true).fetchSemanticsNodes().size)
        assertEquals(0, onAllNodes(hasContentDescription("Remove Paris Club"), useUnmergedTree = true).fetchSemanticsNodes().size)
        assertEquals(1, count("The organizations without ✕ are not yours."))
    }

    @Test
    fun withoutOrganizationsToAddTheAccountStillNeedsOne() = runComposeUiTest {
        show(listOf(paris), selected = emptySet(), editable = setOf("org-es"))

        assertEquals(1, count("A Club Admin needs at least one organization."))
    }
}
