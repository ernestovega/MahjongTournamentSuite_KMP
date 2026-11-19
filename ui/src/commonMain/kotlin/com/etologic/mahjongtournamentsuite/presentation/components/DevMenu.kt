package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowRight
import androidx.compose.material.icons.filled.Api
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryLink
import com.etologic.mahjongtournamentsuite.presentation.telemetry.rememberOpenLink
import mahjongtournamentsuite.ui.generated.resources.Res
import mahjongtournamentsuite.ui.generated.resources.design_system
import mahjongtournamentsuite.ui.generated.resources.dev_api_docs
import mahjongtournamentsuite.ui.generated.resources.dev_architecture_docs
import mahjongtournamentsuite.ui.generated.resources.dev_menu
import mahjongtournamentsuite.ui.generated.resources.dev_privacy_policy
import mahjongtournamentsuite.ui.generated.resources.dev_staging
import mahjongtournamentsuite.ui.generated.resources.dev_versions
import mahjongtournamentsuite.ui.generated.resources.top_bar_activity
import mahjongtournamentsuite.ui.generated.resources.top_bar_app_errors
import mahjongtournamentsuite.ui.generated.resources.top_bar_screenshots
import org.jetbrains.compose.resources.stringResource

/** The gallery of the screenshot tests and the game day videos (docs/screenshot-gallery.md). */
const val SCREENSHOT_GALLERY_URL = "https://mts-staging-screenshots.web.app"

/** The Swagger UI of the backend API (docs/api). */
const val API_DOCS_URL = "https://mts.mahjongmadrid.com/api-docs/"

/** The documentation of the architecture in Markdown on GitHub (docs/arquitectura). */
const val ARCHITECTURE_DOCS_URL = "https://github.com/ernestovega/mahjongtournamentsuite_kmp/tree/main/docs/arquitectura"

/** The admin app of the staging project (docs/staging.md). */
const val STAGING_ADMIN_URL = "https://mts---staging.web.app/admin/"

/** The screens of the admin app that the DEV menu opens. The links to web pages are built in. */
class DevMenuActions(
    val onActivity: () -> Unit,
    val onAppErrors: () -> Unit,
    val onDesignSystem: () -> Unit,
)

private class DevMenuEntry(val text: String, val icon: ImageVector, val dividerAbove: Boolean, val onClick: () -> Unit)

/**
 * One "DEV" button of the top bar with the tools for development: the screens Activity, App errors and Design
 * system, and the links to the screenshot gallery, the versions, the privacy policy, the API (Swagger),
 * the documentation of the architecture (GitHub) and staging.
 * Only for administrators: the caller passes it only to them. On a phone it is one entry of the three-dots menu
 * that opens its own menu.
 */
@Composable
fun DevMenuButton(actions: DevMenuActions) {
    val openGallery = rememberOpenLink(TelemetryLink.SCREENSHOT_GALLERY)
    val openVersions = rememberOpenVersions()
    val openPrivacyPolicy = rememberOpenPrivacyPolicy()
    val openApiDocs = rememberOpenLink(TelemetryLink.API_DOCS)
    val openArchitectureDocs = rememberOpenLink(TelemetryLink.ARCHITECTURE_DOCS)
    val openStaging = rememberOpenLink(TelemetryLink.STAGING)
    val entries = listOf(
        DevMenuEntry(stringResource(Res.string.top_bar_activity), Icons.Default.History, false, actions.onActivity),
        DevMenuEntry(stringResource(Res.string.top_bar_app_errors), Icons.Default.BugReport, false, actions.onAppErrors),
        DevMenuEntry(stringResource(Res.string.design_system), Icons.Default.Palette, false, actions.onDesignSystem),
        DevMenuEntry(stringResource(Res.string.top_bar_screenshots), Icons.Default.PhotoLibrary, true, { openGallery(SCREENSHOT_GALLERY_URL) }),
        DevMenuEntry(stringResource(Res.string.dev_versions), Icons.Default.NewReleases, false, openVersions),
        DevMenuEntry(stringResource(Res.string.dev_privacy_policy), Icons.Default.Policy, false, openPrivacyPolicy),
        DevMenuEntry(stringResource(Res.string.dev_api_docs), Icons.Default.Api, false, { openApiDocs(API_DOCS_URL) }),
        DevMenuEntry(stringResource(Res.string.dev_architecture_docs), Icons.Default.Description, false, { openArchitectureDocs(ARCHITECTURE_DOCS_URL) }),
        DevMenuEntry(stringResource(Res.string.dev_staging), Icons.Default.Science, false, { openStaging(STAGING_ADMIN_URL) }),
    )
    val label = stringResource(Res.string.dev_menu)
    val outerMenu = LocalTopBarMenu.current
    var open by remember { mutableStateOf(false) }
    Box {
        if (outerMenu != null) {
            DropdownMenuItem(
                text = { Text(label) },
                leadingIcon = { Icon(Icons.Default.Code, contentDescription = null) },
                trailingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowRight, contentDescription = null) },
                enabled = LocalAppButtonsEnabled.current,
                onClick = { open = true },
            )
        } else {
            AppTopBarButton(text = label, icon = Icons.Default.Code, onClick = { open = true })
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            entries.forEach { entry ->
                if (entry.dividerAbove) HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(entry.text) },
                    leadingIcon = { Icon(entry.icon, contentDescription = null) },
                    onClick = {
                        open = false
                        outerMenu?.dismiss()
                        entry.onClick()
                    },
                )
            }
        }
    }
}
