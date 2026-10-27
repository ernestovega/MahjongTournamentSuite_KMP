package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import com.etologic.mahjongtournamentsuite.domain.model.GlobalUserRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The filters of the users screen: the search field, the tournament select field and the role buttons. */
@OptIn(ExperimentalTestApi::class)
class UserFiltersUiTest {

    @Test
    fun theRoleButtonsAreInTheOrderAdminEditorPlayer() = runComposeUiTest {
        setContent { UserFilters("", {}, emptySet(), {}, emptyList(), null, {}) }
        val left = listOf("Admin", "Editor", "Player").map { onNodeWithText(it).getBoundsInRoot().left }
        assertEquals(left.sorted(), left)
        assertEquals(3, left.distinct().size)
        assertEquals(listOf(GlobalUserRole.ADMIN, GlobalUserRole.EDITOR, GlobalUserRole.PLAYER), RoleFilterOrder)
    }

    @Test
    fun everyRoleHasOneButtonAndEachButtonTogglesItsRole() = runComposeUiTest {
        val toggled = mutableListOf<GlobalUserRole>()
        setContent { UserFilters("", {}, emptySet(), { toggled += it }, emptyList(), null, {}) }
        onNodeWithText("Player").performClick()
        onNodeWithText("Admin").performClick()
        onNodeWithText("Editor").performClick()
        assertEquals(listOf(GlobalUserRole.PLAYER, GlobalUserRole.ADMIN, GlobalUserRole.EDITOR), toggled)
        assertEquals(GlobalUserRole.entries.toSet(), RoleFilterOrder.toSet())
    }

    @Test
    fun theTournamentSelectFieldEndsAtTheSameHeightAsTheSearchField() = runComposeUiTest {
        setContent { Column(Modifier.width(800.dp)) { UserFilters("", {}, emptySet(), {}, emptyList(), null, {}) } }
        val search = onNode(hasSetTextAction()).getBoundsInRoot()
        val select = onNode(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.DropdownList)).getBoundsInRoot()
        // The node of a text field includes the 8 dp above its border. The node of the select field does not.
        assertEquals(search.bottom, select.bottom)
        assertEquals(search.height, select.height + 8.dp)
        assertTrue(select.left > search.left)
    }
}
