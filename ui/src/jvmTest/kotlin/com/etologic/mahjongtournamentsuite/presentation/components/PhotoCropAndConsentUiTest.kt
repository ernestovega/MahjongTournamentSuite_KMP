package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.swipe
import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.UserProfile
import com.etologic.mahjongtournamentsuite.presentation.platform.SelectedImage
import com.etologic.mahjongtournamentsuite.presentation.telemetry.AnalyticsConsent
import com.etologic.mahjongtournamentsuite.presentation.telemetry.CONSENT_DIALOG_TAG
import com.etologic.mahjongtournamentsuite.presentation.telemetry.LocalAnalyticsConsent
import com.etologic.mahjongtournamentsuite.presentation.theme.LocalThemeController
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import com.etologic.mahjongtournamentsuite.presentation.theme.ThemeController
import com.etologic.mahjongtournamentsuite.presentation.theme.ThemePreference
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

/** The crop dialog of a new photo, removing an own photo, and the analytics switch of the profile. */
@OptIn(ExperimentalTestApi::class)
class PhotoCropAndConsentUiTest {
    private val player = UserProfile(uid = "u1", email = "ada@example.test", alias = "Ada Lovelace", emaId = "10001")

    private fun png(width: Int, height: Int): SelectedImage {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        image.createGraphics().apply {
            color = Color.GREEN
            fillRect(0, 0, width, height)
            dispose()
        }
        val bytes = ByteArrayOutputStream().use {
            ImageIO.write(image, "png", it)
            it.toByteArray()
        }
        return SelectedImage("me.png", "image/png", bytes)
    }

    @Test
    fun theCropDialogGivesASquareOfTheLoadedImage() = runComposeUiTest {
        var cropped: SelectedImage? = null
        var dismissed = false
        setContent {
            MtsTheme(useDarkTheme = false) {
                ImageCropDialog(
                    image = png(300, 200),
                    title = "Crop photo",
                    hint = "Drag the image",
                    onDismiss = { dismissed = true },
                    onCropped = { cropped = it },
                    onError = { error("unexpected $it") },
                )
            }
        }

        onNodeWithContentDescription("Zoom").assertExists()
        onNodeWithContentDescription("Left / right").assertExists()
        onNodeWithContentDescription("Up / down").assertExists()
        // The button waits until the preview knows the size of the image.
        waitUntil(timeoutMillis = 10_000) {
            onAllNodes(hasText("Use crop") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithText("Use crop").assertIsEnabled().performClick()
        waitUntil(timeoutMillis = 5_000) { cropped != null }

        val image = ImageIO.read(ByteArrayInputStream(assertNotNull(cropped).bytes))
        assertEquals(200, image.width)
        assertEquals(200, image.height)
        assertEquals(false, dismissed)
    }

    @Test
    fun aDragOnThePreviewMovesTheCrop() = runComposeUiTest {
        var cropped: SelectedImage? = null
        setContent {
            MtsTheme(useDarkTheme = false) {
                ImageCropDialog(png(300, 200), "Crop photo", "Drag the image", onDismiss = {}, onCropped = { cropped = it }, onError = { error("unexpected $it") })
            }
        }
        waitUntil(timeoutMillis = 10_000) {
            onAllNodes(hasText("Use crop") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        // A wide image moves left and right only: up and down has nothing to show.
        onNodeWithTag(IMAGE_CROP_PREVIEW_TAG).performTouchInput { swipe(center, center + Offset(-80f, -80f)) }
        waitForIdle()
        val position = onNodeWithContentDescription("Left / right").fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current
        assertTrue(position > 0f, "the image moved by $position")

        onNodeWithText("Use crop").performClick()
        waitUntil(timeoutMillis = 5_000) { cropped != null }
    }

    @Test
    fun cancelClosesTheCropDialog() = runComposeUiTest {
        var dismissed = false
        setContent {
            MtsTheme(useDarkTheme = false) {
                ImageCropDialog(png(10, 10), "Crop photo", "Drag the image", onDismiss = { dismissed = true }, onCropped = {}, onError = {})
            }
        }

        onNodeWithText("Cancel").performClick()

        assertEquals(true, dismissed)
    }

    @Test
    fun removingAnOwnPhotoShowsTheEmaPhotoAgain() = runComposeUiTest {
        var removes = 0
        var saved: UserProfile? = null
        setContent {
            ProfileTheme {
                ProfileDialog(
                    profile = player.copy(photoUrl = "https://example.test/me.png", hasOwnPhoto = true),
                    onSavePhoto = { _, _ -> error("unused") },
                    onRemovePhoto = {
                        removes++
                        AppResult.Success(player.copy(photoUrl = "https://example.test/ema.png", hasOwnPhoto = false))
                    },
                    onSaveAlias = { error("unused") },
                    onSaved = { saved = it },
                    onDismiss = {},
                )
            }
        }

        onNodeWithContentDescription("Edit photo").performClick()
        onNodeWithText("Remove photo").performClick()
        waitUntil(timeoutMillis = 5_000) { saved != null }

        assertEquals(1, removes)
        assertEquals(false, saved?.hasOwnPhoto)
        // Now it is the EMA photo: the pencil opens the picker and the info says where the photo comes from.
        onNodeWithContentDescription("This is your EMA player photo.\nUpload a new one if you want.").assertExists()
    }

    @Test
    fun aFailedRemoveShowsTheErrorAndKeepsTheOwnPhoto() = runComposeUiTest {
        setContent {
            ProfileTheme {
                ProfileDialog(
                    profile = player.copy(photoUrl = "https://example.test/me.png", hasOwnPhoto = true),
                    onSavePhoto = { _, _ -> error("unused") },
                    onRemovePhoto = { AppResult.Failure(AppError.Network) },
                    onSaveAlias = { error("unused") },
                    onSaved = { error("nothing was saved") },
                    onDismiss = {},
                )
            }
        }

        onNodeWithContentDescription("Edit photo").performClick()
        onNodeWithText("Remove photo").performClick()
        waitForIdle()

        onNodeWithContentDescription("This is your custom photo.\nYou can remove it to restore your EMA photo.").assertExists()
    }

    @Test
    fun theProfileShowsTheConsentScreenAgainToDeclineOrAccept() = runComposeUiTest {
        val answers = mutableListOf<Boolean>()
        var enabled by mutableStateOf(true)
        // The buttons ignore a second press within 500 ms: the test moves this clock before each press.
        val clock = TestTimeSource()
        setContent {
            ProfileTheme {
                CompositionLocalProvider(
                    LocalAnalyticsConsent provides AnalyticsConsent(enabled) { answers += it; enabled = it },
                    LocalPressTimeSource provides clock,
                ) {
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
        }

        // An account action under the line, not a switch in the form (Ernesto, 2026-11-27).
        onNodeWithTag(PROFILE_ANALYTICS_TAG).performScrollTo().assertHasClickAction().performClick()
        onNodeWithTag(CONSENT_DIALOG_TAG).assertExists()
        onNodeWithText("Will you help us improve the app?", substring = true).assertExists()
        clock += 1.seconds
        onNodeWithText("DECLINE", ignoreCase = true).performClick()
        waitForIdle()
        onNodeWithTag(CONSENT_DIALOG_TAG).assertDoesNotExist()
        assertEquals(listOf(false), answers)

        // Accepting again works the same way. The profile stays open: the app does not close or ask again.
        clock += 1.seconds
        onNodeWithTag(PROFILE_ANALYTICS_TAG).performScrollTo().performClick()
        clock += 1.seconds
        onNodeWithText("ACCEPT", ignoreCase = true).performClick()
        waitForIdle()
        onNodeWithTag(CONSENT_DIALOG_TAG).assertDoesNotExist()
        assertEquals(listOf(false, true), answers)
        onNodeWithText("Profile").assertExists()
    }

    @Test
    fun anAppThatDoesNotAskHasNoAnalyticsButton() = runComposeUiTest {
        setContent {
            ProfileTheme {
                ProfileDialog(player, onSaveAlias = { error("unused") }, onSavePhoto = { _, _ -> error("unused") }, onRemovePhoto = { error("unused") }, onSaved = {}, onDismiss = {})
            }
        }

        onNodeWithText("Profile").assertExists()
        onNodeWithText("Anonymous analytics", ignoreCase = true).assertDoesNotExist()
        onNodeWithTag(PROFILE_ANALYTICS_TAG).assertDoesNotExist()
    }
}

@Composable
private fun ProfileTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalThemeController provides ThemeController(ThemePreference.Light, false, {})) {
        MtsTheme(useDarkTheme = false) { content() }
    }
}
