package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.UserProfile
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ProfileDialogUiTest {
    private val player = UserProfile(uid = "u1", email = "ada@example.test", alias = "Ada Lovelace", emaId = "10001")

    @Test
    fun showsTheAliasTheEmailAndTheEmaNumberOfAPlayer() = runComposeUiTest {
        setContent {
            MtsTheme(useDarkTheme = false) {
                ProfileDialog(profile = player, onSavePhoto = { _, _ -> error("unused") }, onRemovePhoto = { error("unused") }, onSaveAlias = { error("unused") }, onSaved = {}, onDismiss = {})
            }
        }

        onNodeWithText("Profile").assertExists()
        onNodeWithText("Ada Lovelace").assertExists()
        onNodeWithText("ada@example.test").assertExists()
        onNodeWithText("10001").assertExists()
        onNodeWithText("EMA number").assertExists()
    }

    @Test
    fun anAccountWithoutAnEmaNumberShowsNoEmaField() = runComposeUiTest {
        setContent {
            MtsTheme(useDarkTheme = false) {
                ProfileDialog(
                    profile = player.copy(emaId = null),
                    onSavePhoto = { _, _ -> error("unused") }, onRemovePhoto = { error("unused") }, onSaveAlias = { error("unused") },
                    onSaved = {},
                    onDismiss = {},
                )
            }
        }

        onNodeWithText("EMA number").assertDoesNotExist()
    }

    @Test
    fun saveNeedsAChangedAliasAndSendsTheTrimmedText() = runComposeUiTest {
        val sent = mutableListOf<String>()
        var saved: UserProfile? = null
        var closed = false
        setContent {
            MtsTheme(useDarkTheme = false) {
                ProfileDialog(
                    profile = player,
                    onSavePhoto = { _, _ -> error("unused") }, onRemovePhoto = { error("unused") }, onSaveAlias = { alias ->
                        sent += alias
                        AppResult.Success(player.copy(alias = alias))
                    },
                    onSaved = { saved = it },
                    onDismiss = { closed = true },
                )
            }
        }

        onNodeWithText("Save").assertIsNotEnabled()
        onNodeWithText("Alias").performTextReplacement("   ")
        onNodeWithText("Save").assertIsNotEnabled()
        onNodeWithText("Alias").performTextReplacement("  Ada  ")
        onNodeWithText("Save").assertIsEnabled()
        onNodeWithText("Save").performClick()
        waitUntil(timeoutMillis = 5_000) { closed }

        assertEquals(listOf("Ada"), sent)
        assertEquals("Ada", saved?.alias)
        assertEquals("10001", saved?.emaId)
    }

    @Test
    fun aFailedSaveShowsTheErrorAndKeepsTheDialogOpen() = runComposeUiTest {
        var closed = false
        setContent {
            MtsTheme(useDarkTheme = false) {
                ProfileDialog(
                    profile = player,
                    onSavePhoto = { _, _ -> error("unused") }, onRemovePhoto = { error("unused") }, onSaveAlias = { AppResult.Failure(AppError.Network) },
                    onSaved = {},
                    onDismiss = { closed = true },
                )
            }
        }

        onNodeWithText("Alias").performTextReplacement("Ada")
        onNodeWithText("Save").performClick()
        waitForIdle()

        onNodeWithText("Profile").assertExists()
        assertEquals(false, closed)
        onNodeWithText("Save").assertIsEnabled()
    }

    @Test
    fun theUserCanAddAPhotoAndRemovesOnlyAnOwnPhoto() = runComposeUiTest {
        setContent {
            MtsTheme(useDarkTheme = false) {
                ProfileDialog(
                    profile = player,
                    onSavePhoto = { _, _ -> error("unused") },
                    onRemovePhoto = { error("unused") },
                    onSaveAlias = { error("unused") },
                    onSaved = {},
                    onDismiss = {},
                )
            }
        }

        onNodeWithText("Add photo").assertExists()
        onNodeWithText("Remove photo").assertDoesNotExist()
    }

    @Test
    fun aProfileWithAnOwnPhotoOffersChangeAndRemove() = runComposeUiTest {
        setContent {
            MtsTheme(useDarkTheme = false) {
                ProfileDialog(
                    profile = player.copy(photoUrl = "https://example.test/me.png", hasOwnPhoto = true),
                    onSavePhoto = { _, _ -> error("unused") },
                    onRemovePhoto = { error("unused") },
                    onSaveAlias = { error("unused") },
                    onSaved = {},
                    onDismiss = {},
                )
            }
        }

        onNodeWithText("Change photo").assertExists()
        onNodeWithText("Remove photo").assertExists()
    }
}
